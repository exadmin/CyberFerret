package main

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// The cache file keeps the name the README documents and cfcli prints. Renaming it is a
// user-visible change rather than a refactor, so update the documentation and this test together.
func TestDictionaryCacheFilename(t *testing.T) {
	if cacheFileName != "sensitive-signatures.encrypted" {
		t.Fatalf("cacheFileName = %q, want sensitive-signatures.encrypted", cacheFileName)
	}
}

func TestCacheRefresherKeepsFreshCache(t *testing.T) {
	home := t.TempDir()
	now := time.Date(2026, 7, 21, 12, 0, 0, 0, time.UTC)
	cache := writeCacheFile(t, home, "fresh")
	if err := os.Chtimes(cache, now.Add(-8*time.Hour), now.Add(-8*time.Hour)); err != nil {
		t.Fatal(err)
	}
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		requests.Add(1)
		_, _ = w.Write([]byte("new"))
	}))
	defer server.Close()
	refresher := testCacheRefresher(home, now, server.URL, time.Second)
	var messages bytes.Buffer

	result, err := refresher.refresh(context.Background(), newLineOutput(&messages))

	if err != nil {
		t.Fatal(err)
	}
	if result.path != cache || result.home != home || result.state != cacheCurrent || requests.Load() != 0 {
		t.Fatalf("refresh() = %#v, requests = %d; want path %q, home %q, state %v, and 0 requests",
			result, requests.Load(), cache, home, cacheCurrent)
	}
}

func TestCacheRefresherReplacesStaleCache(t *testing.T) {
	home := t.TempDir()
	now := time.Date(2026, 7, 21, 12, 0, 0, 0, time.UTC)
	cache := writeCacheFile(t, home, "stale")
	if err := os.Chtimes(cache, now.Add(-8*time.Hour-time.Second), now.Add(-8*time.Hour-time.Second)); err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("updated"))
	}))
	defer server.Close()
	refresher := testCacheRefresher(home, now, server.URL, time.Second)

	result, err := refresher.refresh(context.Background(), newLineOutput(&bytes.Buffer{}))

	if err != nil {
		t.Fatal(err)
	}
	content, err := os.ReadFile(result.path)
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "updated" {
		t.Fatalf("cache = %q, want updated", content)
	}
	if result.home != home || result.state != cacheUpdated {
		t.Fatalf("refresh() = %#v, want home %q and state %v", result, home, cacheUpdated)
	}
	assertNoTemporaryCacheFiles(t, filepath.Dir(cache))
}

func TestCacheRefresherCreatesMissingCache(t *testing.T) {
	home := t.TempDir()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte("downloaded"))
	}))
	defer server.Close()
	refresher := testCacheRefresher(home, time.Now(), server.URL, time.Second)

	result, err := refresher.refresh(context.Background(), newLineOutput(&bytes.Buffer{}))

	if err != nil {
		t.Fatal(err)
	}
	if want := filepath.Join(home, ".qubership", cacheFileName); result.path != want || result.state != cacheUpdated {
		t.Fatalf("refresh() = %#v, want path %q and state %v", result, want, cacheUpdated)
	}
}

func TestCacheRefresherFallsBackToStaleCache(t *testing.T) {
	home := t.TempDir()
	cache := writeCacheFile(t, home, "stale")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "failure", http.StatusInternalServerError)
	}))
	defer server.Close()
	refresher := testCacheRefresher(home, time.Now().Add(24*time.Hour), server.URL, time.Second)
	var messages bytes.Buffer

	result, err := refresher.refresh(context.Background(), newLineOutput(&messages))

	if err != nil {
		t.Fatal(err)
	}
	if result.path != cache || result.state != cacheFallback ||
		!strings.Contains(messages.String(), "TEXT: Dictionary refresh failed") {
		t.Fatalf("refresh() = %#v, messages = %q", result, messages.String())
	}
}

func TestCacheRefresherFailsWithoutCache(t *testing.T) {
	home := t.TempDir()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "failure", http.StatusInternalServerError)
	}))
	defer server.Close()
	refresher := testCacheRefresher(home, time.Now(), server.URL, time.Second)

	_, err := refresher.refresh(context.Background(), newLineOutput(&bytes.Buffer{}))

	if err == nil {
		t.Fatal("refresh() error = nil, want error")
	}
}

// A download that outlives the refresh timeout never touches the cache afterwards, and leaves no
// temporary file behind. The server here blocks until the request context is cancelled, so a
// failure means the download goroutine still wrote to the cache directory once refresh had already
// returned the stale file.
func TestCacheRefresherTimeoutDoesNotReplaceStaleCacheLater(t *testing.T) {
	home := t.TempDir()
	cache := writeCacheFile(t, home, "stale")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		<-request.Context().Done()
	}))
	defer server.Close()
	refresher := testCacheRefresher(home, time.Now().Add(24*time.Hour), server.URL, 20*time.Millisecond)
	var messages bytes.Buffer

	result, err := refresher.refresh(context.Background(), newLineOutput(&messages))

	if err != nil {
		t.Fatal(err)
	}
	time.Sleep(30 * time.Millisecond)
	content, readErr := os.ReadFile(cache)
	if readErr != nil {
		t.Fatal(readErr)
	}
	if result.path != cache || result.state != cacheFallback || string(content) != "stale" ||
		!strings.Contains(messages.String(), "timed out") {
		t.Fatalf("result = %#v, cache = %q, messages = %q", result, content, messages.String())
	}
	assertNoTemporaryCacheFiles(t, filepath.Dir(cache))
}

func testCacheRefresher(home string, now time.Time, url string, timeout time.Duration) cacheRefresher {
	return cacheRefresher{
		client:  http.DefaultClient,
		now:     func() time.Time { return now },
		homeDir: func() (string, error) { return home, nil },
		url:     url,
		timeout: timeout,
	}
}

func writeCacheFile(t *testing.T, home, content string) string {
	t.Helper()
	directory := filepath.Join(home, ".qubership")
	if err := os.MkdirAll(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(directory, cacheFileName)
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func assertNoTemporaryCacheFiles(t *testing.T, directory string) {
	t.Helper()
	matches, err := filepath.Glob(filepath.Join(directory, cacheFileName+"-*.tmp"))
	if err != nil {
		t.Fatal(err)
	}
	if len(matches) != 0 {
		t.Fatalf("temporary cache files remain: %v", matches)
	}
}

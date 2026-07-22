package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"
)

const (
	dictionaryURL  = "https://raw.githubusercontent.com/exadmin/CyberFerretDictionary/main/dictionary-latest.encrypted"
	cacheFileName  = "sensitive-signatures.encrypted"
	cacheMaxAge    = 8 * time.Hour
	refreshTimeout = 15 * time.Second
	maxCacheSize   = 16 * 1024 * 1024
)

type cacheRefresher struct {
	client  *http.Client
	now     func() time.Time
	homeDir func() (string, error)
	url     string
	timeout time.Duration
}

type cacheState int

const (
	cacheCurrent cacheState = iota
	cacheUpdated
	cacheFallback
)

type cacheResult struct {
	path  string
	home  string
	state cacheState
}

func (r cacheRefresher) refresh(ctx context.Context, output *lineOutput) (cacheResult, error) {
	home, err := r.homeDir()
	if err != nil {
		return cacheResult{}, fmt.Errorf("resolve home directory: %w", err)
	}
	cachePath := filepath.Join(home, ".qubership", cacheFileName)
	info, statErr := os.Stat(cachePath)
	cacheExists := statErr == nil && info.Mode().IsRegular()
	if statErr != nil && !os.IsNotExist(statErr) {
		return cacheResult{}, fmt.Errorf("inspect dictionary cache %q: %w", cachePath, statErr)
	}
	if cacheExists && r.now().Sub(info.ModTime()) <= cacheMaxAge {
		return cacheResult{path: cachePath, home: home, state: cacheCurrent}, nil
	}

	timeout := r.timeout
	if timeout <= 0 {
		timeout = refreshTimeout
	}
	refreshContext, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	result := make(chan error, 1)
	go func() {
		result <- r.download(refreshContext, cachePath)
	}()

	var refreshErr error
	select {
	case refreshErr = <-result:
	case <-refreshContext.Done():
		refreshErr = fmt.Errorf("timed out after %s", timeout)
	}
	if refreshErr == nil {
		return cacheResult{path: cachePath, home: home, state: cacheUpdated}, nil
	}
	if err := output.text("Dictionary refresh failed: %v", refreshErr); err != nil {
		return cacheResult{}, fmt.Errorf("write refresh warning: %w", err)
	}
	if cacheExists {
		return cacheResult{path: cachePath, home: home, state: cacheFallback}, nil
	}
	return cacheResult{}, fmt.Errorf("dictionary cache is unavailable after refresh: %w", refreshErr)
}

func (r cacheRefresher) download(ctx context.Context, destination string) error {
	url := r.url
	if url == "" {
		url = dictionaryURL
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("create dictionary request: %w", err)
	}
	response, err := r.client.Do(request)
	if err != nil {
		return fmt.Errorf("download dictionary: %w", err)
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode > 299 {
		return fmt.Errorf("download dictionary: HTTP status %s", response.Status)
	}

	directory := filepath.Dir(destination)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		return fmt.Errorf("create dictionary cache directory: %w", err)
	}
	temporary, err := os.CreateTemp(directory, cacheFileName+"-*.tmp")
	if err != nil {
		return fmt.Errorf("create temporary dictionary cache: %w", err)
	}
	temporaryPath := temporary.Name()
	defer os.Remove(temporaryPath)
	if err := temporary.Chmod(0o600); err != nil {
		temporary.Close()
		return fmt.Errorf("protect temporary dictionary cache: %w", err)
	}
	written, copyErr := io.Copy(temporary, io.LimitReader(response.Body, maxCacheSize+1))
	closeErr := temporary.Close()
	if copyErr != nil {
		return fmt.Errorf("write temporary dictionary cache: %w", copyErr)
	}
	if closeErr != nil {
		return fmt.Errorf("close temporary dictionary cache: %w", closeErr)
	}
	if written > maxCacheSize {
		return fmt.Errorf("dictionary exceeds %d bytes", maxCacheSize)
	}
	if err := replaceFile(temporaryPath, destination); err != nil {
		return fmt.Errorf("replace dictionary cache: %w", err)
	}
	return nil
}

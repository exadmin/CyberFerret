package main

import (
	"bytes"
	"context"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestRegexpCompileErrorIncludesDictionaryPath(t *testing.T) {
	root := initRepository(t)
	home := t.TempDir()
	cachePath := writeCacheFile(
		t,
		home,
		encryptDictionaryForTest(t, []byte("BAD(regexp)=(?<=token)value\n"), "test-password"),
	)
	dependencies := appDependencies{
		refresher: cacheRefresher{
			now:     time.Now,
			homeDir: func() (string, error) { return home, nil },
		},
		getenv: func(string) string { return "test-password" },
	}
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{root}, &stdout, &stderr, dependencies)

	absolutePath, err := filepath.Abs(cachePath)
	if err != nil {
		t.Fatal(err)
	}
	if exitCode != 3 || !strings.Contains(stderr.String(), absolutePath) {
		t.Fatalf("exit code = %d, stderr = %q; want dictionary path %q", exitCode, stderr.String(), absolutePath)
	}
}

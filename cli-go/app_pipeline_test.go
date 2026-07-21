package main

import (
	"bytes"
	"context"
	"crypto/aes"
	"crypto/cipher"
	"encoding/base64"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestRunWithDependenciesRequiresPassword(t *testing.T) {
	root := initRepository(t)
	dependencies := testAppDependencies(t, "VERSION=1.0\n", "test-password")
	dependencies.getenv = func(string) string { return "" }
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{root}, &stdout, &stderr, dependencies)

	if exitCode != 1 || !strings.Contains(stderr.String(), "TEXT: dictionary password") {
		t.Fatalf("exit code = %d, stderr = %q", exitCode, stderr.String())
	}
}

func TestRunWithDependenciesMapsInvalidRegexpToExitCodeThree(t *testing.T) {
	root := initRepository(t)
	dependencies := testAppDependencies(t, "BAD(regexp)=(?<=token)value\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{root}, &stdout, &stderr, dependencies)

	if exitCode != 3 || !strings.Contains(stderr.String(), "TEXT: Cannot compile dictionary regexp") {
		t.Fatalf("exit code = %d, stderr = %q", exitCode, stderr.String())
	}
}

func TestRunWithDependenciesQuickReturnsTwoOnFirstFinding(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "secret.txt", "contains SECRET")
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{"--mode=quick", root}, &stdout, &stderr, dependencies)

	if exitCode != 2 || !strings.Contains(stdout.String(), `TEXT: Signature "SECRET" found`) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	if strings.Contains(stdout.String(), "TEXT: "+root) {
		t.Fatalf("quick output contains file list: %q", stdout.String())
	}
}

func TestRunWithDependenciesJSONCompletesAndReturnsTwo(t *testing.T) {
	root := initRepository(t)
	file := writeTestFile(t, root, "secret.txt", "SECRET")
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{root}, &stdout, &stderr, dependencies)

	if exitCode != 2 || !strings.Contains(stdout.String(), `JSON: {"key":"SECRET"`) || !strings.Contains(stdout.String(), "TEXT: "+file) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

func TestRunWithDependenciesAcceptsRelativeFolderPath(t *testing.T) {
	root := initRepository(t)
	file := writeTestFile(t, root, "safe.txt", "safe")
	previousDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chdir(filepath.Dir(root)); err != nil {
		t.Fatal(err)
	}
	defer func() {
		if err := os.Chdir(previousDirectory); err != nil {
			t.Errorf("restore working directory: %v", err)
		}
	}()
	dependencies := testAppDependencies(t, "VERSION=1.0\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{filepath.Base(root)},
		&stdout,
		&stderr,
		dependencies,
	)

	if exitCode != 0 || !strings.Contains(stdout.String(), "TEXT: "+file) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

func testAppDependencies(t *testing.T, plaintext, password string) appDependencies {
	t.Helper()
	home := t.TempDir()
	writeCacheFile(t, home, encryptDictionaryForTest(t, []byte(plaintext), password))
	return appDependencies{
		refresher: cacheRefresher{
			client:  nil,
			now:     time.Now,
			homeDir: func() (string, error) { return home, nil },
			timeout: time.Second,
		},
		getenv: func(name string) string {
			if name == "CYBER_FERRET_PASSWORD" {
				return password
			}
			return ""
		},
	}
}

func encryptDictionaryForTest(t *testing.T, plaintext []byte, password string) string {
	t.Helper()
	key := deriveKey([]byte(password), []byte("bsd87918hediu"), 65536, 32)
	block, err := aes.NewCipher(key)
	if err != nil {
		t.Fatal(err)
	}
	paddingLength := aes.BlockSize - len(plaintext)%aes.BlockSize
	padded := append([]byte(nil), plaintext...)
	padded = append(padded, bytes.Repeat([]byte{byte(paddingLength)}, paddingLength)...)
	encrypted := make([]byte, len(padded))
	cipher.NewCBCEncrypter(block, dictionaryIV).CryptBlocks(encrypted, padded)
	return base64.StdEncoding.EncodeToString(encrypted)
}

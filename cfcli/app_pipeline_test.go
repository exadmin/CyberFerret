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

	if exitCode != 1 || !strings.Contains(stderr.String(), "TEXT: Dictionary password") {
		t.Fatalf("exit code = %d, stderr = %q", exitCode, stderr.String())
	}
}

// A dictionary pattern that Go's RE2 engine rejects ends the run with exit code 3 rather than the
// generic 1, so a caller can tell a broken dictionary from a broken environment. The case here is
// a lookbehind, which RE2 has no equivalent for.
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

// Quick mode stops at the first finding: it emits that one found event, exits 2, and prints the
// hint that names the flag combination for exclusion commands rather than the commands themselves.
// It still prints the scan summary the other modes print.
func TestRunWithDependenciesQuickReturnsTwoOnFirstFinding(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "secret.txt", "contains SECRET")
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{"--mode=quick", root}, &stdout, &stderr, dependencies)

	if exitCode != 2 || !strings.Contains(
		stdout.String(),
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":1,"file":"secret.txt"}`,
	) || !strings.Contains(
		stdout.String(),
		"TEXT: To print exclusion commands, run cfcli with --mode=quick --print=details.\n",
	) || strings.Contains(stdout.String(), "cfcli exclude ") {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	if strings.Contains(stdout.String(), "TEXT: "+root) {
		t.Fatalf("quick output contains file list: %q", stdout.String())
	}
	assertCurrentDictionaryOutput(t, stdout.String())
	if !strings.HasSuffix(
		stdout.String(),
		"TEXT: Total files scanned 1\nTEXT: Scanning is finished in 1.234 seconds.\n",
	) {
		t.Fatalf("quick output lacks scan summary: %q", stdout.String())
	}
}

// Quick mode with --print=details prints a ready-to-run exclude command for each supported shell
// in place of the hint, never both.
func TestRunWithDependenciesQuickPrintDetailsPrintsExclusionCommands(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "secret.txt", "contains SECRET")
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{"--mode=quick", "--print=details", root},
		&stdout,
		&stderr,
		dependencies,
	)

	if exitCode != 2 ||
		!strings.Contains(stdout.String(), "TEXT: POSIX: cfcli exclude ") ||
		!strings.Contains(stdout.String(), "TEXT: PowerShell: cfcli exclude ") ||
		!strings.Contains(stdout.String(), "TEXT: cmd.exe: cfcli exclude ") ||
		strings.Contains(stdout.String(), "To print exclusion commands") {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

// The default JSON mode reports a finding and keeps going, so the scan summary and the dictionary
// status lines are all present alongside the found event, and exit code 2 arrives at the end
// rather than in place of the rest of the output.
func TestRunWithDependenciesJSONCompletesAndReturnsTwo(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "secret.txt", "SECRET")
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{root}, &stdout, &stderr, dependencies)

	if exitCode != 2 || !strings.Contains(stdout.String(), `JSON: {"type":"found","key":"SECRET"`) ||
		strings.Contains(stdout.String(), "cfcli exclude ") ||
		!strings.HasSuffix(
			stdout.String(),
			"TEXT: Total files scanned 1\nTEXT: Scanning is finished in 1.234 seconds.\n",
		) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	assertCurrentDictionaryOutput(t, stdout.String())
}

// Outside quick mode --print=details is ignored with a warning on stdout, and no exclude command
// is printed. The scan itself runs to completion and still reports the finding through exit 2.
func TestRunWithDependenciesPrintDetailsWarnsOutsideQuickMode(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "secret.txt", "SECRET")
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{"--print=details", root},
		&stdout,
		&stderr,
		dependencies,
	)

	if exitCode != 2 ||
		!strings.Contains(
			stdout.String(),
			"TEXT: Warning: --print=details applies only to --mode=quick and will be ignored.\n",
		) ||
		strings.Contains(stdout.String(), "cfcli exclude ") {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

func assertCurrentDictionaryOutput(t *testing.T, output string) {
	t.Helper()
	want := currentDictionaryOutput()
	if !strings.Contains(output, want) {
		t.Fatalf("stdout = %q, want dictionary status and path %q", output, want)
	}
}

// FOLDER_PATH may be given relative to the working directory. The test changes the process working
// directory to reach that case, so it cannot run in parallel with anything else in the package.
func TestRunWithDependenciesAcceptsRelativeFolderPath(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "safe.txt", "safe")
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

	if exitCode != 0 || !strings.HasSuffix(
		stdout.String(),
		"TEXT: Total files scanned 1\nTEXT: Scanning is finished in 1.234 seconds.\n",
	) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

// A match covered by a grand-report exclusion is reported as an excluded event instead of a found
// one, so the run still exits 0. The report file itself stays eligible for scanning, which is why
// the count is two for one written file.
func TestRunWithDependenciesAppliesGrandReportExclusions(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "secret.txt", "SECRET")
	writeGrandReport(t, root, `{"exclusions":[{"t-hash":"`+testSHA256("SECRET")+
		`","f-hash":"`+testSHA256("secret.txt")+`"}]}`)
	dependencies := testAppDependencies(t, "VERSION=1.0\nSECRET=SECRET\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(context.Background(), []string{root}, &stdout, &stderr, dependencies)

	if exitCode != 0 || !strings.Contains(
		stdout.String(),
		`JSON: {"type":"excluded","key":"SECRET","found":"SECRET","line":1,"file":"secret.txt"}`,
	) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	if !strings.Contains(stdout.String(), "TEXT: Total files scanned 2\n") {
		t.Fatalf("stdout = %q, want two scanned files including grand-report.json", stdout.String())
	}
}

func TestRunWithDependenciesEnablesVerboseListOutput(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "safe.txt", "safe")
	dependencies := testAppDependencies(t, "VERSION=1.0\n", "test-password")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{"--verbose=true", root},
		&stdout,
		&stderr,
		dependencies,
	)

	if exitCode != 0 || !strings.Contains(stdout.String(), `JSON: {"type":"list","file":"safe.txt"}`) {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

// testAppDependencies builds dependencies for an offline run. The dictionary cache it writes under
// a temporary home is fresh, so the refresher serves it without touching the nil HTTP client and
// the run reports the dictionary as up to date. The clock returns a fixed instant, then one
// 1234 ms later for every call after the first, which is the "1.234 seconds" the expected
// transcripts contain.
func testAppDependencies(t *testing.T, plaintext, password string) appDependencies {
	t.Helper()
	home := t.TempDir()
	writeCacheFile(t, home, encryptDictionaryForTest(t, []byte(plaintext), password))
	start := time.Date(2026, 7, 21, 12, 0, 0, 0, time.UTC)
	times := []time.Time{start, start.Add(1234 * time.Millisecond)}
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
		now: func() time.Time {
			current := times[0]
			if len(times) > 1 {
				times = times[1:]
			}
			return current
		},
	}
}

// encryptDictionaryForTest produces what decryptDictionary expects: PKCS#7-padded AES-CBC under a
// key derived from password with the same salt, iteration count, and IV, then Base64. Change it in
// step with decryptDictionary, or every test that loads a dictionary fails while decrypting.
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

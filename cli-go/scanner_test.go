package main

import (
	"bytes"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

func TestScanFilesQuickStopsAtFirstNonAllowedFinding(t *testing.T) {
	root := t.TempDir()
	first := writeTestFile(t, root, "a.txt", "Token123 SECRET later")
	second := writeTestFile(t, root, "b.txt", "SECRET")
	loaded := dictionary{
		allowed: map[string]struct{}{"token123": {}},
		signatures: []signature{
			{key: "TOKEN", expression: regexp.MustCompile(`(?i)token\d+`)},
			{key: "SECRET", expression: regexp.MustCompile(`(?i)secret`)},
			{key: "SKIPPED", expression: regexp.MustCompile(`(?i)later`)},
		},
	}
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	result, err := scanFiles(root, []string{first, second}, loaded, modeQuick, newLineOutput(&stdout), newLineOutput(&stderr))

	if err != nil {
		t.Fatal(err)
	}
	if !result.found || result.scannedCount != 1 {
		t.Fatalf("scanFiles() result = %#v, want found with one scanned file", result)
	}
	if want := "TEXT: Signature \"SECRET\" found in a.txt at position 9\n"; stdout.String() != want {
		t.Fatalf("stdout = %q, want %q", stdout.String(), want)
	}
	if stderr.Len() != 0 || strings.Contains(stdout.String(), first) || strings.Contains(stdout.String(), "SKIPPED") {
		t.Fatalf("quick mode did not stop cleanly; stdout = %q, stderr = %q", stdout.String(), stderr.String())
	}
}

func TestScanFilesJSONEmitsCompleteFindingsAndTotal(t *testing.T) {
	root := t.TempDir()
	first := writeTestFile(t, root, "nested/result.txt", "ééABCDEFGHIJKLMNOPQ")
	second := writeTestFile(t, root, "other.txt", "XYZ")
	loaded := dictionary{
		allowed: map[string]struct{}{},
		signatures: []signature{
			{key: "LETTERS", expression: regexp.MustCompile(`[A-Z]+`)},
		},
	}
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	result, err := scanFiles(root, []string{first, second}, loaded, modeJSON, newLineOutput(&stdout), newLineOutput(&stderr))

	if err != nil {
		t.Fatal(err)
	}
	if !result.found || result.scannedCount != 2 {
		t.Fatalf("scanFiles() result = %#v, want found with two scanned files", result)
	}
	lines := strings.Split(strings.TrimSpace(stdout.String()), "\n")
	if len(lines) != 2 {
		t.Fatalf("output lines = %#v, want two findings", lines)
	}
	wantFirst := `JSON: {"key":"LETTERS","found":"ABCDEFGHIJKLMNOPQ","position":4,"file":"nested/result.txt"}`
	if lines[0] != wantFirst {
		t.Fatalf("first finding = %q, want %q", lines[0], wantFirst)
	}
	if lines[1] != `JSON: {"key":"LETTERS","found":"XYZ","position":0,"file":"other.txt"}` {
		t.Fatalf("second finding = %q", lines[1])
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestScanFilesHonorsExcludedExtensionsCaseInsensitively(t *testing.T) {
	root := t.TempDir()
	file := writeTestFile(t, root, "archive.ZIP", "SECRET")
	loaded := dictionary{
		allowed: map[string]struct{}{},
		signatures: []signature{{
			key:                "SECRET",
			expression:         regexp.MustCompile(`SECRET`),
			excludedExtensions: map[string]struct{}{"zip": {}},
		}},
	}
	var stdout bytes.Buffer

	result, err := scanFiles(root, []string{file}, loaded, modeJSON, newLineOutput(&stdout), newLineOutput(&bytes.Buffer{}))

	if err != nil || result.found || result.scannedCount != 1 {
		t.Fatalf("scanFiles() result = %#v, error = %v; want no finding and one scanned file", result, err)
	}
	if stdout.Len() != 0 {
		t.Fatalf("stdout = %q", stdout.String())
	}
}

func TestScanFilesReportsReadErrorAndContinues(t *testing.T) {
	root := t.TempDir()
	missing := filepath.Join(root, "missing.txt")
	readable := writeTestFile(t, root, "readable.txt", "SECRET")
	loaded := dictionary{
		allowed:    map[string]struct{}{},
		signatures: []signature{{key: "SECRET", expression: regexp.MustCompile(`SECRET`)}},
	}
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	result, err := scanFiles(root, []string{missing, readable}, loaded, modeJSON, newLineOutput(&stdout), newLineOutput(&stderr))

	if err != nil || !result.found || result.scannedCount != 1 {
		t.Fatalf("scanFiles() result = %#v, error = %v; want finding and one scanned file", result, err)
	}
	if !strings.Contains(stderr.String(), "TEXT: Cannot read file") ||
		!strings.Contains(stdout.String(), `"file":"readable.txt"`) {
		t.Fatalf("stdout = %q, stderr = %q", stdout.String(), stderr.String())
	}
}

func TestScanFilesJSONKeepsCompleteUnicodeMatch(t *testing.T) {
	root := t.TempDir()
	exact := "😀😁😂😃😄😅😆😉😊😋😎😍😘🥰😗😙😚"
	file := writeTestFile(t, root, "unicode.txt", exact)
	loaded := dictionary{
		allowed:    map[string]struct{}{},
		signatures: []signature{{key: "UNICODE", expression: regexp.MustCompile(`.+`)}},
	}
	var stdout bytes.Buffer

	_, err := scanFiles(root, []string{file}, loaded, modeJSON, newLineOutput(&stdout), newLineOutput(&bytes.Buffer{}))

	if err != nil || !strings.Contains(stdout.String(), `"found":"`+exact+`"`) {
		t.Fatalf("scanFiles() error = %v, stdout = %q", err, stdout.String())
	}
}

func TestScanFilesWithExclusionsSkipsFileAndDirectoryWithoutCounting(t *testing.T) {
	root := t.TempDir()
	excludedFile := writeTestFile(t, root, "excluded.txt", "SECRET")
	excludedChild := writeTestFile(t, root, "ignored/child.txt", "SECRET")
	secondExcludedChild := writeTestFile(t, root, "ignored/second.txt", "SECRET")
	includedFile := writeTestFile(t, root, "included.txt", "safe")
	exclusions := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("excluded.txt"): {fullPathExclusionHash: {}},
		testSHA256("ignored"):      {fullPathExclusionHash: {}},
	}}
	loaded := dictionary{
		allowed:    map[string]struct{}{},
		signatures: []signature{{key: "SECRET", expression: regexp.MustCompile(`SECRET`)}},
	}
	var stdout bytes.Buffer

	result, err := scanFilesWithExclusions(
		root,
		[]string{excludedFile, excludedChild, secondExcludedChild, includedFile},
		loaded,
		modeJSON,
		exclusions,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want no finding and one scanned file", result, err)
	}
	want := "JSON: {\"type\":\"excluded\",\"file\":\"excluded.txt\"}\n" +
		"JSON: {\"type\":\"excluded\",\"file\":\"ignored\"}\n"
	if stdout.String() != want {
		t.Fatalf("stdout = %q, want %q", stdout.String(), want)
	}
}

func TestScanFilesWithExclusionsSuppressesOnlyExactFileMatch(t *testing.T) {
	root := t.TempDir()
	excluded := writeTestFile(t, root, "excluded.txt", "SECRET")
	reported := writeTestFile(t, root, "reported.txt", "SECRET")
	exclusions := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("excluded.txt"): {testSHA256("SECRET"): {}},
	}}
	loaded := dictionary{
		allowed:    map[string]struct{}{},
		signatures: []signature{{key: "SECRET", expression: regexp.MustCompile(`SECRET`)}},
	}
	var stdout bytes.Buffer

	result, err := scanFilesWithExclusions(
		root,
		[]string{excluded, reported},
		loaded,
		modeJSON,
		exclusions,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || !result.found || result.scannedCount != 2 {
		t.Fatalf("scan result = %#v, error = %v; want one finding and two scanned files", result, err)
	}
	if !strings.Contains(
		stdout.String(),
		`JSON: {"type":"excluded","key":"SECRET","found":"SECRET","position":0,"file":"excluded.txt"}`,
	) || !strings.Contains(stdout.String(), `"file":"reported.txt"`) {
		t.Fatalf("stdout = %q", stdout.String())
	}
}

func TestScanFilesWithExclusionsQuickOutputIsSilent(t *testing.T) {
	root := t.TempDir()
	excludedFile := writeTestFile(t, root, "excluded.txt", "SECRET")
	excludedMatch := writeTestFile(t, root, "match.txt", "SECRET")
	reported := writeTestFile(t, root, "reported.txt", "SECRET")
	exclusions := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("excluded.txt"): {fullPathExclusionHash: {}},
		testSHA256("match.txt"):    {testSHA256("SECRET"): {}},
	}}
	loaded := dictionary{
		allowed:    map[string]struct{}{},
		signatures: []signature{{key: "SECRET", expression: regexp.MustCompile(`SECRET`)}},
	}
	var stdout bytes.Buffer

	result, err := scanFilesWithExclusions(
		root,
		[]string{excludedFile, excludedMatch, reported},
		loaded,
		modeQuick,
		exclusions,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || !result.found || result.scannedCount != 2 {
		t.Fatalf("scan result = %#v, error = %v; want finding and two scanned files", result, err)
	}
	if strings.Contains(stdout.String(), "JSON:") ||
		stdout.String() != "TEXT: Signature \"SECRET\" found in reported.txt at position 0\n" {
		t.Fatalf("stdout = %q", stdout.String())
	}
}

func TestScanFilesWithExclusionsReportsAllowedExcludedMatch(t *testing.T) {
	root := t.TempDir()
	file := writeTestFile(t, root, "allowed.txt", "SECRET")
	exclusions := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("allowed.txt"): {testSHA256("SECRET"): {}},
	}}
	loaded := dictionary{
		allowed:    map[string]struct{}{"secret": {}},
		signatures: []signature{{key: "SECRET", expression: regexp.MustCompile(`SECRET`)}},
	}
	var stdout bytes.Buffer

	result, err := scanFilesWithExclusions(
		root,
		[]string{file},
		loaded,
		modeJSON,
		exclusions,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want excluded finding and one scanned file", result, err)
	}
	if !strings.Contains(stdout.String(), `"type":"excluded"`) {
		t.Fatalf("stdout = %q, want excluded event", stdout.String())
	}
}

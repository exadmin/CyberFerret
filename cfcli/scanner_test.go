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
	if want := `JSON: {"type":"found","key":"SECRET","found":"SECRET","line":1,"file":"a.txt"}` + "\n"; stdout.String() != want {
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
	wantFirst := `JSON: {"type":"found","key":"LETTERS","found":"ABCDEFGHIJKLMNOPQ","line":1,"file":"nested/result.txt"}`
	if lines[0] != wantFirst {
		t.Fatalf("first finding = %q, want %q", lines[0], wantFirst)
	}
	if lines[1] != `JSON: {"type":"found","key":"LETTERS","found":"XYZ","line":1,"file":"other.txt"}` {
		t.Fatalf("second finding = %q", lines[1])
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q, want empty", stderr.String())
	}
}

func TestScanFilesReportsOneBasedMatchLines(t *testing.T) {
	root := t.TempDir()
	file := writeTestFile(t, root, "lines.txt", "SECRET\né SECRET\r\nx SECRET\ry SECRET SECRET")
	loaded := dictionary{
		allowed: map[string]struct{}{},
		signatures: []signature{{
			key:        "SECRET",
			expression: regexp.MustCompile(`SECRET`),
		}},
	}
	var stdout bytes.Buffer

	result, err := scanFiles(
		root,
		[]string{file},
		loaded,
		modeJSON,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || !result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want five findings in one file", result, err)
	}
	want := strings.Join([]string{
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":1,"file":"lines.txt"}`,
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":2,"file":"lines.txt"}`,
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":3,"file":"lines.txt"}`,
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":4,"file":"lines.txt"}`,
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":4,"file":"lines.txt"}`,
	}, "\n") + "\n"
	if stdout.String() != want {
		t.Fatalf("stdout = %q, want %q", stdout.String(), want)
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

func TestScanFilesHonorsAllowedWildcard(t *testing.T) {
	root := t.TempDir()
	allowedEmail := "user+tag@" + "example.com"
	reportedEmail := "bad@" + "other.com"
	file := writeTestFile(t, root, "emails.txt", allowedEmail+" "+reportedEmail)
	allowedPattern, err := compileAllowedPattern("*@example.com")
	if err != nil {
		t.Fatal(err)
	}
	loaded := dictionary{
		allowed:         map[string]struct{}{},
		allowedPatterns: []*regexp.Regexp{allowedPattern},
		signatures: []signature{{
			key:        "EMAIL",
			expression: regexp.MustCompile(`\S+@\S+`),
		}},
	}
	var stdout bytes.Buffer

	result, err := scanFiles(
		root,
		[]string{file},
		loaded,
		modeJSON,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || !result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want one finding in one file", result, err)
	}
	if !strings.Contains(stdout.String(), `"type":"allowed","key":"EMAIL","found":"`+allowedEmail+`"`) ||
		!strings.Contains(stdout.String(), `"type":"found","key":"EMAIL","found":"`+reportedEmail+`"`) {
		t.Fatalf("stdout = %q", stdout.String())
	}
}

func TestScanFilesJSONReportsExactAllowedWithoutFinding(t *testing.T) {
	root := t.TempDir()
	file := writeTestFile(t, root, "allowed.txt", "Token123")
	loaded := dictionary{
		allowed:    map[string]struct{}{"token123": {}},
		signatures: []signature{{key: "TOKEN", expression: regexp.MustCompile(`Token\d+`)}},
	}
	var stdout bytes.Buffer

	result, err := scanFiles(
		root,
		[]string{file},
		loaded,
		modeJSON,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want allowed event without finding", result, err)
	}
	want := `JSON: {"type":"allowed","key":"TOKEN","found":"Token123","line":1,"file":"allowed.txt"}` + "\n"
	if stdout.String() != want {
		t.Fatalf("stdout = %q, want %q", stdout.String(), want)
	}
}

func TestScanFilesVerboseListsFoldersAndFilesBeforeScanning(t *testing.T) {
	root := t.TempDir()
	first := writeTestFile(t, root, "src/first.txt", "safe")
	second := writeTestFile(t, root, "src/nested/second.txt", "safe")
	var stdout bytes.Buffer

	result, err := scanFilesConfigured(
		root,
		[]string{first, second},
		dictionary{allowed: map[string]struct{}{}},
		modeJSON,
		exclusionSet{},
		true,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || result.found || result.scannedCount != 2 {
		t.Fatalf("scan result = %#v, error = %v; want two scanned files", result, err)
	}
	want := "JSON: {\"type\":\"list\",\"folder\":\"src\"}\n" +
		"JSON: {\"type\":\"list\",\"file\":\"src/first.txt\"}\n" +
		"JSON: {\"type\":\"list\",\"folder\":\"src/nested\"}\n" +
		"JSON: {\"type\":\"list\",\"file\":\"src/nested/second.txt\"}\n"
	if stdout.String() != want {
		t.Fatalf("stdout = %q, want %q", stdout.String(), want)
	}
}

func TestScanFilesVerboseListsExcludedPathsWithoutDescendantFiles(t *testing.T) {
	root := t.TempDir()
	excludedFile := filepath.Join(root, "excluded.txt")
	excludedChild := filepath.Join(root, "ignored", "nested", "child.txt")
	exclusions := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("excluded.txt"): {fullPathExclusionHash: {}},
		testSHA256("ignored"):      {fullPathExclusionHash: {}},
	}}
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	result, err := scanFilesConfigured(
		root,
		[]string{excludedFile, excludedChild},
		dictionary{allowed: map[string]struct{}{}},
		modeQuick,
		exclusions,
		true,
		newLineOutput(&stdout),
		newLineOutput(&stderr),
	)

	if err != nil || result.found || result.scannedCount != 0 {
		t.Fatalf("scan result = %#v, error = %v; want no scanned files", result, err)
	}
	want := "JSON: {\"type\":\"list\",\"file\":\"excluded.txt\"}\n" +
		"JSON: {\"type\":\"excluded\",\"file\":\"excluded.txt\"}\n" +
		"JSON: {\"type\":\"list\",\"folder\":\"ignored\"}\n" +
		"JSON: {\"type\":\"excluded\",\"file\":\"ignored\"}\n"
	if stdout.String() != want || stderr.Len() != 0 {
		t.Fatalf("stdout = %q, stderr = %q, want stdout %q", stdout.String(), stderr.String(), want)
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
		`JSON: {"type":"excluded","key":"SECRET","found":"SECRET","line":1,"file":"excluded.txt"}`,
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
	if stdout.String() !=
		`JSON: {"type":"found","key":"SECRET","found":"SECRET","line":1,"file":"reported.txt"}`+"\n" {
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

func TestScanFilesLimitsOnlyFoundMatchesPerSignatureAndFile(t *testing.T) {
	root := t.TempDir()
	content := "ALLOW EXCLUDE SECRET SECRET SECRET SECRET SECRET SECRET TOKEN"
	file := writeTestFile(t, root, "repeated.txt", content)
	exclusions := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("repeated.txt"): {testSHA256("EXCLUDE"): {}},
	}}
	loaded := dictionary{
		allowed: map[string]struct{}{"allow": {}},
		signatures: []signature{
			{key: "SECRET", expression: regexp.MustCompile(`ALLOW|EXCLUDE|SECRET`)},
			{key: "TOKEN", expression: regexp.MustCompile(`TOKEN`)},
		},
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

	if err != nil || !result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want findings in one scanned file", result, err)
	}
	output := stdout.String()
	if got := strings.Count(output, `"type":"found","key":"SECRET"`); got != 5 {
		t.Fatalf("SECRET found event count = %d, want 5; output = %q", got, output)
	}
	if !strings.Contains(output, `"type":"allowed","key":"SECRET","found":"ALLOW"`) {
		t.Fatalf("output = %q, want allowed event", output)
	}
	if !strings.Contains(output, `"type":"excluded","key":"SECRET","found":"EXCLUDE"`) {
		t.Fatalf("output = %q, want excluded event", output)
	}
	if !strings.Contains(output, `"type":"found","key":"TOKEN","found":"TOKEN"`) {
		t.Fatalf("output = %q, want TOKEN finding after SECRET reaches its limit", output)
	}
}

func TestScanFilesSharesFindingLimitAcrossSignaturesWithSameKey(t *testing.T) {
	root := t.TempDir()
	file := writeTestFile(t, root, "repeated.txt", "A A A A A A B B B B B B")
	loaded := dictionary{
		allowed: map[string]struct{}{},
		signatures: []signature{
			{key: "SHARED", expression: regexp.MustCompile(`A`)},
			{key: "SHARED", expression: regexp.MustCompile(`B`)},
		},
	}
	var stdout bytes.Buffer

	result, err := scanFiles(
		root,
		[]string{file},
		loaded,
		modeJSON,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)

	if err != nil || !result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want findings in one scanned file", result, err)
	}
	if got := strings.Count(stdout.String(), `"type":"found","key":"SHARED"`); got != 5 {
		t.Fatalf("SHARED found event count = %d, want 5; output = %q", got, stdout.String())
	}
}

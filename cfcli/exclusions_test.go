package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
)

func TestLoadExclusionsMatchesExactFinding(t *testing.T) {
	root := t.TempDir()
	relativePath := "src/secret.txt"
	writeGrandReport(t, root, `{
  "exclusions": [{
    "t-hash": "`+testSHA256("SECRET")+`",
    "f-hash": "`+testSHA256(relativePath)+`"
  }]
}`)
	var warnings bytes.Buffer

	loaded := loadExclusions(root, newLineOutput(&warnings))

	if !loaded.excludesMatch(relativePath, "SECRET") {
		t.Fatal("exact finding was not excluded")
	}
	if loaded.excludesMatch(relativePath, "secret") {
		t.Fatal("text exclusion was applied case-insensitively")
	}
	if warnings.Len() != 0 {
		t.Fatalf("unexpected warnings: %q", warnings.String())
	}
}

func TestExclusionSetExcludesDirectoryDescendants(t *testing.T) {
	root := t.TempDir()
	writeGrandReport(t, root, `{
  "exclusions": [{
    "t-hash": "00000000",
    "f-hash": "`+testSHA256("ignored")+`"
  }]
}`)

	loaded := loadExclusions(root, newLineOutput(&bytes.Buffer{}))

	if !loaded.excludesPath("ignored/nested/secret.txt") {
		t.Fatal("descendant of excluded directory was not excluded")
	}
	if loaded.excludesPath("included/secret.txt") {
		t.Fatal("unrelated file was excluded")
	}
}

func TestExclusionSetReturnsExcludedPaths(t *testing.T) {
	loaded := exclusionSet{textHashesByFileHash: map[string]map[string]struct{}{
		testSHA256("outer"):       {fullPathExclusionHash: {}},
		testSHA256("outer/inner"): {fullPathExclusionHash: {}},
	}}

	got := loaded.excludedPaths("outer/inner/secret.txt")
	want := []string{"outer", "outer/inner"}

	if !slices.Equal(got, want) {
		t.Fatalf("excludedPaths() = %#v, want %#v", got, want)
	}
}

func TestLoadExclusionsMissingFileIsSilent(t *testing.T) {
	var warnings bytes.Buffer

	loaded := loadExclusions(t.TempDir(), newLineOutput(&warnings))

	if loaded.excludesPath("anything.txt") || warnings.Len() != 0 {
		t.Fatalf("missing report result = %#v, warnings = %q", loaded, warnings.String())
	}
}

func TestLoadExclusionsMalformedFileWarnsWithAbsolutePath(t *testing.T) {
	root := t.TempDir()
	reportPath := writeGrandReport(t, root, `{invalid`)
	var warnings bytes.Buffer

	loaded := loadExclusions(root, newLineOutput(&warnings))

	if loaded.excludesPath("anything.txt") {
		t.Fatal("malformed report produced exclusions")
	}
	absolutePath, err := filepath.Abs(reportPath)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(warnings.String(), "TEXT:") || !strings.Contains(warnings.String(), absolutePath) {
		t.Fatalf("warning = %q, want absolute path %q", warnings.String(), absolutePath)
	}
}

func writeGrandReport(t *testing.T, root, content string) string {
	t.Helper()
	directory := filepath.Join(root, ".qubership")
	if err := os.MkdirAll(directory, 0o755); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(directory, "grand-report.json")
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func testSHA256(value string) string {
	digest := sha256.Sum256([]byte(value))
	return hex.EncodeToString(digest[:])
}

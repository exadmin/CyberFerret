package main

import (
	"context"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func TestSelectFilesReturnsSortedAbsoluteGitSelection(t *testing.T) {
	root := initRepository(t)
	zPath := writeTestFile(t, root, "z.txt", "z")
	aPath := writeTestFile(t, root, "a.txt", "a")
	writeTestFile(t, root, ".gitignore", "ignored.txt\n")
	writeTestFile(t, root, "ignored.txt", "ignored")

	got, err := selectFiles(context.Background(), root, nil)
	if err != nil {
		t.Fatal(err)
	}

	ignorePath := filepath.Join(root, ".gitignore")
	want := []string{ignorePath, aPath, zPath}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("selectFiles() = %#v, want %#v", got, want)
	}
}

// A listed path is selected only when Git also reports it, so an entry that is
// ignored or nonexistent drops out. Blank lines, repeated entries, and CRLF line
// endings leave the selection unchanged.
func TestSelectFilesRestrictsResultsToListedPaths(t *testing.T) {
	root := initRepository(t)
	included := writeTestFile(t, root, "dir/included.txt", "included")
	writeTestFile(t, root, "not-listed.txt", "not listed")
	writeTestFile(t, root, ".gitignore", "ignored.txt\n")
	writeTestFile(t, root, "ignored.txt", "ignored")
	listPath := writeTestFile(t, t.TempDir(), "staged.txt", "dir/included.txt\r\n\r\ndir/included.txt\r\nignored.txt\r\nmissing.txt\r\n")

	got, err := selectFiles(context.Background(), root, &listPath)
	if err != nil {
		t.Fatal(err)
	}

	want := []string{included}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("selectFiles() = %#v, want %#v", got, want)
	}
}

func TestSelectFilesTreatsListedPathsAsRelativeToNestedFolder(t *testing.T) {
	repository := initRepository(t)
	root := filepath.Join(repository, "nested")
	included := writeTestFile(t, root, "included.txt", "included")
	writeTestFile(t, repository, "outside.txt", "outside")
	listPath := writeTestFile(t, t.TempDir(), "staged.txt", "included.txt\n")

	got, err := selectFiles(context.Background(), root, &listPath)
	if err != nil {
		t.Fatal(err)
	}

	want := []string{included}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("selectFiles() = %#v, want %#v", got, want)
	}
}

func TestSelectFilesRejectsUnsafeListedPaths(t *testing.T) {
	root := initRepository(t)
	tests := []struct {
		name        string
		entry       string
		wantMessage string
	}{
		{name: "absolute", entry: filepath.Join(root, "file.txt"), wantMessage: "path must be relative"},
		{name: "parent traversal", entry: "../outside.txt", wantMessage: "path escapes FOLDER_PATH"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			listPath := writeTestFile(t, t.TempDir(), "staged.txt", test.entry+"\n")

			_, err := selectFiles(context.Background(), root, &listPath)

			if err == nil || !strings.Contains(err.Error(), test.wantMessage) {
				t.Fatalf("selectFiles() error = %v, want message %q", err, test.wantMessage)
			}
		})
	}
}

func TestSelectFilesRejectsInvalidRoot(t *testing.T) {
	rootFile := filepath.Join(t.TempDir(), "root.txt")
	if err := os.WriteFile(rootFile, []byte("file"), 0o600); err != nil {
		t.Fatal(err)
	}

	_, err := selectFiles(context.Background(), rootFile, nil)

	if err == nil || !strings.Contains(err.Error(), "not a directory") {
		t.Fatalf("selectFiles() error = %v, want not-a-directory error", err)
	}
}

// Tracked symbolic links are excluded whether they point to files or directories.
func TestSelectFilesExcludesSymbolicLinks(t *testing.T) {
	root := initRepository(t)
	targetFile := writeTestFile(t, root, "target.txt", "target")
	fileLink := filepath.Join(root, "file-link.txt")
	if err := os.Symlink(targetFile, fileLink); err != nil {
		t.Skipf("symbolic links are unavailable: %v", err)
	}
	targetDirectory := filepath.Join(root, "target-directory")
	if err := os.Mkdir(targetDirectory, 0o755); err != nil {
		t.Fatal(err)
	}
	writeTestFile(t, targetDirectory, "nested.txt", "nested")
	directoryLink := filepath.Join(root, "directory-link")
	if err := os.Symlink(targetDirectory, directoryLink); err != nil {
		t.Fatal(err)
	}
	runTestGit(t, root, "add", "file-link.txt", "directory-link")

	got, err := selectFiles(context.Background(), root, nil)
	if err != nil {
		t.Fatal(err)
	}

	if containsPath(got, fileLink) || containsPath(got, directoryLink) || containsPath(got, filepath.Join(directoryLink, "nested.txt")) {
		t.Fatalf("selectFiles() = %#v, must not include or traverse symbolic links", got)
	}
}

func containsPath(paths []string, want string) bool {
	for _, path := range paths {
		if path == want {
			return true
		}
	}
	return false
}

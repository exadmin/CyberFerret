package main

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"reflect"
	"testing"
)

func TestEnumerateGitFilesIncludesTrackedAndNonIgnoredUntracked(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "tracked.txt", "tracked")
	runTestGit(t, root, "add", "tracked.txt")
	writeTestFile(t, root, "untracked.txt", "untracked")
	writeTestFile(t, root, ".gitignore", "ignored.txt\n")
	writeTestFile(t, root, "ignored.txt", "ignored")

	got, err := enumerateGitFiles(context.Background(), root)
	if err != nil {
		t.Fatal(err)
	}

	want := pathSet(".gitignore", "tracked.txt", "untracked.txt")
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("enumerateGitFiles() = %#v, want %#v", got, want)
	}
}

// An untracked file stays out of the result when any exclude source git
// consults as standard hides it: a .gitignore, .git/info/exclude, or the file
// core.excludesFile names.
func TestEnumerateGitFilesHonorsStandardExclusions(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, ".gitignore", "repository-ignored.txt\n")
	writeTestFile(t, root, "repository-ignored.txt", "ignored")
	writeTestFile(t, root, ".git/info/exclude", "info-ignored.txt\n")
	writeTestFile(t, root, "info-ignored.txt", "ignored")
	globalIgnore := filepath.Join(t.TempDir(), "global-ignore")
	if err := os.WriteFile(globalIgnore, []byte("global-ignored.txt\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	runTestGit(t, root, "config", "core.excludesFile", globalIgnore)
	writeTestFile(t, root, "global-ignored.txt", "ignored")
	writeTestFile(t, root, "included.txt", "included")

	got, err := enumerateGitFiles(context.Background(), root)
	if err != nil {
		t.Fatal(err)
	}

	want := pathSet(".gitignore", "included.txt")
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("enumerateGitFiles() = %#v, want %#v", got, want)
	}
}

func TestEnumerateGitFilesHonorsNegatedRules(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, ".gitignore", "*.txt\n!keep.txt\n")
	writeTestFile(t, root, "drop.txt", "ignored")
	writeTestFile(t, root, "keep.txt", "included")

	got, err := enumerateGitFiles(context.Background(), root)
	if err != nil {
		t.Fatal(err)
	}

	want := pathSet(".gitignore", "keep.txt")
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("enumerateGitFiles() = %#v, want %#v", got, want)
	}
}

// A ~/.gitignore_global excludes untracked files even when no git configuration
// references it: [enumerateGitFiles] passes it to git as an extra exclude file.
func TestEnumerateGitFilesHonorsDetectedHomeGitignoreGlobal(t *testing.T) {
	root := initRepository(t)
	home := t.TempDir()
	t.Setenv("HOME", home)
	t.Setenv("USERPROFILE", home)
	writeTestFile(t, home, ".gitignore_global", "home-global-ignored.txt\n")
	writeTestFile(t, root, "home-global-ignored.txt", "ignored")
	writeTestFile(t, root, "included.txt", "included")

	got, err := enumerateGitFiles(context.Background(), root)
	if err != nil {
		t.Fatal(err)
	}

	want := pathSet("included.txt")
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("enumerateGitFiles() = %#v, want %#v", got, want)
	}
}

func TestEnumerateGitFilesKeepsTrackedFilesThatBecomeIgnored(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "tracked-then-ignored.txt", "tracked")
	runTestGit(t, root, "add", "tracked-then-ignored.txt")
	writeTestFile(t, root, ".gitignore", "tracked-then-ignored.txt\n")

	got, err := enumerateGitFiles(context.Background(), root)
	if err != nil {
		t.Fatal(err)
	}

	want := pathSet(".gitignore", "tracked-then-ignored.txt")
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("enumerateGitFiles() = %#v, want %#v", got, want)
	}
}

func initRepository(t *testing.T) string {
	t.Helper()
	root := t.TempDir()
	runTestGit(t, root, "init", "--quiet")
	return root
}

func runTestGit(t *testing.T, root string, args ...string) {
	t.Helper()
	commandArgs := append([]string{"-C", root}, args...)
	output, err := exec.Command("git", commandArgs...).CombinedOutput()
	if err != nil {
		t.Fatalf("git %v: %v: %s", args, err, output)
	}
}

// writeTestFile writes content to relativePath under root, creating any missing
// parent directories, and returns the path it wrote.
func writeTestFile(t *testing.T, root, relativePath, content string) string {
	t.Helper()
	path := filepath.Join(root, filepath.FromSlash(relativePath))
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func pathSet(paths ...string) map[string]struct{} {
	result := make(map[string]struct{}, len(paths))
	for _, path := range paths {
		result[filepath.FromSlash(path)] = struct{}{}
	}
	return result
}

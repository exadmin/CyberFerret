package main

import (
	"bytes"
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRunRejectsInvalidArgumentCounts(t *testing.T) {
	tests := [][]string{{}, {"root", "list", "extra"}}
	for _, args := range tests {
		var stdout bytes.Buffer
		var stderr bytes.Buffer

		exitCode := run(context.Background(), args, &stdout, &stderr)

		if exitCode != 2 {
			t.Fatalf("run(%q) exit code = %d, want 2", args, exitCode)
		}
		if stdout.Len() != 0 {
			t.Fatalf("run(%q) stdout = %q, want empty", args, stdout.String())
		}
		if !strings.Contains(stderr.String(), "usage: cli-go FOLDER_PATH [PATH_TO_LIST_OF_FILES]") {
			t.Fatalf("run(%q) stderr = %q, want usage", args, stderr.String())
		}
	}
}

func TestRunPrintsSortedAbsolutePaths(t *testing.T) {
	root := initRepository(t)
	first := writeTestFile(t, root, "a.txt", "a")
	second := writeTestFile(t, root, "b.txt", "b")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := run(context.Background(), []string{root}, &stdout, &stderr)

	if exitCode != 0 {
		t.Fatalf("run() exit code = %d, want 0; stderr = %q", exitCode, stderr.String())
	}
	want := first + "\n" + second + "\n"
	if stdout.String() != want {
		t.Fatalf("run() stdout = %q, want %q", stdout.String(), want)
	}
	if stderr.Len() != 0 {
		t.Fatalf("run() stderr = %q, want empty", stderr.String())
	}
}

func TestRunReportsRuntimeErrors(t *testing.T) {
	nonGitDirectory := t.TempDir()
	rootFile := filepath.Join(t.TempDir(), "root.txt")
	if err := os.WriteFile(rootFile, []byte("file"), 0o600); err != nil {
		t.Fatal(err)
	}
	repository := initRepository(t)
	missingList := filepath.Join(t.TempDir(), "missing-list.txt")
	tests := []struct {
		name        string
		args        []string
		wantMessage string
	}{
		{name: "missing root", args: []string{filepath.Join(t.TempDir(), "missing")}, wantMessage: "inspect FOLDER_PATH"},
		{name: "root is file", args: []string{rootFile}, wantMessage: "not a directory"},
		{name: "not Git repository", args: []string{nonGitDirectory}, wantMessage: "list Git files"},
		{name: "missing list", args: []string{repository, missingList}, wantMessage: "read file list"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			var stdout bytes.Buffer
			var stderr bytes.Buffer

			exitCode := run(context.Background(), test.args, &stdout, &stderr)

			if exitCode != 1 {
				t.Fatalf("run() exit code = %d, want 1", exitCode)
			}
			if stdout.Len() != 0 {
				t.Fatalf("run() stdout = %q, want empty", stdout.String())
			}
			if !strings.Contains(stderr.String(), test.wantMessage) {
				t.Fatalf("run() stderr = %q, want message %q", stderr.String(), test.wantMessage)
			}
		})
	}
}

func TestRunReportsUnavailableGit(t *testing.T) {
	root := t.TempDir()
	t.Setenv("PATH", "")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := run(context.Background(), []string{root}, &stdout, &stderr)

	if exitCode != 1 {
		t.Fatalf("run() exit code = %d, want 1", exitCode)
	}
	if stdout.Len() != 0 {
		t.Fatalf("run() stdout = %q, want empty", stdout.String())
	}
	if !strings.Contains(stderr.String(), "git ls-files") {
		t.Fatalf("run() stderr = %q, want Git operation", stderr.String())
	}
}

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

		exitCode := runWithDependencies(context.Background(), args, &stdout, &stderr, appDependencies{})

		if exitCode != 1 {
			t.Fatalf("run(%q) exit code = %d, want 1", args, exitCode)
		}
		if stdout.Len() != 0 {
			t.Fatalf("run(%q) stdout = %q, want empty", args, stdout.String())
		}
		if !strings.Contains(
			stderr.String(),
			"TEXT: usage: cfcli [--mode=quick|--mode=json] [--verbose=true|--verbose=false] FOLDER_PATH [PATH_TO_LIST_OF_FILES]",
		) {
			t.Fatalf("run(%q) stderr = %q, want usage", args, stderr.String())
		}
	}
}

func TestRunReportsTotalFilesScanned(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "a.txt", "a")
	writeTestFile(t, root, "b.txt", "b")
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{root},
		&stdout,
		&stderr,
		testAppDependencies(t, "VERSION=1.0\n", "test-password"),
	)

	if exitCode != 0 {
		t.Fatalf("run() exit code = %d, want 0; stderr = %q", exitCode, stderr.String())
	}
	want := currentDictionaryOutput() +
		"TEXT: Dictionary version: 1.0\n" +
		"TEXT: Scanning is in progress. Please wait.\n" +
		"TEXT: Total files scanned 2\n" +
		"TEXT: Scanning is finished in 1.234 seconds.\n"
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

			exitCode := runWithDependencies(
				context.Background(),
				test.args,
				&stdout,
				&stderr,
				testAppDependencies(t, "VERSION=1.0\n", "test-password"),
			)

			if exitCode != 1 {
				t.Fatalf("run() exit code = %d, want 1", exitCode)
			}
			wantOutput := currentDictionaryOutput() +
				"TEXT: Dictionary version: 1.0\nTEXT: Scanning is in progress. Please wait.\n"
			if stdout.String() != wantOutput {
				t.Fatalf("run() stdout = %q, want version and progress", stdout.String())
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

	exitCode := runWithDependencies(
		context.Background(),
		[]string{root},
		&stdout,
		&stderr,
		testAppDependencies(t, "VERSION=1.0\n", "test-password"),
	)

	if exitCode != 1 {
		t.Fatalf("run() exit code = %d, want 1", exitCode)
	}
	wantOutput := currentDictionaryOutput() +
		"TEXT: Dictionary version: 1.0\nTEXT: Scanning is in progress. Please wait.\n"
	if stdout.String() != wantOutput {
		t.Fatalf("run() stdout = %q, want version and progress", stdout.String())
	}
	if !strings.Contains(stderr.String(), "git ls-files") {
		t.Fatalf("run() stderr = %q, want Git operation", stderr.String())
	}
}

func TestDictionaryStatusMessage(t *testing.T) {
	tests := []struct {
		state cacheState
		want  string
	}{
		{state: cacheCurrent, want: "Dictionary is up to date."},
		{state: cacheUpdated, want: "Dictionary was updated."},
		{
			state: cacheFallback,
			want:  "Dictionary was not updated due to network issues; using the existing dictionary.",
		},
	}
	for _, test := range tests {
		if got := dictionaryStatusMessage(test.state); got != test.want {
			t.Errorf("dictionaryStatusMessage(%v) = %q, want %q", test.state, got, test.want)
		}
	}
}

func TestDictionaryDisplayPath(t *testing.T) {
	home := filepath.Join(string(filepath.Separator), "users", "scanner")
	cache := filepath.Join(home, ".qubership", cacheFileName)

	if got, want := dictionaryDisplayPath(cache, home), "~/.qubership/"+cacheFileName; got != want {
		t.Fatalf("dictionaryDisplayPath() = %q, want %q", got, want)
	}
}

func currentDictionaryOutput() string {
	return "TEXT: Dictionary is up to date.\n" +
		"TEXT: Dictionary path: ~/.qubership/" + cacheFileName + "\n"
}

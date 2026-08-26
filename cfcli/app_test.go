package main

import (
	"bytes"
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// A scan invocation with no positional argument, or with more than two, exits 1 with the usage
// text on stderr and nothing on stdout. The zero-value dependencies are part of the rule: the
// argument count has to be rejected before anything calls the nil homeDir behind the refresher.
func TestRunRejectsInvalidArgumentCounts(t *testing.T) {
	tests := [][]string{{}, {"root", "list", "extra"}}
	for _, args := range tests {
		var stdout bytes.Buffer
		var stderr bytes.Buffer

		exitCode := runWithDependencies(context.Background(), args, &stdout, &stderr, appDependencies{})

		if exitCode != exitFailure {
			t.Fatalf("run(%q) exit code = %d, want %d", args, exitCode, exitFailure)
		}
		if stdout.Len() != 0 {
			t.Fatalf("run(%q) stdout = %q, want empty", args, stdout.String())
		}
		if !strings.Contains(
			stderr.String(),
			"TEXT: Usage:\n",
		) {
			t.Fatalf("run(%q) stderr = %q, want usage", args, stderr.String())
		}
	}
}

// A wrong argument count for either command form prints the whole usage block, scan form and
// exclude form together, so a user who mistyped one still sees the other. Add an entry to the
// expected list here when writeHelp gains a line.
func TestRunPrintsExpandedHelpForInsufficientArguments(t *testing.T) {
	tests := [][]string{
		nil,
		{"exclude"},
		{"exclude", "root"},
		{"exclude", "root", `{}`, "extra"},
	}
	for _, args := range tests {
		var stdout bytes.Buffer
		var stderr bytes.Buffer

		exitCode := runWithDependencies(context.Background(), args, &stdout, &stderr, appDependencies{})

		if exitCode != exitFailure || stdout.Len() != 0 {
			t.Fatalf("run(%q) exit code = %d, stdout = %q", args, exitCode, stdout.String())
		}
		for _, expected := range []string{
			"TEXT: Usage:\n",
			"TEXT:   cfcli [--mode=quick|--mode=json] [--print=details]",
			"TEXT:   cfcli exclude <add|remove> FOLDER_PATH JSON_OBJECT\n",
			"TEXT:   cfcli exclude FOLDER_PATH JSON_OBJECT\n",
			"TEXT: cfcli scans a Git repository for sensitive values",
			"TEXT: Before scanning:\n",
			"TEXT:   Set CYBER_FERRET_PASSWORD to the dictionary decryption password.\n",
			"TEXT: Arguments and options:\n",
			"TEXT:   --mode=json",
			"TEXT:   --print=details",
			"TEXT:   JSON_OBJECT",
			"TEXT:   add",
			"TEXT:   remove",
			"TEXT: Use exclude only when you consider the detected value acceptable",
			"TEXT: The exclude command does not refresh or decrypt the dictionary",
		} {
			if !strings.Contains(stderr.String(), expected) {
				t.Fatalf("run(%q) stderr = %q, want %q", args, stderr.String(), expected)
			}
		}
	}
}

// A clean scan exits 0 and prints the dictionary status, path, and version, then the progress
// line, the file count, and the elapsed time, with nothing on stderr. The check compares the whole
// stdout transcript, so a line added anywhere in the run has to be added to want as well.
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

	if exitCode != exitClean {
		t.Fatalf("run() exit code = %d, want %d; stderr = %q", exitCode, exitClean, stderr.String())
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

// Every invocation whose file set cannot be established exits 1: a missing directory, a file in
// place of a directory, a directory outside any Git repository, and a file list that cannot be
// read. The stdout transcript stops after the progress line, and stderr names the operation that
// failed.
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

			if exitCode != exitFailure {
				t.Fatalf("run() exit code = %d, want %d", exitCode, exitFailure)
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

	if exitCode != exitFailure {
		t.Fatalf("run() exit code = %d, want %d", exitCode, exitFailure)
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

// Every cacheState maps to its own status line. Add a case here when a state joins the cacheState
// constants, or the run prints a bare "TEXT: " line for it.
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

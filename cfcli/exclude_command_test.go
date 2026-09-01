package main

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
)

func TestUpdateExclusionsAcceptsRawAndPrefixedFoundEvents(t *testing.T) {
	tests := []struct {
		name    string
		encoded string
	}{
		{
			name:    "raw JSON",
			encoded: `{"type":"found","found":"SECRET","file":"src/file.txt"}`,
		},
		{
			name:    "JSON prefix",
			encoded: ` JSON: {"type":"found","found":"SECRET","file":"src/file.txt"} `,
		},
		{
			name: "Base64 prefix",
			encoded: "BASE64:" + base64.RawURLEncoding.EncodeToString(
				[]byte(`{"type":"found","found":"SECRET","file":"src/file.txt"}`),
			),
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()

			reportPath, err := updateExclusions(root, test.encoded)

			if err != nil {
				t.Fatal(err)
			}
			content, err := os.ReadFile(reportPath)
			if err != nil {
				t.Fatal(err)
			}
			if len(content) == 0 || content[len(content)-1] != '\n' {
				t.Fatalf("report does not end with a newline: %q", content)
			}
			var report grandReport
			if err := json.Unmarshal(content, &report); err != nil {
				t.Fatal(err)
			}
			want := []grandReportExclusion{{
				TextHash: testSHA256("SECRET"),
				FileHash: testSHA256("src/file.txt"),
			}}
			if !reflect.DeepEqual(report.Exclusions, want) {
				t.Fatalf("exclusions = %#v, want %#v", report.Exclusions, want)
			}
		})
	}
}

func TestUpdateExclusionsAcceptsFormattedEmptyReport(t *testing.T) {
	root := t.TempDir()
	writeGrandReport(t, root, "{\n\n}\n")

	reportPath, err := updateExclusions(
		root,
		`{"type":"found","found":"SECRET","file":"src/file.txt"}`,
	)

	if err != nil {
		t.Fatal(err)
	}
	content, err := os.ReadFile(reportPath)
	if err != nil {
		t.Fatal(err)
	}
	var report grandReport
	if err := json.Unmarshal(content, &report); err != nil {
		t.Fatal(err)
	}
	want := []grandReportExclusion{{
		TextHash: testSHA256("SECRET"),
		FileHash: testSHA256("src/file.txt"),
	}}
	if !reflect.DeepEqual(report.Exclusions, want) {
		t.Fatalf("exclusions = %#v, want %#v", report.Exclusions, want)
	}
}

func TestUpdateExclusionsPreservesDeduplicatesAndSorts(t *testing.T) {
	root := t.TempDir()
	duplicateTextHash := testSHA256("new")
	duplicateFileHash := testSHA256("new.txt")
	writeGrandReport(t, root, fmt.Sprintf(`{"exclusions":[
		{"t-hash":%q,"f-hash":%q},
		{"t-hash":%q,"f-hash":%q},
		{"t-hash":"earlier","f-hash":"0000"}
	]}`, duplicateTextHash, duplicateFileHash, duplicateTextHash, duplicateFileHash))

	reportPath, err := updateExclusions(root, `{"type":"found","found":"new","file":"new.txt"}`)

	if err != nil {
		t.Fatal(err)
	}
	content, err := os.ReadFile(reportPath)
	if err != nil {
		t.Fatal(err)
	}
	var report grandReport
	if err := json.Unmarshal(content, &report); err != nil {
		t.Fatal(err)
	}
	if !sort.SliceIsSorted(report.Exclusions, func(i, j int) bool {
		if report.Exclusions[i].FileHash == report.Exclusions[j].FileHash {
			return report.Exclusions[i].TextHash < report.Exclusions[j].TextHash
		}
		return report.Exclusions[i].FileHash < report.Exclusions[j].FileHash
	}) {
		t.Fatalf("exclusions are not sorted: %#v", report.Exclusions)
	}
	if len(report.Exclusions) != 2 {
		t.Fatalf("exclusions = %#v, want one existing and one deduplicated addition", report.Exclusions)
	}
}

func TestUpdateExclusionsRejectsInvalidEventsWithoutCreatingReport(t *testing.T) {
	tests := []struct {
		name    string
		encoded string
	}{
		{name: "unsupported type", encoded: `{"type":"allowed","found":"SECRET","file":"file.txt"}`},
		{name: "invalid JSON", encoded: `{invalid`},
		{name: "missing type", encoded: `{"found":"SECRET","file":"file.txt"}`},
		{name: "missing found", encoded: `{"type":"found","file":"file.txt"}`},
		{name: "missing file", encoded: `{"type":"found","found":"SECRET"}`},
		{name: "empty found", encoded: `{"type":"found","found":"","file":"file.txt"}`},
		{name: "non-string file", encoded: `{"type":"found","found":"SECRET","file":42}`},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()
			reportPath := filepath.Join(root, ".qubership", "grand-report.json")

			_, err := updateExclusions(root, test.encoded)

			if err == nil || !strings.Contains(err.Error(), "No files were changed.") {
				t.Fatalf("error = %v, want unchanged-file error", err)
			}
			if _, statErr := os.Stat(reportPath); !os.IsNotExist(statErr) {
				t.Fatalf("report was created; stat error = %v", statErr)
			}
		})
	}
}

func TestUpdateExclusionsLeavesMalformedReportUnchanged(t *testing.T) {
	root := t.TempDir()
	original := []byte("{invalid\n")
	reportPath := writeGrandReport(t, root, string(original))

	_, err := updateExclusions(root, `{"type":"found","found":"SECRET","file":"file.txt"}`)

	if err == nil || !strings.Contains(err.Error(), "No files were changed.") {
		t.Fatalf("error = %v, want unchanged-file error", err)
	}
	content, readErr := os.ReadFile(reportPath)
	if readErr != nil {
		t.Fatal(readErr)
	}
	if !reflect.DeepEqual(content, original) {
		t.Fatalf("report = %q, want original %q", content, original)
	}
}

func TestUpdateExclusionsRejectsInvalidReportShapes(t *testing.T) {
	tests := []struct {
		name    string
		content string
	}{
		{name: "null exclusions", content: `{"exclusions":null}`},
		{name: "unknown root field", content: `{"exclusions":[],"unknown":true}`},
		{name: "missing text hash", content: `{"exclusions":[{"f-hash":"file"}]}`},
		{name: "reversed hash fields", content: `{"exclusions":[{"f-hash":"file","t-hash":"text"}]}`},
		{name: "unknown exclusion field", content: `{"exclusions":[{"t-hash":"text","f-hash":"file","unknown":true}]}`},
		{name: "duplicate exclusion field", content: `{"exclusions":[{"t-hash":"first","t-hash":"second","f-hash":"file"}]}`},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()
			original := []byte(test.content)
			reportPath := writeGrandReport(t, root, test.content)

			_, err := updateExclusions(root, `{"type":"found","found":"SECRET","file":"file.txt"}`)

			if err == nil || !strings.Contains(err.Error(), "No files were changed.") {
				t.Fatalf("error = %v, want unchanged-file error", err)
			}
			content, readErr := os.ReadFile(reportPath)
			if readErr != nil {
				t.Fatal(readErr)
			}
			if !reflect.DeepEqual(content, original) {
				t.Fatalf("report = %q, want original %q", content, original)
			}
		})
	}
}

func TestUpdateExclusionsRequiresExistingFolder(t *testing.T) {
	parent := t.TempDir()
	missingRoot := filepath.Join(parent, "missing")

	_, err := updateExclusions(missingRoot, `{"type":"found","found":"SECRET","file":"file.txt"}`)

	if err == nil || !strings.Contains(err.Error(), "No files were changed.") {
		t.Fatalf("error = %v, want unchanged-file error", err)
	}
	if _, statErr := os.Stat(missingRoot); !os.IsNotExist(statErr) {
		t.Fatalf("root was created; stat error = %v", statErr)
	}
}

func TestUpdateExclusionsRejectsFileAsFolder(t *testing.T) {
	rootFile := filepath.Join(t.TempDir(), "root.txt")
	if err := os.WriteFile(rootFile, []byte("content"), 0o600); err != nil {
		t.Fatal(err)
	}

	_, err := updateExclusions(rootFile, `{"type":"found","found":"SECRET","file":"file.txt"}`)

	if err == nil || !strings.Contains(err.Error(), "is not a directory") {
		t.Fatalf("error = %v, want directory error", err)
	}
}

func TestRunExcludeUpdatesReportWithoutScanDependencies(t *testing.T) {
	root := t.TempDir()
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{"exclude", root, `JSON: {"type":"found","found":"SECRET","file":"src/file.txt"}`},
		&stdout,
		&stderr,
		appDependencies{},
	)

	reportPath := filepath.Join(root, ".qubership", "grand-report.json")
	if exitCode != exitClean {
		t.Fatalf("exit code = %d, want %d; stderr = %q", exitCode, exitClean, stderr.String())
	}
	wantOutput := currentAppVersionOutput() + fmt.Sprintf("TEXT: Exclusions file was updated: %s\n", reportPath)
	if stdout.String() != wantOutput {
		t.Fatalf("stdout = %q", stdout.String())
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q", stderr.String())
	}
}

func TestRunExcludeRejectsUnsupportedType(t *testing.T) {
	root := t.TempDir()
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{"exclude", root, `{"type":"allowed","found":"SECRET","file":"file.txt"}`},
		&stdout,
		&stderr,
		appDependencies{},
	)

	want := "TEXT: Cannot exclude event type \"allowed\": supported types are \"found\", \"file\", and \"folder\". No files were changed.\n"
	if exitCode != exitFailure || stdout.String() != currentAppVersionOutput() || stderr.String() != want {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q; want stderr %q", exitCode, stdout.String(), stderr.String(), want)
	}
	if _, err := os.Stat(filepath.Join(root, ".qubership", "grand-report.json")); !os.IsNotExist(err) {
		t.Fatalf("report was created; stat error = %v", err)
	}
}

func TestUpdateExclusionSupportsFoundFileAndFolderTargets(t *testing.T) {
	tests := []struct {
		name     string
		encoded  string
		textHash string
		fileHash string
	}{
		{
			name:     "found",
			encoded:  `{"type":"found","found":"SECRET","file":"src/file.txt"}`,
			textHash: testSHA256("SECRET"),
			fileHash: testSHA256("src/file.txt"),
		},
		{
			name:     "file",
			encoded:  `{"type":"file","file":"src\\generated/../file.txt"}`,
			textHash: fullPathExclusionHash,
			fileHash: testSHA256("src/file.txt"),
		},
		{
			name:     "folder",
			encoded:  `{"type":"folder","folder":"src/generated"}`,
			textHash: fullPathExclusionHash,
			fileHash: testSHA256("src/generated"),
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()

			change, err := updateExclusion(root, test.encoded, exclusionAdd)

			if err != nil {
				t.Fatal(err)
			}
			if !change.Changed {
				t.Fatal("first add did not report a change")
			}
			content, err := os.ReadFile(change.ReportPath)
			if err != nil {
				t.Fatal(err)
			}
			var report grandReport
			if err := json.Unmarshal(content, &report); err != nil {
				t.Fatal(err)
			}
			want := []grandReportExclusion{{TextHash: test.textHash, FileHash: test.fileHash}}
			if !reflect.DeepEqual(report.Exclusions, want) {
				t.Fatalf("exclusions = %#v, want %#v", report.Exclusions, want)
			}
		})
	}
}

func TestUpdateExclusionAddAndRemoveAreIdempotent(t *testing.T) {
	root := t.TempDir()
	event := `{"type":"file","file":"src/file.txt"}`

	firstAdd, err := updateExclusion(root, event, exclusionAdd)
	if err != nil || !firstAdd.Changed {
		t.Fatalf("first add = %#v, error = %v", firstAdd, err)
	}
	secondAdd, err := updateExclusion(root, event, exclusionAdd)
	if err != nil || secondAdd.Changed {
		t.Fatalf("second add = %#v, error = %v", secondAdd, err)
	}
	firstRemove, err := updateExclusion(root, event, exclusionRemove)
	if err != nil || !firstRemove.Changed {
		t.Fatalf("first remove = %#v, error = %v", firstRemove, err)
	}
	secondRemove, err := updateExclusion(root, event, exclusionRemove)
	if err != nil || secondRemove.Changed {
		t.Fatalf("second remove = %#v, error = %v", secondRemove, err)
	}
}

func TestRunExcludeSupportsExplicitOperations(t *testing.T) {
	root := t.TempDir()
	event := `{"type":"folder","folder":"generated"}`

	for _, test := range []struct {
		operation string
		message   string
	}{
		{operation: "add", message: "Exclusion added"},
		{operation: "add", message: "Exclusion already exists"},
		{operation: "remove", message: "Exclusion removed"},
		{operation: "remove", message: "Exclusion does not exist"},
	} {
		var stdout bytes.Buffer
		var stderr bytes.Buffer

		exitCode := runExcludeCommand(
			[]string{"exclude", test.operation, root, event},
			&stdout,
			&stderr,
		)

		if exitCode != exitClean || stderr.Len() != 0 || !strings.Contains(stdout.String(), test.message) {
			t.Fatalf(
				"operation %q: exit = %d, stdout = %q, stderr = %q",
				test.operation,
				exitCode,
				stdout.String(),
				stderr.String(),
			)
		}
	}
}

func TestRunExcludeLegacyFormAcceptsOnlyFoundTargets(t *testing.T) {
	root := t.TempDir()
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runExcludeCommand(
		[]string{"exclude", root, `{"type":"file","file":"src/file.txt"}`},
		&stdout,
		&stderr,
	)

	if exitCode != exitFailure || stdout.Len() != 0 ||
		!strings.Contains(stderr.String(), "legacy exclude form accepts only type \"found\"") {
		t.Fatalf("exit = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	if _, err := os.Stat(filepath.Join(root, ".qubership", "grand-report.json")); !os.IsNotExist(err) {
		t.Fatalf("report was created; stat error = %v", err)
	}
}

func TestUpdateExclusionRejectsPathsOutsideRoot(t *testing.T) {
	for _, encoded := range []string{
		`{"type":"file","file":"../outside.txt"}`,
		`{"type":"folder","folder":"/absolute"}`,
		`{"type":"file","file":"C:\\absolute.txt"}`,
	} {
		root := t.TempDir()

		_, err := updateExclusion(root, encoded, exclusionAdd)

		if err == nil || !strings.Contains(err.Error(), "No files were changed.") {
			t.Fatalf("event %s: error = %v, want unchanged-file error", encoded, err)
		}
	}
}

func TestRunExcludeRejectsInvalidInvocation(t *testing.T) {
	tests := []struct {
		name string
		args func(string) []string
	}{
		{
			name: "invalid JSON",
			args: func(root string) []string {
				return []string{"exclude", root, `{invalid`}
			},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := t.TempDir()
			var stdout bytes.Buffer
			var stderr bytes.Buffer

			exitCode := runWithDependencies(
				context.Background(),
				test.args(root),
				&stdout,
				&stderr,
				appDependencies{},
			)

			if exitCode != exitFailure || stdout.String() != currentAppVersionOutput() ||
				!strings.HasPrefix(stderr.String(), "TEXT:") {
				t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
			}
			if _, err := os.Stat(filepath.Join(root, ".qubership", "grand-report.json")); !os.IsNotExist(err) {
				t.Fatalf("report was created; stat error = %v", err)
			}
		})
	}
}

func TestRunExcludeLeavesMalformedReportUnchanged(t *testing.T) {
	root := t.TempDir()
	original := []byte("{invalid\n")
	reportPath := writeGrandReport(t, root, string(original))
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{"exclude", root, `{"type":"found","found":"SECRET","file":"file.txt"}`},
		&stdout,
		&stderr,
		appDependencies{},
	)

	if exitCode != exitFailure || stdout.String() != currentAppVersionOutput() ||
		!strings.Contains(stderr.String(), "No files were changed.") {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	content, err := os.ReadFile(reportPath)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(content, original) {
		t.Fatalf("report = %q, want original %q", content, original)
	}
}

func TestIsExcludeCommandDoesNotReserveTwoArgumentScan(t *testing.T) {
	if isExcludeCommand([]string{"root", "exclude"}) {
		t.Fatal("two-argument scan was treated as an exclude command")
	}
}

func TestIsExcludeCommandUsesFirstArgument(t *testing.T) {
	if !isExcludeCommand([]string{"exclude", "root", `{}`}) {
		t.Fatal("subcommand-first invocation was not treated as an exclude command")
	}
	if isExcludeCommand([]string{"root", "exclude", `{}`}) {
		t.Fatal("removed folder-first invocation was treated as an exclude command")
	}
}

func TestRunScansWithListFileNamedExclude(t *testing.T) {
	root := initRepository(t)
	writeTestFile(t, root, "scanned.txt", "safe")
	writeTestFile(t, root, "exclude", "scanned.txt\n")
	previousDirectory, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chdir(root); err != nil {
		t.Fatal(err)
	}
	defer func() {
		if err := os.Chdir(previousDirectory); err != nil {
			t.Errorf("restore working directory: %v", err)
		}
	}()
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{root, "exclude"},
		&stdout,
		&stderr,
		testAppDependencies(t, "VERSION=1.0\n", "test-password"),
	)

	if exitCode != exitClean || !strings.Contains(stdout.String(), "TEXT: Total files scanned 1\n") {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
}

func TestRunRejectsRemovedFolderFirstExcludeSyntax(t *testing.T) {
	root := t.TempDir()
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{root, "exclude", `{"type":"found","found":"SECRET","file":"file.txt"}`},
		&stdout,
		&stderr,
		appDependencies{},
	)

	if exitCode != exitFailure || stdout.String() != currentAppVersionOutput() ||
		!strings.Contains(stderr.String(), "TEXT: Usage:") {
		t.Fatalf("exit code = %d, stdout = %q, stderr = %q", exitCode, stdout.String(), stderr.String())
	}
	if _, err := os.Stat(filepath.Join(root, ".qubership", "grand-report.json")); !os.IsNotExist(err) {
		t.Fatalf("report was created; stat error = %v", err)
	}
}

func TestFormatExcludeCommandsForAllShells(t *testing.T) {
	root := filepath.Join(t.TempDir(), "repo's folder")
	if err := os.Mkdir(root, 0o755); err != nil {
		t.Fatal(err)
	}
	event := finding{
		Type:  "found",
		Key:   "EMAIL",
		Found: "it's secret",
		Line:  7,
		File:  "docs/a'b.txt",
	}
	encoded := `JSON: {"type":"found","key":"EMAIL","found":"it's secret","line":7,"file":"docs/a'b.txt"}`

	commands, err := formatExcludeCommands(root, event)
	if err != nil {
		t.Fatal(err)
	}
	if len(commands) != 3 {
		t.Fatalf("command count = %d, want 3", len(commands))
	}
	want := []shellCommand{
		{
			Label: "POSIX",
			Command: "cfcli exclude " +
				"'" + strings.ReplaceAll(root, "'", "'\"'\"'") + "' " +
				"'" + strings.ReplaceAll(encoded, "'", "'\"'\"'") + "'",
		},
		{
			Label: "PowerShell",
			Command: "cfcli exclude " +
				"'" + strings.ReplaceAll(root, "'", "''") + "' " +
				"'" + strings.ReplaceAll(encoded, "'", "''") + "'",
		},
		{
			Label:   "cmd.exe",
			Command: `cfcli exclude "` + root + `" "` + strings.ReplaceAll(encoded, `"`, `\"`) + `"`,
		},
	}
	if !reflect.DeepEqual(commands, want) {
		t.Fatalf("commands = %#v, want %#v", commands, want)
	}
}

func TestQuoteCmdArgumentUsesWindowsArgvRules(t *testing.T) {
	tests := []struct {
		value string
		want  string
	}{
		{value: `simple`, want: `"simple"`},
		{value: `a"b`, want: `"a\"b"`},
		{value: `a\"b`, want: `"a\\\"b"`},
		{value: `a\\"b`, want: `"a\\\\\"b"`},
		{value: `a\`, want: `"a\\"`},
		{value: `a\\`, want: `"a\\\\"`},
		{value: `Hello`, want: `"Hello"`},
	}
	for _, test := range tests {
		if got := quoteCmdArgument(test.value); got != test.want {
			t.Fatalf("quoteCmdArgument(%q) = %q, want %q", test.value, got, test.want)
		}
	}
}

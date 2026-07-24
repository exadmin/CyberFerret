# cfcli help and quick exclusion command implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add expanded CLI help, move `exclude` before `FOLDER_PATH`, and print an OS-quoted exclusion command after a
quick-mode finding.

**Architecture:** Centralize help emission and dispatch `exclude` when it is the first argument. Build one `finding`
value for each ordinary match, serialize it for the existing JSON line, and pass the same value to a pure command
formatter that selects PowerShell or POSIX quoting from an explicit OS argument.

**Tech Stack:** Go 1.21, standard-library JSON/runtime/path packages, Go `testing`

## Global constraints

- Support only `cfcli exclude FOLDER_PATH JSON_OBJECT`; remove the folder-first exclusion form.
- Print expanded `TEXT:` help for no arguments and invalid argument counts.
- Preserve the first quick finding line as the complete `JSON:` event.
- Print the exclusion command on the following `TEXT:` line.
- Use PowerShell quoting on Windows and POSIX quoting on every other OS.
- Preserve immediate quick-mode termination and JSON-mode output.
- Preserve unrelated worktree changes.

---

### Task 1: Move exclude dispatch and expand help

**Files:**

- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`
- Modify: `cfcli/options.go`
- Modify: `cfcli/options_test.go`
- Modify: `cfcli/exclude_command.go`
- Modify: `cfcli/exclude_command_test.go`

**Interfaces:**

- Produces: `writeHelp(output *lineOutput) error`.
- Produces: `isExcludeCommand(args []string) bool` for first-token dispatch.
- Consumes: existing `runExcludeCommand` and `updateExclusions`.

- [ ] **Step 1: Write failing help and dispatch tests**

Add run-level tests for no arguments and incomplete exclusion forms:

```go
func TestRunPrintsExpandedHelpForInsufficientArguments(t *testing.T) {
	for _, args := range [][]string{
		nil,
		{"exclude"},
		{"exclude", "root"},
	} {
		var stdout bytes.Buffer
		var stderr bytes.Buffer

		exitCode := runWithDependencies(context.Background(), args, &stdout, &stderr, appDependencies{})

		if exitCode != 1 || stdout.Len() != 0 {
			t.Fatalf("run(%q) exit code = %d, stdout = %q", args, exitCode, stdout.String())
		}
		for _, expected := range []string{
			"TEXT: Usage:\n",
			"TEXT:   cfcli exclude FOLDER_PATH JSON_OBJECT\n",
			"TEXT: The exclude command adds a found event",
			"TEXT: JSON_OBJECT may start with \"JSON:\"",
		} {
			if !strings.Contains(stderr.String(), expected) {
				t.Fatalf("run(%q) stderr = %q, want %q", args, stderr.String(), expected)
			}
		}
	}
}
```

Update the successful exclude test to call:

```go
[]string{"exclude", root, `JSON: {"type":"found","found":"SECRET","file":"src/file.txt"}`}
```

Add a regression test asserting that `[]string{root, "exclude", jsonObject}` returns help and does not create a report.
Keep `TestIsExcludeCommandDoesNotReserveTwoArgumentScan`.

- [ ] **Step 2: Run focused tests and verify RED**

Run from `cfcli`:

```powershell
$env:GOCACHE=(Resolve-Path 'tmp\go-cache')
$env:GOTMPDIR=(Resolve-Path 'tmp\go-tmp')
go test ./... -run 'TestRunPrintsExpandedHelp|TestRunExclude|TestIsExcludeCommand' -count=1
```

Expected: FAIL because dispatch still expects `FOLDER_PATH exclude JSON_OBJECT` and help is only a usage string.

- [ ] **Step 3: Implement first-token dispatch and help writer**

Use first-token command detection:

```go
func isExcludeCommand(args []string) bool {
	return len(args) > 0 && args[0] == "exclude"
}
```

Update `runExcludeCommand` to require three arguments and pass `args[1]` and `args[2]` to `updateExclusions`. On an
invalid count, call `writeHelp(errorOutput)` and return 1.

Replace the embedded multi-line `usage` value with a help writer:

```go
func writeHelp(output *lineOutput) error {
	lines := []string{
		"Usage:",
		"  cfcli [--mode=quick|--mode=json] [--verbose=true|--verbose=false] FOLDER_PATH [PATH_TO_LIST_OF_FILES]",
		"  cfcli exclude FOLDER_PATH JSON_OBJECT",
		"",
		"The exclude command adds a found event to FOLDER_PATH/.qubership/grand-report.json.",
		"JSON_OBJECT may start with \"JSON:\" and must have type \"found\", found, and file fields.",
	}
	for _, line := range lines {
		if err := output.text("%s", line); err != nil {
			return err
		}
	}
	return nil
}
```

Introduce a private `usageError` in `options.go`. Return it for missing or excess positional arguments. Remove appended
usage text from other option errors. In `runWithDependencies`, print a specific non-usage error first, then call
`writeHelp`; for `usageError`, print only help.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```powershell
gofmt -w app.go app_test.go options.go options_test.go exclude_command.go exclude_command_test.go
go test ./... -run 'TestRunPrintsExpandedHelp|TestRunExclude|TestIsExcludeCommand|TestParseOptions' -count=1
```

Expected: PASS.

- [ ] **Step 5: Verify Task 1 regressions**

Run:

```powershell
go test ./... -run 'TestRunRejectsInvalidArgumentCounts|TestRunReportsRuntimeErrors|TestRunExclude|TestParseOptions' -count=1
```

Expected: all relevant tests pass except the known unrelated
`TestRunReportsRuntimeErrors/not_Git_repository` baseline failure if it remains.

---

### Task 2: Print an OS-quoted exclusion command in quick mode

**Files:**

- Modify: `cfcli/exclude_command.go`
- Modify: `cfcli/exclude_command_test.go`
- Modify: `cfcli/scanner.go`
- Modify: `cfcli/scanner_test.go`
- Modify: `cfcli/app_pipeline_test.go`
- Modify: `cfcli/README.md`

**Interfaces:**

- Produces: `newFinding(key, exact string, line int, relativePath string) finding`.
- Produces: `formatExcludeCommand(root string, event finding, goos string) (string, error)`.
- Produces: `quoteShellArgument(value, goos string) string`.
- Consumes: `runtime.GOOS`, `lineOutput.json`, and `lineOutput.text`.

- [ ] **Step 1: Write failing quoting tests**

Add pure formatter tests:

```go
func TestFormatExcludeCommandQuotesForPOSIXAndPowerShell(t *testing.T) {
	event := finding{Type: "found", Key: "EMAIL", Found: "it's secret", Line: 7, File: "docs/a'b.txt"}
	root := filepath.Join(t.TempDir(), "repo's folder")

	tests := []struct {
		name string
		goos string
		wantPathFragment string
		wantJSONFragment string
	}{
		{
			name: "POSIX",
			goos: "linux",
			wantPathFragment: `'repo'"'"'s folder'`,
			wantJSONFragment: `"it'"'"'s secret"`,
		},
		{
			name: "PowerShell",
			goos: "windows",
			wantPathFragment: `'repo''s folder'`,
			wantJSONFragment: `"it''s secret"`,
		},
	}
```

For each case, call `formatExcludeCommand`, assert it starts with `cfcli exclude `, contains the expected escaped path
and JSON fragments, contains `JSON: {"type":"found"`, and does not contain `TEXT:`.

- [ ] **Step 2: Run formatter tests and verify RED**

Run:

```powershell
go test ./... -run TestFormatExcludeCommandQuotesForPOSIXAndPowerShell -count=1
```

Expected: FAIL because `formatExcludeCommand` is undefined.

- [ ] **Step 3: Implement the pure formatter**

Add:

```go
func quoteShellArgument(value, goos string) string {
	if goos == "windows" {
		return "'" + strings.ReplaceAll(value, "'", "''") + "'"
	}
	return "'" + strings.ReplaceAll(value, "'", "'\"'\"'") + "'"
}

func formatExcludeCommand(root string, event finding, goos string) (string, error) {
	absoluteRoot, err := filepath.Abs(root)
	if err != nil {
		return "", fmt.Errorf("resolve repository path: %w", err)
	}
	encoded, err := json.Marshal(event)
	if err != nil {
		return "", fmt.Errorf("encode finding: %w", err)
	}
	return "cfcli exclude " +
		quoteShellArgument(filepath.Clean(absoluteRoot), goos) + " " +
		quoteShellArgument("JSON: "+string(encoded), goos), nil
}
```

- [ ] **Step 4: Run formatter tests and verify GREEN**

Run:

```powershell
gofmt -w exclude_command.go exclude_command_test.go
go test ./... -run TestFormatExcludeCommandQuotesForPOSIXAndPowerShell -count=1
```

Expected: PASS.

- [ ] **Step 5: Change the quick expectation and verify RED**

Update `TestScanFilesQuickStopsAtFirstNonAllowedFinding` to expect two exact lines. Build the second line with
`formatExcludeCommand(root, newFinding("SECRET", "SECRET", 1, "a.txt"), runtime.GOOS)` so the test is portable:

```go
wantCommand, err := formatExcludeCommand(root, newFinding("SECRET", "SECRET", 1, "a.txt"), runtime.GOOS)
if err != nil {
	t.Fatal(err)
}
want := `JSON: {"type":"found","key":"SECRET","found":"SECRET","line":1,"file":"a.txt"}` + "\n" +
	"TEXT: " + wantCommand + "\n"
```

Retain the assertions for one scanned file and absence of `SKIPPED`.

- [ ] **Step 6: Run the quick test and verify RED**

Run:

```powershell
go test ./... -run TestScanFilesQuickStopsAtFirstNonAllowedFinding -count=1
```

Expected: FAIL because quick mode prints only the JSON event.

- [ ] **Step 7: Emit the quick exclusion command**

Create one event before mode branching:

```go
event := newFinding(currentSignature.key, exact, line, relativePath)
if err := output.json(event); err != nil {
	return scanResult{}, fmt.Errorf("write finding: %w", err)
}
if mode == modeQuick {
	command, err := formatExcludeCommand(root, event, runtime.GOOS)
	if err != nil {
		return scanResult{}, fmt.Errorf("format quick exclusion command: %w", err)
	}
	if err := output.text("%s", command); err != nil {
		return scanResult{}, fmt.Errorf("write quick exclusion command: %w", err)
	}
	return scanResult{found: true, scannedCount: scannedCount}, nil
}
```

Use `newFinding` for JSON mode as well. Remove `writeFoundFinding` after all callers use the new construction path.

- [ ] **Step 8: Update affected integration expectations**

Update quick expectations in `scanner_test.go` and `app_pipeline_test.go` to require the `TEXT: cfcli exclude` line.
Keep JSON-mode assertions unchanged and add an assertion that JSON output does not contain `cfcli exclude`.

Update `cfcli/README.md` examples to use `cfcli exclude FOLDER_PATH JSON_OBJECT` and document the quick-mode command
line and OS-specific quoting.

- [ ] **Step 9: Run final verification**

Run:

```powershell
gofmt -w app.go app_test.go options.go options_test.go exclude_command.go exclude_command_test.go scanner.go `
  scanner_test.go app_pipeline_test.go
go test ./... -run 'TestRunPrintsExpandedHelp|TestRunExclude|TestFormatExcludeCommand|TestScanFilesQuick' -count=1
go vet ./...
go test ./... -count=1
git diff --check
```

Expected: focused tests and `go vet` pass. The full suite passes except for the previously observed unrelated
`TestRunReportsRuntimeErrors/not_Git_repository` failure if that baseline issue remains.

- [ ] **Step 10: Review the focused diff**

Run:

```powershell
git diff -- app.go app_test.go options.go options_test.go exclude_command.go exclude_command_test.go scanner.go `
  scanner_test.go app_pipeline_test.go README.md
```

Expected: only help, exclude dispatch, command formatting, quick output, documentation, and their tests change. A commit
is omitted because this environment does not permit writes to `.git`.

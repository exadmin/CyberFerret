# cfcli exclude command implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `cfcli FOLDER_PATH exclude JSON_OBJECT` to persist a `found` event in the FX-compatible grand report.

**Architecture:** Keep exclusion-command parsing and persistence in a focused `exclude_command.go` unit. Dispatch the
exact command form at the start of `runWithDependencies`, before any dictionary or Git work, and reuse the existing
grand-report structs and SHA-256 helper from `exclusions.go`.

**Tech Stack:** Go 1.21, standard-library JSON and filesystem packages, Go `testing`

## Global constraints

- Accept raw JSON and JSON with a `JSON:` prefix.
- Support only events whose `type` is `found`.
- Hash the exact `found` and `file` strings with SHA-256.
- Preserve, deduplicate, and sort existing exclusions like the FX module.
- Never change an existing report when input validation or report decoding fails.
- Print the updated report path through a `TEXT:` message after success.
- Preserve existing scan behavior and unrelated worktree changes.

---

### Task 1: Parse and persist found-event exclusions

**Files:**

- Create: `cfcli/exclude_command.go`
- Create: `cfcli/exclude_command_test.go`
- Modify: `cfcli/exclusions.go`

**Interfaces:**

- Consumes: `grandReport`, `grandReportExclusion`, and `sha256Hex` from `cfcli/exclusions.go`.
- Produces: `updateExclusions(root, encodedEvent string) (string, error)`.
- Produces: typed errors `unsupportedExcludeEventTypeError` and `excludeCommandError`.

- [ ] **Step 1: Write failing parser and creation tests**

Create table-driven tests that call `updateExclusions` with both accepted input forms:

```go
func TestUpdateExclusionsAcceptsRawAndPrefixedFoundEvents(t *testing.T) {
	for _, encoded := range []string{
		`{"type":"found","found":"SECRET","file":"src/file.txt"}`,
		` JSON: {"type":"found","found":"SECRET","file":"src/file.txt"} `,
	} {
		t.Run(encoded[:min(len(encoded), 8)], func(t *testing.T) {
			root := t.TempDir()
			reportPath, err := updateExclusions(root, encoded)
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
		})
	}
}
```

Use stable case names instead of deriving them from input if the repository's Go version or linter rejects that helper
expression.

- [ ] **Step 2: Run the focused test and verify RED**

Run from `cfcli`:

```powershell
$env:GOCACHE=(Resolve-Path 'tmp\go-cache')
$env:GOTMPDIR=(Resolve-Path 'tmp\go-tmp')
go test ./... -run TestUpdateExclusionsAcceptsRawAndPrefixedFoundEvents -count=1
```

Expected: FAIL because `updateExclusions` is undefined.

- [ ] **Step 3: Implement event parsing and new-report persistence**

Create `exclude_command.go` with:

```go
type excludeEvent struct {
	Type  string `json:"type"`
	Found string `json:"found"`
	File  string `json:"file"`
}

type unsupportedExcludeEventTypeError struct {
	eventType string
}

func (e *unsupportedExcludeEventTypeError) Error() string {
	return fmt.Sprintf(
		"Cannot exclude event type %q: only \"found\" is supported. No files were changed.",
		e.eventType,
	)
}

type excludeCommandError struct {
	message string
}

func (e *excludeCommandError) Error() string {
	return e.message + " No files were changed."
}

func parseExcludeEvent(encoded string) (excludeEvent, error) {
	encoded = strings.TrimSpace(encoded)
	if strings.HasPrefix(encoded, "JSON:") {
		encoded = strings.TrimSpace(strings.TrimPrefix(encoded, "JSON:"))
	}
	var event excludeEvent
	if err := json.Unmarshal([]byte(encoded), &event); err != nil {
		return excludeEvent{}, &excludeCommandError{
			message: fmt.Sprintf("Cannot parse exclude event: %v.", err),
		}
	}
	if event.Type != "found" {
		return excludeEvent{}, &unsupportedExcludeEventTypeError{eventType: event.Type}
	}
	if event.Found == "" {
		return excludeEvent{}, &excludeCommandError{message: "Cannot exclude event: \"found\" must be a nonempty string."}
	}
	if event.File == "" {
		return excludeEvent{}, &excludeCommandError{message: "Cannot exclude event: \"file\" must be a nonempty string."}
	}
	return event, nil
}
```

Implement `updateExclusions` so it validates first, resolves
`<root>/.qubership/grand-report.json`, treats a missing file as `grandReport{}`, rejects every other read or unmarshal
error, removes duplicate pairs, appends the new pair, sorts by `FileHash` and then `TextHash`, marshals with
`json.MarshalIndent`, creates the `.qubership` directory, writes a same-directory temporary file, and renames it over
the report. Always remove the temporary file with a deferred cleanup. Require `FOLDER_PATH` to exist and be a directory
before creating `.qubership`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```powershell
gofmt -w exclude_command.go exclude_command_test.go exclusions.go
go test ./... -run TestUpdateExclusionsAcceptsRawAndPrefixedFoundEvents -count=1
```

Expected: PASS.

- [ ] **Step 5: Write failing compatibility and safety tests**

Add focused tests that establish the full persistence contract:

```go
func TestUpdateExclusionsPreservesDeduplicatesAndSorts(t *testing.T) {
	root := t.TempDir()
	duplicateTextHash := testSHA256("new")
	duplicateFileHash := testSHA256("new.txt")
	writeGrandReport(t, root, fmt.Sprintf(`{"exclusions":[
		{"t-hash":%q,"f-hash":%q},
		{"t-hash":%q,"f-hash":%q},
		{"t-hash":"earlier","f-hash":"0000"}
	]}`, duplicateTextHash, duplicateFileHash, duplicateTextHash, duplicateFileHash))

	_, err := updateExclusions(root, `{"type":"found","found":"new","file":"new.txt"}`)
	if err != nil {
		t.Fatal(err)
	}

	content, err := os.ReadFile(filepath.Join(root, ".qubership", "grand-report.json"))
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
```

Add separate tests for an unsupported type, invalid JSON, missing fields, a missing root folder, and malformed existing
reports. Include syntactically valid but FX-incompatible shapes with unknown, missing, null, and duplicate fields. For
every validation failure, assert that the error contains `No files were changed.` and that a missing report remains
absent or an existing report remains byte-for-byte identical.

- [ ] **Step 6: Run the safety tests and verify RED or existing GREEN**

Run:

```powershell
go test ./... -run 'TestUpdateExclusions' -count=1
```

Expected: the new assertions fail until deduplication, sorting, and error preservation are all implemented. If the
minimal implementation from Step 3 already satisfies an assertion, retain that passing regression test.

- [ ] **Step 7: Complete persistence behavior**

Adjust `updateExclusions` only as required by the failing tests. Use exact pair equality:

```go
filtered := report.Exclusions[:0]
for _, existing := range report.Exclusions {
	if existing.TextHash != addition.TextHash || existing.FileHash != addition.FileHash {
		filtered = append(filtered, existing)
	}
}
report.Exclusions = append(filtered, addition)
sort.Slice(report.Exclusions, func(i, j int) bool {
	if report.Exclusions[i].FileHash == report.Exclusions[j].FileHash {
		return report.Exclusions[i].TextHash < report.Exclusions[j].TextHash
	}
	return report.Exclusions[i].FileHash < report.Exclusions[j].FileHash
})
```

- [ ] **Step 8: Verify Task 1**

Run:

```powershell
gofmt -w exclude_command.go exclude_command_test.go exclusions.go
go test ./... -run 'TestUpdateExclusions' -count=1
```

Expected: PASS.

---

### Task 2: Dispatch the exclude command before scanning

**Files:**

- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`
- Modify: `cfcli/options.go`
- Modify: `cfcli/options_test.go`
- Test: `cfcli/exclude_command_test.go`

**Interfaces:**

- Consumes: `updateExclusions(root, encodedEvent string) (string, error)` from Task 1.
- Produces: `isExcludeCommand(args []string) bool` and `runExcludeCommand(args []string, stdout, stderr io.Writer) int`.

- [ ] **Step 1: Write a failing successful-dispatch test**

Add a run-level test with empty dependencies to prove exclusion bypasses the scanner:

```go
func TestRunExcludeUpdatesReportWithoutScanDependencies(t *testing.T) {
	root := t.TempDir()
	var stdout bytes.Buffer
	var stderr bytes.Buffer

	exitCode := runWithDependencies(
		context.Background(),
		[]string{root, "exclude", `JSON: {"type":"found","found":"SECRET","file":"src/file.txt"}`},
		&stdout,
		&stderr,
		appDependencies{},
	)

	reportPath := filepath.Join(root, ".qubership", "grand-report.json")
	if exitCode != 0 {
		t.Fatalf("exit code = %d, want 0; stderr = %q", exitCode, stderr.String())
	}
	if stdout.String() != fmt.Sprintf("TEXT: Exclusions file was updated: %s\n", reportPath) {
		t.Fatalf("stdout = %q", stdout.String())
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr = %q", stderr.String())
	}
}
```

- [ ] **Step 2: Run the dispatch test and verify RED**

Run:

```powershell
go test ./... -run TestRunExcludeUpdatesReportWithoutScanDependencies -count=1
```

Expected: FAIL because the existing option parser rejects three positional arguments.

- [ ] **Step 3: Implement early dispatch and usage**

Change `usage` to include an alternative command line:

```go
const usage = "usage: cfcli [--mode=quick|--mode=json] [--verbose=true|--verbose=false] " +
	"FOLDER_PATH [PATH_TO_LIST_OF_FILES]\n       cfcli FOLDER_PATH exclude JSON_OBJECT"
```

At the start of `runWithDependencies`, before `parseOptions`, add:

```go
if isExcludeCommand(args) {
	return runExcludeCommand(args, stdout, stderr)
}
```

Implement:

```go
func isExcludeCommand(args []string) bool {
	return len(args) == 3 && args[1] == "exclude"
}

func runExcludeCommand(args []string, stdout, stderr io.Writer) int {
	errorOutput := newLineOutput(stderr)
	if len(args) != 3 {
		writeFatal(errorOutput, "%s", usage)
		return 1
	}
	reportPath, err := updateExclusions(args[0], args[2])
	if err != nil {
		writeFatal(errorOutput, "%v", err)
		return 1
	}
	if err := newLineOutput(stdout).text("Exclusions file was updated: %s", reportPath); err != nil {
		writeFatal(errorOutput, "Cannot write updated exclusions path: %v", err)
		return 1
	}
	return 0
}
```

- [ ] **Step 4: Run the dispatch test and verify GREEN**

Run:

```powershell
gofmt -w app.go app_test.go options.go options_test.go exclude_command.go exclude_command_test.go
go test ./... -run TestRunExcludeUpdatesReportWithoutScanDependencies -count=1
```

Expected: PASS.

- [ ] **Step 5: Write failing command-error tests**

Add table-driven run-level tests for unsupported type, invalid JSON, and malformed report. Assert exit code 1, empty
stdout, a `TEXT:` stderr message, and unchanged report bytes. Add a dispatch regression proving that the two-argument
scan form with a list filename of `exclude` is not reserved as a command. The unsupported-type case must contain:

```text
Cannot exclude event type "allowed": only "found" is supported. No files were changed.
```

- [ ] **Step 6: Run command-error tests and verify RED or existing GREEN**

Run:

```powershell
go test ./... -run 'TestRunExclude' -count=1
```

Expected: failures identify any missing dispatch validation or error wording. Cases already satisfied remain as passing
regression coverage.

- [ ] **Step 7: Complete command errors and option regressions**

Make only the changes required by Step 6. Update existing usage assertions in `app_test.go` and `options_test.go` to
accept the new two-line usage text. Add an option-parser regression test proving ordinary
`cfcli FOLDER_PATH PATH_TO_LIST_OF_FILES` parsing remains unchanged.

- [ ] **Step 8: Verify Task 2 and all Go checks**

Run:

```powershell
gofmt -w app.go app_test.go options.go options_test.go exclude_command.go exclude_command_test.go exclusions.go
go test ./... -run 'TestRunExclude|TestUpdateExclusions|TestParseOptions' -count=1
go vet ./...
go test ./... -count=1
git diff --check
```

Expected: focused tests and `go vet` pass. The full suite passes except for the previously observed unrelated
`TestRunReportsRuntimeErrors/not_Git_repository` failure if that baseline issue remains.

- [ ] **Step 9: Review the focused diff**

Run:

```powershell
git diff -- app.go app_test.go options.go options_test.go exclude_command.go exclude_command_test.go exclusions.go
```

Expected: the diff contains only exclusion command parsing, persistence, dispatch, usage text, and their tests. A commit
is omitted because this environment does not permit writes to `.git`.

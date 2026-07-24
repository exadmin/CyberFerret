# cfcli quick print details implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make quick mode print a short exclusion hint by default and print the three shell commands only when
`--print=details` is present.

**Architecture:** Parse the new option into an explicit boolean and pass it through the application into the scanner.
The scanner keeps quick-mode termination unchanged and selects either the hint or the existing command formatter.

**Tech Stack:** Go standard library, table-driven Go tests, Markdown documentation.

## Global constraints

- `--print=details` is the only accepted print value.
- Without quick mode, `--print=details` prints a warning and is ignored.
- Quick mode always stops after the first found signature.
- Detailed output preserves the POSIX, PowerShell, and cmd.exe command order.
- English CLI and documentation text follows the repository's American English style.
- Markdown body lines do not exceed 120 characters.

---

### Task 1: Parse and document the print option

**Files:**

- Modify: `cfcli/options.go`
- Modify: `cfcli/options_test.go`
- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`

**Interfaces:**

- Produces: `options.printDetails bool`
- Consumes: leading argument `--print=details`

- [ ] **Step 1: Write failing parser and help tests**

Add table cases that require `--print=details` to set `printDetails`, reject `--print=full`, and reject
`--print=details` after the root. Update the help expectation to include:

```text
cfcli [--mode=quick|--mode=json] [--print=details] [--verbose=true|--verbose=false]
```

- [ ] **Step 2: Verify the parser tests fail**

Run:

```powershell
go test ./... -run 'TestParseOptions|TestRun.*Usage' -count=1
```

Expected: FAIL because `options` has no `printDetails` field and the parser does not recognize `--print`.

- [ ] **Step 3: Implement strict option parsing and update help**

Add `printDetails bool` to `options`. In the leading-option switch, accept only the exact argument
`--print=details`; return `invalid print value %q` for other `--print=` values. Include `--print=` in the positional
option-order validation and add `[--print=details]` to the scan usage line.

- [ ] **Step 4: Verify the parser and help tests pass**

Run:

```powershell
go test ./... -run 'TestParseOptions|TestRun.*Usage' -count=1
```

Expected: PASS.

### Task 2: Select compact or detailed quick output

**Files:**

- Modify: `cfcli/scanner.go`
- Modify: `cfcli/scanner_test.go`
- Modify: `cfcli/app.go`
- Modify: `cfcli/app_pipeline_test.go`

**Interfaces:**

- Consumes: `printDetails bool` from parsed options
- Produces: exact quick hint or three shell-specific exclusion commands

- [ ] **Step 1: Write failing scanner and pipeline tests**

Update quick scanner calls to pass `printDetails`. Assert that `false` produces exactly:

```text
TEXT: To print exclusion commands, run cfcli with --mode=quick --print=details.
```

Assert that `true` produces the existing POSIX, PowerShell, and cmd.exe lines. Preserve assertions that later files and
matches are not scanned.

- [ ] **Step 2: Verify the quick-output tests fail**

Run:

```powershell
go test ./... -run 'TestScanFilesQuick|TestRunWithDependenciesQuick' -count=1
```

Expected: FAIL because the scanner does not accept or apply `printDetails`.

- [ ] **Step 3: Implement quick-output selection**

Add `printDetails bool` to `scanFilesConfigured`. Pass `parsed.printDetails` from `runWithDependencies`. After writing
the first quick JSON finding, write the three formatted commands when `printDetails` is true. Otherwise write the exact
hint. Return immediately in both branches.

- [ ] **Step 4: Verify the quick-output tests pass**

Run:

```powershell
go test ./... -run 'TestScanFilesQuick|TestRunWithDependenciesQuick' -count=1
```

Expected: PASS.

### Task 3: Warn outside quick mode and update documentation

**Files:**

- Modify: `cfcli/app.go`
- Modify: `cfcli/app_pipeline_test.go`
- Modify: `cfcli/README.md`

**Interfaces:**

- Consumes: `parsed.printDetails` and `parsed.mode`
- Produces: one warning on standard output before scanning

- [ ] **Step 1: Write a failing JSON-mode warning test**

Run the application with `--print=details` and the default JSON mode. Assert that standard output contains:

```text
TEXT: Warning: --print=details applies only to --mode=quick and will be ignored.
```

Also assert that no `cfcli exclude` command is printed and scanning continues normally.

- [ ] **Step 2: Verify the warning test fails**

Run:

```powershell
go test ./... -run 'TestRunWithDependencies.*PrintDetails' -count=1
```

Expected: FAIL because the warning is absent.

- [ ] **Step 3: Implement the warning and update README**

After parsing options, write the warning to standard output when `parsed.printDetails && parsed.mode != modeQuick`.
Document the default hint, detailed quick invocation, three command lines, and ignored JSON-mode behavior in README.

- [ ] **Step 4: Verify focused behavior**

Run:

```powershell
go test ./... -run 'TestParseOptions|TestScanFilesQuick|TestRunWithDependencies.*PrintDetails' -count=1
```

Expected: PASS.

- [ ] **Step 5: Run repository checks**

Run:

```powershell
go test ./... -count=1
go vet ./...
git diff --check
```

Expected: new and focused tests PASS; `go vet` and `git diff --check` PASS. Record the pre-existing
`TestRunReportsRuntimeErrors/not_Git_repository` failure if it remains.

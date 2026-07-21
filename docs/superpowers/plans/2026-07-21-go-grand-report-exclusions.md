# Go CLI grand-report exclusions implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply `.qubership/grand-report.json` exclusions to Go CLI file and signature scanning.

**Architecture:** Add a focused exclusion loader and immutable hash index in `exclusions.go`. Load it in the application
pipeline before file selection, then pass it to `scanFiles` for ancestor-path and exact-match checks.

**Tech Stack:** Go standard library (`crypto/sha256`, `encoding/hex`, `encoding/json`, `os`, and `path/filepath`), Go test

## Global constraints

- Add no dependencies.
- Preserve the existing CLI arguments, output prefixes, flush behavior, and exit codes.
- Normalize relative file paths to `/` before hashing.
- Continue without exclusions after a recoverable grand-report read or decode error.
- Do not count fully excluded files as scanned.

---

### Task 1: Exclusion model and loader

**Files:**

- Create: `cli-go/exclusions.go`
- Create: `cli-go/exclusions_test.go`

**Interfaces:**

- Produces: `loadExclusions(root string, warnings *lineOutput) exclusionSet`
- Produces: `exclusionSet.excludesPath(relativePath string) bool`
- Produces: `exclusionSet.excludesMatch(relativePath, exact string) bool`

- [ ] **Step 1: Write failing loader and matching tests**

Add table-driven tests that write `.qubership/grand-report.json`, hash paths and text with the test helper, then verify:

```go
if !loaded.excludesPath("ignored/nested/secret.txt") {
    t.Fatal("descendant of excluded directory was not excluded")
}
if !loaded.excludesMatch("src/secret.txt", "SECRET") {
    t.Fatal("exact finding was not excluded")
}
```

Also verify that a missing file is silent and malformed JSON prints a `TEXT:` warning with the absolute report path.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
$env:GOCACHE = 'C:\Development\GitHub\CyberFerret\cli-go\.gocache'
& 'C:\Program Files\Go\bin\go.exe' test -run 'TestLoadExclusions|TestExclusionSet' ./...
```

Expected: compilation fails because `loadExclusions` and `exclusionSet` do not exist.

- [ ] **Step 3: Implement the model and loader**

Define JSON wire structs with `json:"exclusions"`, `json:"t-hash"`, and `json:"f-hash"` tags. Resolve the report path
with `filepath.Abs(filepath.Join(root, ".qubership", "grand-report.json"))`, decode it with `encoding/json`, and index
each pair in `map[string]map[string]struct{}`. Implement a SHA-256 helper over UTF-8 Go strings.

For `excludesPath`, hash the normalized relative file path and each nonempty parent path. Return true if any indexed file
hash contains `00000000`. For `excludesMatch`, look up the exact text hash under the current file hash.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit the exclusion component**

```powershell
git add -- cli-go/exclusions.go cli-go/exclusions_test.go
git commit -m "feat(cli-go): load grand-report exclusions"
```

### Task 2: Scanner and application integration

**Files:**

- Modify: `cli-go/scanner.go`
- Modify: `cli-go/scanner_test.go`
- Modify: `cli-go/app.go`
- Modify: `cli-go/app_pipeline_test.go`

**Interfaces:**

- Consumes: `loadExclusions(root string, warnings *lineOutput) exclusionSet`
- Changes: `scanFiles` accepts an `exclusionSet` before output arguments.

- [ ] **Step 1: Write failing scanner and pipeline tests**

Add scanner tests proving that a fully excluded file and a file under an excluded directory are not read or counted.
Add tests proving that an exact finding exclusion suppresses only that finding. Add a pipeline test proving the report is
loaded before scanning and an invalid report prints a warning while scanning continues.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```powershell
$env:GOCACHE = 'C:\Development\GitHub\CyberFerret\cli-go\.gocache'
& 'C:\Program Files\Go\bin\go.exe' test -run 'TestScanFiles.*Exclusion|TestRunWithDependencies.*Exclusion' ./...
```

Expected: FAIL because `scanFiles` does not apply exclusions and `runWithDependencies` does not load the report.

- [ ] **Step 3: Implement scanner integration**

Load exclusions after the progress message and before `selectFiles`. In `scanFiles`, calculate the normalized relative
path before reading each file. Skip the file when `excludesPath` returns true. Increment `scannedCount` only after a
successful read. After the dictionary allowed-value check, skip a match when `excludesMatch` returns true.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2. Expected: PASS.

- [ ] **Step 5: Commit integration**

```powershell
git add -- cli-go/app.go cli-go/app_pipeline_test.go cli-go/scanner.go cli-go/scanner_test.go
git commit -m "feat(cli-go): apply grand-report exclusions"
```

### Task 3: Documentation and verification

**Files:**

- Modify: `cli-go/README.md`
- Modify: `docs/superpowers/specs/2026-07-21-go-grand-report-exclusions-design.md`
- Modify: `docs/superpowers/plans/2026-07-21-go-grand-report-exclusions.md`

**Interfaces:** None.

- [ ] **Step 1: Document the exclusion file**

Add a concise README section describing the report path, SHA-256 inputs, `00000000` inheritance, malformed-file warning,
and exclusion of skipped files from the scanned count.

- [ ] **Step 2: Format and run the full test suite**

Run:

```powershell
& 'C:\Program Files\Go\bin\gofmt.exe' -w exclusions.go exclusions_test.go scanner.go scanner_test.go app.go app_pipeline_test.go
$env:GOCACHE = 'C:\Development\GitHub\CyberFerret\cli-go\.gocache'
& 'C:\Program Files\Go\bin\go.exe' test -count=1 ./...
& 'C:\Program Files\Go\bin\go.exe' vet ./...
& 'C:\Program Files\Go\bin\go.exe' build ./...
```

Expected: every command exits 0 with no warnings.

- [ ] **Step 3: Inspect the final diff**

Run `git diff --check` and `git status --short`. Expected: no whitespace errors and only intended Go and documentation
files changed.

- [ ] **Step 4: Commit documentation**

```powershell
git add -- cli-go/README.md docs/superpowers/specs/2026-07-21-go-grand-report-exclusions-design.md \
    docs/superpowers/plans/2026-07-21-go-grand-report-exclusions.md
git commit -m "docs(cli-go): describe grand-report exclusions"
```

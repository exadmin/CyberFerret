# Go CLI exclusion output implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Report applied grand-report exclusions in JSON mode while preserving silent exclusion behavior in quick mode.

**Architecture:** Extend the exclusion lookup to return every matching file or ancestor path. The scanner emits typed
JSON events, deduplicates full-path events by relative path, and continues to skip excluded subtrees before file reads.

**Tech Stack:** Go standard library and Go test

## Global constraints

- Add no dependencies.
- Emit exclusion events only in JSON mode.
- Flush every event through `lineOutput`.
- Do not treat exclusion events as findings or change exit codes because of them.
- Do not read or count files below a fully excluded directory.

---

### Task 1: Return matched path exclusions

**Files:**

- Modify: `cli-go/exclusions.go`
- Modify: `cli-go/exclusions_test.go`

**Interfaces:**

- Produce: `exclusionSet.excludedPaths(relativePath string) []string`
- Preserve: `exclusionSet.excludesPath(relativePath string) bool`

- [ ] **Step 1: Add a failing test**

Test a file below two registered excluded ancestors and assert that `excludedPaths` returns normalized relative paths
from the outer directory to the inner directory without duplicates.

- [ ] **Step 2: Verify RED**

Run `go test -run TestExclusionSetReturnsExcludedPaths ./...`. Expected: compilation fails because `excludedPaths` does
not exist.

- [ ] **Step 3: Implement the lookup**

Build the file and ancestor candidates once, hash each candidate, and return candidates containing `00000000`.
Implement `excludesPath` as `len(e.excludedPaths(relativePath)) > 0`.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 2: Emit JSON exclusion events

**Files:**

- Modify: `cli-go/scanner.go`
- Modify: `cli-go/scanner_test.go`

**Interfaces:**

- Add: `excludedPathEvent` with JSON fields `type` and `file`.
- Add `Type string` with JSON name `type` to excluded signature output without changing ordinary finding output.

- [ ] **Step 1: Add failing JSON-mode tests**

Verify one event for an excluded file, one deduplicated event for an excluded directory with multiple descendants, and
an excluded signature event containing `type`, `key`, `found`, `position`, and `file`. Assert excluded files are not
read or counted and excluded events do not set `scanResult.found`.

- [ ] **Step 2: Add a failing quick-mode test**

Verify quick mode applies full-path and signature exclusions without emitting `JSON:` lines, then stops only on the
first nonexcluded match.

- [ ] **Step 3: Verify RED**

Run `go test -run 'TestScanFilesWithExclusions.*Output' ./...`. Expected: FAIL because exclusions are silent.

- [ ] **Step 4: Implement event output**

Track emitted full-path exclusions in `map[string]struct{}`. In JSON mode, emit each matched path once before skipping
the file. When an exact match is excluded, emit a typed finding event and continue without setting `foundAny`.

- [ ] **Step 5: Verify GREEN**

Run the command from Step 3. Expected: PASS.

### Task 3: Document and verify

**Files:**

- Modify: `cli-go/README.md`

**Interfaces:** None.

- [ ] **Step 1: Document JSON exclusion events**

Add both JSON shapes, JSON-only behavior, directory-event deduplication, and unchanged exit-code semantics.

- [ ] **Step 2: Run complete verification**

Run `gofmt` on changed Go files, followed by `go test -count=1 ./...`, `go vet ./...`, `go build ./...`, and
`git diff --check`. Expected: every command exits 0.

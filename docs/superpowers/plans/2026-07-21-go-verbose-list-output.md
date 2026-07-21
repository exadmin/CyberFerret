# CF CLI verbose list output implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in JSON progress events for folders and files processed by the Go scanner.

**Architecture:** Extend CLI options with a boolean verbose flag. Keep existing scanner entry points as nonverbose
wrappers and add a configured scanner that emits deduplicated folder events and per-file events before exclusion or read.

**Tech Stack:** Go standard library and Go test

## Global constraints

- Add no dependencies.
- Accept only `--verbose=true` and `--verbose=false` before positional arguments.
- Do not emit the root folder.
- Emit parent folders before children and each folder once.
- Never read, scan, count, or list descendant files under a fully excluded directory.
- Preserve nonverbose behavior.

---

### Task 1: Parse the verbose option

**Files:**

- Modify: `cfcli/options.go`
- Modify: `cfcli/options_test.go`
- Modify: `cfcli/app.go`

**Interfaces:**

- Add: `options.verbose bool`

- [ ] **Step 1: Add failing option tests**

Test default false, explicit true and false, both option orders, invalid values, and placement after `FOLDER_PATH`.

- [ ] **Step 2: Verify RED**

Run `go test -run TestParseOptions ./...`. Expected: FAIL because verbose is not parsed.

- [ ] **Step 3: Implement leading-option parsing**

Consume leading `--mode=` and `--verbose=` arguments in a loop, validate their exact values, and reject either option
after the first positional argument. Pass `parsed.verbose` into the configured scanner from `runWithDependencies`.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 2: Emit verbose list events

**Files:**

- Modify: `cfcli/scanner.go`
- Modify: `cfcli/scanner_test.go`

**Interfaces:**

- Add: `listPathEvent` with `type`, optional `file`, and optional `folder` JSON fields.
- Add: `scanFilesConfigured(..., exclusions exclusionSet, verbose bool, ...)`.

- [ ] **Step 1: Add failing normal-path test**

Scan files in nested and shared directories. Assert parent-first folder events, one event per folder, and a file event
immediately before each file's scan output.

- [ ] **Step 2: Add failing exclusion tests**

Assert an excluded file emits `list file` then `excluded`, and an excluded directory emits `list folder` then
`excluded` without descendant file list events or scanned-count increments. Verify the pair also appears in quick mode.

- [ ] **Step 3: Verify RED**

Run `go test -run 'TestScanFilesVerbose' ./...`. Expected: compilation fails because the configured scanner does not
exist.

- [ ] **Step 4: Implement list emission**

Derive ancestor paths from each normalized relative file path. Track emitted folders in a set. For exclusions, classify
a matched path equal to the selected file as a file and all other matched paths as folders. Emit path exclusions when
JSON mode or verbose is active, then continue before `os.ReadFile`.

- [ ] **Step 5: Verify GREEN**

Run the command from Step 3. Expected: PASS.

### Task 3: Document and verify

**Files:**

- Modify: `cfcli/README.md`

**Interfaces:** None.

- [ ] **Step 1: Document usage and event shapes**

Add verbose examples, ordering, excluded-path behavior, and unchanged count and exit semantics.

- [ ] **Step 2: Run complete verification**

Run `gofmt`, `go test -count=1 ./...`, `go vet ./...`, `go build ./...`, and `git diff --check`. Expected: every command
exits 0, then commit the verified changes.

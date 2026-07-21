# CF CLI finding status output implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every JSON signature event an explicit `found`, `allowed`, or `excluded` type.

**Architecture:** Reuse the existing `finding` JSON structure with a required `type` field. Emit allowed matches before
continuing, while retaining grand-report priority and setting `scanResult.found` only for reportable findings.

**Tech Stack:** Go standard library and Go test

## Global constraints

- Add no dependencies.
- Emit allowed signature events only in JSON mode.
- Keep quick-mode allowed and excluded matches silent.
- Preserve excluded path event shape.
- Return exit code 2 only when at least one `found` event exists.

---

### Task 1: Define and test typed JSON findings

**Files:**

- Modify: `cfcli/scanner_test.go`
- Modify: `cfcli/app_pipeline_test.go`
- Modify: `cfcli/scanner.go`

**Interfaces:**

- Change: `finding.Type` becomes required in JSON.

- [ ] **Step 1: Update approved existing tests and add allowed-event tests**

Update ordinary JSON expectations to include `"type":"found"`. Add exact and wildcard allowed cases that expect
`"type":"allowed"`, no finding state, and no exit code 2. Preserve existing excluded expectations.

- [ ] **Step 2: Verify RED**

Run `go test -run 'TestScanFiles.*(FindingType|Allowed)|TestRunWithDependencies.*Allowed' ./...`. Expected: FAIL because
ordinary findings omit `type` and allowed matches are silent.

- [ ] **Step 3: Emit typed events**

Make `finding.Type` use `json:"type"`. Emit an `allowed` finding in JSON mode before continuing. Set `Type: "found"`
for ordinary JSON findings and retain `Type: "excluded"` for excluded matches.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 2: Document and verify

**Files:**

- Modify: `cfcli/README.md`

**Interfaces:** None.

- [ ] **Step 1: Document all JSON event shapes**

Document `found`, `allowed`, and `excluded`, including priority, mode behavior, and exit-code semantics.

- [ ] **Step 2: Run complete verification**

Run `gofmt`, `go test -count=1 ./...`, `go vet ./...`, `go build ./...`, and `git diff --check`. Expected: every command
exits 0, then commit the verified changes.

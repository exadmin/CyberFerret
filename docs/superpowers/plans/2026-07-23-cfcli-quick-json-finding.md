# cfcli quick JSON finding implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emit the first quick-mode signature finding with the same complete JSON event as JSON mode.

**Architecture:** Add one focused helper in `scanner.go` that constructs a `found` event and writes it through
`lineOutput.json`. Call the helper from both output paths, retaining quick mode's immediate return and JSON mode's
continued scan.

**Tech Stack:** Go, standard-library JSON encoding, Go `testing`

## Global constraints

- Preserve quick mode's immediate stop after the first ordinary signature finding.
- Emit the same `type`, `key`, `found`, `line`, and `file` fields in quick and JSON modes.
- Keep allowed matches, excluded matches, counters, scan order, and exit status unchanged.
- Preserve unrelated staged and unstaged work in the shared worktree.

---

### Task 1: Share JSON finding output

**Files:**

- Modify: `cfcli/scanner_test.go`
- Modify: `cfcli/scanner.go`

**Interfaces:**

- Consumes: `lineOutput.json(value any) error` and the existing `finding` type.
- Produces: `writeFoundFinding(output *lineOutput, key, exact string, line int, relativePath string) error`.

- [ ] **Step 1: Write the failing quick-mode test**

In `TestScanFilesQuickStopsAtFirstNonAllowedFinding`, replace the text expectation with the complete JSON event:

```go
if want := `JSON: {"type":"found","key":"SECRET","found":"SECRET","line":1,"file":"a.txt"}` + "\n"; stdout.String() != want {
	t.Fatalf("stdout = %q, want %q", stdout.String(), want)
}
```

Keep the existing result and clean-stop assertions so the test still proves that quick mode scans one file and stops
before the `SKIPPED` signature.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
go test ./cfcli -run TestScanFilesQuickStopsAtFirstNonAllowedFinding -count=1
```

Expected: FAIL because stdout starts with `TEXT: Signature` instead of the expected complete `JSON:` event.

- [ ] **Step 3: Add the shared finding writer**

Add this helper near the other scanner helpers in `cfcli/scanner.go`:

```go
func writeFoundFinding(output *lineOutput, key, exact string, line int, relativePath string) error {
	return output.json(finding{
		Type:  "found",
		Key:   key,
		Found: exact,
		Line:  line,
		File:  relativePath,
	})
}
```

Replace the quick-mode text write with:

```go
if err := writeFoundFinding(output, currentSignature.key, exact, line, relativePath); err != nil {
	return scanResult{}, fmt.Errorf("write quick finding: %w", err)
}
return scanResult{found: true, scannedCount: scannedCount}, nil
```

Replace the JSON-mode inline `finding` construction with:

```go
if err := writeFoundFinding(output, currentSignature.key, exact, line, relativePath); err != nil {
	return scanResult{}, fmt.Errorf("write JSON finding: %w", err)
}
```

- [ ] **Step 4: Format and verify GREEN**

Run:

```powershell
gofmt -w cfcli/scanner.go cfcli/scanner_test.go
go test ./cfcli -run TestScanFilesQuickStopsAtFirstNonAllowedFinding -count=1
```

Expected: PASS.

- [ ] **Step 5: Run regression checks**

Run:

```powershell
go test ./cfcli -count=1
go test ./...
go vet ./...
```

Expected: all commands exit with code 0 and produce no test failures or vet findings.

- [ ] **Step 6: Review the focused diff**

Run:

```powershell
git diff --check
git diff -- cfcli/scanner.go cfcli/scanner_test.go
```

Expected: no whitespace errors; the diff only changes the quick expectation, introduces the helper, and routes both
ordinary finding paths through it. A commit is omitted because this environment does not permit writes to `.git`.

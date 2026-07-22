# cfcli Per-Signature Finding Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop checking one signature key in one file after emitting its fifth `found` result.

**Architecture:** Keep a key-indexed finding counter inside the existing per-file loop and share it across compiled
signatures. Count only matches that pass the exclusion and allowed-value checks, emit the fifth finding normally, and
then stop checking that key so the remaining signature keys and files continue to be scanned.

**Tech Stack:** Go standard library, Go `testing` package

## Global Constraints

- The limit is five `found` matches for each file and signature key.
- Matches classified as `allowed` or `excluded` do not consume the limit.
- Compiled signatures with the same key share the limit.
- The scanner must continue with other signature keys and files after reaching the limit.
- Quick mode keeps terminating the complete scan on its first `found` match.

---

### Task 1: Enforce the Per-Signature Finding Limit

**Files:**

- Modify: `cfcli/scanner.go`
- Test: `cfcli/scanner_test.go`

**Interfaces:**

- Consumes: the existing `scanFiles` scanner entry point and JSON finding events.
- Produces: a package-level `maxFindingsPerSignaturePerFile` constant and bounded per-signature scanning behavior.

- [ ] **Step 1: Write a failing scanner test**

Add a JSON-mode test with one file containing an allowed match, an excluded match, six ordinary matches for `SECRET`,
and a match for `TOKEN`. Configure the dictionary and exclusions so the test asserts that the output contains one
`allowed`, one `excluded`, exactly five `found` events for `SECRET`, and one `found` event for `TOKEN`.

```go
func TestScanFilesLimitsOnlyFoundMatchesPerSignatureAndFile(t *testing.T) {
	t.Parallel()

	root := t.TempDir()
	content := "ALLOW EXCLUDE SECRET SECRET SECRET SECRET SECRET SECRET TOKEN"
	file := writeTestFile(t, root, "repeated.txt", content)
	exclusions := exclusionSet{
		testSHA256("repeated.txt"): {testSHA256("EXCLUDE"): {}},
	}
	loaded := dictionary{
		allowed: map[string]struct{}{"allow": {}},
		signatures: []signature{
			{key: "SECRET", expression: regexp.MustCompile(`ALLOW|SECRET|EXCLUDE`)},
			{key: "TOKEN", expression: regexp.MustCompile(`TOKEN`)},
		},
	}

	var stdout bytes.Buffer
	result, err := scanFilesWithExclusions(
		root,
		[]string{file},
		loaded,
		modeJSON,
		exclusions,
		newLineOutput(&stdout),
		newLineOutput(&bytes.Buffer{}),
	)
	if err != nil || !result.found || result.scannedCount != 1 {
		t.Fatalf("scan result = %#v, error = %v; want findings in one scanned file", result, err)
	}

	output := stdout.String()
	if got := strings.Count(output, `"type":"found","key":"SECRET"`); got != 5 {
		t.Fatalf("SECRET found event count = %d, want 5; output = %q", got, output)
	}
	if !strings.Contains(output, `"type":"allowed","key":"SECRET","found":"ALLOW"`) {
		t.Fatalf("output = %q, want allowed event", output)
	}
	if !strings.Contains(output, `"type":"excluded","key":"SECRET","found":"EXCLUDE"`) {
		t.Fatalf("output = %q, want excluded event", output)
	}
	if !strings.Contains(output, `"type":"found","key":"TOKEN","found":"TOKEN"`) {
		t.Fatalf("output = %q, want TOKEN finding after SECRET reaches its limit", output)
	}
}
```

- [ ] **Step 2: Run the focused test and verify that it fails**

Run from `cfcli`:

```powershell
go test -count=1 -run TestScanFilesLimitsOnlyFoundMatchesPerSignatureAndFile ./...
```

Expected: FAIL because all six ordinary `SECRET` matches are emitted.

- [ ] **Step 3: Implement the finding limit**

Add the named constant and increment a counter only after allowed and excluded matches have continued past the current
iteration. After emitting a JSON finding, break from the current match loop when the counter reaches five.

```go
const maxFindingsPerSignaturePerFile = 5
```

```go
foundCount := 0
for _, match := range currentSignature.expression.FindAllIndex(content, -1) {
	// Existing exclusion and allowed-value checks remain here.

	foundAny = true
	foundCount++
	if mode == modeQuick {
		// Existing quick-mode output and return remain here.
	}
	if err := output.json(finding{
		Type:     "found",
		Key:      currentSignature.key,
		Found:    exact,
		Position: match[0],
		File:     relativePath,
	}); err != nil {
		return scanResult{}, fmt.Errorf("write JSON finding: %w", err)
	}
	if foundCount == maxFindingsPerSignaturePerFile {
		break
	}
}
```

- [ ] **Step 4: Run the focused test and the complete cfcli test suite**

Run from `cfcli`:

```powershell
go test -count=1 -run TestScanFilesLimitsOnlyFoundMatchesPerSignatureAndFile ./...
go test -count=1 ./...
go vet ./...
```

Expected: all commands exit successfully.

- [ ] **Step 5: Check formatting and the final diff**

Run from the repository root:

```powershell
gofmt -w cfcli/scanner.go cfcli/scanner_test.go
git diff --check
git diff -- cfcli/scanner.go cfcli/scanner_test.go
```

Expected: `git diff --check` exits successfully, and the diff contains only the new test and scanner limit.

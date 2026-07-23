# cfcli Finding Line Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace signature byte offsets with one-based source line numbers across `cfcli` and its JavaFX consumer.

**Architecture:** The Go scanner calculates line numbers from ascending regexp match offsets and emits `line` for every
signature event. JavaFX parses that contract and derives display context from the reported line and exact match.

**Tech Stack:** Go 1.21, Java 17, JUnit 5, Maven

## Global Constraints

- Treat `LF`, `CRLF`, and standalone `CR` as line endings.
- Use the line containing the first byte of the match.
- Do not retain `position` as a compatibility alias.
- Preserve event ordering, classifications, prefixes, exit codes, and finding limits.
- Do not modify or discard unrelated worktree changes.

---

### Task 1: Go line calculation and output

**Files:**

- Modify: `cfcli/scanner_test.go`
- Modify: `cfcli/scanner.go`

**Interfaces:**

- Consumes: regexp matches as ascending `[start, end]` byte offsets.
- Produces: `finding.Line int` serialized as `line`, plus quick text ending in `at line N`.

- [ ] **Step 1: Write failing scanner tests**

Update existing output assertions from `position` to `line`, then add a table-driven test whose content covers byte
zero, Unicode before a match, `LF`, `CRLF`, standalone `CR`, and two matches on one line:

```go
func TestScanFilesReportsOneBasedMatchLines(t *testing.T) {
	content := "SECRET é\nx SECRET\r\ny SECRET\rz SECRET SECRET"
	// Scan the file, decode each JSON line, and assert line values 1, 2, 3, 4, 4.
}
```

Keep the complete expected JSON strings in existing tests so they prove that `position` disappeared. Change the quick
assertion to:

```go
want := "TEXT: Signature \"SECRET\" found in a.txt at line 1\n"
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```shell
cd cfcli
go test ./... -run 'TestScanFiles(QuickStopsAtFirstNonAllowedFinding|JSONEmitsCompleteFindingsAndTotal|ReportsOneBasedMatchLines)'
```

Expected: FAIL because output still contains `position` and the new line values are absent.

- [ ] **Step 3: Implement the minimal Go change**

Change the finding field to:

```go
Line int `json:"line"`
```

Add a focused helper that advances through bytes without recounting earlier content:

```go
func lineAt(content []byte, offset, cursor, line int) (nextCursor, matchLine int) {
	for cursor < offset {
		switch content[cursor] {
		case '\r':
			line++
			cursor++
			if cursor < offset && content[cursor] == '\n' {
				cursor++
			}
		case '\n':
			line++
			cursor++
		default:
			cursor++
		}
	}
	return cursor, line
}
```

Initialize `cursor := 0` and `line := 1` for each signature expression. Calculate the line before match
classification, assign it to all three finding types, and print `at line %d` in quick mode.

- [ ] **Step 4: Run all Go tests and verify GREEN**

Run:

```shell
cd cfcli
gofmt -w scanner.go scanner_test.go
go test ./...
```

Expected: PASS.

### Task 2: Pipeline fixtures and Go documentation

**Files:**

- Modify: `cfcli/app_pipeline_test.go`
- Modify: `cfcli/README.md`

**Interfaces:**

- Consumes: the `line` JSON contract from Task 1.
- Produces: end-to-end coverage and user-facing examples of the new contract.

- [ ] **Step 1: Update the failing pipeline assertion**

Change the excluded-finding fixture to:

```text
JSON: {"type":"excluded","key":"SECRET","found":"SECRET","line":1,"file":"secret.txt"}
```

- [ ] **Step 2: Run the pipeline test**

Run:

```shell
cd cfcli
go test ./... -run TestRunWithDependenciesAppliesGrandReportExclusions
```

Expected: PASS after Task 1, proving the application pipeline emits the new field.

- [ ] **Step 3: Update the CLI reference**

Replace all finding examples with `"line":43`, change quick-output wording to `at line 43`, and document `line` as a
one-based line number. Remove the statement that `position` is a zero-based byte offset.

- [ ] **Step 4: Verify active Go sources and docs**

Run:

```shell
rg -n '"position"|at position|byte offset' cfcli
cd cfcli
go test ./...
```

Expected: `rg` returns no matches and `go test` passes.

### Task 3: JavaFX parser contract

**Files:**

- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliMessageParserTests.java`
- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliTreeAssemblerTests.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliMessage.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliMessageParser.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliTreeAssembler.java`

**Interfaces:**

- Consumes: signature JSON containing numeric `line`.
- Produces: `CfCliMessage.line()` as a nullable `Long`, required and positive for signature events.

- [ ] **Step 1: Write failing parser tests**

Change fixtures to `"line":17`, assert `message.line() == 17L`, reject `"line":0`, and add a test proving a signature
event containing only `"position":17` is rejected.

- [ ] **Step 2: Run parser tests and verify RED**

Run:

```shell
mvn -pl fx -Dtest=CfCliMessageParserTests test
```

Expected: FAIL because `CfCliMessage` and the parser still expose `position`.

- [ ] **Step 3: Implement the parser rename**

Change the record component to:

```java
Long line
```

Read `line` in `CfCliMessageParser`, require it for signature events, and validate:

```java
if (message.line() < 1) throw new IOException("Signature line must be positive");
```

Update test builders and `CfCliTreeAssembler` call sites from `position` to `line`.

- [ ] **Step 4: Run parser and assembler tests**

Run:

```shell
mvn -pl fx -Dtest=CfCliMessageParserTests,CfCliTreeAssemblerTests test
```

Expected: parser tests pass; assembler tests may remain RED until Task 4 changes context extraction.

### Task 4: JavaFX context extraction by line

**Files:**

- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/FileMatchContextTests.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/FileMatchContext.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliTreeAssembler.java`

**Interfaces:**

- Consumes: file bytes, positive one-based line number, and exact matched text.
- Produces: `FileMatchContext.from(byte[] content, long line, String exact)`.

- [ ] **Step 1: Replace offset tests with failing line tests**

Cover line 1, `LF`, `CRLF`, standalone `CR`, Unicode, repeated exact text on one line, a line outside the file, and a
line that does not contain `exact`. Keep assertions for the existing 50-code-point excerpt and whitespace
normalization.

- [ ] **Step 2: Run context tests and verify RED**

Run:

```shell
mvn -pl fx -Dtest=FileMatchContextTests test
```

Expected: FAIL because the implementation interprets the argument as a byte offset.

- [ ] **Step 3: Implement line-based extraction**

Walk the byte array once to find the requested line's `[start, end)` range, treating `CRLF` as one delimiter. Decode
only that range as UTF-8, find `exact` with `indexOf`, and build the excerpt around the first occurrence. Return the
reported line unchanged. Throw these existing-style errors when validation fails:

```java
throw new IOException("Signature line is outside the file");
throw new IOException("File line does not contain the reported signature");
```

Pass `message.line()` from `CfCliTreeAssembler`.

- [ ] **Step 4: Run focused Java tests and verify GREEN**

Run:

```shell
mvn -pl fx -Dtest=FileMatchContextTests,CfCliMessageParserTests,CfCliTreeAssemblerTests test
```

Expected: PASS.

### Task 5: Repository-wide verification

**Files:**

- Verify all active source, test, and user-documentation files.

**Interfaces:**

- Consumes: completed Go and Java changes.
- Produces: evidence that the protocol is consistent and regression-free.

- [ ] **Step 1: Scan for stale protocol names**

Run:

```shell
rg -n '"position"|at position|\.position\(\)|Position:' cfcli fx/src
```

Expected: no matches related to the `cfcli` finding protocol.

- [ ] **Step 2: Run Go verification**

Run:

```shell
cd cfcli
go test ./...
go vet ./...
```

Expected: both commands pass.

- [ ] **Step 3: Run Maven verification**

Run:

```shell
mvn test
```

Expected: PASS with no new warnings.

- [ ] **Step 4: Inspect the final diff**

Run:

```shell
git diff --check
git status --short
```

Expected: no whitespace errors; only intended files plus the user's pre-existing changes are present.

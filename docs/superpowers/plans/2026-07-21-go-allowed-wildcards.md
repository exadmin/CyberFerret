# CF CLI allowed-value wildcards implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow dictionary `(allowed)` entries to use `*` as a one-or-more nonwhitespace wildcard.

**Architecture:** Preserve the exact allowed-value map and add compiled wildcard regexps to `dictionary`. Centralize both
checks in `dictionary.isAllowed`, which the scanner calls after grand-report exclusion processing.

**Tech Stack:** Go standard library `regexp`, strings, and Go test

## Global constraints

- Add no dependencies.
- Preserve exact allowed-value behavior and case-insensitive matching.
- Treat every non-`*` character literally.
- Make `*` match `\S+` across the complete found string.
- Preserve grand-report exclusion priority and all output contracts.

---

### Task 1: Parse and match allowed wildcards

**Files:**

- Modify: `cfcli/dictionary.go`
- Modify: `cfcli/dictionary_test.go`

**Interfaces:**

- Add: `dictionary.allowedPatterns []*regexp.Regexp`
- Add: `dictionary.isAllowed(exact string) bool`

- [ ] **Step 1: Add failing tests**

Load `EMAIL(allowed)=*@example.com` and assert that `isAllowed` accepts mixed-case addresses with `.`, `-`, or `+` in
the wildcard portion. Assert it rejects an empty wildcard, whitespace, trailing text, and a different literal domain.
Also retain coverage for exact allowed values.

- [ ] **Step 2: Verify RED**

Run `go test -run 'TestLoadDictionary.*AllowedWildcard|TestDictionaryIsAllowed' ./...`. Expected: compilation fails
because `isAllowed` and `allowedPatterns` do not exist.

- [ ] **Step 3: Implement wildcard compilation**

Split an allowed value on `*`, escape every segment with `regexp.QuoteMeta`, join the segments with `\S+`, and compile
`(?i)^...$`. Store values without `*` in the existing lowercase exact map. Implement `isAllowed` to check the map and
then each compiled expression.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 2: Integrate and verify scanner behavior

**Files:**

- Modify: `cfcli/scanner.go`
- Modify: `cfcli/scanner_test.go`
- Modify: `cfcli/README.md`

**Interfaces:**

- Consume: `dictionary.isAllowed(exact string) bool`

- [ ] **Step 1: Add a failing scanner test**

Scan one wildcard-allowed address and one address containing whitespace. Assert only the allowed complete match is
suppressed and that ordinary findings retain their current JSON shape.

- [ ] **Step 2: Verify RED**

Run `go test -run TestScanFilesHonorsAllowedWildcard ./...`. Expected: FAIL because the scanner checks only the exact
allowed map.

- [ ] **Step 3: Use the centralized matcher**

Replace the scanner's direct map lookup with `loaded.isAllowed(exact)` after grand-report exclusion processing.

- [ ] **Step 4: Document and verify**

Document `*` semantics in the README. Run `gofmt`, `go test -count=1 ./...`, `go vet ./...`, `go build ./...`, and
`git diff --check`. Expected: every command exits 0.

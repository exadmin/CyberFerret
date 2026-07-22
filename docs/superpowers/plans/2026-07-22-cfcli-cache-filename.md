# CF CLI cache filename implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store and report the encrypted dictionary as `~/.qubership/sensitive-signatures.encrypted`.

**Architecture:** Change the single cache filename constant so cache checks, downloads, temporary files, reads, and
status output use the new name together. Update active documentation and tests without adding migration behavior.

**Tech Stack:** Go 1.21+, standard library, Markdown

## Global constraints

- Use `sensitive-signatures.encrypted` as the only cache filename.
- Do not read, move, or delete `dictionary-latest-cache.encrypted`.
- Preserve cache freshness, download, timeout, fallback, and output behavior.
- Do not add dependencies.

---

### Task 1: Rename the cache file

**Files:**

- Modify: `cfcli/dictionary_cache_test.go`
- Modify: `cfcli/dictionary_cache.go`
- Modify: `cfcli/README.md`
- Modify: `docs/superpowers/specs/2026-07-22-cfcli-dictionary-status-output-design.md`
- Modify: `docs/superpowers/plans/2026-07-22-cfcli-dictionary-status-output.md`

**Interfaces:**

- Consumes: the existing `cacheFileName` constant used by all cache operations.
- Produces: the canonical cache path `~/.qubership/sensitive-signatures.encrypted`.

- [ ] **Step 1: Write the failing filename test**

Add a direct assertion:

```go
func TestDictionaryCacheFilename(t *testing.T) {
    if cacheFileName != "sensitive-signatures.encrypted" {
        t.Fatalf("cacheFileName = %q, want sensitive-signatures.encrypted", cacheFileName)
    }
}
```

- [ ] **Step 2: Verify RED**

Run `go test -run TestDictionaryCacheFilename ./...`.

Expected: FAIL because `cacheFileName` is `dictionary-latest-cache.encrypted`.

- [ ] **Step 3: Change the filename constant**

Set:

```go
cacheFileName = "sensitive-signatures.encrypted"
```

Do not add fallback or migration code for the previous filename.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2.

Expected: PASS.

- [ ] **Step 5: Update active documentation**

Replace `~/.qubership/dictionary-latest-cache.encrypted` with
`~/.qubership/sensitive-signatures.encrypted` in the current README, dictionary-status design, and dictionary-status
implementation plan. Leave historical 2026-07-21 design and plan documents unchanged.

- [ ] **Step 6: Format and verify**

Run:

```text
gofmt -w dictionary_cache.go dictionary_cache_test.go
go test -count=1 ./...
go vet ./...
```

Expected: all commands exit zero.

- [ ] **Step 7: Review the working tree**

Run `git diff --check` and `git status --short`. Confirm that the old filename remains only in historical documents and
the new no-migration specification.

# CF CLI dictionary status output implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Print the dictionary cache status and user-friendly path before `cfcli` reads and decrypts the dictionary.

**Architecture:** Return a typed cache result from `cacheRefresher.refresh`, including the path, home directory, and
state. `app.go` maps the state to a `TEXT:` message and shortens the path for display without changing the path used for
file access.

**Tech Stack:** Go 1.21+, standard library, table-driven Go tests

## Global constraints

- Print status and path in both `quick` and `json` modes.
- Write both lines through `lineOutput.text` so each line uses `TEXT:` and flushes immediately.
- Display cache paths under home as `~/<relative-path>` with `/` separators.
- Keep refresh timing, timeout, fallback, decryption, and scanning behavior unchanged.
- Preserve the detailed refresh warning on standard error.
- Do not add dependencies.

---

### Task 1: Return the dictionary cache state

**Files:**

- Modify: `cfcli/dictionary_cache.go`
- Modify: `cfcli/dictionary_cache_test.go`

**Interfaces:**

- Produces: `cacheResult{path string, home string, state cacheState}` from `cacheRefresher.refresh`.
- Consumes: the existing freshness, download, timeout, and fallback decisions.

- [ ] **Step 1: Write failing state assertions**

Update the fresh, stale-download, and fallback tests to inspect `result.path` and require `cacheCurrent`,
`cacheUpdated`, and `cacheFallback`, respectively. Use this assertion shape:

```go
result, err := refresher.refresh(context.Background(), newLineOutput(&messages))
if err != nil {
    t.Fatal(err)
}
if result.path != cache || result.state != cacheCurrent {
    t.Fatalf("refresh() = %#v, want path %q and state %v", result, cache, cacheCurrent)
}
```

- [ ] **Step 2: Verify RED**

Run:

```text
go test -run 'TestCacheRefresher' ./...
```

Expected: compilation fails because `cacheResult` and the state constants do not exist.

- [ ] **Step 3: Add the typed result**

Add these types and return the matching state at each successful exit:

```go
type cacheState int

const (
    cacheCurrent cacheState = iota
    cacheUpdated
    cacheFallback
)

type cacheResult struct {
    path  string
    home  string
    state cacheState
}
```

Change `refresh` to return `(cacheResult, error)`. Return `cacheCurrent` for a fresh file, `cacheUpdated` after a
successful download, and `cacheFallback` after a failed refresh when the previous file exists. Return an empty result
with every error.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2.

Expected: all cache refresher tests pass.

### Task 2: Print status and shortened path

**Files:**

- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`
- Modify: `cfcli/app_pipeline_test.go`

**Interfaces:**

- Consumes: `cacheResult` from Task 1.
- Produces: two flushed `TEXT:` lines before dictionary file access.

- [ ] **Step 1: Write failing output tests**

Add or extend application tests for both modes. Require the output prefix to contain one status followed by the path:

```text
TEXT: Dictionary is up to date.
TEXT: Dictionary path: ~/.qubership/sensitive-signatures.encrypted
```

Add focused cases for updated and fallback status messages. Keep the existing stderr refresh warning assertion for the
fallback case.

- [ ] **Step 2: Verify RED**

Run:

```text
go test -run 'TestRun|TestDictionaryDisplayPath' ./...
```

Expected: tests fail because the new lines are absent.

- [ ] **Step 3: Implement path display**

Add a helper that uses `filepath.Rel`, rejects parent traversal, and converts separators:

```go
func dictionaryDisplayPath(path, home string) string {
    relative, err := filepath.Rel(home, path)
    outsideHome := relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator))
    if err == nil && !filepath.IsAbs(relative) && !outsideHome {
        return "~/" + filepath.ToSlash(relative)
    }
    return filepath.ToSlash(filepath.Clean(path))
}
```

- [ ] **Step 4: Implement status output**

After refresh succeeds, map the state to these exact messages:

```go
statusMessages := map[cacheState]string{
    cacheCurrent:  "Dictionary is up to date.",
    cacheUpdated:  "Dictionary was updated.",
    cacheFallback: "Dictionary was not updated due to network issues; using the existing dictionary.",
}
```

Write the status and `Dictionary path: %s` through the standard output. On write failure, report
`Cannot write dictionary status` or `Cannot write dictionary path` and return exit code 1. Continue reading the file
from `result.path`.

- [ ] **Step 5: Update existing exact output expectations**

Insert the two new lines before `Dictionary version` in existing expected output strings. Do not change JSON findings,
scan counters, durations, or exit codes.

- [ ] **Step 6: Verify GREEN**

Run the focused command from Step 2.

Expected: all selected tests pass in both modes.

### Task 3: Verify the complete Go module

**Files:**

- Review: `cfcli/dictionary_cache.go`
- Review: `cfcli/app.go`
- Review: all modified `cfcli/*_test.go` files

**Interfaces:**

- Consumes: the completed cache result and output behavior.
- Produces: verification evidence without release binaries or cache artifacts.

- [ ] **Step 1: Format modified Go files**

Run `gofmt -w` on only the modified Go source and test files.

- [ ] **Step 2: Run tests and vet**

Run:

```text
go test -count=1 ./...
go vet ./...
```

Expected: both commands exit zero.

- [ ] **Step 3: Review the working tree**

Run `git diff --check` and `git status --short`. Confirm that no binary, downloaded dictionary, credential, or temporary
Go cache is present.

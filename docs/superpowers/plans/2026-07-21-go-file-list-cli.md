# Go file-list CLI implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Go command that prints absolute paths selected by Git rules, optionally restricted to a
newline-delimited list of staged paths.

**Architecture:** Keep the command in one Go `main` package with a thin process entry point, an application runner,
Git-backed enumeration, and path filtering. Execute Git directly without a shell and use only the Go standard library.

**Tech Stack:** Go 1.21 or newer, the Go standard library, Git CLI, and Go's `testing` package.

## Global constraints

- Create and modify files only under `cli-go` and `docs/superpowers`.
- Do not modify Java modules or run Maven verification.
- Add no third-party dependencies.
- Preserve Git ignore semantics by using `git ls-files`.
- Accept `FOLDER_PATH [PATH_TO_LIST_OF_FILES]` and reject every other argument count.
- Treat list entries as newline-delimited Git paths relative to `FOLDER_PATH`; NUL-delimited lists are out of scope.
- Print deduplicated, lexicographically sorted absolute paths, one per line.
- Write diagnostics to standard error and return a nonzero exit code on errors.

---

## File structure

- `cli-go/go.mod`: declares the standalone Go module and minimum Go version.
- `cli-go/main.go`: adapts `os.Args`, standard streams, and process exit to the testable runner.
- `cli-go/app.go`: validates arguments, coordinates selection, prints output, and maps failures to exit codes.
- `cli-go/git_files.go`: runs Git commands and parses NUL-delimited file names.
- `cli-go/paths.go`: parses the optional list, validates relative paths, filters file types, deduplicates, and sorts.
- `cli-go/app_test.go`: covers CLI behavior and diagnostics with temporary repositories.
- `cli-go/git_files_test.go`: covers Git selection and standard exclusion sources.
- `cli-go/paths_test.go`: covers list parsing, path safety, filtering, and ordering.

### Task 1: Module scaffold and CLI contract

**Files:**

- Create: `cli-go/go.mod`
- Create: `cli-go/main.go`
- Create: `cli-go/app.go`
- Create: `cli-go/app_test.go`

**Interfaces:**

- Produces: `func run(ctx context.Context, args []string, stdout, stderr io.Writer) int`
- Consumes: `func selectFiles(ctx context.Context, rootArg string, listArg *string) ([]string, error)` from Task 4

- [ ] **Step 1: Write failing CLI tests**

Create table-driven tests that call `run` directly:

```go
func TestRunRejectsInvalidArgumentCounts(t *testing.T) {
    tests := [][]string{{}, {"root", "list", "extra"}}
    for _, args := range tests {
        var stdout bytes.Buffer
        var stderr bytes.Buffer

        exitCode := run(context.Background(), args, &stdout, &stderr)

        if exitCode != 2 {
            t.Fatalf("run(%q) exit code = %d, want 2", args, exitCode)
        }
        if stdout.Len() != 0 {
            t.Fatalf("run(%q) stdout = %q, want empty", args, stdout.String())
        }
        if !strings.Contains(stderr.String(), "usage: cli-go FOLDER_PATH [PATH_TO_LIST_OF_FILES]") {
            t.Fatalf("run(%q) stderr = %q, want usage", args, stderr.String())
        }
    }
}
```

- [ ] **Step 2: Run the tests and confirm the RED state**

Run: `cd cli-go && go test ./...`

Expected: compilation fails because `run` is undefined.

- [ ] **Step 3: Add the module and minimal runner**

Declare module `github.com/exadmin/cyberferret/cli-go` with Go 1.21. Implement `run` so invalid argument counts print
the exact usage line and return `2`. For one or two arguments, call `selectFiles`; print `error: <cause>` and return `1`
on failure, otherwise print each path and return `0`. Implement `main` as:

```go
func main() {
    os.Exit(run(context.Background(), os.Args[1:], os.Stdout, os.Stderr))
}
```

Use a temporary `selectFiles` declaration only by completing Task 2 in the same red-green cycle; do not add a stub that
can accidentally ship.

- [ ] **Step 4: Run the focused test**

Run: `cd cli-go && go test -run TestRunRejectsInvalidArgumentCounts ./...`

Expected: PASS.

- [ ] **Step 5: Commit the CLI contract**

```text
git add cli-go/go.mod cli-go/main.go cli-go/app.go cli-go/app_test.go
git commit -m "feat(cli-go): add command-line contract"
```

### Task 2: Git-backed file enumeration

**Files:**

- Create: `cli-go/git_files.go`
- Create: `cli-go/git_files_test.go`

**Interfaces:**

- Produces: `func enumerateGitFiles(ctx context.Context, root string) (map[string]struct{}, error)`
- Produces: `func runGitLsFiles(ctx context.Context, root string, args ...string) ([]byte, error)`

- [ ] **Step 1: Write a failing integration test for tracked and untracked files**

Use helpers `initRepository(t)` and `runGit(t, root, args...)` to create a temporary repository. Add `tracked.txt`,
leave `untracked.txt` untracked, create `.gitignore` containing `ignored.txt`, and create `ignored.txt`. Assert that
`enumerateGitFiles` returns `tracked.txt` and `untracked.txt`, but not `ignored.txt`.

```go
got, err := enumerateGitFiles(context.Background(), root)
if err != nil {
    t.Fatal(err)
}
want := map[string]struct{}{"tracked.txt": {}, "untracked.txt": {}}
if !reflect.DeepEqual(got, want) {
    t.Fatalf("enumerateGitFiles() = %#v, want %#v", got, want)
}
```

- [ ] **Step 2: Run the test and confirm the RED state**

Run: `cd cli-go && go test -run TestEnumerateGitFilesIncludesTrackedAndNonIgnoredUntracked ./...`

Expected: compilation fails because `enumerateGitFiles` is undefined.

- [ ] **Step 3: Implement Git enumeration**

Run these commands through `exec.CommandContext`, with `Cmd.Dir = root` and no shell:

```text
git ls-files --cached -z
git ls-files --others --exclude-standard -z
```

Capture standard error separately. When Git fails, return an error that names the failed operation and includes trimmed
Git diagnostics. Split standard output on NUL bytes, ignore the final empty field, normalize Git `/` separators with
`filepath.FromSlash`, and merge entries into a map.

- [ ] **Step 4: Run the focused test**

Run: `cd cli-go && go test -run TestEnumerateGitFilesIncludesTrackedAndNonIgnoredUntracked ./...`

Expected: PASS.

- [ ] **Step 5: Commit Git enumeration**

```text
git add cli-go/git_files.go cli-go/git_files_test.go
git commit -m "feat(cli-go): enumerate files with Git"
```

### Task 3: Standard Git exclusion sources

**Files:**

- Modify: `cli-go/git_files_test.go`

**Interfaces:**

- Consumes: `enumerateGitFiles` from Task 2

- [ ] **Step 1: Add failing tests for all exclusion sources and negation**

Add isolated temporary-repository tests that verify:

```text
.gitignore          excludes repository-ignored.txt
.git/info/exclude   excludes info-ignored.txt
core.excludesFile   excludes global-ignored.txt
!keep.txt           re-includes keep.txt after *.txt
```

Set the global excludes path per repository with
`git config core.excludesFile <absolute-temp-path>` so tests do not read or modify the user's Git configuration. Also
stage `tracked-then-ignored.txt` before adding its ignore rule and assert that it remains selected.

- [ ] **Step 2: Run the exclusion tests and inspect the RED state**

Run: `cd cli-go && go test -run 'TestEnumerateGitFiles(Honors|Keeps)' ./...`

Expected: any mismatch fails with the missing or unexpected relative path. If all tests pass immediately, retain them
as evidence that delegating ignore semantics to Git already implements this requirement.

- [ ] **Step 3: Make only the minimal correction required by the failing test**

Keep `--exclude-standard` on the untracked command and do not apply ignore filtering to the cached command. Correct
argument construction or NUL parsing only if a test exposed a defect.

- [ ] **Step 4: Run all Git enumeration tests**

Run: `cd cli-go && go test -run TestEnumerateGitFiles ./...`

Expected: PASS.

- [ ] **Step 5: Commit exclusion coverage**

```text
git add cli-go/git_files.go cli-go/git_files_test.go
git commit -m "test(cli-go): cover standard Git exclusions"
```

### Task 4: Safe optional-list filtering and absolute output

**Files:**

- Create: `cli-go/paths.go`
- Create: `cli-go/paths_test.go`
- Modify: `cli-go/app.go`

**Interfaces:**

- Produces: `func selectFiles(ctx context.Context, rootArg string, listArg *string) ([]string, error)`
- Produces: `func readListedPaths(path string) ([]string, error)`
- Produces: `func validateRelativeGitPath(path string) error`
- Consumes: `enumerateGitFiles` from Task 2

- [ ] **Step 1: Write failing path-selection tests**

Create temporary repositories and assert these behaviors:

- No list returns existing selected regular files as absolute paths.
- A newline-delimited list restricts output to listed selected paths.
- Blank lines and duplicate entries do not duplicate output.
- Results use `sort.Strings` order.
- Missing files and directories are omitted.
- An absolute list entry returns an error containing `path must be relative`.
- A `../outside.txt` entry returns an error containing `path escapes FOLDER_PATH`.
- A symbolic link to a regular file is included when the platform permits creating it.
- A symbolic link to a directory is omitted and never traversed.

Use `t.Skip` only when `os.Symlink` itself returns a platform permission error.

- [ ] **Step 2: Run the path tests and confirm the RED state**

Run: `cd cli-go && go test -run 'TestSelectFiles|TestValidateRelativeGitPath' ./...`

Expected: compilation fails because the selection functions are undefined.

- [ ] **Step 3: Implement list parsing and path validation**

Read the list with `bufio.Scanner`, increase its buffer to accept paths up to 1 MiB, trim a trailing `\r` for CRLF,
and ignore empty lines. Do not trim other whitespace because spaces are valid in Git paths.

Reject paths when `filepath.IsAbs(path)` is true. Clean each path and reject `.` plus any result equal to `..` or
starting with `..` plus the OS path separator. Convert `/` to the native separator before cleaning.

- [ ] **Step 4: Implement file selection**

Resolve `rootArg` with `filepath.Abs` and `filepath.Clean`; require `os.Stat` to report a directory. Call
`enumerateGitFiles`. If a list is provided, validate every entry before intersecting it with the Git map.

For every candidate, join it to the root, use `os.Stat` so a symbolic link to a file resolves to a regular file, and
include only `Mode().IsRegular()`. Deduplicate absolute paths in a map, convert the map to a slice, and call
`sort.Strings`.

- [ ] **Step 5: Run all path and CLI tests**

Run: `cd cli-go && go test -run 'TestSelectFiles|TestValidateRelativeGitPath|TestRun' ./...`

Expected: PASS.

- [ ] **Step 6: Commit safe selection**

```text
git add cli-go/paths.go cli-go/paths_test.go cli-go/app.go cli-go/app_test.go
git commit -m "feat(cli-go): filter listed repository files"
```

### Task 5: Error handling and end-to-end behavior

**Files:**

- Modify: `cli-go/app_test.go`
- Modify: `cli-go/git_files_test.go`
- Modify: `cli-go/paths_test.go`
- Modify: `cli-go/app.go`
- Modify: `cli-go/git_files.go`
- Modify: `cli-go/paths.go`

**Interfaces:**

- Consumes all production interfaces from Tasks 1–4.

- [ ] **Step 1: Add failing error and end-to-end tests**

Test `run` with a real temporary repository and assert exact stdout contains sorted absolute paths with the platform
line separator behavior represented by `fmt.Fprintln`. Add cases for a missing root, a root that is a file, a non-Git
directory, a missing list file, and Git unavailable through a test-local empty `PATH`. Assert exit code `1`, empty
stdout, and a diagnostic that contains the failing resource or Git operation.

- [ ] **Step 2: Run the new tests and confirm the RED state**

Run: `cd cli-go && go test -run 'TestRun(Prints|Reports)' ./...`

Expected: one or more assertions fail with incomplete diagnostics or incorrect exit behavior.

- [ ] **Step 3: Complete contextual errors**

Wrap errors with `%w` and name the resource:

```go
return nil, fmt.Errorf("resolve FOLDER_PATH %q: %w", rootArg, err)
return nil, fmt.Errorf("read file list %q: %w", *listArg, err)
return nil, fmt.Errorf("list Git files in %q: %w", root, err)
```

Keep the user-facing prefix in `run` as `error: `. Do not print usage for runtime failures.

- [ ] **Step 4: Run the full test suite with race detection**

Run: `cd cli-go && go test -race ./...`

Expected: PASS with zero test failures and no race reports.

- [ ] **Step 5: Commit end-to-end behavior**

```text
git add cli-go
git commit -m "test(cli-go): cover command failures"
```

### Task 6: Usage documentation and final verification

**Files:**

- Create: `cli-go/README.md`

**Interfaces:**

- Consumes the completed command-line interface.

- [ ] **Step 1: Write focused usage documentation**

Document the Git and Go prerequisites, build command, both invocation forms, newline-delimited list format, stdout and
stderr contracts, ignore behavior, and tracked-file exception. Include these runnable examples:

```text
go build -o cli-go .
./cli-go /path/to/repository
./cli-go /path/to/repository /path/to/staged-files.txt
```

- [ ] **Step 2: Format and statically inspect the module**

Run: `cd cli-go && gofmt -w *.go && go vet ./...`

Expected: `gofmt` makes no subsequent changes and `go vet` exits with code `0`.

- [ ] **Step 3: Run fresh full verification**

Run:

```text
cd cli-go
go test -race ./...
go build ./...
```

Expected: both commands exit with code `0`; tests report PASS and the build reports no errors.

- [ ] **Step 4: Inspect the scoped diff**

Run: `git status --short && git diff --check && git diff --stat HEAD`

Expected: only intended `cli-go` and `docs/superpowers` changes appear, with no whitespace errors.

- [ ] **Step 5: Commit documentation**

```text
git add cli-go/README.md cli-go
git commit -m "docs(cli-go): document file-list command"
```


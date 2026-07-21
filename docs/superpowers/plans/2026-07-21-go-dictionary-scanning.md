# Go dictionary scanning implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `cfcli` to refresh, decrypt, load, and apply the CyberFerret dictionary with quick and JSON output
modes.

**Architecture:** Preserve the existing Git selection code and add a sequential application pipeline. Keep output,
cache refresh, cryptography, dictionary parsing, and scanning in focused standard-library-only Go units.

**Tech Stack:** Go 1.21 or newer, Go standard library, Git CLI, AES-CBC, PBKDF2-HMAC-SHA256, RE2, and Go `testing`.

## Global constraints

- Modify only `cfcli` and `docs/superpowers`.
- Do not modify Java modules or run Maven.
- Add no third-party dependencies.
- Prefix every complete output line with `TEXT: ` or `JSON: ` and flush it immediately.
- Keep the existing Git ignore and optional staged-list behavior.
- Use exit code `0` for no findings, `1` for fatal runtime errors, `2` for findings, and `3` for invalid RE2.
- Never write decrypted dictionary content to disk.

---

## File structure

- `cfcli/output.go`: synchronized, prefixing, per-line-flushing output.
- `cfcli/options.go`: mode and positional argument parsing.
- `cfcli/dictionary_cache.go`: freshness checks, bounded download, and atomic cache replacement.
- `cfcli/decrypt.go`: Java-compatible PBKDF2 and AES-CBC decryption.
- `cfcli/dictionary.go`: Properties-subset parsing and compiled dictionary model.
- `cfcli/scanner.go`: deterministic file scanning and finding serialization.
- `cfcli/app.go`: sequential orchestration and exit-code mapping.
- Matching `*_test.go` files: focused unit and integration coverage for each component.
- `cfcli/README.md`: updated CLI, dictionary, output, and exit-code documentation.

### Task 1: Flush-safe output and option parsing

**Files:**

- Create: `cfcli/output.go`
- Create: `cfcli/output_test.go`
- Create: `cfcli/options.go`
- Create: `cfcli/options_test.go`
- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`

**Interfaces:**

- Produces: `type lineOutput struct { writer *bufio.Writer; mu sync.Mutex }`
- Produces: `func newLineOutput(writer io.Writer) *lineOutput`
- Produces: `func (o *lineOutput) text(format string, args ...any) error`
- Produces: `func (o *lineOutput) json(value any) error`
- Produces: `type scanMode string` with `modeQuick` and `modeJSON`
- Produces: `type options struct { mode scanMode; root string; listPath *string }`
- Produces: `func parseOptions(args []string) (options, error)`

- [ ] **Step 1: Write failing output tests**

Use a `bytes.Buffer` as the underlying writer. Assert that the complete line is visible immediately after the method
returns, which proves that the internal `bufio.Writer` was flushed:

```go
var destination bytes.Buffer
output := newLineOutput(&destination)
if err := output.text("Dictionary version: %s", "1.35"); err != nil { t.Fatal(err) }
if got, want := destination.String(), "TEXT: Dictionary version: 1.35\n"; got != want { t.Fatalf(...) }
```

Assert that `json(finding{...})` emits compact valid JSON prefixed by `JSON: ` and flushes once.

- [ ] **Step 2: Run output tests and confirm RED**

Run: `cd cfcli && go test -run TestLineOutput ./...`

Expected: compilation fails because `newLineOutput` is undefined.

- [ ] **Step 3: Implement output**

Construct a `bufio.Writer` around the supplied writer. Lock each method for one whole line. `text` uses `fmt.Fprintf`
with `TEXT: ` and a trailing newline. `json` calls `json.Marshal`, writes `JSON: <bytes>\n`, and then calls `Flush`.
Return write, marshal, and flush errors to the caller.

- [ ] **Step 4: Write failing option tests**

Cover these exact cases:

```text
root                         -> mode=json, root=root
root list                    -> mode=json, root=root, list=list
--mode=quick root            -> mode=quick
--mode=json root list        -> mode=json
--mode=bad root              -> error
--mode=quick                 -> error
root list extra              -> error
root --mode=quick            -> error because mode must be first
```

- [ ] **Step 5: Implement option parsing and run tests**

Strip only a leading `--mode=` argument. Accept `quick` and `json`, default to `json`, then require one or two
positional arguments. Return errors that include the usage string.

Run: `cd cfcli && go test -run 'TestLineOutput|TestParseOptions' ./...`

Expected: PASS.

- [ ] **Step 6: Commit output and options**

```text
git add cfcli/output.go cfcli/output_test.go cfcli/options.go cfcli/options_test.go cfcli/app.go cfcli/app_test.go
git commit -m "feat(cfcli): add scan modes and prefixed output"
```

### Task 2: Bounded dictionary cache refresh

**Files:**

- Create: `cfcli/dictionary_cache.go`
- Create: `cfcli/dictionary_cache_test.go`

**Interfaces:**

- Produces: `const dictionaryURL string`
- Produces: `type cacheRefresher struct { client *http.Client; now func() time.Time; homeDir func() (string, error) }`
- Produces: `func (r cacheRefresher) refresh(ctx context.Context, output *lineOutput) (string, error)`
- Produces: `func (r cacheRefresher) download(ctx context.Context, destination string) error`

- [ ] **Step 1: Write failing freshness and fallback tests**

Inject a fixed clock, temporary home, and `httptest.Server`. Assert that:

- A cache younger than eight hours makes zero HTTP requests.
- A cache exactly eight hours old makes zero HTTP requests.
- A cache older than eight hours is replaced with the response body.
- A missing cache is created under `.qubership/dictionary-latest-cache.encrypted`.
- HTTP 500 keeps an existing stale cache and emits one `TEXT:` warning.
- HTTP 500 with no cache returns an error.
- Context timeout keeps an existing stale cache and prevents a later replacement.
- The temporary download file is absent after success and failure.

- [ ] **Step 2: Run cache tests and confirm RED**

Run: `cd cfcli && go test -run TestCacheRefresher ./...`

Expected: compilation fails because `cacheRefresher` is undefined.

- [ ] **Step 3: Implement freshness and asynchronous refresh**

Resolve home, build the fixed cache path, and inspect `ModTime`. For stale or missing cache, create a child context with
15-second timeout and start one goroutine that calls `download`. Select between its result and `ctx.Done`. Cancel the
child context before returning. After any refresh failure, return the old path only when `os.Stat` confirms a regular
cache file.

- [ ] **Step 4: Implement atomic download**

Create an HTTP GET with context. Require status `200..299`. Create `.qubership` with mode `0700`, write into
`os.CreateTemp` in that directory, apply `0600`, close successfully, and call `os.Rename` to the destination. Defer
removal of the temporary path. Limit the response to 16 MiB with `io.LimitReader` and reject a larger body.

- [ ] **Step 5: Run cache tests**

Run: `cd cfcli && go test -run TestCacheRefresher ./...`

Expected: PASS, including timeout and no-late-replacement assertions.

- [ ] **Step 6: Commit cache refresh**

```text
git add cfcli/dictionary_cache.go cfcli/dictionary_cache_test.go
git commit -m "feat(cfcli): refresh encrypted dictionary cache"
```

### Task 3: Java-compatible in-memory decryption

**Files:**

- Create: `cfcli/decrypt.go`
- Create: `cfcli/decrypt_test.go`

**Interfaces:**

- Produces: `func decryptDictionary(ciphertext []byte, password string) ([]byte, error)`
- Produces: `func deriveKey(password, salt []byte, iterations, keyLength int) []byte`
- Produces: `func removePKCS7Padding(plaintext []byte, blockSize int) ([]byte, error)`

- [ ] **Step 1: Write failing Java-vector test**

Generate one fixed ciphertext with the repository's `PasswordBasedEncryption` using password `test-password` and
plaintext `VERSION=1.0\nTOKEN=secret\n`. Store only the Base64 ciphertext literal in the Go test. Assert exact plaintext
bytes from `decryptDictionary`.

- [ ] **Step 2: Write failing error tests**

Assert errors for empty password, invalid Base64, ciphertext not divisible by AES block size, invalid padding, and
decrypted bytes that are not valid UTF-8.

- [ ] **Step 3: Run decrypt tests and confirm RED**

Run: `cd cfcli && go test -run 'TestDecryptDictionary|TestRemovePKCS7Padding' ./...`

Expected: compilation fails because `decryptDictionary` is undefined.

- [ ] **Step 4: Implement PBKDF2 and AES-CBC**

Implement RFC 8018 PBKDF2 with `crypto/hmac` and `sha256`: compute blocks `U1 = PRF(password, salt || INT(i))`, XOR
iterations 2 through 65,536, and truncate to 32 bytes. Base64-decode trimmed cache content, create AES, verify block
alignment, decrypt with `cipher.NewCBCDecrypter` and the specified 16-byte IV, and validate every padding byte.

- [ ] **Step 5: Validate plaintext and run tests**

Reject invalid UTF-8 with `utf8.Valid`. Return a copy of unpadded plaintext. Run:

`cd cfcli && go test -run 'TestDecryptDictionary|TestRemovePKCS7Padding|TestDeriveKey' ./...`

Expected: PASS and exact agreement with the Java-generated vector.

- [ ] **Step 6: Commit decryption**

```text
git add cfcli/decrypt.go cfcli/decrypt_test.go
git commit -m "feat(cfcli): decrypt Java-compatible dictionary"
```

### Task 4: Dictionary parsing and compilation

**Files:**

- Create: `cfcli/dictionary.go`
- Create: `cfcli/dictionary_test.go`

**Interfaces:**

- Produces: `type signature struct { key string; expression *regexp.Regexp; excludedExtensions map[string]struct{} }`
- Produces: `type dictionary struct { version string; signatures []signature; allowed map[string]struct{} }`
- Produces: `type regexpCompileError struct { key, expression string; cause error }`
- Produces: `func loadDictionary(plaintext []byte, output *lineOutput) (dictionary, error)`
- Produces: `func literalExpression(value string) string`

- [ ] **Step 1: Write failing parser tests**

Use one plaintext fixture containing blank lines, indented `#` comments, `VERSION`, plain, `(regexp)`, `(allowed)`, and
`(exclude-ext)` entries. Assert version, signature order, case-insensitive matching, dot-all matching, literal escaping,
each literal space becoming `\s+`, global lowercased allowed values, and normalized extension sets.

- [ ] **Step 2: Write failing duplicate and error tests**

Assert that duplicate keys emit `TEXT: Duplicate dictionary key "KEY"; using last value` and keep the final value at
the final occurrence position. Assert fatal parse errors for missing `=`, empty keys, and unsupported suffixes. Assert
that invalid RE2 returns `*regexpCompileError` containing the key and expression.

- [ ] **Step 3: Run dictionary tests and confirm RED**

Run: `cd cfcli && go test -run 'TestLoadDictionary|TestLiteralExpression' ./...`

Expected: compilation fails because `loadDictionary` is undefined.

- [ ] **Step 4: Implement ordered Properties-subset parsing**

Scan lines with a 1 MiB buffer. Remove a trailing carriage return. Ignore blank and left-trimmed `#` lines. Split on
the first `=`. Decode Java property escapes (`\\`, `\t`, `\n`, `\r`, `\f`, and `\uXXXX`) in keys and values. Track key
indices in a map; on duplicate, emit the error and remove the earlier effective entry before appending the final entry.

- [ ] **Step 5: Compile the model**

Compile with `(?is)` prefix. For literal entries, build `\b` + `regexp.QuoteMeta` per non-space segment joined by
`\s+` per literal space + `\b`. Lowercase allowed values with `strings.ToLower`. Normalize exclusions with trim,
leading-dot removal, and lowercase. Associate exclusions by base signature key after all entries are parsed.

- [ ] **Step 6: Run dictionary tests and commit**

Run: `cd cfcli && go test -run 'TestLoadDictionary|TestLiteralExpression' ./...`

Expected: PASS.

```text
git add cfcli/dictionary.go cfcli/dictionary_test.go
git commit -m "feat(cfcli): load signature dictionary"
```

### Task 5: Deterministic quick and JSON scanning

**Files:**

- Create: `cfcli/scanner.go`
- Create: `cfcli/scanner_test.go`

**Interfaces:**

- Produces: `type finding struct { Key string; Found string; Position int; File string }` with JSON tags
- Produces: `type scanResult struct { found bool; scannedCount int }`
- Produces: `func scanFiles(root string, files []string, dictionary dictionary, mode scanMode, output, errors *lineOutput) (scanResult, error)`

- [ ] **Step 1: Write failing quick-mode tests**

Create two files and two signatures. Assert deterministic file/signature/match order, exclusion by extension, global
case-insensitive allowed values, exact quick message, immediate stop after the first non-allowed match, and no path
list output. Assert that the returned count includes the successfully read finding file and excludes unvisited files.

- [ ] **Step 2: Write failing JSON-mode tests**

Assert all findings are emitted as compact JSON and the result has `found=true`. Cover JSON escaping, a complete
match longer than 16 Unicode code points, a multibyte prefix proving `position` is a byte offset, `/` relative path
separators, no selected-path output, and a count of all successfully read files.

- [ ] **Step 3: Write failing file-error test**

Supply a selected path that becomes unreadable or disappears before scanning. Assert one `TEXT:` warning on the error
stream, continued scanning of the next file, and a returned count that excludes the unreadable file.

- [ ] **Step 4: Run scanner tests and confirm RED**

Run: `cd cfcli && go test -run TestScanFiles ./...`

Expected: compilation fails because `scanFiles` is undefined.

- [ ] **Step 5: Implement scanning**

Iterate the already sorted `files` slice. Read each file with `os.ReadFile`. For each ordered signature not excluded by
extension, call `FindAllIndex(content, -1)`. Compare the full match against lowercased allowed values. Quick mode emits
the exact text message and returns immediately. JSON mode marshals a `finding` with the complete exact match and
continues. Increment the scanned count after each successful file read. Return `scanResult{found, scannedCount}` and do
not emit selected paths or summary messages from the scanner.

- [ ] **Step 6: Run scanner tests and commit**

Run: `cd cfcli && go test -run TestScanFiles ./...`

Expected: PASS.

```text
git add cfcli/scanner.go cfcli/scanner_test.go
git commit -m "feat(cfcli): scan files for dictionary signatures"
```

### Task 6: Sequential application pipeline and exit codes

**Files:**

- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`
- Modify: `cfcli/main.go`

**Interfaces:**

- Produces: `type appDependencies struct { refresher cacheRefresher; getenv func(string) string; now func() time.Time }`
- Produces: `func runWithDependencies(ctx context.Context, args []string, stdout, stderr io.Writer, deps appDependencies) int`
- Preserves: `func run(ctx context.Context, args []string, stdout, stderr io.Writer) int`

- [ ] **Step 1: Write failing pipeline tests**

Inject temporary cache/home and HTTP behavior. Cover these outcomes:

```text
invalid args                         -> TEXT usage, exit 1
missing password                     -> TEXT error, exit 1
no cache plus failed refresh         -> TEXT error, exit 1
decryption failure                   -> TEXT error, exit 1
invalid RE2                          -> TEXT error, exit 3
quick finding                        -> TEXT finding + count + elapsed time, exit 2
JSON findings after complete scan    -> JSON lines + count + elapsed time, exit 2
JSON scan without findings           -> count + elapsed time, exit 0
```

Assert that every emitted line has a valid prefix.

- [ ] **Step 2: Run pipeline tests and confirm RED**

Run: `cd cfcli && go test -run TestRunWithDependencies ./...`

Expected: compilation fails because `runWithDependencies` is undefined.

- [ ] **Step 3: Implement the sequential pipeline**

Create line outputs, parse options, refresh cache, read encrypted bytes, require `CYBER_FERRET_PASSWORD`, decrypt, and
load the dictionary. Print its version, capture `start := now()`, print the progress message, select Git files, and scan.
For both modes, print `Total files scanned N` and `Scanning is finished in %.3f seconds.` in that order. Use
`errors.As` for `regexpCompileError` and map it to `3`; map every other fatal error to `1`; map `result.found` to `2`.

- [ ] **Step 4: Configure production dependencies**

`run` creates an `http.Client`, `cacheRefresher{client: client, now: time.Now, homeDir: os.UserHomeDir}`, and uses
`os.Getenv` plus `time.Now`. Keep `main` calling `run` with standard streams. The refresher owns the 15-second child
timeout.

- [ ] **Step 5: Run all tests**

Run: `cd cfcli && go test -count=1 ./...`

Expected: PASS with existing Git selection tests unchanged.

- [ ] **Step 6: Commit pipeline**

```text
git add cfcli/app.go cfcli/app_test.go cfcli/main.go
git commit -m "feat(cfcli): orchestrate dictionary scanning"
```

### Task 7: Documentation and Go-only verification

**Files:**

- Modify: `cfcli/README.md`

**Interfaces:**

- Documents the completed public command and output contract.

- [ ] **Step 1: Update README**

Document `CYBER_FERRET_PASSWORD`, cache path, refresh age and timeout, default JSON mode, quick mode, prefixed output,
JSON fields, progress output, final scanned-file count, elapsed seconds, absence of selected-path output, allowed
values, extension exclusions, and exit codes `0` through `3`. Include:

```text
cfcli --mode=quick FOLDER_PATH [PATH_TO_LIST_OF_FILES]
cfcli --mode=json FOLDER_PATH [PATH_TO_LIST_OF_FILES]
```

- [ ] **Step 2: Format and inspect**

Run: `cd cfcli && go fmt ./... && go vet ./...`

Expected: both commands exit with code `0`.

- [ ] **Step 3: Run fresh verification**

Run:

```text
cd cfcli
go test -count=1 ./...
go build ./...
```

Expected: tests report PASS and build exits with code `0`. Run `go test -race ./...` only when `CGO_ENABLED=1` and a C
compiler is available; otherwise record the environment limitation.

- [ ] **Step 4: Inspect scope and whitespace**

Run: `git status --short && git diff --check && git diff --stat HEAD`

Expected: only intended `cfcli` and `docs/superpowers` files appear, with no whitespace errors.

- [ ] **Step 5: Commit documentation**

```text
git add cfcli/README.md
git commit -m "docs(cfcli): document dictionary scanning"
```

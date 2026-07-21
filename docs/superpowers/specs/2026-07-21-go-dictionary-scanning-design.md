# Go dictionary scanning design

## Goal

Extend `cfcli` into a Git-aware signature scanner. The command refreshes and decrypts an encrypted dictionary, loads
its signatures, scans selected files, and emits flush-safe machine-readable output.

## Command-line interface

The command accepts these forms:

```text
cfcli [--mode=quick|--mode=json] FOLDER_PATH [PATH_TO_LIST_OF_FILES]
```

`--mode` must precede positional arguments when present. Its default is `json`. `FOLDER_PATH` and the optional staged
file list keep their existing semantics.

The command uses these exit codes:

- `0`: Scanning completes without a non-allowed match.
- `1`: Arguments, cache access, password lookup, decryption, or another fatal runtime operation fails.
- `2`: At least one non-allowed match is found.
- `3`: A dictionary regular expression is incompatible with Go RE2.

## Output contract

Every output line starts with one of these prefixes:

- `TEXT: ` for status messages, errors, quick-mode findings, and the final scan count.
- `JSON: ` for JSON-mode findings.

The application uses an output abstraction that flushes after every complete line. Normal output goes to standard
output. Fatal errors and recoverable warnings go to standard error. Both streams follow the same prefix and flush
rules.

After printing the dictionary version, both modes print `TEXT: Scanning is in progress. Please wait.` and start timing
Git selection and file scanning. Both modes finish with `TEXT: Total files scanned N` followed by
`TEXT: Scanning is finished in S.SSS seconds.`. `N` counts files that were read and processed successfully. Files
skipped after a read error are not counted. Quick mode includes the file that contains its first finding, prints the
finding, and then prints the final count and elapsed time. The command does not print the selected file list.

## Dictionary refresh

The encrypted cache is `~/.qubership/dictionary-latest-cache.encrypted`. The command refreshes it when the file is
missing or its modification time is more than eight hours old.

Refresh runs in a goroutine under a 15-second context timeout. It downloads from:

```text
https://raw.githubusercontent.com/exadmin/CyberFerretDictionary/main/dictionary-latest.encrypted
```

The HTTP response must have a successful status. The command writes new content to a temporary file in the cache
directory, closes it, and atomically replaces the cache. A failed or timed-out refresh prints a `TEXT:` warning. The
command continues with the old cache when it exists. If no cache is available after refresh, the command prints a
`TEXT:` error and exits with code `1`.

Cancellation prevents a timed-out download from replacing the cache later. The cache directory and file use
owner-only permissions where the operating system supports them.

## In-memory decryption

The command reads the Base64 ciphertext and decrypts it without writing plaintext to disk. It reads the password from
`CYBER_FERRET_PASSWORD` and reproduces `PasswordBasedEncryption`:

- PBKDF2-HMAC-SHA256 with 65,536 iterations.
- Salt `bsd87918hediu`.
- A 256-bit AES key.
- AES-CBC with IV bytes `{0,2,3,4,5,4,3,2,1,0,1,2,3,4,5,0}`.
- Base64 decoding and PKCS#5/PKCS#7 padding validation.
- UTF-8 plaintext.

The Go module implements PBKDF2 with the standard library and adds no dependency. A missing password, invalid
ciphertext, invalid padding, or unreadable cache prints a `TEXT:` error and exits with code `1`.

## Dictionary loading

The plaintext uses the requested Java Properties subset. The parser ignores blank lines and lines whose first
non-whitespace character is `#`. Every other nonblank line must contain `key=value`. It preserves value whitespace
except for the line ending and decodes Java property backslash escapes, including `\\`, control escapes, and
`\uXXXX`.

Duplicate keys print a `TEXT:` error naming the key. Parsing continues, and the last value replaces earlier values.
Malformed entries are fatal and exit with code `1`.

Entries have these meanings:

- `VERSION=<value>` stores debug metadata and prints `TEXT: Dictionary version: <value>` after loading.
- `KEY(regexp)=<value>` compiles the value directly as a case-insensitive, dot-all Go regular expression.
- `KEY=<value>` quotes regular expression metacharacters, converts each literal space to `\s+`, wraps the expression in
  word boundaries, and compiles it case-insensitively with dot-all behavior.
- `KEY(allowed)=<value>` adds the value to a global exact-match allowlist. Allowlist comparison is case-insensitive.
- `KEY(exclude-ext)=ext1,ext2` stores a case-insensitive CSV set of extensions for `KEY`. Whitespace and a leading dot
  are removed from each extension.

Signature order follows the final occurrence order in the dictionary. An invalid RE2 expression prints a `TEXT:`
error with its key and exits with code `3`. Unsupported parenthesized suffixes are malformed entries and exit with
code `1`.

## Scanning

The existing Git file selection remains responsible for the ordered list of files. The scanner processes files in
that deterministic order, signatures in dictionary order, and matches in ascending byte position.

Before applying a signature, the scanner checks its case-insensitive `(exclude-ext)` set against the file extension.
It reads a file into memory and applies Go regular expressions to its bytes. A file read error prints a `TEXT:` warning
and scanning continues with the next file.

A match is allowed when its complete value equals any `(allowed)` value under case-insensitive comparison. Allowed
matches produce no finding.

Quick mode prints the first non-allowed match in this form and exits with code `2`:

```text
TEXT: Signature "KEY" found in relative/path.txt at position 123
```

It then prints the actual scanned-file count and elapsed time before exiting.

JSON mode prints every non-allowed match as compact one-line JSON:

```text
JSON: {"key":"KEY","found":"complete exact match","position":123,"file":"relative/path.txt"}
```

`found` contains the complete exact match without truncation. `position` is the zero-based byte offset of the match
head. `file` is relative to `FOLDER_PATH` and always uses `/` separators. JSON mode finishes the complete file list,
prints the total successfully scanned file count and elapsed time, and returns code `2` when it emitted at least one
finding. Otherwise, it returns code `0`.

## Components

The implementation adds focused units under `cfcli`:

- Output writer: prefixes and flushes text and JSON lines.
- Argument parser: validates mode and positional arguments.
- Cache refresher: checks freshness, downloads with cancellation, and performs atomic replacement.
- Decryptor: derives the key, decrypts AES-CBC, validates padding, and returns plaintext bytes.
- Dictionary parser: reports metadata and duplicates, compiles signatures, and builds allow and exclusion sets.
- Scanner: reads selected files, applies exclusions and signatures, and emits mode-specific findings.
- Application pipeline: coordinates the components and maps typed failures to exit codes.

The implementation does not modify Java modules and adds no Go dependency.

## Testing and verification

Development follows test-driven development. Tests cover:

- Optional and invalid modes, positional arguments, exit codes, prefixes, and per-line flushing.
- Fresh, stale, missing, successful, failed, and timed-out cache refresh behavior.
- Atomic replacement and fallback to an existing stale cache.
- Java-compatible key derivation and AES-CBC decryption with a fixed Java-generated test vector.
- Missing passwords, invalid Base64, invalid block sizes, invalid padding, and non-UTF-8 plaintext.
- Dictionary comments, blank lines, duplicates, malformed entries, all supported suffixes, and `VERSION` output.
- Plain-string escaping, flexible whitespace, case-insensitive matching, invalid RE2, and extension exclusions.
- Global case-insensitive allowed values.
- Quick-mode early termination and JSON-mode complete scanning.
- Complete JSON match values, JSON escaping, byte offsets, relative paths, deterministic ordering, and file read errors.
- Preservation of existing Git selection behavior without file-list output.
- Progress output and deterministic elapsed-time formatting in both modes.
- Final counts that exclude read-error files and include the quick-mode finding file.

Verification runs `go test ./...`, `go vet ./...`, and `go build ./...`. Race tests run when a CGO toolchain is
available. Maven is outside this change's scope.

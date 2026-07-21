# CyberFerret Go scanner

The Go CLI refreshes and decrypts the CyberFerret dictionary, then scans files selected by Git. It includes tracked
files and untracked files that standard Git ignore rules do not exclude.

## Prerequisites

- Go 1.21 or newer to build the command.
- Git available on `PATH` at runtime.
- `CYBER_FERRET_PASSWORD` set to the dictionary decryption password.

## Build

Run this command from `cli-go`:

```shell
go build -o cli-go .
```

On Windows, the output file is `cli-go.exe`.

## Usage

Scan every selected file in the default JSON mode:

```shell
./cli-go /path/to/repository
```

Stop after the first non-allowed finding:

```shell
./cli-go --mode=quick /path/to/repository
```

Restrict either mode to a staged-file list:

```shell
./cli-go --mode=json /path/to/repository /path/to/staged-files.txt
```

`--mode` is optional and must precede `FOLDER_PATH`. The optional list contains one Git path per line. Each path is
relative to `FOLDER_PATH`. Empty lines and duplicate paths are ignored. Absolute paths and paths that escape
`FOLDER_PATH` are rejected.

## Dictionary cache

The encrypted cache is `~/.qubership/dictionary-latest-cache.encrypted`. A missing cache or one older than eight hours
is refreshed from the CyberFerretDictionary repository. Refresh has a 15-second timeout. A failed refresh falls back
to an existing cache.

The decrypted dictionary remains in memory. `VERSION` is reported for diagnostics, `(allowed)` values suppress exact
case-insensitive matches, and `(exclude-ext)` values skip signatures for the listed file extensions.

## Output

Every flushed output line starts with `TEXT: ` or `JSON: `. JSON findings have this shape:

```text
JSON: {"key":"SIGNATURE","found":"matched value","position":42,"file":"relative/path.txt"}
```

`found` contains the complete exact match, and `position` is the zero-based byte offset. JSON mode does not print the
selected paths. After scanning, it prints `TEXT: Total files scanned N`, where `N` excludes files skipped after read
errors. Quick mode prints its first finding as a `TEXT:` line and stops without printing a final count.

Exit codes are:

- `0`: The scan completes without findings.
- `1`: Arguments, cache access, password lookup, decryption, or another runtime operation fails.
- `2`: At least one non-allowed match is found.
- `3`: A dictionary expression is incompatible with Go RE2.

## Ignore behavior

Git determines which files are eligible. The command honors repository `.gitignore` files, `.git/info/exclude`, Git's
configured global excludes file, and `~/.gitignore_global` when that file exists. A tracked file remains eligible even
if a later ignore rule matches it, which is standard Git behavior.

# CyberFerret Go scanner

The Go CLI refreshes and decrypts the CyberFerret dictionary, then scans files selected by Git. It includes tracked
files and untracked files that standard Git ignore rules do not exclude.

## Prerequisites

- Go 1.21 or newer to build the command.
- Git available on `PATH` at runtime.
- `CYBER_FERRET_PASSWORD` set to the dictionary decryption password.

## Build

Run this command from `cfcli`:

```shell
go build -o cfcli .
```

On Windows, the output file is `cfcli.exe`.

## Usage

Scan every selected file in the default JSON mode:

```shell
./cfcli /path/to/repository
```

Stop after the first non-allowed finding:

```shell
./cfcli --mode=quick /path/to/repository
```

Print copy-ready exclusion commands after the first non-allowed finding:

```shell
./cfcli --mode=quick --print=details /path/to/repository
```

Restrict either mode to a staged-file list:

```shell
./cfcli --mode=json /path/to/repository /path/to/staged-files.txt
```

Report each folder and file immediately before the scanner processes it:

```shell
./cfcli --verbose=true --mode=json /path/to/repository
```

`--mode`, `--print`, and `--verbose` are optional, may appear in any order, and must precede `FOLDER_PATH`.
`--print=details` applies only to quick mode. In other modes, the scanner prints a warning and ignores it. Verbose
defaults to `false` and accepts only `true` or `false`. The optional list contains one Git path per line. Each path is
relative to `FOLDER_PATH`. Empty lines and duplicate paths are ignored. Absolute paths and paths that escape
`FOLDER_PATH` are rejected.

## Exclude a finding

Add a `found` event to the repository's `.qubership/grand-report.json`:

```shell
./cfcli exclude /path/to/repository \
  '{"type":"found","key":"EMAIL","found":"ci.noreply@example.com","line":89,"file":"docs/notifications.md"}'
```

The JSON argument may include the CLI protocol prefix:

```shell
./cfcli exclude /path/to/repository \
  'JSON: {"type":"found","key":"EMAIL","found":"ci.noreply@example.com","line":89,"file":"docs/notifications.md"}'
```

The command hashes the exact `found` and `file` values with SHA-256, preserves existing exclusions, removes a duplicate
of the same pair, and sorts the report in the same order as the FX application. It creates `.qubership` and
`grand-report.json` when needed. The repository folder must already exist.

Only `found` events are supported. Invalid events and invalid existing reports produce an error without changing the
report. This command does not refresh or decrypt the dictionary and does not require `CYBER_FERRET_PASSWORD`.

Running `cfcli` without arguments or with an incomplete `exclude` command prints help for both scan and exclusion
syntax.

## Dictionary cache

The encrypted cache is `~/.qubership/sensitive-signatures.encrypted`. A missing cache or one older than eight hours
is refreshed from the CyberFerretDictionary repository. Refresh has a 15-second timeout. A failed refresh falls back
to an existing cache.

The decrypted dictionary remains in memory. `VERSION` is reported for diagnostics, and `(exclude-ext)` values skip
signatures for the listed file extensions. An `(allowed)` value without `*` suppresses an exact case-insensitive match.
In an `(allowed)` value with wildcards, each `*` matches one or more nonwhitespace characters. Other characters are
literal, and the pattern must match the complete detected value. For example, `*@example.com` allows
a nonempty, whitespace-free local part at `example.com`. It rejects an empty local part or one containing spaces.

## Output

Every flushed output line starts with `TEXT: ` or `JSON: `. JSON findings have this shape:

```text
JSON: {"type":"found","key":"SIGNATURE","found":"matched value","line":43,"file":"relative/path.txt"}
```

After a quick-mode finding, the scanner prints a short hint:

```text
TEXT: To print exclusion commands, run cfcli with --mode=quick --print=details.
```

With `--mode=quick --print=details`, three `TEXT:` lines contain the matching `cfcli exclude` command for POSIX
shells, PowerShell, and `cmd.exe`. Copy the command after the label for your shell to add the finding to
`grand-report.json`. The variants are always printed in that order, regardless of the operating system that runs
`cfcli`.

```text
TEXT: POSIX: cfcli exclude '/path/to/repository' 'JSON: {"type":"found",...}'
TEXT: PowerShell: cfcli exclude 'C:\path\to\repository' 'JSON: {"type":"found",...}'
TEXT: cmd.exe: cfcli exclude "C:\path\to\repository" "JSON: {\"type\":\"found\",...}"
```

`cmd.exe` expands text such as `%NAME%` as an environment variable before starting `cfcli`, including inside double
quotes. Use the PowerShell command when a path or finding contains percent-delimited text that could be expanded.

JSON mode also reports dictionary allowed values and applied grand-report exclusions:

```text
JSON: {"type":"allowed","key":"SIGNATURE","found":"matched value","line":43,"file":"relative/path.txt"}
JSON: {"type":"excluded","file":"relative/directory"}
JSON: {"type":"excluded","key":"SIGNATURE","found":"matched value","line":43,"file":"relative/path.txt"}
```

With `--verbose=true`, the command emits parent folders once and each file before processing it:

```text
JSON: {"type":"list","folder":"relative/directory"}
JSON: {"type":"list","file":"relative/directory/file.txt"}
```

The root folder is not emitted. A fully excluded path produces its `list` event followed by its `excluded` event. Files
below a fully excluded directory are not listed, read, scanned, or counted. Verbose list events do not affect the exit
code or scanned-file count.

Each fully excluded file or directory is reported once. Quick mode does not report allowed or excluded signature
matches. With verbose enabled, it does report full path exclusions after their `list` events. Only `found` events count
as findings and cause exit code `2`. If a match is both allowed and excluded, only the `excluded` event is emitted.

`found` contains the complete exact match, and `line` is the one-based number of the line containing the match. JSON
mode does not print the selected paths. After the dictionary version, both modes print
`TEXT: Scanning is in progress. Please wait.`. After scanning, both modes print `TEXT: Total files scanned N` and
`TEXT: Scanning is finished in S.SSS seconds.`. `N` excludes files skipped after read errors or full grand-report
exclusions. In quick mode, it includes the file with the first finding and excludes files that were not visited after
the stop.

Exit codes are:

- `0`: The scan completes without findings, or the `exclude` command succeeds.
- `1`: Arguments, cache access, password lookup, decryption, or another runtime operation fails.
- `2`: At least one non-allowed match is found.
- `3`: A dictionary expression is incompatible with Go RE2.

## Ignore behavior

Git determines which files are eligible. The command honors repository `.gitignore` files, `.git/info/exclude`, Git's
configured global excludes file, and `~/.gitignore_global` when that file exists. A tracked file remains eligible even
if a later ignore rule matches it, which is standard Git behavior.

Before scanning, the command also loads `<FOLDER_PATH>/.qubership/grand-report.json` when it exists. Each exclusion
contains a SHA-256 hash of a relative file path in `f-hash` and either a SHA-256 hash of an exact match in `t-hash` or
the special value `00000000`. Relative paths use `/` separators. Exact-match exclusions suppress only that text in that
file. The special value excludes the named file or an entire directory subtree.

Files skipped by a full file or directory exclusion are not included in `Total files scanned`. If the report cannot be
read or parsed, the command prints a `TEXT:` warning with the absolute report path and continues without exclusions.

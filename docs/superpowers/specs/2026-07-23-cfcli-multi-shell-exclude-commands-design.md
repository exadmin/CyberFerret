# cfcli multi-shell exclusion commands design

## Goal

Print ready-to-copy exclusion commands for POSIX shells, PowerShell, and `cmd.exe` after every quick-mode finding,
without trying to infer the parent shell.

## Quick-mode output

Quick mode keeps the complete JSON finding as the first finding line. It then prints three labeled `TEXT:` lines:

```text
JSON: {"type":"found","key":"EMAIL","found":"matched value","line":89,"file":"relative/path.txt"}
TEXT: POSIX: cfcli exclude '/absolute/repository' 'JSON: {"type":"found","key":"EMAIL","found":"matched value","line":89,"file":"relative/path.txt"}'
TEXT: PowerShell: cfcli exclude 'C:\absolute\repository' 'JSON: {"type":"found","key":"EMAIL","found":"matched value","line":89,"file":"relative/path.txt"}'
TEXT: cmd.exe: cfcli exclude "C:\absolute\repository" "JSON: {\"type\":\"found\",\"key\":\"EMAIL\",\"found\":\"matched value\",\"line\":89,\"file\":\"relative/path.txt\"}"
```

The labels and order are fixed: `POSIX`, `PowerShell`, then `cmd.exe`. The user copies the command portion after
`TEXT: <Shell>: `.

All commands contain:

- the literal executable name `cfcli`;
- the same absolute, cleaned repository path;
- the same compact JSON serialization of the `finding`, prefixed with `JSON: `.

Quick mode stops immediately after all three command lines. Its result, scanned-file count, summary, and exit code stay
unchanged. JSON mode does not print any exclusion command.

## Shell quoting

Formatting does not inspect `runtime.GOOS` and does not attempt to detect the parent process.

### POSIX

Each argument uses single quotes. An embedded single quote is replaced with:

```text
'"'"'
```

### PowerShell

Each argument uses single quotes. An embedded single quote is doubled:

```text
''
```

### cmd.exe

Each argument uses double quotes. The formatter follows Windows command-line argument rules:

- an internal `"` is preceded by a backslash;
- a run of backslashes immediately before `"` is doubled before escaping the quote;
- a trailing run of backslashes is doubled before the closing quote;
- other backslashes remain unchanged.

These rules keep the JSON object in one argument and preserve its literal quotes when Go constructs `os.Args`.

`cmd.exe` expands `%NAME%` before starting the executable, including inside double quotes. The generated command cannot
prevent that expansion for arbitrary finding text. The README directs users to the PowerShell variant when a path or
finding contains percent-delimited text that could be expanded.

## Components

- A shell-command model stores the display label and formatted command.
- A formatter serializes the event once and returns all three commands in fixed order.
- Separate quoting helpers implement POSIX, PowerShell, and Windows command-line rules.
- Quick mode writes every returned command through `lineOutput.text` with its label.
- The existing `exclude` command parser and grand-report updater do not change.

## Errors

The formatter resolves the absolute repository path and serializes the event before producing any command. A resolution
or serialization error stops quick-mode finding output through the existing scan-error path.

If writing one of the command lines fails, scanning returns an error naming the shell label whose line could not be
written. Earlier lines may already be present because output is flushed line by line.

## Testing

Focused tests cover:

- exact formatter labels and ordering;
- exact POSIX single-quote escaping;
- exact PowerShell single-quote escaping;
- exact `cmd.exe` escaping for JSON quotes, embedded quotes, backslashes before quotes, and trailing backslashes;
- the same absolute root and event in every command;
- quick output with one JSON line followed by three `TEXT:` command lines;
- immediate quick-mode termination after the four finding-related lines;
- absence of command lines in JSON mode;
- README examples and the `cmd.exe` percent-expansion warning.

Run focused Go tests, the complete `cfcli` suite, `go vet ./...`, and `git diff --check`. Report the known unrelated
`TestRunReportsRuntimeErrors/not_Git_repository` baseline failure separately if it remains.

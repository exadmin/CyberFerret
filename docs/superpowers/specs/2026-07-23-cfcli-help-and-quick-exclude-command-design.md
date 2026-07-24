# cfcli help and quick exclusion command design

## Goal

Document the `exclude` command in CLI help, move it to a subcommand-first syntax, and print a ready-to-copy exclusion
command after a quick-mode finding.

## Command syntax and help

The only supported exclusion syntax is:

```text
cfcli exclude FOLDER_PATH JSON_OBJECT
```

The previous `cfcli FOLDER_PATH exclude JSON_OBJECT` form is removed.

Starting `cfcli` without arguments prints expanded help to stderr and returns exit code 1. An invocation with too few
or too many positional arguments also prints the help. An incomplete exclusion command such as `cfcli exclude` or
`cfcli exclude FOLDER_PATH` follows the same behavior.

The help uses `TEXT:` protocol lines and includes:

```text
TEXT: Usage:
TEXT:   cfcli [--mode=quick|--mode=json] [--verbose=true|--verbose=false] FOLDER_PATH [PATH_TO_LIST_OF_FILES]
TEXT:   cfcli exclude FOLDER_PATH JSON_OBJECT
TEXT:
TEXT: The exclude command adds a found event to FOLDER_PATH/.qubership/grand-report.json.
TEXT: JSON_OBJECT may start with "JSON:" and must have type "found", found, and file fields.
```

Specific option-validation errors may precede the same help. A help write failure returns exit code 1.

The dispatcher recognizes `exclude` only as the first argument. The two-argument scan form can continue using a list
file named `exclude`.

## Quick-mode output

Quick mode keeps the complete JSON finding as its first finding line:

```text
JSON: {"type":"found","key":"EMAIL","found":"matched value","line":89,"file":"relative/path.txt"}
```

The next line contains the complete exclusion command with a `TEXT:` prefix:

```text
TEXT: cfcli exclude '/absolute/repository' 'JSON: {"type":"found","key":"EMAIL","found":"matched value","line":89,"file":"relative/path.txt"}'
```

The user copies the command portion after `TEXT: `. Quick mode then stops immediately, preserving its existing scan and
exit-code behavior. JSON mode does not print an exclusion command.

The command contains:

- the literal executable name `cfcli`;
- the absolute, cleaned repository path;
- the exact finding event, including the `JSON:` prefix and all finding fields.

The finding event used in the command is serialized from the same `finding` value as the preceding JSON output.

## Shell quoting

Runtime OS selects the quoting rules:

- Windows uses PowerShell single-quoted arguments and escapes an embedded `'` as `''`.
- Other operating systems use POSIX single-quoted arguments and escapes an embedded `'` as `'"'"'`.

Both the absolute repository path and the complete `JSON: {...}` argument are quoted. Pure formatting helpers accept
an explicit OS value so tests cover Windows and POSIX output on every development platform.

## Components

- A help writer owns the multi-line CLI help and emits each line with `TEXT:`.
- The top-level dispatcher recognizes `exclude FOLDER_PATH JSON_OBJECT` before parsing scan options.
- Finding construction is shared by quick and JSON modes.
- A command formatter serializes the finding, resolves the root path, applies OS-specific quoting, and returns the
  command text without the protocol prefix.
- Quick mode writes the formatter result through `lineOutput.text`.

## Errors

If the repository path cannot be resolved for the quick exclusion command, scanning returns an error before printing a
partial command. If JSON serialization or either output write fails, scanning returns its existing runtime-error path.

The `exclude` command retains its existing validation, grand-report safety, success message, and exit codes.

## Testing

Focused tests cover:

- expanded help with no arguments;
- expanded help for incomplete and overlong `exclude` invocations;
- successful dispatch of `cfcli exclude FOLDER_PATH JSON_OBJECT`;
- rejection of the removed folder-first syntax;
- preservation of a scan list file named `exclude`;
- exact POSIX quoting, including embedded single quotes;
- exact PowerShell quoting, including embedded single quotes;
- quick output containing the JSON event followed by the `TEXT:` command;
- an absolute repository path and the same complete event in the command;
- unchanged immediate quick-mode termination;
- absence of exclusion commands in JSON mode.

Run focused Go tests, the complete `cfcli` test suite, `go vet ./...`, and `git diff --check`. Report the known
unrelated `TestRunReportsRuntimeErrors/not_Git_repository` baseline failure separately if it remains.

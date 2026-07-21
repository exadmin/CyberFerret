# CF CLI rename design

## Goal

Rename the Go scanner and its JavaFX integration from `cli-go` to `cfcli` without retaining compatibility aliases.

## Go command

Rename the source directory from `cli-go` to `cfcli` and change the Go module path to
`github.com/exadmin/cyberferret/cfcli`. The command name, usage text, examples, ignored build artifacts, and README use
`cfcli` consistently.

Build artifacts are named:

- `cfcli.exe` for Windows;
- `cfcli` for Linux and macOS.

The command-line arguments, output protocol, scanning behavior, and exit codes do not change.

## JavaFX integration

Rename the Java package `com.github.exadmin.cyberferret.gocli` to
`com.github.exadmin.cyberferret.cfcli`. Rename its public types as follows:

| Old name | New name |
| --- | --- |
| `GoCliExecutable` | `CfCliExecutable` |
| `GoCliMessage` | `CfCliMessage` |
| `GoCliMessageParser` | `CfCliMessageParser` |
| `GoCliScanner` | `CfCliScanner` |
| `GoCliTreeAssembler` | `CfCliTreeAssembler` |

Rename the associated test classes and packages. The default executable resolved through `PATH` is `cfcli`. Process
commands, errors, logs, and UI text use the new name.

## Persistence

Replace the Java property constant `GO_CLI_PATH` with `CF_CLI_PATH` and replace the persisted key `go-cli.path` with
`cfcli.path`. The application does not read or migrate the old key. Existing property files containing `go-cli.path`
produce the existing unknown-key warning and use the default `cfcli` command until the user selects a new executable.

The online settings pane labels the file field `CF CLI executable`.

## Documentation scope

Update repository files that describe or invoke the command. Historical design and implementation documents are also
updated so repository searches do not present stale command, directory, package, or binary names. Git history is not
rewritten.

## Verification

Use TDD for changed observable behavior. Run:

1. `go test -count=1 ./...` and `go vet ./...` from `cfcli`;
2. Windows build with `go build -o cfcli.exe .`;
3. Linux cross-build with `GOOS=linux GOARCH=amd64 go build -o cfcli .`;
4. focused and full JavaFX tests;
5. `mvn clean package assembly:single`;
6. a repository search that confirms active source, tests, build instructions, and documentation contain no stale
   `cli-go`, `GoCli`, `gocli`, `GO_CLI_PATH`, or `go-cli.path` names.

# CyberFerret Go file-list CLI

The Go file-list CLI prints absolute paths for files selected by Git. It includes tracked files and untracked files
that standard Git ignore rules do not exclude.

## Prerequisites

- Go 1.21 or newer to build the command.
- Git available on `PATH` at runtime.

## Build

Run this command from `cli-go`:

```shell
go build -o cli-go .
```

On Windows, the output file is `cli-go.exe`.

## Usage

Scan every selected file below a repository directory:

```shell
./cli-go /path/to/repository
```

Restrict the result to a staged-file list:

```shell
./cli-go /path/to/repository /path/to/staged-files.txt
```

The optional list contains one Git path per line. Each path is relative to `FOLDER_PATH`. Empty lines and duplicate
paths are ignored. Absolute paths and paths that escape `FOLDER_PATH` are rejected.

The command writes one normalized absolute file path per line to standard output. It deduplicates and sorts the output
lexicographically. Errors go to standard error and produce a nonzero exit code.

## Ignore behavior

Git determines which files are eligible. The command honors repository `.gitignore` files, `.git/info/exclude`, Git's
configured global excludes file, and `~/.gitignore_global` when that file exists. A tracked file remains eligible even
if a later ignore rule matches it, which is standard Git behavior.

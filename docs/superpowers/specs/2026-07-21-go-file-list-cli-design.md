# Go file-list CLI design

## Goal

Add a standalone Go command under `cli-go` that prints the absolute paths of files selected from a Git repository.
The command accepts a repository directory and an optional newline-delimited list of staged paths.

## Command-line interface

The command has this form:

```text
cli-go FOLDER_PATH [PATH_TO_LIST_OF_FILES]
```

`FOLDER_PATH` is required. It must identify an existing directory inside a valid Git work tree. The command resolves
it to an absolute, normalized path before processing files.

`PATH_TO_LIST_OF_FILES` is optional. The file contains one Git path per line. Each path is relative to `FOLDER_PATH`.
NUL-delimited input is out of scope.

The command prints one absolute path per line to standard output. Output is deduplicated and sorted lexicographically
for deterministic results. Diagnostics go to standard error. Invalid arguments, unreadable input, an invalid Git work
tree, unsafe paths, and Git failures produce a nonzero exit code.

## File selection

Git is the source of truth for file selection and ignore behavior. This avoids reproducing Git's pattern semantics and
adds no Go dependency.

Without `PATH_TO_LIST_OF_FILES`, the command combines these sets:

- Tracked files returned by `git ls-files --cached -z`.
- Untracked, non-ignored files returned by `git ls-files --others --exclude-standard -z`.

`--exclude-standard` applies repository `.gitignore` files, `.git/info/exclude`, and the configured global excludes
file. A tracked file remains selected if a later ignore rule matches it, which matches Git behavior.

With `PATH_TO_LIST_OF_FILES`, the command reads the listed paths, removes blank lines and duplicates, and intersects
them with the same Git-selected set. The list is expected to describe staged files, but Git remains responsible for
deciding whether each listed path is selectable.

Only paths that resolve beneath `FOLDER_PATH` are eligible. Absolute input paths and relative paths that escape through
`..` are errors. Missing paths and directories are omitted. A symbolic link to a regular file is eligible, but the
command never traverses a symbolic link to a directory.

## Components

The module contains a small command entry point and focused internal functions:

- Argument validation resolves the root and optional list path.
- Git enumeration executes Git without a shell and parses NUL-delimited output.
- List parsing reads newline-delimited Git paths and validates path safety.
- Selection intersects optional input with Git results, checks file types, deduplicates paths, and sorts output.
- Output and error handling map successful results to standard output and failures to standard error and a nonzero
  exit code.

The Go standard library is sufficient. The module does not modify the Java Maven modules or add repository
dependencies.

## Testing

Development follows test-driven development. Unit and integration tests use temporary Git repositories and cover:

- Argument validation and invalid repositories.
- Tracked and untracked file enumeration.
- `.gitignore`, `.git/info/exclude`, global excludes, and negated patterns.
- Optional-list filtering, blank lines, duplicate paths, and deterministic ordering.
- Absolute paths, `..` traversal, missing files, and directories.
- Git execution and input-file errors.
- Symbolic links where the operating system permits creating them.

Verification runs `go test ./...`, a Go build, and the repository's required Maven build:

```text
mvn clean package assembly:single
```


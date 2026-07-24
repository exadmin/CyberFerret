# cfcli exclude command design

## Goal

Add an `exclude` command that persists a `found` event as a signature exclusion compatible with the CyberFerret FX
module.

## Command contract

The command accepts a repository folder and one JSON event:

```text
cfcli FOLDER_PATH exclude '{"type":"found","key":"EMAIL","found":"ci.noreply@example.com","line":89,"file":"docs/notifications.md"}'
```

It also accepts the event with the protocol prefix:

```text
cfcli FOLDER_PATH exclude 'JSON: {"type":"found","key":"EMAIL","found":"ci.noreply@example.com","line":89,"file":"docs/notifications.md"}'
```

The parser trims surrounding whitespace. If the remaining value starts with `JSON:`, it removes the prefix and trims
the whitespace that follows it before decoding the JSON object.

The event must contain nonempty string values for `type`, `found`, and `file`. Only `type: "found"` is supported.
Additional fields, including `key` and `line`, are accepted and ignored.

The `exclude` command is dispatched before the scan pipeline. It does not refresh or decrypt the dictionary, inspect
Git files, or require `CYBER_FERRET_PASSWORD`.

## Exclusion persistence

The command computes the same values used by the FX module:

- `t-hash` is the lowercase hexadecimal SHA-256 digest of the exact `found` value.
- `f-hash` is the lowercase hexadecimal SHA-256 digest of the exact `file` value.

The command reads `FOLDER_PATH/.qubership/grand-report.json`. A missing file represents an empty report. A missing
`.qubership` directory is created.

Existing exclusions are preserved. Before appending the new pair, the command removes every identical pair so the
saved report contains it once. It sorts all exclusions by `f-hash` and then by `t-hash`, matching the FX module.

The report uses the existing `{"exclusions":[...]}` model and ends with a newline. The command writes a temporary file
in the destination directory and replaces the report only after encoding and writing succeed. Replacement uses the Go
standard library's platform behavior and does not promise cross-platform atomicity.

After a successful update, stdout contains:

```text
TEXT: Exclusions file was updated: FOLDER_PATH/.qubership/grand-report.json
```

The reported path uses the resolved filesystem path chosen by the command.

## Errors and file safety

Unsupported event types return exit code 1 and write this message to stderr:

```text
TEXT: Cannot exclude event type "allowed": only "found" is supported. No files were changed.
```

Invalid JSON, missing or empty required fields, an unreadable report, and an invalid existing report also return exit
code 1. Their messages identify the invalid input or report and state that no files were changed.

Validation, root-folder inspection, and report decoding complete before any directory or temporary file is created. A
failure while preparing or replacing the updated report returns exit code 1. Temporary files are removed after failed
updates.

## Components

- The top-level dispatcher recognizes the exact positional form `FOLDER_PATH exclude JSON_OBJECT`. A two-argument scan
  whose list filename is `exclude` retains its existing meaning.
- An event parser removes the optional `JSON:` prefix, decodes the object, and validates its supported fields.
- An exclusion updater loads, deduplicates, sorts, and saves the grand-report model through a same-directory temporary
  file and platform-standard replacement.
- The existing scan option parser and scan pipeline retain their current behavior.

The grand-report JSON structs and SHA-256 helper already used by scanning remain the shared compatibility boundary.

## Testing

Focused tests cover:

- parsing raw and `JSON:`-prefixed events;
- rejecting an unsupported event type without creating or changing a report;
- rejecting invalid JSON and missing or empty required fields;
- creating `.qubership/grand-report.json` with the expected hashes;
- preserving existing exclusions while adding a new pair;
- deduplicating an existing pair and sorting output like FX;
- leaving a malformed existing report byte-for-byte unchanged;
- rejecting a missing or non-directory `FOLDER_PATH` without creating it;
- rejecting unknown, missing, null, and duplicate grand-report fields;
- bypassing dictionary refresh and password requirements;
- reporting the updated report path and returning exit code 0.

Run the focused Go tests, the complete `cfcli` Go test suite, and `go vet ./...`. The known unrelated
`TestRunReportsRuntimeErrors/not_Git_repository` baseline failure must be reported separately if it remains.

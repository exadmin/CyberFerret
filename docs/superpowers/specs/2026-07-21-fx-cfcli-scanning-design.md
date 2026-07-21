# JavaFX scanning through the CF CLI design

## Goal

Make the JavaFX `Start Scanning` action run `cfcli` from `PATH` and build the explorer tree incrementally from its
one-line JSON protocol. Keep dictionary download and loading UI unchanged for a later refactor.

## Process boundary

The FX module starts this command with `ProcessBuilder`:

```text
cfcli --mode=json --verbose=true <FOLDER_PATH>
```

The command inherits the parent environment, including `CYBER_FERRET_PASSWORD`. A coordinator runs outside the JavaFX
application thread. Separate stream pumps consume stdout and stderr concurrently. Stdout `JSON:` lines are parsed and
applied in order; `TEXT:` lines and stderr are logged. Exit codes 0 and 2 are successful scan outcomes. Exit codes 1 and
3, process startup failures, I/O failures, and malformed JSON produce a scan error. The button is disabled while the
process runs and re-enabled from a `finally` path.

## Protocol parsing

`CfCliMessageParser` parses the fixed one-line JSON object without adding a JSON dependency. It supports JSON string
escapes, Unicode escapes, integer values, and the fields emitted by `cfcli`: `type`, `file`, `folder`, `key`, `found`,
and `position`. Unknown fields are skipped for forward compatibility. Missing fields required by a specific event type
are rejected with a protocol error.

## Tree assembly

`CfCliTreeAssembler` consumes parsed messages on the stdout pump thread and writes model objects to
`FoundItemsContainer`. Existing container listeners marshal changes to the JavaFX thread.

- `list` plus `folder` creates the relative directory hierarchy.
- `list` plus `file` creates the file and any missing parent directories.
- `found`, `allowed`, and signature-level `excluded` create signature children under the file.
- Path-level `excluded` marks an existing file or directory as ignored.

Relative paths use `/`, are resolved under the selected scan root, and are rejected if normalization escapes that root.
Each path is indexed to prevent duplicate tree nodes. `FoundPathItem` keeps the final path component as its visual name;
signature nodes use the JSON `key` as their visual name. Allowed signature nodes set `allowedValue`; excluded path and
signature nodes set `ignored`.

The model listener gains a backward-compatible default update callback. This allows `list` followed by `excluded` to
refresh existing TreeTableView cells without replacing nodes or changing existing listener implementations.

## Line number and found text

For each signature, the assembler reads the referenced file as bytes because Go reports a zero-based UTF-8 byte offset.
It counts newline bytes before the offset to produce a one-based line number. The display excerpt is restricted to the
same physical line and contains at most 50 Unicode code points before the match, the complete exact match, and at most
50 code points after it. Whitespace in the excerpt is replaced with ordinary spaces. `Exact Signature` preserves the
JSON value unchanged.

File data is cached for the current file so consecutive signature events do not reread it. A changed or unreadable file
produces a warning and leaves line and excerpt fields empty without discarding the signature event.

## UI integration

`SceneBuilder` no longer creates `RunnableScanner` when `Start Scanning` is pressed. It clears the previous generation,
creates an assembler and Go process runner, disables the button, and starts a daemon coordinator thread. Existing online
and offline dictionary controls remain present, but their Java signature maps are no longer scan inputs.

The explorer columns retain their existing bindings:

- `Path name`: short file or folder name, structured by parent nodes.
- `To be ignored`: excluded state.
- `Allowed`: allowed state.
- `Line #`: computed one-based line number.
- `Exact Signature`: complete match.
- `Found Text`: same-line context excerpt.

The existing mark-as-ignored, open, copy, row coloring, selection, and generation behavior remain available.


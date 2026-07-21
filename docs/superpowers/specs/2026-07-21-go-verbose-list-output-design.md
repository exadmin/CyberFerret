# CF CLI verbose list output design

## Goal

Add `--verbose=true` to report each relative folder and file immediately before the scanner processes it.

## Options

`--verbose` defaults to `false` and accepts only `true` or `false`. It may appear in either order with `--mode`, but all
options must precede `FOLDER_PATH`. The root folder itself is not emitted.

## Events and ordering

Verbose events use `JSON: {"type":"list","folder":"relative/path"}` for folders and
`JSON: {"type":"list","file":"relative/path"}` for files. Each folder is emitted once, parents before children. A
file event is emitted immediately before its exclusion check and content read.

For a fully excluded file, the scanner emits its `list` event followed by its existing `excluded` event. For a fully
excluded directory, it emits the directory `list` event followed by its `excluded` event and does not read, scan, count,
or list descendant files. When verbose is enabled, these path-exclusion pairs are emitted in quick and JSON modes.
Excluded signature events remain JSON-mode-only.

List events do not affect the scanned count, finding state, or exit code. Every event uses the existing flushed JSON
writer.


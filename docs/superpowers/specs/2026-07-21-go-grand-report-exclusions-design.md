# Go CLI grand-report exclusions design

## Goal

Load repository exclusions from `<FOLDER_PATH>/.qubership/grand-report.json` before scanning and suppress excluded
files, directory subtrees, and exact signature matches.

## File format and loading

The optional JSON document contains an `exclusions` array. Each item has a `t-hash` and an `f-hash`. The loader indexes
the values as `f-hash -> set of t-hash` and collapses duplicate pairs. Unknown JSON fields are ignored.

If the file does not exist, the scanner uses an empty exclusion set. If the file cannot be read or decoded, the CLI
prints a flushed `TEXT:` warning containing its absolute path and continues with an empty exclusion set. This matches
the Java scanner's recoverable behavior.

## Hash compatibility

Both hashes use SHA-256 over UTF-8 bytes and lowercase hexadecimal output. File hashes use the path relative to
`FOLDER_PATH`, with `/` separators and no leading slash. Text hashes use the complete, exact regexp match without case
conversion or truncation. These rules match `MiscUtils.getRelativeFileName` and `MiscUtils.getSHA256AsHex`.

## Exclusion behavior

The special text hash `00000000` excludes an entire path. Before opening a selected file, the scanner checks the file's
relative-path hash and every parent-directory hash. A matching full-path exclusion skips the file. Skipped files are not
included in `Total files scanned`.

In JSON mode, the scanner emits one diagnostic event for every matched full-path exclusion:

```text
JSON: {"type":"excluded","file":"relative/path"}
```

An excluded directory is emitted once, even when several selected files are below it. Files below that directory are
skipped before any content is read. Quick mode applies the exclusion without emitting the diagnostic event.

For every regexp match, the scanner checks the exact-match hash together with the current relative-file hash. A matching
pair suppresses the finding and does not affect the exit code. Exact-text exclusions apply only to the named file. Only
`00000000` exclusions inherit from a directory to its descendants.

JSON mode emits a suppressed match with the existing finding fields and `"type":"excluded"`. Quick mode suppresses the
match silently. Exclusion diagnostics do not count as findings and do not cause exit code 2.

Dictionary allowed values and grand-report exclusions are independent. A finding is reportable only when neither rule
allows it.

## Modes and output

JSON mode scans every nonexcluded file, emits each reportable finding, and returns exit code 2 if any remain. Quick mode
stops at the first reportable finding and returns exit code 2. Both modes preserve the existing flushed `TEXT:` summary
messages. The scanned count excludes files skipped through a full file or ancestor-directory exclusion.

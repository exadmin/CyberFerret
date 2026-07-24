# cfcli quick JSON finding design

## Goal

Make the first signature finding emitted in `--mode=quick` identical to a `found` event from JSON mode while preserving
quick mode's immediate stop after that finding.

## Output contract

Quick mode emits the first ordinary signature finding through the JSON output channel:

```text
JSON: {"type":"found","key":"SIGNATURE","found":"matched value","line":3,"file":"relative/path.txt"}
```

The event contains the same `type`, `key`, `found`, `line`, and `file` fields and uses the same JSON serialization as
JSON mode. Quick mode no longer emits a `TEXT:` signature-found line.

Allowed and excluded matches keep their existing quick-mode behavior. Scan order, exit status, counters, and immediate
termination after the first ordinary finding do not change.

## Implementation

Extract a helper that accepts the signature key, exact match, source line, and relative file path. The helper constructs
the `finding` value and writes it with `lineOutput.json`.

Both quick and JSON paths call this helper for ordinary findings. Quick mode returns immediately after the helper
succeeds. JSON mode continues scanning and keeps its existing per-signature limit.

If JSON output fails, each mode returns an error that identifies its calling context. No partially formatted fallback
message is written.

## Testing

Update the focused quick-mode scanner test first and verify that it fails because the implementation still emits
`TEXT:`. The expected output must contain the complete JSON event, including the exact matched text and line number.
The test must continue to prove that only one file is scanned and later signatures are not processed.

Run the focused scanner tests, the complete Go test suite, and `go vet` after implementation.

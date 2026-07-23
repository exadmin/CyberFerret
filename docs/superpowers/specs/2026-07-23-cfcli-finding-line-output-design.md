# cfcli Finding Line Output

## Goal

Report the one-based source line containing a signature instead of its zero-based byte offset. Apply the new contract
to every `cfcli` finding classification and to the JavaFX consumer.

## Output contract

JSON events with a signature use `line` instead of `position`:

```text
JSON: {"type":"found","key":"SIGNATURE","found":"matched value","line":3,"file":"relative/path.txt"}
```

The same shape applies to `allowed` and signature-level `excluded` events. Path-level `excluded` events and all `list`
events remain unchanged.

Quick mode reports:

```text
TEXT: Signature "SIGNATURE" found in relative/path.txt at line 3
```

Line numbers start at one and identify the line containing the first byte of the match. The scanner treats `LF`,
`CRLF`, and standalone `CR` as line endings. A match at byte zero is on line 1. Multiple matches on the same line
report the same line number.

## Go scanner

Rename the finding model's `Position` field to `Line` and change its JSON name to `line`. While processing the
ascending match offsets returned by Go's regular expression engine, maintain a byte cursor and one-based line number
for each signature scan. Advance the cursor only to the start of the next match and count each line ending once.

The line calculation applies before classifying a match, so `found`, `allowed`, and `excluded` events use the same
rules. Quick mode uses the calculated line in its text output.

## JavaFX consumer

Rename the parsed message value from `position` to `line` and require signature events to provide a value of at least
one. The parser no longer accepts the old `position` contract.

Build display context from the reported line and exact match. Read the requested line from the file, locate the exact
match on that line, and use it to build the existing excerpt. If the line is outside the file or does not contain the
reported match, emit the existing context warning and still add the signature item.

When the same exact value occurs more than once on the reported line, use the first occurrence. The CLI contract does
not include a column, and the resulting line number and display excerpt are identical for equivalent exact matches.

## Compatibility

This is an intentional protocol change between `cfcli` and JavaFX. Both producer and consumer change together. Other
event fields, event classifications, prefixes, exit codes, finding limits, and ordering remain unchanged.

## Verification

Automated tests cover:

- `found`, `allowed`, and signature-level `excluded` JSON events with `line`;
- quick output using `at line N`;
- matches on the first line and after `LF`, `CRLF`, and standalone `CR`;
- Unicode content before a match;
- multiple matches on one line;
- JavaFX parsing and validation of `line`;
- JavaFX context extraction by line, including invalid lines and changed content;
- removal of `position` from active code, tests, and user documentation.

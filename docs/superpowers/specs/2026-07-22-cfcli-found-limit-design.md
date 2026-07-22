# cfcli Per-Signature Finding Limit

## Goal

Limit repeated findings so that one signature key cannot produce more than five `found` results for the same file.

## Behavior

The scanner maintains an independent counter for each file and signature key. All compiled regular expressions that
produce the same key share this counter. A match increments the counter only after it has been classified as `found`.
Matches classified as `allowed` or `excluded` remain visible in JSON mode but do not increment the counter.

The fifth `found` match is processed normally. After that match, the scanner stops applying the current signature to
the remainder of the current file. It continues scanning the file with the remaining signature keys and continues
scanning subsequent files normally.

Quick mode retains its existing behavior: the first `found` match terminates the complete scan. The new limit therefore
primarily affects JSON mode.

## Implementation

Define the limit as a named constant in the scanner. Create a key-indexed counter map for each file and share it across
the file's compiled signatures. Iterate through matches in their existing order, preserving the current exclusion and
allowed-value classification. Increment the key's counter immediately before processing a match as `found`, and leave
the match loop after the fifth such result has been emitted.

The scanner cannot use the regular expression API's fixed match-count argument because allowed and excluded matches do
not count toward the limit.

## Verification

Automated scanner tests will verify that:

- only the first five `found` matches for one signature key are emitted from a file;
- allowed and excluded matches do not consume the limit;
- multiple compiled signatures with the same key share one limit;
- another signature key is still checked in the same file after the first key reaches its limit.

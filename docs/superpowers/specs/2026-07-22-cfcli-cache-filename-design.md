# CF CLI cache filename design

## Goal

Rename the encrypted dictionary cache from `~/.qubership/dictionary-latest-cache.encrypted` to
`~/.qubership/sensitive-signatures.encrypted`.

## Behavior

Use `sensitive-signatures.encrypted` as the only cache filename for freshness checks, downloads, temporary replacement
files, dictionary reads, error messages, and the informational `Dictionary path` output.

Do not read, move, delete, or otherwise migrate the old cache file. If only the old file exists, treat the new cache as
missing and download it through the existing refresh flow. All timeout and failure behavior remains unchanged.

## Documentation and testing

Update the current `cfcli` README and active dictionary-status design and plan. Keep historical design documents that
describe the original implementation unchanged.

Change the cache constant first through a failing test that requires the new filename. Run all Go tests and `go vet`
after updating affected expectations.

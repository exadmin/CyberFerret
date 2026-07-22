# CF CLI dictionary status output design

## Goal

Report whether the encrypted dictionary cache was updated, remained current, or could not be updated before scanning.
Print the dictionary path on the following line in both `quick` and `json` modes.

## Output contract

Write the status and path to standard output through `lineOutput.text`, which adds the `TEXT:` prefix and flushes each
line. Emit the messages after cache preparation succeeds and before reading and decrypting the dictionary.

Use one of these status messages:

```text
TEXT: Dictionary was updated.
TEXT: Dictionary is up to date.
TEXT: Dictionary was not updated due to network issues; using the existing dictionary.
```

Always follow the status with:

```text
TEXT: Dictionary path: ~/.qubership/sensitive-signatures.encrypted
```

The fallback status accompanies the existing detailed `Dictionary refresh failed: ...` warning on standard error.
The application still uses the stale cache when it is available.

## Cache result

Change `cacheRefresher.refresh` to return a result containing the cache path and one of three states: current, updated,
or fallback. Keep cache freshness checks, timeout behavior, downloads, and fallback rules unchanged.

The application maps each state to its status message. This keeps cache operations separate from user-facing output
and ensures both scan modes follow the same code path.

## Path display

When the cache is under the resolved home directory, replace that prefix with `~` and convert path separators to `/`.
For example, both Windows and Linux display `~/.qubership/sensitive-signatures.encrypted`.

If the path is outside the home directory or cannot be made relative safely, display the cleaned path with `/`
separators. Path shortening affects only informational output; file access continues to use the original path.

## Error handling

If either new `TEXT:` line cannot be written, exit with code 1 and report a `Cannot write dictionary status` or
`Cannot write dictionary path` error through the existing fatal-output path.

Cache preparation failures without a usable dictionary keep the existing exit behavior and do not print a successful
status or path.

## Testing

Add focused tests for fresh, updated, and fallback cache states. Verify the status message order, `~/` path shortening,
forward slashes, and output in both `quick` and `json` application modes. Run the full Go test and vet commands after
the focused tests pass.

# FX Tree Status Column Design

## Goal

Replace the Explorer tree's Boolean `Allowed` column with a textual `Status` column and remove only the visual
`To be ignored` column.

## Status mapping

The `Status` cell value depends on the `FoundPathItem` type and flags:

- `DIRECTORY` displays `Folder`.
- `FILE` displays `File`.
- `SIGNATURE` with `isAllowedValue()` set displays `Allowed`.
- `SIGNATURE` with `isIgnored()` set displays `Excluded`.
- Every other `SIGNATURE` displays `Warning`.

Allowed takes precedence over excluded if both flags are set. The CLI tree assembler does not normally create that
combination, but a deterministic order keeps the UI behavior defined.

## UI changes

`SceneBuilder` removes the `To be ignored` column, its checkbox cell factory, its width binding, and its addition to
the `TreeTableView`. The existing ignored state remains in `FoundPathItem` and continues to control row coloring,
context-menu actions, and exclusion persistence.

The former `Allowed` column becomes a non-editable, non-sortable `TreeTableColumn<FoundPathItem, String>` named
`Status`. A package-visible status-mapping method supplies its cell value so the mapping can be tested without showing
a JavaFX window.

The persisted width setting changes from `tree-table.allowed-column.width` to `tree-table.status-column.width`. The old
setting is not migrated because it controls a column that no longer exists.

## Testing

A focused unit test creates directory, file, allowed-signature, excluded-signature, and ordinary-signature items. It
asserts the exact five displayed status values. The FX module test suite verifies that the surrounding tree assembly
and scanning behavior remain unchanged.

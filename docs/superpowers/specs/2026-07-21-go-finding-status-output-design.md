# Go CLI finding status output design

## Goal

Emit every signature match in JSON mode with an explicit status while preserving quick-mode output and exit semantics.

## JSON contract

Every signature event contains `type`, `key`, `found`, `position`, and `file`. The type is `found` for a reportable
finding, `allowed` for a dictionary allowed-value match, and `excluded` for a grand-report match. Full file and directory
exclusions keep their shorter `{"type":"excluded","file":"..."}` shape.

Grand-report exclusions have priority over allowed values, so a match present in both produces only an `excluded` event.
Only `found` events set the scan's finding state and cause exit code 2. Allowed and excluded events are diagnostics.

## Modes

JSON mode emits all three signature event types and completes the scan. Quick mode emits neither allowed nor excluded
events and retains its existing `TEXT:` message for the first reportable finding.


# CFCLI versioning

Update the `appVersion` constant in `cfcli/version.go` every time you change the `cfcli` application. The version uses
the `MAJOR.MINOR.PATCH` format.

- Increment `PATCH` for a bug fix.
- Increment `MINOR` for a change request (CR), and reset `PATCH` to `0`.
- Change `MAJOR` only when the user explicitly requests it. An agent must never increment `MAJOR` independently.

For example, a bug fix changes `1.2.0` to `1.2.1`. A subsequent CR changes `1.2.1` to `1.3.0`.

# Application versioning

The `cfcli` and `fx` applications share one version. Every change to either application must update both version
declarations to the same `MAJOR.MINOR.PATCH` value:

- the `appVersion` constant in `cfcli/version.go`;
- the `revision` property in the root `pom.xml`, which supplies the `fx` application version.

Never update one application version without updating the other. Choose the shared version based on the change:

- Increment `PATCH` for a bug fix.
- Increment `MINOR` for a change request (CR), and reset `PATCH` to `0`.
- Change `MAJOR` only when the user explicitly requests it. An agent must never increment `MAJOR` independently.

For example, if both applications are at `1.2.0`, a bug fix in either application changes both declarations to
`1.2.1`. A subsequent CR in either application changes both declarations from `1.2.1` to `1.3.0`.

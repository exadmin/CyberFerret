# Application versioning

The `cfcli` and `fx` applications share one version. Every version update must set all three locations to the same
`MAJOR.MINOR.PATCH` value:

- the `appVersion` constant in `cfcli/version.go`;
- the `revision` property in the root `pom.xml`, which supplies the `fx` application version;
- the version in the root `README.md` title, including the `v` prefix (for example, `# Cyber Ferret, v2.2.0`).

Every change to either application must update the shared version. Never update one location without updating the
other two. Use the exact version in the README title, rather than a placeholder such as `v2.x.x`.
Choose the shared version based on the change:

- Increment `PATCH` for a bug fix.
- Increment `MINOR` for a change request (CR), and reset `PATCH` to `0`.
- Change `MAJOR` only when the user explicitly requests it. An agent must never increment `MAJOR` independently.

For example, if both applications are at `1.2.0`, a bug fix in either application changes all three locations to
`1.2.1` (with `v1.2.1` in the README title). A subsequent CR changes the shared version from `1.2.1` to `1.3.0`.

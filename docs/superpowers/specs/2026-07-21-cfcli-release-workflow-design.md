# CF CLI release workflow design

## Goal

Publish minimal-size Windows and Linux AMD64 builds of `cfcli` as GitHub Release assets when a matching version tag is
pushed.

## Trigger and permissions

The Netcracker workflow-template catalog has no Go binary release template. Its `go-build.yaml` template covers CI
builds, but it does not cover cross-compilation, UPX compression, or GitHub Release assets. The workflow is therefore
repository-specific rather than a fork of an unrelated release template.

Add `.github/workflows/cfcli-release.yml`. It runs only for pushed tags matching `cfcli-v*` and includes a descriptive
`run-name`. Declare `permissions: {}` at workflow level and grant only `contents: write` at the release-job level. No
package, pull-request, issue, or workflow write permissions are granted.

Use release-safe concurrency keyed by `github.ref`, with `cancel-in-progress: false`, so a newer run cannot cancel a
release that is already publishing. Set an explicit job timeout.

The workflow uses an existing tag rather than creating one. `gh release create` runs with `--verify-tag` and
`--generate-notes`.

## Build

Use one `ubuntu-latest` job. Pin every action to a full 40-character commit SHA and retain a trailing version comment.
Use the Qubership-tested `actions/checkout` v6.0.3 pin and set `persist-credentials: false`. Pin `actions/setup-go` to
the immutable SHA of its selected release, select Go from `cfcli/go.mod`, and configure its dependency cache for the Go
module. Resolve and verify that SHA from the official action repository before implementation.

Run `go test -count=1 ./...` and `go vet ./...` from the module before building. GitHub expressions are passed to shell
steps through environment variables rather than interpolated directly into scripts.

Cross-compile with `CGO_ENABLED=0`, `-trimpath`, and `-ldflags="-s -w -buildid="`. Produce:

- `dist/cfcli-windows-amd64.exe` with `GOOS=windows` and `GOARCH=amd64`;
- `dist/cfcli-linux-amd64` with `GOOS=linux` and `GOARCH=amd64`.

These flags remove local path metadata, the Go symbol table, DWARF debug data, and the Go build ID.

## Compression and validation

Download the official UPX 5.1.1 AMD64 Linux archive from the `upx/upx` GitHub release. Pin its official SHA-256 digest
in the workflow and reject the archive if verification fails. Extract only the UPX executable needed by the job.

Compress both release binaries with `upx -9`. Run `upx -t` against both compressed files. Mark the Linux artifact as
executable and invoke it without positional arguments, accepting its documented argument-error exit while asserting
that the process starts and prints the `cfcli` usage line.

## Release

Do not use Qubership `tag-action`: this workflow reacts to an existing pushed tag and must not create another tag.
`assets-action` would require a separate release-creation operation. The runner's built-in GitHub CLI exactly covers
the required single operation, including release creation, generated notes, tag verification, and asset upload:

```text
gh release create "$GITHUB_REF_NAME" dist/cfcli-windows-amd64.exe dist/cfcli-linux-amd64 \
  --verify-tag --generate-notes
```

Set `GH_TOKEN` from `github.token` through the step environment. No repository secret or release environment is
required. A duplicate release or failed build causes the workflow to fail instead of silently replacing assets.

## Verification

Add a workflow contract test covering the trigger, immutable action pins, least-privilege permissions, checkout
credential handling, concurrency, timeout, build flags, UPX validation, and release behavior. Validate the YAML with
`actionlint` when available, inspect the workflow diff, and run the existing Go tests and vet checks. The actual release
creation is verified after pushing a `cfcli-v*` tag because local execution does not have GitHub's release token or
hosted-runner environment.

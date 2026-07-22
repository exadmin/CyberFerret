# CF CLI release workflow design

## Goal

Publish minimal-size Windows and Linux AMD64 builds of `cfcli` as GitHub Release assets from either a matching pushed
tag or a manual workflow run.

## Trigger and permissions

The Netcracker workflow-template catalog has no Go binary release template. Its `go-build.yaml` template covers CI
builds, but it does not cover cross-compilation, UPX compression, or GitHub Release assets. The workflow is therefore
repository-specific rather than a fork of an unrelated release template.

Add `.github/workflows/cfcli-release.yml`. It runs for pushed tags matching `cfcli-v*` and through
`workflow_dispatch`. Manual runs require a `tag` input in full `cfcli-v*` format. The user selects the source branch or
commit through the standard GitHub Actions ref selector.

The workflow includes a descriptive `run-name`. Declare `permissions: {}` at workflow level and grant only
`contents: write` at the release-job level. No package, pull-request, issue, or workflow write permissions are granted.

Use release-safe concurrency keyed by `github.ref`, with `cancel-in-progress: false`, so a newer run cannot cancel a
release that is already publishing. Set an explicit job timeout.

For a tag push, the workflow uses the existing tag and runs `gh release create` with `--verify-tag`. For a manual run,
it validates the requested `cfcli-v*` tag, rejects an existing tag, and creates the tag at `github.sha` by passing
`--target` to `gh release create`. Both paths use `--generate-notes`.

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

Compress both release binaries with `upx -9`. Run `upx -t` against both compressed files and mark the Linux artifact as
executable.

## Release

Do not use Qubership `tag-action`: the automatic path reacts to an existing tag, while the manual path must create the
tag at the exact commit selected in the GitHub Actions UI. `assets-action` would require a separate release-creation
operation. The runner's built-in GitHub CLI covers both paths.

For a pushed tag:

```text
gh release create "$RELEASE_TAG" dist/cfcli-windows-amd64.exe dist/cfcli-linux-amd64 \
  --verify-tag --generate-notes
```

For a manual run:

```text
gh release create "$RELEASE_TAG" dist/cfcli-windows-amd64.exe dist/cfcli-linux-amd64 \
  --target "$RELEASE_TARGET" --generate-notes
```

The release tag and title use the exact `cfcli-v*` value. They do not add a `Release-` prefix.

Set `GH_TOKEN` from `github.token` through the step environment. No repository secret or release environment is
required. A duplicate release or failed build causes the workflow to fail instead of silently replacing assets.

## Verification

Add a workflow contract test covering the trigger, immutable action pins, least-privilege permissions, checkout
credential handling, concurrency, timeout, build flags, UPX validation, and release behavior. Validate the YAML with
`actionlint` when available, inspect the workflow diff, and run the existing Go tests and vet checks. The actual release
creation is verified after pushing a `cfcli-v*` tag or starting a manual run because local execution does not have
GitHub's release token or hosted-runner environment.

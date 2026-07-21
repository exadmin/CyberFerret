# CF CLI Release Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create GitHub Releases containing stripped and UPX-compressed Windows and Linux AMD64 builds of `cfcli`.

**Architecture:** A single Ubuntu job tests the Go module, cross-compiles both platforms, downloads and verifies an
official pinned UPX archive, compresses and validates both binaries, then creates a release for the pushed tag with the
built-in GitHub CLI. No curated Qubership template covers Go binary releases, so the repository-specific workflow
applies the Qubership security and pinning conventions directly. A lightweight repository test validates the workflow
contract without requiring a live GitHub token.

**Tech Stack:** GitHub Actions, Go 1.21+, UPX 5.1.1, GitHub CLI, JUnit 5

## Global Constraints

- Trigger only on pushed tags matching `cfcli-v*`.
- Build only Windows AMD64 and Linux AMD64 artifacts.
- Use `CGO_ENABLED=0`, `-trimpath`, and `-ldflags="-s -w -buildid="`.
- Compress both artifacts with `upx -9` and validate them with `upx -t`.
- Declare `permissions: {}` globally and grant only `contents: write` at the release-job level.
- Pin every action to a full 40-character commit SHA with a trailing version comment.
- Disable persisted checkout credentials.
- Use release concurrency with `cancel-in-progress: false` and an explicit job timeout.
- Do not replace an existing release or existing release assets.
- Do not add runtime dependencies.

---

### Task 1: Define the release workflow contract

**Files:**
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/workflow/CfCliReleaseWorkflowTests.java`
- Create later: `.github/workflows/cfcli-release.yml`

**Interfaces:**
- Produces: a repository-level test that treats the workflow as text and verifies its security- and release-critical
  contract.

- [ ] **Step 1: Write the failing test**

Read `.github/workflows/cfcli-release.yml` and assert that it contains the `cfcli-v*` tag trigger, global
global `permissions: {}`, job-level `contents: write`, full-SHA action pins, `persist-credentials: false`, release-safe
concurrency, an explicit timeout, both artifact names, `CGO_ENABLED: 0`, `-trimpath`, `-s -w -buildid=`, `upx -9`,
`upx -t`, `--verify-tag`, and `--generate-notes`.

- [ ] **Step 2: Verify RED**

Run:

```text
mvn -pl fx -am "-Dtest=CfCliReleaseWorkflowTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: the test fails because `.github/workflows/cfcli-release.yml` does not exist.

### Task 2: Implement the workflow

**Files:**
- Create: `.github/workflows/cfcli-release.yml`

**Interfaces:**
- Consumes: `cfcli/go.mod` and pushed tags matching `cfcli-v*`.
- Produces: release assets `cfcli-windows-amd64.exe` and `cfcli-linux-amd64`.

- [ ] **Step 1: Pin official tool inputs**

Use the Qubership-tested `actions/checkout` v6.0.3 commit SHA and resolve the selected `actions/setup-go` release to
its official full commit SHA. Add trailing version comments. Use UPX 5.1.1 and store its official AMD64 Linux archive
digest as an environment constant.

- [ ] **Step 2: Implement test, build, and strip steps**

Add `run-name`, global and job permissions, release-safe concurrency, job timeout, and checkout with
`persist-credentials: false`. Run Go test and vet from `cfcli`. Build both platforms into `dist/` with the specified
environment and flags. Pass GitHub expression values to scripts through step environment variables.

- [ ] **Step 3: Implement UPX installation and compression**

Download the pinned official archive and verify it with `sha256sum --check`. Extract it, run `upx -9` for both
artifacts, and run `upx -t` for both artifacts.

- [ ] **Step 4: Implement release creation**

Set `GH_TOKEN: ${{ github.token }}` and run:

```text
gh release create "$GITHUB_REF_NAME" \
  dist/cfcli-windows-amd64.exe \
  dist/cfcli-linux-amd64 \
  --verify-tag \
  --generate-notes
```

- [ ] **Step 5: Verify GREEN**

Run the focused Maven test from Task 1. Expected: the workflow contract test passes.

### Task 3: Verify builds and repository integration

**Files:**
- Review: `.github/workflows/cfcli-release.yml`
- Review: `fx/src/test/java/com/github/exadmin/cyberferret/workflow/CfCliReleaseWorkflowTests.java`

- [ ] **Step 1: Verify Go locally**

Run from `cfcli`:

```text
go test -count=1 ./...
go vet ./...
```

Expected: both commands exit zero.

- [ ] **Step 2: Verify cross-build flags locally**

Build Windows and Linux AMD64 binaries with the exact workflow flags and confirm both files exist. Then remove only
these generated verification files.

- [ ] **Step 3: Validate YAML and shell syntax**

Use an available YAML parser and `actionlint` if already installed. Do not add a project dependency solely for
validation. Review each multiline shell block with `bash -n` after substituting GitHub expressions with inert values.

- [ ] **Step 4: Run the required build**

Run:

```text
mvn clean package assembly:single
```

Expected: `BUILD SUCCESS` for every reactor module.

- [ ] **Step 5: Final review**

Run `git diff --check` and `git status --short`. Confirm that no compiled artifacts, downloaded UPX archives, tokens,
or credentials are present in the working tree.

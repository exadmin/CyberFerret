# CF CLI manual release implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow maintainers to create a `cfcli` tag and GitHub Release manually from a selected branch or commit.

**Architecture:** Extend the existing workflow with `workflow_dispatch` and a required `tag` input. The release step
keeps `--verify-tag` for tag-push runs and uses `--target` for manual runs, which creates the requested tag at the
selected commit. The existing JUnit workflow contract protects both paths.

**Tech Stack:** GitHub Actions, GitHub CLI, JUnit 5

## Global constraints

- Keep the automatic `cfcli-v*` tag-push trigger.
- Require a full `cfcli-v*` tag value for manual runs.
- Create a manual tag at `github.sha`, which represents the ref selected in the Actions UI.
- Reject a malformed or existing manual tag before release creation.
- Use the exact tag as the release tag and title without a `Release-` prefix.
- Pass GitHub context values to shell through `env`.
- Preserve immutable action pins, permissions, timeout, concurrency, build flags, UPX compression, and asset names.

---

### Task 1: Add the manual release path

**Files:**

- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/workflow/CfCliReleaseWorkflowTests.java`
- Modify: `.github/workflows/cfcli-release.yml`

**Interfaces:**

- Consumes: a manually selected Git ref and required `inputs.tag` value.
- Produces: a new `cfcli-v*` tag at `github.sha` and a GitHub Release containing the existing two binary assets.

- [ ] **Step 1: Extend the contract test**

Add assertions for `workflow_dispatch`, the `tag` input, `--target`, `github.sha`, manual-event branching, and the
absence of `Release-`:

```java
assertContains(workflow, "workflow_dispatch:");
assertContains(workflow, "tag:");
assertContains(workflow, "github.sha");
assertContains(workflow, "workflow_dispatch");
assertContains(workflow, "--target");
assertFalse(workflow.contains("Release-"), "Release tags must not add a Release- prefix");
```

- [ ] **Step 2: Verify RED**

Run:

```text
mvn -o -pl fx -am "-Dtest=CfCliReleaseWorkflowTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: FAIL because the workflow does not contain `workflow_dispatch` or `--target`, and it still contains
`Release-`.

- [ ] **Step 3: Add the manual trigger**

Extend the trigger and make the run name identify either the manual input or pushed tag:

```yaml
run-name: Release CF CLI ${{ github.event_name == 'workflow_dispatch' && inputs.tag || github.ref_name }}

on:
  push:
    tags:
      - "cfcli-v*"
  workflow_dispatch:
    inputs:
      tag:
        description: Tag to create in cfcli-v* format
        required: true
        type: string
```

Key concurrency by the requested tag for manual runs and by the pushed ref for automatic runs:

```yaml
concurrency:
  group: release-${{ github.event_name == 'workflow_dispatch' && inputs.tag || github.ref }}
  cancel-in-progress: false
```

- [ ] **Step 4: Implement both release paths**

Replace the release step environment and script with:

```yaml
env:
  EVENT_NAME: ${{ github.event_name }}
  GH_TOKEN: ${{ github.token }}
  RELEASE_TAG: ${{ github.event_name == 'workflow_dispatch' && inputs.tag || github.ref_name }}
  RELEASE_TARGET: ${{ github.sha }}
shell: bash
run: |
  set -euo pipefail
  if [[ ! "$RELEASE_TAG" =~ ^cfcli-v.+$ ]]; then
    echo "Invalid release tag '$RELEASE_TAG': expected cfcli-v* format." >&2
    exit 1
  fi
  if [[ "$EVENT_NAME" == "workflow_dispatch" ]]; then
    if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/$RELEASE_TAG" >/dev/null 2>&1; then
      echo "Cannot create release tag '$RELEASE_TAG': the tag already exists." >&2
      exit 1
    fi
    gh release create "$RELEASE_TAG" \
      dist/cfcli-windows-amd64.exe \
      dist/cfcli-linux-amd64 \
      --target "$RELEASE_TARGET" \
      --generate-notes \
      --title "$RELEASE_TAG"
  else
    gh release create "$RELEASE_TAG" \
      dist/cfcli-windows-amd64.exe \
      dist/cfcli-linux-amd64 \
      --verify-tag \
      --generate-notes \
      --title "$RELEASE_TAG"
  fi
```

- [ ] **Step 5: Verify GREEN**

Run the focused Maven command from Step 2.

Expected: `CfCliReleaseWorkflowTests` passes with zero failures.

- [ ] **Step 6: Validate the workflow**

Parse `.github/workflows/cfcli-release.yml` with the available YAML parser. Run `actionlint` if installed. Verify that
every `uses:` reference remains a full 40-character SHA with a version comment.

Expected: YAML parsing and all available lint checks pass.

- [ ] **Step 7: Run unaffected Go checks**

Run from `cfcli`:

```text
go test -count=1 ./...
go vet ./...
```

Expected: both commands exit zero.

- [ ] **Step 8: Review the final diff**

Run:

```text
git diff --check
git status --short
```

Expected: no whitespace errors, build artifacts, downloaded archives, tokens, or credentials are present.

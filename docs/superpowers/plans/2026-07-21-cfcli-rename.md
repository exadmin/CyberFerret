# CF CLI Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the Go scanner and every active integration point from `cli-go` to `cfcli`.

**Architecture:** Perform an atomic source-level rename while preserving the scanner protocol and runtime behavior. Rename Go and Java filesystem locations together with their declared module/package names, then update persistence, UI, tests, and documentation. Do not add compatibility aliases.

**Tech Stack:** Go 1.21+, Java 21+, JavaFX 21+, Maven 3.9+, JUnit 5

## Global Constraints

- The only command name is `cfcli` (`cfcli.exe` on Windows).
- The Go source directory and module suffix are `cfcli`.
- The Java package and public type prefix are `cfcli` and `CfCli`.
- The only persisted executable key is `cfcli.path`.
- Do not change command arguments, output JSON, scanning behavior, or exit codes.
- Do not add dependencies.

---

### Task 1: Rename the Go command

**Files:**
- Rename: `cli-go/` to `cfcli/`
- Modify: `cfcli/go.mod`
- Modify: `cfcli/app.go`
- Modify: `cfcli/app_test.go`
- Modify: `cfcli/.gitignore`
- Modify: `cfcli/README.md`

**Interfaces:**
- Produces: Go module `github.com/exadmin/cyberferret/cfcli` and command `cfcli`.

- [ ] **Step 1: Update the usage test first**

Change the expected usage prefix in `app_test.go` from the old command to:

```text
TEXT: usage: cfcli [--mode=quick|--mode=json] [--verbose=true|--verbose=false] FOLDER_PATH [PATH_TO_LIST_OF_FILES]
```

- [ ] **Step 2: Verify RED**

Run `go test -run TestRun ./...` from the existing Go directory. Expected: the usage assertion fails because production usage still has the old name.

- [ ] **Step 3: Rename and update Go sources**

Rename the directory, change `go.mod`, change production usage, ignore `cfcli` and `cfcli.exe`, and update README build and invocation examples.

- [ ] **Step 4: Verify GREEN**

Run from `cfcli`:

```text
go test -count=1 ./...
go vet ./...
```

Expected: both commands exit zero.

### Task 2: Rename Java protocol and process types

**Files:**
- Rename: `fx/src/main/java/com/github/exadmin/cyberferret/gocli/` to `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/`
- Rename: `fx/src/test/java/com/github/exadmin/cyberferret/gocli/` to `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/`
- Modify all renamed Java source and test files.
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/fxui/SceneBuilder.java`

**Interfaces:**
- Produces: `CfCliExecutable`, `CfCliMessage`, `CfCliMessageParser`, `CfCliScanner`, and `CfCliTreeAssembler` in package `com.github.exadmin.cyberferret.cfcli`.

- [ ] **Step 1: Update executable expectations first**

Change the default-command assertion to expect `cfcli`, and change scanner assertions and fixtures to use `cfcli.exe`.

- [ ] **Step 2: Verify RED**

Run the focused executable and scanner tests. Expected: assertions fail because production still resolves and reports the old command.

- [ ] **Step 3: Rename packages, files, types, and messages**

Rename directories and Java files, update package declarations/imports/type references, set the default command to `cfcli`, and replace user-facing process messages with `cfcli`.

- [ ] **Step 4: Verify GREEN**

Run:

```text
mvn -pl fx -am "-Dtest=CfCliExecutableTests,CfCliScannerTests,CfCliScannerExecutableTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: all focused tests pass.

### Task 3: Rename persistence and UI settings

**Files:**
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/persistence/PersistentPropertiesManager.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/fxui/SceneBuilder.java`
- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/persistence/PersistentPropertiesManagerTests.java`
- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/fxui/helpers/ChooserBuilderTests.java`

**Interfaces:**
- Produces: `PersistentPropertiesManager.CF_CLI_PATH`, registered as `cfcli.path`.

- [ ] **Step 1: Update persistence tests first**

Change fixtures and assertions to `cfcli.path` and `C:\\Tools\\cfcli.exe`. Assert that a saved property file does not contain `go-cli.path`.

- [ ] **Step 2: Verify RED**

Run the focused persistence test. Expected: compilation fails because `CF_CLI_PATH` does not exist.

- [ ] **Step 3: Rename the property and UI binding**

Replace `GO_CLI_PATH` with `CF_CLI_PATH`, register `cfcli.path`, bind the online pane to the new property, label it `CF CLI executable`, and update validation messages.

- [ ] **Step 4: Verify GREEN**

Run `mvn -pl fx -am test`. Expected: all common and FX tests pass.

### Task 4: Rename repository documentation

**Files:**
- Modify: repository Markdown files under `docs/superpowers/` that refer to the old command, directory, module, Java package, type names, or persisted key.

- [ ] **Step 1: Find stale names**

Run a repository search excluding `.git`, build output, caches, and the approved rename design/plan. Record occurrences of `cli-go`, `GoCli`, `gocli`, `GO_CLI_PATH`, and `go-cli.path`.

- [ ] **Step 2: Replace semantic references**

Update command examples, paths, headings, module names, package names, class names, persisted keys, and commit-message examples to their `cfcli` equivalents.

- [ ] **Step 3: Verify the rename**

Repeat the search. Expected: no stale occurrences outside the rename design and plan, where old names document the mapping.

### Task 5: Build artifacts and full verification

**Files:**
- Generated and removed after inspection: `cfcli/cfcli.exe`, `cfcli/cfcli`.

- [ ] **Step 1: Format and verify Go**

Run from `cfcli`:

```text
gofmt -w *.go
go test -count=1 ./...
go vet ./...
go build -o cfcli.exe .
```

Expected: all commands exit zero and `cfcli.exe` exists.

- [ ] **Step 2: Cross-build Linux**

Run with `GOOS=linux` and `GOARCH=amd64`:

```text
go build -o cfcli .
```

Expected: the executable `cfcli` exists without a suffix.

- [ ] **Step 3: Remove generated verification binaries**

Remove only the two verified artifacts `cfcli/cfcli.exe` and `cfcli/cfcli`. Confirm both paths are inside the renamed Go module before removal.

- [ ] **Step 4: Run the required Maven build**

Run:

```text
mvn clean package assembly:single
```

Expected: `BUILD SUCCESS` for every reactor module.

- [ ] **Step 5: Review the final tree**

Run `git diff --check`, `git status --short`, and the stale-name search. Expected: no whitespace errors, no generated binaries, no old Go directory, and no stale active references.

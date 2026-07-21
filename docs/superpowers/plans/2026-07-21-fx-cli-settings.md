# FX CLI Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the dictionary selection panes with persistent CF CLI executable settings and a read-only environment password display.

**Architecture:** Store only the CLI executable path in the existing persistent property registry. Build the online settings pane from the environment password and the persistent CLI property, then inject the resolved executable into `CfCliScanner`. Keep validation separate from JavaFX controls so it can be tested without launching the toolkit.

**Tech Stack:** Java 21, JavaFX 21, Maven 3.9+, JUnit 5

## Global Constraints

- Do not persist `CYBER_FERRET_PASSWORD`.
- An empty CLI path resolves to `cfcli` through `PATH`.
- Existing dictionary loading code remains intact.
- `Online Dictionary` and `Repository` remain expanded and cannot collapse.
- Do not add dependencies.

---

### Task 1: Persistent CLI executable setting

**Files:**
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/persistence/PersistentPropertiesManager.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/persistence/PersistentPropertiesManagerTests.java`

**Interfaces:**
- Produces: `PersistentPropertiesManager.CF_CLI_PATH`, an `AbstractPersistentProperty<String>` registered as `cfcli.path`.
- Removes: the registered `PASSWORD` property so secrets cannot be loaded from or written to `app.properties`.

- [ ] **Step 1: Write failing persistence tests**

Create tests that write `cfcli.path=C:\\Tools\\cfcli.exe`, construct the manager, and assert `CF_CLI_PATH`. Set a legacy password value, save the file, and assert that `dictionary.password` is absent.

- [ ] **Step 2: Verify RED**

Run:

```text
mvn -pl fx -am "-Dtest=PersistentPropertiesManagerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because `CF_CLI_PATH` does not exist or the password is still persisted.

- [ ] **Step 3: Implement the property change**

Register:

```java
public static final AbstractPersistentProperty<String> CF_CLI_PATH =
        new AppStringProperty("cfcli.path", "", REG_MAP);
```

Remove the registered password property and the environment-to-persistence loading block.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2. Expected: all tests pass.

### Task 2: Executable resolution and process command

**Files:**
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliScanner.java`
- Modify: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliScannerTests.java`
- Create: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliExecutable.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliExecutableTests.java`

**Interfaces:**
- Produces: `CfCliExecutable.resolve(String): String` and `CfCliExecutable.validateExplicitPath(String): Optional<String>`.
- Changes: `CfCliScanner` public constructor accepts `String executable` before `Path root`.

- [ ] **Step 1: Write failing resolution and command tests**

Assert that blank input resolves to `cfcli`, a nonblank value remains unchanged, regular files validate, and missing files or directories return an error. Update the scanner command test to expect the injected executable at index zero.

- [ ] **Step 2: Verify RED**

Run:

```text
mvn -pl fx -am "-Dtest=CfCliExecutableTests,CfCliScannerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because the resolver and constructor do not exist.

- [ ] **Step 3: Implement executable resolution**

Implement a small immutable helper that trims only for blank detection, returns `cfcli` for blank input, and validates only explicit paths with `Files.isRegularFile`. Store the resolved executable in `CfCliScanner` and use it as the command's first element.

- [ ] **Step 4: Verify GREEN**

Run the focused command from Step 2. Expected: all tests pass.

### Task 3: Online settings UI and fixed panes

**Files:**
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/fxui/SceneBuilder.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/fxui/helpers/ChooserBuilder.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/fxui/helpers/ChooserBuilderTests.java`

**Interfaces:**
- Consumes: `CF_CLI_PATH`, `CfCliExecutable.resolve`, and `CfCliExecutable.validateExplicitPath`.
- Produces: a file chooser row bound to `CF_CLI_PATH`; the password field reads `System.getenv(SYS_ENV_VAR_PASSWORD)` and is non-editable.

- [ ] **Step 1: Write failing chooser behavior test**

Extract and test the initial-directory calculation used by the file chooser. Assert that a configured executable uses its parent directory and an empty path yields no initial directory.

- [ ] **Step 2: Verify RED**

Run:

```text
mvn -pl fx -am "-Dtest=ChooserBuilderTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected: compilation fails because the path calculation API does not exist.

- [ ] **Step 3: Implement the UI**

Remove creation of the offline pane and the dictionary `Accordion`. Add the online and repository panes directly to the root `VBox`, set both to `collapsible=false` and `expanded=true`, and replace the online download controls with:

```text
Password             [********]
CF CLI executable    [C:\Tools\cfcli.exe] [Select ...]
```

Make the password field non-editable. Bind the executable chooser to `CF_CLI_PATH`. Before scanning, validate the explicit path and pass the resolved executable to `CfCliScanner`.

- [ ] **Step 4: Verify GREEN**

Run all FX tests:

```text
mvn -pl fx -am test
```

Expected: all tests pass.

### Task 4: Full verification

**Files:**
- Review all files changed in Tasks 1–3.

- [ ] **Step 1: Check the diff**

Run:

```text
git diff --check
git status --short
```

Expected: no whitespace errors and only intended files are modified.

- [ ] **Step 2: Run the required build**

Run:

```text
mvn clean package assembly:single
```

Expected: `BUILD SUCCESS` for every reactor module.

- [ ] **Step 3: Review security and behavior**

Confirm that no property named `dictionary.password` is registered or saved, no password value is logged, the default executable remains `cfcli`, and both settings panes are non-collapsible.

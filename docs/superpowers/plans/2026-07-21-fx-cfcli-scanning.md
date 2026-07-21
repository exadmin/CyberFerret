# JavaFX scanning through the CF CLI implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Java-side FX scanning with asynchronous `cfcli` execution and streaming TreeTableView population.

**Architecture:** Split the feature into a protocol parser, tree assembler, and process runner. Keep JavaFX concerns in
`SceneBuilder`; keep process and model business logic independently testable without starting the JavaFX toolkit.

**Tech Stack:** Java 21, JavaFX 21, JUnit 5, Maven, and the Java standard library

## Global constraints

- Add no runtime dependencies.
- Add only the approved existing JUnit 5 test dependencies to `fx/pom.xml`.
- Invoke `cfcli --mode=json --verbose=true <FOLDER_PATH>` from `PATH`.
- Treat process exit codes 0 and 2 as successful.
- Never block the JavaFX application thread on process or file I/O.
- Preserve existing public APIs unless a backward-compatible listener default method is required.
- Keep dictionary loading UI unchanged and remove it only from the scan execution path.

---

### Task 1: Model update notifications

**Files:**

- Modify: `common/src/main/java/com/github/exadmin/cyberferret/model/FoundFileItemListener.java`
- Modify: `common/src/main/java/com/github/exadmin/cyberferret/model/FoundItemsContainer.java`
- Modify: `common/src/test/java/com/github/exadmin/cyberferret/model/FoundItemsContainerTests.java`

**Interfaces:**

- Add: `FoundFileItemListener.itemUpdated(FoundPathItem item, long generation)` as a default method.
- Add: `FoundItemsContainer.notifyItemUpdated(FoundPathItem item)`.

- [ ] **Step 1: Add a failing notification test**

Create an item, add it to the container, mutate its ignored state, call `notifyItemUpdated`, and assert that the listener
receives the same item and current generation.

- [ ] **Step 2: Verify RED**

Run `mvn -pl common -am -Dtest=FoundItemsContainerTests test`. Expected: compilation fails because the update API does
not exist.

- [ ] **Step 3: Implement backward-compatible notification**

Add no-op default overloads to the listener and a container method that captures the current generation under the same
lock used for additions before notifying the listener.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 2: CF CLI protocol parser

**Files:**

- Modify: `fx/pom.xml`
- Create: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliMessage.java`
- Create: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliMessageParser.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliMessageParserTests.java`

**Interfaces:**

- Add: immutable `CfCliMessage` fields for type, file, folder, key, found, and position.
- Add: `CfCliMessageParser.parse(String line)` returning an optional message for `JSON:` lines.

- [ ] **Step 1: Add approved JUnit dependencies and failing parser tests**

Cover all five event shapes, escaped quotes, backslashes, Unicode escapes, negative and missing positions, unknown fields,
non-JSON lines, malformed JSON, and missing required fields.

- [ ] **Step 2: Verify RED**

Run `mvn -pl fx -am -Dtest=CfCliMessageParserTests -Dsurefire.failIfNoSpecifiedTests=false test`. Expected: compilation
fails because parser classes do not exist.

- [ ] **Step 3: Implement the fixed-schema parser**

Write a cursor-based parser for one JSON object. Parse strings and integral numbers, skip supported primitive unknown
values, validate event-specific fields, and return empty for lines without the `JSON:` prefix.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 3: File context and tree assembly

**Files:**

- Create: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/FileMatchContext.java`
- Create: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliTreeAssembler.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/FileMatchContextTests.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliTreeAssemblerTests.java`

**Interfaces:**

- Add: `FileMatchContext.from(byte[] content, int position, String exact)`.
- Add: `CfCliTreeAssembler.accept(CfCliMessage message)`.

- [ ] **Step 1: Add failing context tests**

Test one-based line numbers, UTF-8 byte offsets, 50-code-point limits, same-line clipping, whitespace normalization,
beginning and end boundaries, and invalid offsets.

- [ ] **Step 2: Add failing assembler tests**

Test short visual names, nested hierarchy, node deduplication, found, allowed, and excluded flags, signature field mapping,
path traversal rejection, path-level updates, and unreadable-file fallback.

- [ ] **Step 3: Verify RED**

Run `mvn -pl fx -am -Dtest='FileMatchContextTests,CfCliTreeAssemblerTests' -Dsurefire.failIfNoSpecifiedTests=false test`.
Expected: compilation fails because the context and assembler do not exist.

- [ ] **Step 4: Implement context extraction and assembly**

Resolve normalized paths under the root, index path nodes, create missing parents, cache current file bytes, construct
signature items, and notify the container when path exclusions mutate an existing node.

- [ ] **Step 5: Verify GREEN**

Run the command from Step 3. Expected: PASS.

### Task 4: Asynchronous process runner

**Files:**

- Create: `fx/src/main/java/com/github/exadmin/cyberferret/cfcli/CfCliScanner.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/cfcli/CfCliScannerTests.java`

**Interfaces:**

- Add: injectable package-private process launcher for tests.
- Add: callbacks for messages, ordinary logs, scan errors, and completion.

- [ ] **Step 1: Add failing process tests**

Use a fake `Process` to assert the exact command, ordered stdout parsing, concurrent stderr consumption, successful exit
codes 0 and 2, failure exit codes, malformed protocol handling, and guaranteed completion callback.

- [ ] **Step 2: Verify RED**

Run `mvn -pl fx -am -Dtest=CfCliScannerTests -Dsurefire.failIfNoSpecifiedTests=false test`. Expected: compilation fails
because the runner does not exist.

- [ ] **Step 3: Implement concurrent stream pumping**

Start the process through the launcher, use a two-thread executor for stdout and stderr, wait for both pumps and the
process, destroy the process on protocol failure, and call completion from `finally`.

- [ ] **Step 4: Verify GREEN**

Run the command from Step 2. Expected: PASS.

### Task 5: SceneBuilder integration

**Files:**

- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/fxui/SceneBuilder.java`

**Interfaces:**

- Consume: `CfCliScanner` and `CfCliTreeAssembler`.

- [ ] **Step 1: Replace Start Scanning behavior**

Validate the selected directory, clear the container, create an assembler, disable the button, and start a daemon thread
running `CfCliScanner`. Do not read or pass Java signature maps to the scanner.

- [ ] **Step 2: Refresh updated tree items**

Implement the new listener callback by calling `TreeTableView.refresh()` on the JavaFX thread for the matching generation.

- [ ] **Step 3: Verify module behavior**

Run `mvn -pl fx -am test`. Expected: all common and FX tests pass.

### Task 6: Full verification and documentation review

**Files:**

- Modify: `docs/superpowers/specs/2026-07-21-fx-go-cli-scanning-design.md`
- Modify: `docs/superpowers/plans/2026-07-21-fx-go-cli-scanning.md`

**Interfaces:** None.

- [ ] **Step 1: Run the repository build**

Run `mvn clean package assembly:single`. Expected: all modules compile, all tests pass, and assemblies are created.

- [ ] **Step 2: Inspect the final diff**

Run `git diff --check` and `git status --short`. Expected: no whitespace errors and only intended common, FX, test, and
design files change.

- [ ] **Step 3: Commit verified changes**

Commit with `feat(fx): scan repositories through go cli` after the repository hook passes.

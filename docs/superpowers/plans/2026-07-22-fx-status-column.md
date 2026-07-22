# FX Tree Status Column Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Explorer tree's ignored and allowed Boolean columns with one textual `Status` column.

**Architecture:** Add a package-visible status-mapping method to `SceneBuilder`, then bind a string column to it. Remove
only the visual ignored column and rename its surviving width property so exclusion behavior remains unchanged.

**Tech Stack:** Java 21, JavaFX TreeTableView, JUnit 5, Maven

## Global Constraints

- `DIRECTORY` displays `Folder`, and `FILE` displays `File`.
- Allowed, excluded, and ordinary signatures display `Allowed`, `Excluded`, and `Warning`, respectively.
- Allowed takes precedence if a signature has both allowed and excluded flags.
- Row coloring, context-menu actions, and exclusion persistence remain unchanged.
- The old `tree-table.allowed-column.width` setting is not migrated.

---

### Task 1: Add and Use the Status Mapping

**Files:**

- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/fxui/SceneBuilder.java`
- Modify: `fx/src/main/java/com/github/exadmin/cyberferret/persistence/PersistentPropertiesManager.java`
- Create: `fx/src/test/java/com/github/exadmin/cyberferret/fxui/SceneBuilderStatusTests.java`

**Interfaces:**

- Consumes: `FoundPathItem.getType()`, `isAllowedValue()`, and `isIgnored()`.
- Produces: package-visible `SceneBuilder.statusFor(FoundPathItem)` returning the displayed status string.

- [ ] **Step 1: Write a failing status-mapping test**

```java
package com.github.exadmin.cyberferret.fxui;

import com.github.exadmin.cyberferret.model.FoundPathItem;
import com.github.exadmin.cyberferret.model.ItemType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneBuilderStatusTests {
    @Test
    void mapsTreeItemsToDisplayedStatuses() {
        FoundPathItem folder = item("folder", ItemType.DIRECTORY);
        FoundPathItem file = item("file.txt", ItemType.FILE);
        FoundPathItem allowed = item("allowed", ItemType.SIGNATURE);
        allowed.setAllowedValue(true);
        FoundPathItem excluded = item("excluded", ItemType.SIGNATURE);
        excluded.setIgnored(true);
        FoundPathItem warning = item("warning", ItemType.SIGNATURE);

        assertEquals("Folder", SceneBuilder.statusFor(folder));
        assertEquals("File", SceneBuilder.statusFor(file));
        assertEquals("Allowed", SceneBuilder.statusFor(allowed));
        assertEquals("Excluded", SceneBuilder.statusFor(excluded));
        assertEquals("Warning", SceneBuilder.statusFor(warning));
    }

    private static FoundPathItem item(String path, ItemType type) {
        return new FoundPathItem(Path.of(path), type, null);
    }
}
```

- [ ] **Step 2: Run the focused test and verify that it fails**

Run from the repository root:

```powershell
mvn -pl fx -am -Dtest=SceneBuilderStatusTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `SceneBuilder.statusFor` does not exist.

- [ ] **Step 3: Implement the status mapping and replace the columns**

Add this package-visible method to `SceneBuilder`:

```java
static String statusFor(FoundPathItem item) {
    return switch (item.getType()) {
        case DIRECTORY -> "Folder";
        case FILE -> "File";
        case SIGNATURE -> {
            if (item.isAllowedValue()) yield "Allowed";
            if (item.isIgnored()) yield "Excluded";
            yield "Warning";
        }
    };
}
```

Replace the two Boolean columns with one string column:

```java
TreeTableColumn<FoundPathItem, String> colStatus = new TreeTableColumn<>("Status");
colStatus.setCellValueFactory(param -> new ReadOnlyStringWrapper(statusFor(param.getValue().getValue())));
```

Remove the ignored checkbox cell factory, ignored-column width handling, and both old column additions. Keep row styling
and context-menu code unchanged. Rename the persistent property and key:

```java
public static final AbstractPersistentProperty<Number> STATUS_COLUMN_WIDTH =
        new AppDoubleProperty("tree-table.status-column.width", 80d, REG_MAP);
```

Use `STATUS_COLUMN_WIDTH` for the new column's initial width, deferred width, and width listener.

- [ ] **Step 4: Run focused and complete tests**

Run from the repository root:

```powershell
mvn -pl fx -am -Dtest=SceneBuilderStatusTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl fx -am test
```

Expected: both commands exit successfully.

- [ ] **Step 5: Review the final change**

Run from the repository root:

```powershell
git diff --check
git diff -- fx/src/main/java/com/github/exadmin/cyberferret/fxui/SceneBuilder.java `
  fx/src/main/java/com/github/exadmin/cyberferret/persistence/PersistentPropertiesManager.java `
  fx/src/test/java/com/github/exadmin/cyberferret/fxui/SceneBuilderStatusTests.java
```

Expected: the whitespace check succeeds, and the diff contains no changes to row styling or context-menu behavior.

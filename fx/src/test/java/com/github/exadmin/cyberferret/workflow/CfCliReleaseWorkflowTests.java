package com.github.exadmin.cyberferret.workflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfCliReleaseWorkflowTests {

    private static final Path WORKFLOW = repositoryRoot()
            .resolve(".github")
            .resolve("workflows")
            .resolve("release.yml");

    @Test
    void definesSecureCombinedReleaseContract() throws IOException {
        assertTrue(Files.exists(WORKFLOW), "Combined release workflow must exist");

        String workflow = Files.readString(WORKFLOW);

        assertContains(workflow, "\"v*\"");
        assertContains(workflow, "workflow_dispatch:");
        assertContains(workflow, "tag:");
        assertContains(workflow, "github.sha");
        assertContains(workflow, "permissions: {}");
        assertContains(workflow, "contents: write");
        assertContains(workflow, "persist-credentials: false");
        assertContains(workflow, "cancel-in-progress: false");
        assertContains(workflow, "timeout-minutes:");
        assertContains(workflow, "build-cli:");
        assertContains(workflow, "build-fx:");
        assertContains(workflow, "publish-maven:");
        assertContains(workflow, "build windows amd64 \".exe\"");
        assertContains(workflow, "build windows arm64 \".exe\"");
        assertContains(workflow, "build linux amd64 \"\"");
        assertContains(workflow, "build linux arm64 \"\"");
        assertContains(workflow, "build darwin amd64 \"\"");
        assertContains(workflow, "build darwin arm64 \"\"");
        assertContains(workflow, "cyberferret-fx-");
        assertContains(workflow, "mkdir --parents dist");
        assertContains(workflow, "CGO_ENABLED: \"0\"");
        assertContains(workflow, "-trimpath");
        assertContains(workflow, "-s -w -buildid=");
        assertContains(workflow, "upx -9");
        assertContains(workflow, "upx -t");
        assertContains(workflow, "--repo \"$GITHUB_REPOSITORY\"");
        assertContains(workflow, "--verify-tag");
        assertContains(workflow, "--generate-notes");
        List<String> actionReferences = workflow.lines()
                .map(String::strip)
                .filter(line -> line.startsWith("uses:"))
                .toList();
        assertFalse(actionReferences.isEmpty(), "Workflow must use at least one action");
        assertTrue(actionReferences.stream().allMatch(line -> Pattern
                        .compile("uses: [^\\s@]+@[0-9a-f]{40}\\s+# v\\d+\\.\\d+\\.\\d+")
                        .matcher(line)
                        .matches()),
                "Every action must use an immutable commit SHA reference with a version comment");
        assertFalse(
                Pattern.compile("uses: [^\\s]+@v\\d").matcher(workflow).find(),
                "Actions must not use floating version tags"
        );
    }

    private static void assertContains(String workflow, String expected) {
        assertTrue(workflow.contains(expected), () -> "Workflow must contain: " + expected);
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve(".git"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Cannot locate repository root from the test working directory");
        }
        return candidate;
    }
}

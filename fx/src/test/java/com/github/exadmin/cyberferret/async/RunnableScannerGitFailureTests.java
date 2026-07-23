package com.github.exadmin.cyberferret.async;

import com.github.exadmin.cyberferret.model.FoundItemsContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RunnableScannerGitFailureTests {
    @TempDir
    Path tempDir;

    @Test
    public void guiMode_reportsGitEnumerationFailure() throws Exception {
        Path repository = tempDir.resolve("repository");
        Files.createDirectories(repository.resolve(".git"));
        Files.writeString(
                repository.resolve(".git/config"),
                "[invalid",
                StandardCharsets.UTF_8);

        List<CallbackMessage> messages = new ArrayList<>();
        RunnableScanner scanner = new RunnableScanner(false);
        scanner.setDirToScan(repository.toString());
        scanner.setFoundItemsContainer(new FoundItemsContainer());
        scanner.setSignaturesMap(Map.of("test", Pattern.compile("secret")));
        scanner.setFxCallback((type, message) -> messages.add(new CallbackMessage(type, message)));

        scanner.run();

        assertTrue(messages.stream().anyMatch(message ->
                message.type() == FxCallback.FxCallbackType.ERROR
                        && message.text().contains("Cannot list Git repository files")));
    }

    private record CallbackMessage(FxCallback.FxCallbackType type, String text) {
    }
}

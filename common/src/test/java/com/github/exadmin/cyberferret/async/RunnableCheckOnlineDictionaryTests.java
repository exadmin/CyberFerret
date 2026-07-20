package com.github.exadmin.cyberferret.async;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnableCheckOnlineDictionaryTests {
    @TempDir
    Path tempDir;

    @Test
    void promoteDownloadedDictionary_keepsLastKnownGoodCacheWhenCandidateIsInvalid() throws Exception {
        Path target = tempDir.resolve("dictionary.enc");
        Path candidate = tempDir.resolve("candidate.tmp");
        Files.writeString(target, "known-good", StandardCharsets.UTF_8);
        Files.writeString(candidate, "invalid", StandardCharsets.UTF_8);
        TestDownloader downloader = new TestDownloader();
        downloader.setDownloadedDictionaryValidator(path -> false);

        boolean promoted = downloader.promote(candidate, target);

        assertFalse(promoted);
        assertTrue(downloader.hasRejectedDownloadedDictionary());
        assertEquals("known-good", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    void promoteDownloadedDictionary_atomicallyReplacesCacheWhenCandidateIsValid() throws Exception {
        Path target = tempDir.resolve("dictionary.enc");
        Path candidate = tempDir.resolve("candidate.tmp");
        Files.writeString(target, "known-good", StandardCharsets.UTF_8);
        Files.writeString(candidate, "replacement", StandardCharsets.UTF_8);
        TestDownloader downloader = new TestDownloader();
        downloader.setDownloadedDictionaryValidator(path -> true);

        boolean promoted = downloader.promote(candidate, target);

        assertTrue(promoted);
        assertFalse(downloader.hasRejectedDownloadedDictionary());
        assertEquals("replacement", Files.readString(target, StandardCharsets.UTF_8));
    }

    private static final class TestDownloader extends RunnableCheckOnlineDictionary {
        private TestDownloader() {
            super(true);
        }

        private boolean promote(Path candidate, Path target) throws IOException {
            return promoteDownloadedDictionary(candidate, target);
        }
    }
}

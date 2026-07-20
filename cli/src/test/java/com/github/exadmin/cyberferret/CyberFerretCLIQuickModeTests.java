package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CyberFerretCLIQuickModeTests {
    private static final String PASSWORD = "test-password";
    private static final String SENTINEL = "DO_NOT_PRINT_THIS_SECRET";

    @TempDir
    Path tempDir;

    @Test
    void quickMode_returnsZeroForCleanRepository() throws Exception {
        Path cache = prepareDictionary();
        Path repository = prepareRepository("ordinary content");

        Invocation invocation = invoke("--mode=quick", "--offline", cacheOption(cache), repository.toString());

        assertEquals(0, invocation.exitCode());
        assertFalse(invocation.stdout().contains(SENTINEL));
        assertFalse(invocation.stderr().contains(SENTINEL));
    }

    @Test
    void quickMode_returnsOneWithoutRevealingFinding() throws Exception {
        Path cache = prepareDictionary();
        Path repository = prepareRepository(SENTINEL);

        Invocation invocation = invoke("--mode=quick", "--offline", cacheOption(cache), repository.toString());

        assertEquals(1, invocation.exitCode());
        assertTrue(invocation.stdout().contains("Findings detected"));
        assertFalse(invocation.stdout().contains(SENTINEL));
        assertFalse(invocation.stderr().contains(SENTINEL));
    }

    @Test
    void quickMode_returnsTwoForMissingOfflineDictionary() throws Exception {
        Path repository = prepareRepository("ordinary content");

        Invocation invocation = invoke(
                "--mode=quick",
                "--offline",
                cacheOption(tempDir.resolve("missing-cache")),
                repository.toString());

        assertEquals(2, invocation.exitCode());
        assertFalse(invocation.stdout().contains(PASSWORD));
        assertFalse(invocation.stderr().contains(PASSWORD));
    }

    @Test
    void quickMode_returnsTwoForIncompleteSignatureSets() throws Exception {
        Path repository = prepareRepository(SENTINEL);
        for (String dictionary : new String[]{
                "VERSION=1.4\n",
                "VERSION=1.4\nBROKEN(regexp)=[\n",
                "VERSION=1.4\nSECRET=" + SENTINEL + "\nBROKEN(regexp)=[\n"
        }) {
            Path cache = prepareDictionary(dictionary);

            Invocation invocation = invoke("--mode=quick", "--offline", cacheOption(cache), repository.toString());

            assertEquals(2, invocation.exitCode());
            assertFalse(invocation.stdout().contains(SENTINEL));
            assertFalse(invocation.stderr().contains(SENTINEL));
            assertFalse(invocation.stderr().contains(PASSWORD));
        }
    }

    @Test
    void dictionaryVersion_printsExactlyOneSafeLine() throws Exception {
        Path cache = prepareDictionary();

        Invocation invocation = invoke("--dictionary-version", cacheOption(cache));

        assertEquals(0, invocation.exitCode());
        assertEquals("1.4" + System.lineSeparator(), invocation.stdout());
        assertEquals("", invocation.stderr());
        assertFalse(invocation.stdout().contains(SENTINEL));
        assertFalse(invocation.stderr().contains(PASSWORD));
    }

    @Test
    void dictionaryVersion_rejectsUnsafeMetadata() throws Exception {
        Path cache = prepareDictionary("VERSION=unsafe version\nSECRET=" + SENTINEL + "\n");

        Invocation invocation = invoke("--dictionary-version", cacheOption(cache));

        assertEquals(2, invocation.exitCode());
        assertEquals("", invocation.stdout());
        assertEquals("Dictionary version is invalid." + System.lineSeparator(), invocation.stderr());
        assertFalse(invocation.stderr().contains(SENTINEL));
        assertFalse(invocation.stderr().contains(PASSWORD));
    }

    private Invocation invoke(String... arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = CyberFerretCLI.run(
                arguments,
                Map.of(AppConstants.SYS_ENV_VAR_PASSWORD, PASSWORD),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Invocation(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private Path prepareDictionary() throws Exception {
        return prepareDictionary("VERSION=1.4\nSECRET=" + SENTINEL + "\n");
    }

    private Path prepareDictionary(String dictionary) throws Exception {
        Path cache = tempDir.resolve("cache");
        Files.createDirectories(cache);
        Files.writeString(
                cache.resolve(AppConstants.DICTIONARY_FILE_PATH_ENCRYPTED),
                encrypt(dictionary, PASSWORD),
                StandardCharsets.UTF_8);
        return cache;
    }

    private Path prepareRepository(String content) throws Exception {
        Path repository = Files.createTempDirectory(tempDir, "repository-");
        Files.writeString(repository.resolve("source.txt"), content, StandardCharsets.UTF_8);
        return repository;
    }

    private static String cacheOption(Path cache) {
        return "--cache-dir=" + cache;
    }

    private static String encrypt(String value, String password) throws Exception {
        byte[] salt = "bsd87918hediu".getBytes(StandardCharsets.UTF_8);
        byte[] iv = {0, 2, 3, 4, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 0};
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKey temporaryKey = factory.generateSecret(spec);
        SecretKeySpec secretKey = new SecretKeySpec(temporaryKey.getEncoded(), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}

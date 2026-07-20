package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.spec.KeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DictionarySessionTests {
    private static final String PASSWORD = "test-password";
    private static final String ENTRY = "SECRET_ENTRY_VALUE";

    @TempDir
    Path tempDir;

    @Test
    void prepare_readsOfflineDictionaryAndVersion() throws Exception {
        writeEncryptedDictionary("VERSION=1.4\nSECRET=" + ENTRY + "\n");

        DictionarySession session = DictionarySession.prepare(tempDir, true, PASSWORD);

        assertEquals("1.4", session.dictionaryVersion());
        assertFalse(session.signaturesMap().isEmpty());
    }

    @Test
    void prepare_returnsUnknownWhenVersionIsMissing() throws Exception {
        writeEncryptedDictionary("SECRET=" + ENTRY + "\n");

        DictionarySession session = DictionarySession.prepare(tempDir, true, PASSWORD);

        assertEquals("unknown", session.dictionaryVersion());
    }

    @Test
    void prepare_rejectsMissingOfflineDictionarySafely() {
        DictionarySession.DictionaryException exception = assertThrows(
                DictionarySession.DictionaryException.class,
                () -> DictionarySession.prepare(tempDir, true, PASSWORD));

        assertEquals("Cached dictionary is unavailable.", exception.getMessage());
    }

    @Test
    void prepare_rejectsWrongPasswordWithoutSecretValues() throws Exception {
        writeEncryptedDictionary("VERSION=1.4\nSECRET=" + ENTRY + "\n");

        DictionarySession.DictionaryException exception = assertThrows(
                DictionarySession.DictionaryException.class,
                () -> DictionarySession.prepare(tempDir, true, "wrong-" + PASSWORD));

        assertEquals("Cannot decrypt the dictionary.", exception.getMessage());
        assertFalse(exception.toString().contains(PASSWORD));
        assertFalse(exception.toString().contains(ENTRY));
    }

    @Test
    void prepare_rejectsMalformedPropertiesSafely() throws Exception {
        writeEncryptedDictionary("VERSION=1.4\nBROKEN=\\u12ZZ\n");

        DictionarySession.DictionaryException exception = assertThrows(
                DictionarySession.DictionaryException.class,
                () -> DictionarySession.prepare(tempDir, true, PASSWORD));

        assertEquals("Cannot parse the dictionary.", exception.getMessage());
    }

    private void writeEncryptedDictionary(String dictionary) throws Exception {
        Path encryptedPath = tempDir.resolve(AppConstants.DICTIONARY_FILE_PATH_ENCRYPTED);
        Files.writeString(encryptedPath, encrypt(dictionary, PASSWORD), StandardCharsets.UTF_8);
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
}

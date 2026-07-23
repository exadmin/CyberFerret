package com.github.exadmin.cyberferret.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

public class MiscUtils {
    public static String getSHA256AsHex(String str) {
        try {
            try (InputStream is = new ByteArrayInputStream(str.getBytes())) {
                byte[] hash = getSha256FromInputStream(is);
                return bytesToHex(hash);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static byte[] getSha256FromInputStream(InputStream is) throws IOException, NoSuchAlgorithmException {
        byte[] buffer= new byte[8192];
        int count;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (BufferedInputStream bis = new BufferedInputStream(is)) {
            while ((count = bis.read(buffer)) > 0) {
                digest.update(buffer, 0, count);
            }
        }

        return digest.digest();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String getRelativeFileName(Path rootDir, Path fileName) {
        // Normalize both paths to handle '/./' and '/../' components
        Path normRoot = rootDir.normalize();
        Path normFile = fileName.normalize();

        // Make sure the file path is within the root directory
        if (normFile.startsWith(normRoot)) {
            // Relativize the path (returns the relative path from rootDir to fileName)
            Path relativePath = normRoot.relativize(normFile);
            String result = relativePath.toString();
            return result.replace("\\", "/"); // switch to unix style in windows running case
        }

        throw new IllegalStateException("Unexpected file path '" + normFile + "'");
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    private static final String VERSION_PROPERTIES_RESOURCE = "/version.properties";

    public static String loadApplicationVersion() {
        Properties props = new Properties();
        try (InputStream input = MiscUtils.class.getResourceAsStream(VERSION_PROPERTIES_RESOURCE)) {
            if (input == null) {
                // log.error("Resource {} is not found on the classpath", VERSION_PROPERTIES_RESOURCE);
                throw new RuntimeException("Resource '" + VERSION_PROPERTIES_RESOURCE + "' not found");
            }
            props.load(input);
            return props.getProperty("application.version");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
            // log.error("Error while loading {}", VERSION_PROPERTIES_RESOURCE, ex);
        }

        // return "UNDEFINED";
    }
}

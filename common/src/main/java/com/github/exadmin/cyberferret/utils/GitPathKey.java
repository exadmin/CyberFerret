package com.github.exadmin.cyberferret.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

final class GitPathKey {
    private static final byte SEPARATOR = (byte) '/';

    private final byte[] value;

    private GitPathKey(byte[] value) {
        this.value = value;
    }

    static GitPathKey fromGitOutput(byte[] output, int start, int end) {
        return new GitPathKey(Arrays.copyOfRange(output, start, end));
    }

    static GitPathKey fromNativePath(Path root, Path path) throws IOException {
        String rootPath = root.toUri().getRawPath();
        if (!rootPath.endsWith("/")) {
            rootPath += "/";
        }

        String pathValue = path.toUri().getRawPath();
        if (!pathValue.startsWith(rootPath)) {
            throw new IOException("Repository path is outside the scan root: " + path);
        }

        String relativePath = pathValue.substring(rootPath.length());
        if (relativePath.endsWith("/")) {
            relativePath = relativePath.substring(0, relativePath.length() - 1);
        }
        return new GitPathKey(decodeUriPath(relativePath));
    }

    GitPathKey parent() {
        for (int index = value.length - 1; index >= 0; index--) {
            if (value[index] == SEPARATOR) {
                return new GitPathKey(Arrays.copyOf(value, index));
            }
        }
        return null;
    }

    private static byte[] decodeUriPath(String rawPath) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(rawPath.length());
        for (int index = 0; index < rawPath.length(); index++) {
            char current = rawPath.charAt(index);
            if (current == '%') {
                if (index + 2 >= rawPath.length()) {
                    throw new IOException("Invalid encoded repository path: " + rawPath);
                }
                int high = Character.digit(rawPath.charAt(index + 1), 16);
                int low = Character.digit(rawPath.charAt(index + 2), 16);
                if (high < 0 || low < 0) {
                    throw new IOException("Invalid encoded repository path: " + rawPath);
                }
                bytes.write((high << 4) + low);
                index += 2;
                continue;
            }

            int codePoint = rawPath.codePointAt(index);
            byte[] encoded = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
            bytes.writeBytes(encoded);
            if (Character.isSupplementaryCodePoint(codePoint)) {
                index++;
            }
        }
        return bytes.toByteArray();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GitPathKey otherKey && Arrays.equals(value, otherKey.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }
}

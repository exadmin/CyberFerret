package com.github.exadmin.cyberferret.cfcli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public record FileMatchContext(long lineNumber, String displayText) {
    private static final int CONTEXT_CODE_POINTS = 50;

    public static FileMatchContext from(byte[] content, long position, String exact) throws IOException {
        if (position < 0 || position > Integer.MAX_VALUE || position > content.length) {
            throw new IOException("Signature byte position is outside the file");
        }
        int matchStart = (int) position;
        byte[] exactBytes = exact.getBytes(StandardCharsets.UTF_8);
        int matchEnd = matchStart + exactBytes.length;
        if (matchEnd > content.length
                || !Arrays.equals(exactBytes, Arrays.copyOfRange(content, matchStart, matchEnd))) {
            throw new IOException("File content does not match the reported signature position");
        }

        long lineNumber = 1;
        for (int index = 0; index < matchStart; index++) {
            if (content[index] == '\n'
                    || (content[index] == '\r'
                            && (index + 1 >= matchStart || content[index + 1] != '\n'))) {
                lineNumber++;
            }
        }

        int lineStart = matchStart;
        while (lineStart > 0 && !isLineBreak(content[lineStart - 1])) lineStart--;
        int lineEnd = matchEnd;
        while (lineEnd < content.length && !isLineBreak(content[lineEnd])) lineEnd++;

        String before = new String(content, lineStart, matchStart - lineStart, StandardCharsets.UTF_8);
        String after = new String(content, matchEnd, lineEnd - matchEnd, StandardCharsets.UTF_8);
        String excerpt = lastCodePoints(before, CONTEXT_CODE_POINTS)
                + exact
                + firstCodePoints(after, CONTEXT_CODE_POINTS);
        return new FileMatchContext(lineNumber, normalizeWhitespace(excerpt));
    }

    private static boolean isLineBreak(byte value) {
        return value == '\n' || value == '\r';
    }

    private static String lastCodePoints(String value, int count) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= count) return value;
        int start = value.offsetByCodePoints(0, codePointCount - count);
        return value.substring(start);
    }

    private static String firstCodePoints(String value, int count) {
        int codePointCount = value.codePointCount(0, value.length());
        if (codePointCount <= count) return value;
        int end = value.offsetByCodePoints(0, count);
        return value.substring(0, end);
    }

    private static String normalizeWhitespace(String value) {
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint)) {
                result.append(' ');
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }
}

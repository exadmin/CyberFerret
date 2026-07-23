package com.github.exadmin.cyberferret.cfcli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public record FileMatchContext(long lineNumber, String displayText) {
    private static final int CONTEXT_CODE_POINTS = 50;

    public static FileMatchContext from(byte[] content, long line, String exact) throws IOException {
        if (line < 1) {
            throw new IOException("Signature line must be positive");
        }

        int lineStart = 0;
        long currentLine = 1;
        while (currentLine < line) {
            int lineBreak = findLineBreak(content, lineStart);
            if (lineBreak == content.length) {
                throw new IOException("Signature line is outside the file");
            }
            lineStart = afterLineBreak(content, lineBreak);
            currentLine++;
        }

        int lineEnd = findLineBreak(content, lineStart);
        String lineText = new String(content, lineStart, lineEnd - lineStart, StandardCharsets.UTF_8);
        int matchStart = lineText.indexOf(exact);
        if (matchStart < 0) {
            throw new IOException("File line does not contain the reported signature");
        }
        int matchEnd = matchStart + exact.length();

        String before = lineText.substring(0, matchStart);
        String after = lineText.substring(matchEnd);
        String excerpt = lastCodePoints(before, CONTEXT_CODE_POINTS)
                + exact
                + firstCodePoints(after, CONTEXT_CODE_POINTS);
        return new FileMatchContext(line, normalizeWhitespace(excerpt).strip());
    }

    private static int findLineBreak(byte[] content, int start) {
        int index = start;
        while (index < content.length && content[index] != '\n' && content[index] != '\r') {
            index++;
        }
        return index;
    }

    private static int afterLineBreak(byte[] content, int index) {
        if (content[index] == '\r' && index + 1 < content.length && content[index + 1] == '\n') {
            return index + 2;
        }
        return index + 1;
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

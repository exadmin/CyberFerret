package com.github.exadmin.cyberferret.cfcli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileMatchContextTests {

    @Test
    public void calculatesOneBasedLineFromUtf8ByteOffset() throws Exception {
        String prefix = "first line\n😀 prefix ";
        String exact = "TOKEN";
        byte[] content = (prefix + exact + " suffix\nlast line").getBytes(StandardCharsets.UTF_8);
        long position = prefix.getBytes(StandardCharsets.UTF_8).length;

        FileMatchContext context = FileMatchContext.from(content, position, exact);

        assertEquals(2, context.lineNumber());
        assertEquals("😀 prefix TOKEN suffix", context.displayText());
    }

    @Test
    public void limitsContextToFiftyCodePointsAndCurrentLine() throws Exception {
        String before = "x".repeat(60);
        String after = "y".repeat(60);
        String exact = "MATCH";
        String contentText = "previous text that must not appear\n" + before + exact + after + "\nnext text";
        byte[] content = contentText.getBytes(StandardCharsets.UTF_8);
        long position = ("previous text that must not appear\n" + before).getBytes(StandardCharsets.UTF_8).length;

        FileMatchContext context = FileMatchContext.from(content, position, exact);

        assertEquals("x".repeat(50) + exact + "y".repeat(50), context.displayText());
    }

    @Test
    public void normalizesWhitespaceInsideTheCurrentLine() throws Exception {
        String contentText = "before\tMATCH after";
        long position = "before\t".getBytes(StandardCharsets.UTF_8).length;

        FileMatchContext context = FileMatchContext.from(
                contentText.getBytes(StandardCharsets.UTF_8), position, "MATCH");

        assertEquals("before MATCH after", context.displayText());
    }

    @Test
    public void removesWhitespaceFromExcerptEdges() throws Exception {
        String contentText = "\t   before MATCH after  \t";
        long position = "\t   before ".getBytes(StandardCharsets.UTF_8).length;

        FileMatchContext context = FileMatchContext.from(
                contentText.getBytes(StandardCharsets.UTF_8), position, "MATCH");

        assertEquals("before MATCH after", context.displayText());
    }

    @Test
    public void rejectsInvalidPositionOrChangedContent() {
        byte[] content = "value".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> FileMatchContext.from(content, 99, "value"));
        assertThrows(IOException.class, () -> FileMatchContext.from(content, 0, "other"));
    }

    @Test
    public void countsStandaloneCarriageReturnsAsLineBreaks() throws Exception {
        String contentText = "first\rMATCH";
        long position = "first\r".getBytes(StandardCharsets.UTF_8).length;

        FileMatchContext context = FileMatchContext.from(
                contentText.getBytes(StandardCharsets.UTF_8), position, "MATCH");

        assertEquals(2, context.lineNumber());
        assertEquals("MATCH", context.displayText());
    }
}

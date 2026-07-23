package com.github.exadmin.cyberferret.cfcli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileMatchContextTests {

    @Test
    public void buildsContextFromOneBasedLineWithUtf8Content() throws Exception {
        String prefix = "first line\n😀 prefix ";
        String exact = "TOKEN";
        byte[] content = (prefix + exact + " suffix\nlast line").getBytes(StandardCharsets.UTF_8);

        FileMatchContext context = FileMatchContext.from(content, 2, exact);

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

        FileMatchContext context = FileMatchContext.from(content, 2, exact);

        assertEquals("x".repeat(50) + exact + "y".repeat(50), context.displayText());
    }

    @Test
    public void normalizesWhitespaceInsideTheCurrentLine() throws Exception {
        String contentText = "before\tMATCH after";

        FileMatchContext context = FileMatchContext.from(
                contentText.getBytes(StandardCharsets.UTF_8), 1, "MATCH");

        assertEquals("before MATCH after", context.displayText());
    }

    @Test
    public void removesWhitespaceFromExcerptEdges() throws Exception {
        String contentText = "\t   before MATCH after  \t";

        FileMatchContext context = FileMatchContext.from(
                contentText.getBytes(StandardCharsets.UTF_8), 1, "MATCH");

        assertEquals("before MATCH after", context.displayText());
    }

    @Test
    public void rejectsInvalidLineOrChangedContent() {
        byte[] content = "value".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> FileMatchContext.from(content, 99, "value"));
        assertThrows(IOException.class, () -> FileMatchContext.from(content, 0, "value"));
        assertThrows(IOException.class, () -> FileMatchContext.from(content, 1, "other"));
    }

    @Test
    public void countsStandaloneCarriageReturnsAsLineBreaks() throws Exception {
        String contentText = "first\rMATCH";

        FileMatchContext context = FileMatchContext.from(
                contentText.getBytes(StandardCharsets.UTF_8), 2, "MATCH");

        assertEquals(2, context.lineNumber());
        assertEquals("MATCH", context.displayText());
    }

    @Test
    public void treatsCrLfAsOneLineBreak() throws Exception {
        FileMatchContext context = FileMatchContext.from(
                "first\r\nMATCH".getBytes(StandardCharsets.UTF_8), 2, "MATCH");

        assertEquals(2, context.lineNumber());
        assertEquals("MATCH", context.displayText());
    }

    @Test
    public void usesFirstRepeatedMatchOnTheReportedLine() throws Exception {
        FileMatchContext context = FileMatchContext.from(
                "before MATCH middle MATCH after".getBytes(StandardCharsets.UTF_8), 1, "MATCH");

        assertEquals("before MATCH middle MATCH after", context.displayText());
    }
}

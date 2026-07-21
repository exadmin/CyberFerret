package com.github.exadmin.cyberferret.cfcli;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CfCliMessageParserTests {
    private final CfCliMessageParser parser = new CfCliMessageParser();

    @Test
    public void ignoresNonJsonLines() throws Exception {
        assertTrue(parser.parse("TEXT: Scanning is in progress").isEmpty());
    }

    @Test
    public void parsesListAndPathExclusionEvents() throws Exception {
        CfCliMessage folder = parser.parse("JSON: {\"type\":\"list\",\"folder\":\"src/main\"}").orElseThrow();
        CfCliMessage file = parser.parse("JSON: {\"type\":\"list\",\"file\":\"src/main/App.java\"}").orElseThrow();
        CfCliMessage excluded = parser.parse(
                "JSON: {\"type\":\"excluded\",\"file\":\"src/generated\"}").orElseThrow();

        assertEquals("src/main", folder.folder());
        assertEquals("src/main/App.java", file.file());
        assertEquals("src/generated", excluded.file());
        assertFalse(excluded.isSignature());
    }

    @Test
    public void parsesEverySignatureStatusAndEscapes() throws Exception {
        for (String type : new String[]{"found", "allowed", "excluded"}) {
            String line = "JSON: {\"unknown\":true,\"type\":\"" + type
                    + "\",\"key\":\"TOKEN\\\"\\\\\\u0020KEY\",\"found\":\"value\\n😀\","
                    + "\"position\":17,\"file\":\"src/file.txt\"}";

            CfCliMessage message = parser.parse(line).orElseThrow();

            assertEquals(type, message.type());
            assertEquals("TOKEN\"\\ KEY", message.key());
            assertEquals("value\n😀", message.found());
            assertEquals(17L, message.position());
            assertTrue(message.isSignature());
        }
    }

    @Test
    public void rejectsMalformedOrIncompleteEvents() {
        assertThrows(IOException.class, () -> parser.parse("JSON: {invalid}"));
        assertThrows(IOException.class, () -> parser.parse("JSON: {\"type\":\"list\"}"));
        assertThrows(IOException.class, () -> parser.parse(
                "JSON: {\"type\":\"found\",\"key\":\"K\",\"found\":\"V\",\"file\":\"f\"}"));
        assertThrows(IOException.class, () -> parser.parse(
                "JSON: {\"type\":\"found\",\"key\":\"K\",\"found\":\"V\","
                        + "\"position\":-1,\"file\":\"f\"}"));
        assertThrows(IOException.class, () -> parser.parse("JSON: {\"type\":\"future\",\"file\":\"f\"}"));
    }
}

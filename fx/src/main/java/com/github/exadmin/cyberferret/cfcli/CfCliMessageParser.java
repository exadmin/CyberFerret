package com.github.exadmin.cyberferret.cfcli;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CfCliMessageParser {
    private static final String JSON_PREFIX = "JSON:";

    public Optional<CfCliMessage> parse(String line) throws IOException {
        if (line == null || !line.startsWith(JSON_PREFIX)) return Optional.empty();

        Cursor cursor = new Cursor(line.substring(JSON_PREFIX.length()));
        Map<String, Object> values = cursor.parseObject();
        cursor.skipWhitespace();
        if (!cursor.isAtEnd()) throw cursor.error("Unexpected content after JSON object");

        CfCliMessage message = new CfCliMessage(
                stringValue(values, "type"),
                stringValue(values, "file"),
                stringValue(values, "folder"),
                stringValue(values, "key"),
                stringValue(values, "found"),
                longValue(values, "line"));
        validate(message);
        return Optional.of(message);
    }

    private static String stringValue(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw new IOException("JSON field \"" + key + "\" must be a string");
    }

    private static Long longValue(Map<String, Object> values, String key) throws IOException {
        Object value = values.get(key);
        if (value == null) return null;
        if (value instanceof Long number) return number;
        throw new IOException("JSON field \"" + key + "\" must be an integer");
    }

    private static void validate(CfCliMessage message) throws IOException {
        if (message.type() == null) throw new IOException("JSON event is missing \"type\"");
        switch (message.type()) {
            case "list" -> {
                if ((message.file() == null) == (message.folder() == null)) {
                    throw new IOException("List event must contain exactly one of \"file\" or \"folder\"");
                }
            }
            case "found", "allowed" -> validateSignature(message);
            case "excluded" -> {
                if (message.file() == null) throw new IOException("Excluded event is missing \"file\"");
                boolean hasSignatureField = message.key() != null || message.found() != null || message.line() != null;
                if (hasSignatureField) validateSignature(message);
            }
            default -> throw new IOException("Unsupported JSON event type \"" + message.type() + "\"");
        }
    }

    private static void validateSignature(CfCliMessage message) throws IOException {
        if (message.file() == null || message.key() == null || message.found() == null || message.line() == null) {
            throw new IOException("Signature event is missing a required field");
        }
        if (message.line() < 1) throw new IOException("Signature line must be positive");
    }

    private static final class Cursor {
        private final String input;
        private int index;

        private Cursor(String input) {
            this.input = input;
        }

        private Map<String, Object> parseObject() throws IOException {
            skipWhitespace();
            expect('{');
            Map<String, Object> result = new HashMap<>();
            skipWhitespace();
            if (consume('}')) return result;

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                result.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) return result;
                expect(',');
            }
        }

        private Object parseValue() throws IOException {
            if (isAtEnd()) throw error("Missing JSON value");
            return switch (input.charAt(index)) {
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Object parseLiteral(String literal, Object value) throws IOException {
            if (!input.startsWith(literal, index)) throw error("Invalid JSON value");
            index += literal.length();
            return value;
        }

        private long parseNumber() throws IOException {
            int start = index;
            if (consume('-') && isAtEnd()) throw error("Invalid JSON number");
            while (!isAtEnd() && Character.isDigit(input.charAt(index))) index++;
            if (start == index || (input.charAt(start) == '-' && start + 1 == index)) {
                throw error("Invalid JSON number");
            }
            try {
                return Long.parseLong(input.substring(start, index));
            } catch (NumberFormatException ex) {
                throw error("JSON integer is out of range", ex);
            }
        }

        private String parseString() throws IOException {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!isAtEnd()) {
                char current = input.charAt(index++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    if (current < 0x20) throw error("Unescaped control character in JSON string");
                    result.append(current);
                    continue;
                }
                if (isAtEnd()) throw error("Incomplete JSON escape");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(parseUnicodeEscape());
                    default -> throw error("Unsupported JSON escape \\" + escaped);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseUnicodeEscape() throws IOException {
            if (index + 4 > input.length()) throw error("Incomplete Unicode escape");
            try {
                char value = (char) Integer.parseInt(input.substring(index, index + 4), 16);
                index += 4;
                return value;
            } catch (NumberFormatException ex) {
                throw error("Invalid Unicode escape", ex);
            }
        }

        private void expect(char expected) throws IOException {
            if (!consume(expected)) throw error("Expected \"" + expected + "\"");
        }

        private boolean consume(char expected) {
            if (isAtEnd() || input.charAt(index) != expected) return false;
            index++;
            return true;
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private boolean isAtEnd() {
            return index >= input.length();
        }

        private IOException error(String message) {
            return new IOException(message + " at JSON offset " + index);
        }

        private IOException error(String message, Exception cause) {
            return new IOException(message + " at JSON offset " + index, cause);
        }
    }
}

package com.github.exadmin.cyberferret.exclude;

import com.github.exadmin.cyberferret.utils.ConsoleUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExcludeFileJsonCodec {
    private static final Pattern OBJECT_REGEXP = Pattern.compile("\\{\\s*\"t-hash\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*\"f-hash\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*}");
    private static final Pattern ROOT_REGEXP = Pattern.compile("^\\s*\\{\\s*\"exclusions\"\\s*:\\s*\\[(.*)]\\s*}\\s*$", Pattern.DOTALL);
    private static final Pattern EMPTY_MODEL_REGEX = Pattern.compile("^\\s*\\{\\s*}\\s*$", Pattern.DOTALL);

    private ExcludeFileJsonCodec() {
    }

    public static ExcludeFileModel fromJson(String json) {
        ExcludeFileModel model = new ExcludeFileModel();

        // Check if string contains an empty model like "{}".
        Matcher emptyModelMatcher = EMPTY_MODEL_REGEX.matcher(json);
        if (emptyModelMatcher.matches()) {
            return model;
        }

        Matcher rootMatcher = ROOT_REGEXP.matcher(json);
        if (!rootMatcher.matches()) {
            ConsoleUtils.warn("Failed to parse json configuration with exclusions. Fall back into empty configuration.");
            return model;
        }


        String body = rootMatcher.group(1).trim();
        if (body.isEmpty()) {
            return model;
        }

        Matcher objectMatcher = OBJECT_REGEXP.matcher(body);
        int currentIndex = 0;
        while (objectMatcher.find()) {
            String gap = body.substring(currentIndex, objectMatcher.start()).trim();
            if (!gap.isEmpty() && !",".equals(gap)) {
                throw new IllegalArgumentException("Invalid exclusions json content");
            }

            model.add(unescape(objectMatcher.group(1)), unescape(objectMatcher.group(2)));
            currentIndex = objectMatcher.end();
        }

        if (currentIndex != body.length()) {
            String tail = body.substring(currentIndex).trim();
            if (!tail.isEmpty()) {
                throw new IllegalArgumentException("Invalid exclusions json tail");
            }
        }

        return model;
    }

    public static String toJson(ExcludeFileModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"exclusions\" : [ ");

        for (int i = 0; i < model.getSignatures().size(); i++) {
            ExcludeSignatureItem item = model.getSignatures().get(i);
            if (i > 0) {
                sb.append(", ");
            }

            sb.append("{\n");
            sb.append("    \"t-hash\" : \"").append(escape(item.getTextHash())).append("\",\n");
            sb.append("    \"f-hash\" : \"").append(escape(item.getFileHash())).append("\"\n");
            sb.append("  }");
        }

        sb.append(" ]\n");
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\') {
                sb.append(ch);
                continue;
            }

            if (i + 1 >= value.length()) {
                throw new IllegalArgumentException("Invalid escape sequence");
            }

            char next = value.charAt(++i);
            switch (next) {
                case '\\' -> sb.append('\\');
                case '"' -> sb.append('"');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> throw new IllegalArgumentException("Unsupported escape sequence");
            }
        }
        return sb.toString();
    }
}

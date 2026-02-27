package com.github.exadmin.cyberferret.utils;

public class ConsoleUtils {

    public static void debug(String msg, Object ... binds) {
        String result = "[DEBUG] " + format(msg, binds);
        System.out.println(result);
    }

    public static void error(String msg, Object ... binds) {
        String result = "[ERROR] " + format(msg, binds);
        System.out.println(result);
    }

    public static String format(String msg, Object... binds) {
        if (binds == null || binds.length == 0) {
            return msg;
        }

        StringBuilder result = new StringBuilder();
        int lastIndex = 0;
        int bindIndex = 0;

        // Seek all {} place-holders in the message
        int nextPlaceholder = msg.indexOf("{}", lastIndex);

        while (nextPlaceholder != -1 && bindIndex < binds.length) {
            result.append(msg, lastIndex, nextPlaceholder);

            Object value = binds[bindIndex++];
            result.append(value != null ? value.toString() : "null");

            lastIndex = nextPlaceholder + 2; // "{}" has two chars

            // seek for next bind
            nextPlaceholder = msg.indexOf("{}", lastIndex);
        }

        // appending last part of the messge string
        if (lastIndex < msg.length()) {
            result.append(msg, lastIndex, msg.length());
        }

        // Если остались неиспользованные binds, добавляем их в конец через пробел
        if (bindIndex < binds.length) {
            result = new StringBuilder();
            result.append("Unused binds: [");
            for (int i = bindIndex; i < binds.length; i++) {
                if (i > bindIndex) {
                    result.append(", ");
                }
                result.append(binds[i] != null ? binds[i].toString() : "null");
            }
            result.append("]");

            throw new IllegalArgumentException(result.toString());
        }

        return result.toString();
    }
}

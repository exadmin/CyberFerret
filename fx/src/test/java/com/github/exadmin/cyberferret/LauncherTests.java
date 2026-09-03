package com.github.exadmin.cyberferret;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherTests {
    /**
     * Verifies that the executable entry point is a plain class with the standard public static main method.
     *
     * @throws NoSuchMethodException if the launcher does not expose the required entry point
     */
    @Test
    void exposesPlainJavaEntryPoint() throws NoSuchMethodException {
        Method main = Launcher.class.getMethod("main", String[].class);

        assertEquals(Object.class, Launcher.class.getSuperclass());
        assertEquals(void.class, main.getReturnType());
        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }
}

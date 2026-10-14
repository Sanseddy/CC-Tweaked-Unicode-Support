package ru.sanseddy.cctweakedunicodesupport.compat;

import dan200.computercraft.core.terminal.Terminal;

import java.lang.reflect.Method;

/**
 * Safe reflection-based bridge for CC: Graphics compatibility without hard compile-time dependency.
 */
public final class CCGraphicsCompat {
    private static Method getGraphicsModeMethod;
    private static boolean initialized = false;

    private static synchronized Method getMethod() {
        if (!initialized) {
            initialized = true;
            try {
                Class<?> duck = Class.forName("com.sashafiesta.ccgraphics.duck.IGraphicsTerminal");
                getGraphicsModeMethod = duck.getMethod("ccgraphics$getGraphicsMode");
            } catch (Throwable ignored) {
            }
        }
        return getGraphicsModeMethod;
    }

    private CCGraphicsCompat() {
    }

    public static boolean isPresent() {
        return getMethod() != null;
    }

    public static boolean isGraphicsMode(Terminal terminal) {
        if (terminal == null) return false;
        var m = getMethod();
        if (m != null) {
            try {
                return ((Integer) m.invoke(terminal)) > 0;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}

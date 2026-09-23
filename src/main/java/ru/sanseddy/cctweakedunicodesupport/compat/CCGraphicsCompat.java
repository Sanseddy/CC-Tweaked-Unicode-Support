package ru.sanseddy.cctweakedunicodesupport.compat;

import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import dan200.computercraft.shared.computer.terminal.TerminalState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Safe reflection-based bridge for CC: Graphics compatibility without hard compile-time dependency.
 */
public final class CCGraphicsCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(CCGraphicsCompat.class);
    private static Method onReadMethod;
    private static boolean initialized = false;

    private static synchronized Method getOnReadMethod() {
        if (!initialized) {
            initialized = true;
            try {
                for (var m : NetworkedTerminal.class.getDeclaredMethods()) {
                    if (m.getName().equals("ccgraphics$onRead")
                        && Arrays.equals(m.getParameterTypes(), new Class<?>[]{TerminalState.class, CallbackInfo.class})
                        && (m.getReturnType() == void.class || m.getReturnType() == Void.TYPE)) {
                        m.setAccessible(true);
                        onReadMethod = m;
                        LOGGER.debug("Discovered CC: Graphics onRead hook");
                        break;
                    }
                }
            } catch (SecurityException e) {
                LOGGER.warn("Security restriction accessing CC: Graphics onRead method: {}", e.getMessage());
            }
        }
        return onReadMethod;
    }

    private CCGraphicsCompat() {
    }

    public static boolean isPresent() {
        return getOnReadMethod() != null;
    }

    public static void onNetworkedTerminalRead(NetworkedTerminal terminal, TerminalState state, CallbackInfo ci) {
        var m = getOnReadMethod();
        if (m != null) {
            try {
                m.invoke(terminal, state, ci);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Failed to dispatch CC: Graphics onRead event: {}", e.getMessage());
            }
        }
    }
}


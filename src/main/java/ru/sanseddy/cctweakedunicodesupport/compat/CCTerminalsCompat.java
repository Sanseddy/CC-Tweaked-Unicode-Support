package ru.sanseddy.cctweakedunicodesupport.compat;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Safe reflection-based bridge for CC: Terminals compatibility.
 */
public final class CCTerminalsCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(CCTerminalsCompat.class);

    private static final Method HANDLE_INPUT_METHOD;
    private static final Object CHAR_INPUT_TYPE;
    private static final Method BLOCK_ENTITY_HANDLE_INPUT;
    private static final Field BLOCK_ENTITY_FIELD;

    static {
        Method m = null;
        Object charType = null;
        Method beHandle = null;
        Field beField = null;
        try {
            Class<?> menuClass = Class.forName("com.sashafiesta.ccterminals.TerminalMenu");
            Class<?> inputTypeClass = Class.forName("com.sashafiesta.ccterminals.network.InputType");
            Class<?> beClass = Class.forName("com.sashafiesta.ccterminals.TerminalBlockEntity");

            Object[] enumConstants = inputTypeClass.getEnumConstants();
            if (enumConstants != null) {
                for (Object constant : enumConstants) {
                    if ("CHAR".equals(((Enum<?>) constant).name())) {
                        charType = constant;
                        break;
                    }
                }
            }

            if (charType != null) {
                m = menuClass.getMethod("handleInput", inputTypeClass, int.class, int.class, int.class, String.class);
                m.setAccessible(true);

                beField = menuClass.getDeclaredField("blockEntity");
                beField.setAccessible(true);

                beHandle = beClass.getMethod("handleInput", inputTypeClass, int.class, int.class, int.class, String.class);
                beHandle.setAccessible(true);
                LOGGER.debug("Discovered CC: Terminals handleInput integration");
            } else {
                LOGGER.warn("CC: Terminals found but InputType.CHAR enum constant is missing");
            }
        } catch (ClassNotFoundException e) {
            LOGGER.debug("CC: Terminals not installed: {}", e.getMessage());
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            LOGGER.warn("CC: Terminals API mismatch, character integration disabled: {}", e.getMessage());
        } catch (SecurityException e) {
            LOGGER.warn("Security restriction accessing CC: Terminals API: {}", e.getMessage());
        }
        HANDLE_INPUT_METHOD = m;
        CHAR_INPUT_TYPE = charType;
        BLOCK_ENTITY_FIELD = beField;
        BLOCK_ENTITY_HANDLE_INPUT = beHandle;
    }

    private CCTerminalsCompat() {
    }

    public static boolean tryHandleChar(AbstractContainerMenu menu, int codepoint) {
        if (CHAR_INPUT_TYPE != null && menu != null
            && menu.getClass().getName().equals("com.sashafiesta.ccterminals.TerminalMenu")) {
            try {
                if (BLOCK_ENTITY_FIELD != null && BLOCK_ENTITY_HANDLE_INPUT != null) {
                    var be = BLOCK_ENTITY_FIELD.get(menu);
                    if (be != null) {
                        BLOCK_ENTITY_HANDLE_INPUT.invoke(be, CHAR_INPUT_TYPE, codepoint, 0, 0, "");
                        return true;
                    }
                }
                if (HANDLE_INPUT_METHOD != null) {
                    HANDLE_INPUT_METHOD.invoke(menu, CHAR_INPUT_TYPE, codepoint, 0, 0, "");
                    return true;
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Failed to invoke CC: Terminals handleInput: {}", e.getMessage());
            }
        }
        return false;
    }
}


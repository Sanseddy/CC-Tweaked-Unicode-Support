package ru.sanseddy.cctweakedunicodesupport;

import dan200.computercraft.shared.computer.menu.ComputerMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaString;
import org.squiddev.cobalt.OperationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.sanseddy.cctweakedunicodesupport.network.WideCharPayload;
import ru.sanseddy.cctweakedunicodesupport.text.Utf8;

@Mod(CCTweakedUnicodeSupport.MOD_ID)
public class CCTweakedUnicodeSupport {
    public static final String MOD_ID = "cc_tweaked_unicode_support";

    private static final Logger LOGGER = LoggerFactory.getLogger(CCTweakedUnicodeSupport.class);

    public CCTweakedUnicodeSupport(IEventBus modEventBus) {
        verifyUnicodeLengthOperator();
        LOGGER.info("CC: Tweaked Unicode terminal support loaded");
        modEventBus.addListener(CCTweakedUnicodeSupport::registerPayloads);
    }

    private static void verifyUnicodeLengthOperator() {
        try {
            var cyrillicLetter = LuaString.valueOf(new byte[]{(byte) 0xD0, (byte) 0xAF});
            var hashActual = OperationHelper.length(new LuaState(), cyrillicLetter).checkInteger();
            if (hashActual != 1) {
                throw new IllegalStateException(
                    "Cobalt's # operator counts UTF-8 bytes instead of Unicode characters (#="
                        + hashActual + "). Ensure the patched Cobalt library is active."
                );
            }
            LOGGER.info("Verified Unicode-aware Lua string length contract (# operator is active)");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable failure) {
            throw new IllegalStateException("Failed to verify Cobalt Unicode length operator", failure);
        }
    }



    private static void registerPayloads(RegisterPayloadHandlersEvent event) {

        event.registrar("2").playToServer(WideCharPayload.TYPE, WideCharPayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (!payload.isValid()) return;
                var player = context.player();
                if (player.containerMenu.containerId == payload.containerId()) {
                    if (player.containerMenu instanceof ComputerMenu menu) {
                        menu.getComputer().queueEvent("char", new Object[]{Utf8.encodePreferLegacy(payload.codepoint())});
                    } else {
                        ru.sanseddy.cctweakedunicodesupport.compat.CCTerminalsCompat.tryHandleChar(player.containerMenu, payload.codepoint());
                    }
                }
            })
        );
    }
}

//? if neoforge {
package ru.sanseddy.cctweakedunicodesupport.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import ru.sanseddy.cctweakedunicodesupport.CCTweakedUnicodeSupport;
import ru.sanseddy.cctweakedunicodesupport.network.WideCharPayload;

@Mod(CCTweakedUnicodeSupport.MOD_ID)
public class CCTweakedUnicodeSupportNeoForge {
    public CCTweakedUnicodeSupportNeoForge(IEventBus modEventBus) {
        CCTweakedUnicodeSupport.init();
        modEventBus.addListener(CCTweakedUnicodeSupportNeoForge::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(WideCharPayload.TYPE, WideCharPayload.STREAM_CODEC, (payload, context) ->
            context.enqueueWork(() -> {
                if (!payload.isValid()) return;
                CCTweakedUnicodeSupport.handleWideChar(payload, context.player());
            })
        );
    }
}
//?}

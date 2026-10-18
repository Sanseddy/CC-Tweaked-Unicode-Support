//? if fabric {
/*package ru.sanseddy.cctweakedunicodesupport.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import ru.sanseddy.cctweakedunicodesupport.CCTweakedUnicodeSupport;
import ru.sanseddy.cctweakedunicodesupport.network.WideCharPayload;

public class CCTweakedUnicodeSupportFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CCTweakedUnicodeSupport.init();
        //? if >1.21.1 {
        PayloadTypeRegistry.serverboundPlay().register(WideCharPayload.TYPE, WideCharPayload.STREAM_CODEC);
        //?} else {
        PayloadTypeRegistry.playC2S().register(WideCharPayload.TYPE, WideCharPayload.STREAM_CODEC);
        //?}
        ServerPlayNetworking.registerGlobalReceiver(WideCharPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (!payload.isValid()) return;
                CCTweakedUnicodeSupport.handleWideChar(payload, context.player());
            });
        });
    }

    public static void initClient() {
    }
}
*///?}

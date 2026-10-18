package ru.sanseddy.cctweakedunicodesupport.network;

public final class WideCharSender {
    private WideCharSender() {
    }

    public static void send(int containerId, int codepoint) {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new WideCharPayload(containerId, codepoint)
        );
        *///?} else if >1.21.1 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
            new WideCharPayload(containerId, codepoint)
        );
        *///?} else {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new WideCharPayload(containerId, codepoint)
        );
        //?}
    }
}

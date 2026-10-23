package ru.sanseddy.cctweakedunicodesupport.network;

//? if >1.21.1 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.sanseddy.cctweakedunicodesupport.CCTweakedUnicodeSupport;
import ru.sanseddy.cctweakedunicodesupport.text.CraftOsCharset;

public record WideCharPayload(int containerId, int codepoint) implements CustomPacketPayload {
    //? if >1.21.1 {
    /*public static final Type<WideCharPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(CCTweakedUnicodeSupport.MOD_ID, "wide_char"));
    *///?} else {
    public static final Type<WideCharPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CCTweakedUnicodeSupport.MOD_ID, "wide_char"));
    //?}

    public static final StreamCodec<RegistryFriendlyByteBuf, WideCharPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WideCharPayload::containerId,
        ByteBufCodecs.VAR_INT, WideCharPayload::codepoint,
        WideCharPayload::new
    );

    public boolean isValid() {
        return codepoint > 0 && codepoint <= Character.MAX_CODE_POINT
            && (codepoint < Character.MIN_SURROGATE || codepoint > Character.MAX_SURROGATE)
            && !CraftOsCharset.isInternalMarker(codepoint);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

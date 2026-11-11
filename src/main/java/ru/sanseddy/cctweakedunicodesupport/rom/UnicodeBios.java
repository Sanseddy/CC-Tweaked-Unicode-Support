package ru.sanseddy.cctweakedunicodesupport.rom;

import java.io.InputStream;

/** Loads the bundled Unicode BIOS independently of CC: Tweaked's resource API. */
public final class UnicodeBios {
    private UnicodeBios() {
    }

    public static InputStream open() {
        var stream = UnicodeBios.class.getResourceAsStream("/data/cc_tweaked_unicode_support/lua/bios.lua");
        if (stream == null) throw new IllegalStateException("Missing bundled Unicode BIOS");
        return stream;
    }
}

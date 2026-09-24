package com.rinkynooble.scalpel.forge.net;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;

/**
 * Client and server must run the same Scalpel version with the same rules, or their registries differ and the
 * connection fails with a confusing registry error. This channel carries no messages: its version string is the mod
 * version plus the rules hash, and Forge compares channel versions when a player joins, before registries are
 * synced. On a mismatch Forge's disconnect screen names the {@code scalpel:rules} channel with both versions.
 */
public final class Handshake {
    public static final ResourceLocation CHANNEL = new ResourceLocation(Scalpel.MOD_ID, "rules");

    private Handshake() {
    }

    public static String version(ScalpelCore core) {
        return core.modVersion() + "+rules." + core.hash().shortForm();
    }

    public static void register() {
        String version = version(Scalpel.core());
        NetworkRegistry.newSimpleChannel(CHANNEL, () -> version, version::equals, version::equals);
        Scalpel.core().log().detail("Handshake channel " + CHANNEL + " version " + version);
    }
}

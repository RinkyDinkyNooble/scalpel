package com.rinkynooble.scalpel.forge.net;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;

/**
 * Client and server must run the same Scalpel version with the same rules, or their registries differ and the
 * connection fails with a confusing registry error. This channel carries no messages: its version string is the mod
 * version plus the rules hash, and Forge compares channel versions when a player joins, before registries are
 * synced. On a mismatch Forge's disconnect screen names the {@code scalpel:rules} channel with both versions, and both
 * sides log what differs.
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
        NetworkRegistry.newSimpleChannel(CHANNEL, () -> version,
                server -> accepts(version, server, "the server"),
                client -> accepts(version, client, "a joining player"));
        Scalpel.core().log().detail("Handshake channel " + CHANNEL + " version " + version);
    }

    private static boolean accepts(String ours, String theirs, String who) {
        if (ours.equals(theirs)) {
            return true;
        }
        // Forge also asks with its "absent" marker for server list pings; refuse quietly there.
        if (!NetworkRegistry.ABSENT.version().equals(theirs) && !NetworkRegistry.ACCEPTVANILLA.equals(theirs)) {
            Scalpel.core().log().warn("Connection refused: " + who + " has Scalpel " + theirs + ", this side has " + ours
                    + ". The part after 'rules.' is a hash of the rules files and settings: copy config/scalpel/ so both"
                    + " sides match (logs/scalpel/report.txt lists the hash of each file).");
        }
        return false;
    }
}

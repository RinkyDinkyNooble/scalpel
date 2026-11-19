package com.rinkynooble.scalpel.forge.net;

import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.common.Scalpel;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;

/**
 * Client and server must run the same Scalpel version with the same rules, or their registries differ and the
 * connection fails with a confusing registry error. This channel carries no messages: its version stands for the mod
 * version plus the rules hash, and Forge compares channel versions while a player joins (its channel versions task
 * runs before registry sync). Since 1.20.2 Forge's channel versions are whole numbers, so the version is the hash code
 * of the string {@code <mod version>+rules.<hash8>}, made non-negative; the log line on a mismatch names this side's string.
 * On a mismatch Forge's disconnect screen names the {@code scalpel:rules} channel.
 */
public final class Handshake {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(Scalpel.MOD_ID, "rules");

    private Handshake() {
    }

    public static String version(ScalpelCore core) {
        return core.modVersion() + "+rules." + core.hash().shortForm();
    }

    public static void register() {
        String version = version(Scalpel.core());
        // Forge refuses a negative channel version.
        int protocol = version.hashCode() & Integer.MAX_VALUE;
        ChannelBuilder.named(CHANNEL)
                .networkProtocolVersion(protocol)
                // Run by the server on the joining player's version, and by the client on the server's.
                .clientAcceptedVersions((status, theirs) -> accepts(version, protocol, status, theirs, "a joining player"))
                .serverAcceptedVersions((status, theirs) -> accepts(version, protocol, status, theirs, "the server"))
                .eventNetworkChannel();
        Scalpel.core().log().detail("Handshake channel " + CHANNEL + " version " + version + " (" + protocol + ")");
    }

    private static boolean accepts(String ours, int protocol, Channel.VersionTest.Status status, int theirs, String who) {
        if (status == Channel.VersionTest.Status.PRESENT && theirs == protocol) {
            return true;
        }
        // MISSING (no Scalpel there) and VANILLA (server list pings, vanilla) are refused quietly; Forge's screen says so.
        if (status == Channel.VersionTest.Status.PRESENT) {
            Scalpel.core().log().warn("Connection refused: " + who + " has a different Scalpel version or rules (channel "
                    + "version " + theirs + "), this side has Scalpel " + ours + " (" + protocol + "). The part after 'rules.'"
                    + " is a hash of the rules files and settings: copy config/scalpel/ so both sides match"
                    + " (logs/scalpel/report.txt lists the hash of each file).");
        }
        return false;
    }
}

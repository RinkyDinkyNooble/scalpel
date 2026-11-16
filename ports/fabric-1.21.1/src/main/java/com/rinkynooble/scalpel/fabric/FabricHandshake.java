package com.rinkynooble.scalpel.fabric;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.core.ScalpelCore;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

/**
 * Client and server must run the same Scalpel version with the same rules, or their registries differ. Fabric has no
 * channel versions to compare, so the server asks a joining player for theirs during login and refuses a mismatch
 * (or a player without Scalpel) with a message saying what differs. Server list pings are not affected.
 */
public final class FabricHandshake {
    public static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath(Scalpel.MOD_ID, "rules");

    private FabricHandshake() {
    }

    /** {@code 0.4.0+rules.1a2b3c4d}, the same string the Forge port uses as its channel version. */
    public static String version(ScalpelCore core) {
        return core.modVersion() + "+rules." + core.hash().shortForm();
    }

    static void registerServer() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
                sender.sendPacket(CHANNEL, PacketByteBufs.create()));
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL, (server, handler, understood, buf, synchronizer, responseSender) -> {
            String ours = version(Scalpel.core());
            if (!understood) {
                handler.disconnect(Component.literal("This server uses Scalpel " + ours
                        + ". Install the same Scalpel version and copy the server's config/scalpel folder to join."));
                return;
            }
            String theirs = buf.readUtf(256);
            if (!ours.equals(theirs)) {
                Scalpel.core().log().warn("Connection refused: a joining player has Scalpel " + theirs + ", this side has " + ours
                        + ". The part after 'rules.' is a hash of the rules files and settings: copy config/scalpel/ so both"
                        + " sides match (logs/scalpel/report.txt lists the hash of each file).");
                handler.disconnect(Component.literal("Scalpel rules differ. Server: " + ours + ", you: " + theirs
                        + ". Copy the server's config/scalpel folder (rules and scalpel.toml) and restart."));
            }
        });
        Scalpel.core().log().detail("Handshake channel " + CHANNEL + " version " + version(Scalpel.core()));
    }

    static void registerClient() {
        ClientLoginNetworking.registerGlobalReceiver(CHANNEL, (client, handler, buf, listenerAdder) -> {
            FriendlyByteBuf reply = PacketByteBufs.create();
            reply.writeUtf(version(Scalpel.core()), 256);
            return CompletableFuture.completedFuture(reply);
        });
    }
}

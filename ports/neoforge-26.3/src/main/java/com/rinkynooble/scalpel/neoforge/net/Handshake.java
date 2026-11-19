package com.rinkynooble.scalpel.neoforge.net;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.core.ScalpelCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client and server must run the same Scalpel version with the same rules, or their registries differ and the
 * connection fails with a confusing registry error. This payload is never sent: its version is the mod version plus
 * the rules hash, and NeoForge compares payload versions when a player joins, before registries are synced. On a
 * mismatch NeoForge's disconnect screen names {@code scalpel:rules} with both versions. A player without Scalpel is
 * refused the same way, since the payload isn't optional.
 */
public final class Handshake {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(Scalpel.MOD_ID, "rules");

    private Handshake() {
    }

    public static String version(ScalpelCore core) {
        return core.modVersion() + "+rules." + core.hash().shortForm();
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        String version = version(Scalpel.core());
        // NeoForge 21.11 wants a client handler for every clientbound payload ("missing client-side handlers"), and the
        // three-argument form no longer uses one handler for both sides. Both do nothing: the payload is never sent.
        event.registrar(version).playBidirectional(RulesPayload.TYPE, RulesPayload.CODEC, (payload, context) -> {
        }, (payload, context) -> {
        });
        Scalpel.core().log().detail("Handshake payload " + CHANNEL + " version " + version);
    }

    /** Carries nothing; only its version matters. */
    public record RulesPayload() implements CustomPacketPayload {
        public static final Type<RulesPayload> TYPE = new Type<>(CHANNEL);
        public static final StreamCodec<ByteBuf, RulesPayload> CODEC = StreamCodec.unit(new RulesPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}

package com.rinkynooble.scalpel.neoforge.net;

import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.core.ScalpelCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client and server must run the same Scalpel version with the same rules, or their registries differ and the
 * connection fails with a confusing registry error. This payload is never sent: its version is the mod version plus
 * the rules hash, and NeoForge compares payload versions when a player joins, before registries are synced. On a
 * mismatch NeoForge's disconnect screen names {@code scalpel:rules} with both versions. A player without Scalpel is
 * refused the same way, since the payload isn't optional.
 */
public final class Handshake {
    public static final ResourceLocation CHANNEL = ResourceLocation.fromNamespaceAndPath(Scalpel.MOD_ID, "rules");

    private Handshake() {
    }

    public static String version(ScalpelCore core) {
        return core.modVersion() + "+rules." + core.hash().shortForm();
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        String version = version(Scalpel.core());
        event.registrar(version).playBidirectional(RulesPayload.TYPE, RulesPayload.CODEC, (payload, context) -> {
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

package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.content.Redacted;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Since 1.21.2 an item needs its id before it is built, and mods take a block item's id from the block's own holder:
 * Biomes O' Plenty (through GlitchCore) calls {@code block.builtInRegistryHolder().key()} for every block item and
 * registers the item under that key. A refused block the mod kept has an unbound holder ("Trying to access unbound
 * value"), so its holder answers with the block's original id: a redacted original gets its placeholder's holder (same
 * id, registered), a removed one a stand-alone holder under its own id (not {@code scalpel:removed}, or the mod would
 * register its item under Scalpel's id). Registered blocks have a bound holder and skip this after one check.
 */
@Mixin(Block.class)
public abstract class BlockHolderMixin {
    @Unique
    private Holder.Reference<Block> scalpel$refusedHolder;

    @Inject(method = "builtInRegistryHolder", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$originalIdHolder(CallbackInfoReturnable<Holder.Reference<Block>> cir) {
        Holder.Reference<Block> holder = cir.getReturnValue();
        if (holder == null || holder.isBound()) {
            return;
        }
        if (scalpel$refusedHolder != null) {
            cir.setReturnValue(scalpel$refusedHolder);
            return;
        }
        String id = RegistryCutter.refusedId(this);
        if (id == null) {
            return;
        }
        Object standIn = RegistryCutter.standInFor(this);
        if (standIn instanceof Redacted && standIn instanceof Block placeholder && standIn != (Object) this) {
            scalpel$refusedHolder = placeholder.builtInRegistryHolder();
        } else {
            scalpel$refusedHolder = Holder.Reference.createStandAlone((HolderOwner<Block>) BuiltInRegistries.BLOCK,
                    ResourceKey.create(Registries.BLOCK, Identifier.parse(id)));
        }
        cir.setReturnValue(scalpel$refusedHolder);
    }
}

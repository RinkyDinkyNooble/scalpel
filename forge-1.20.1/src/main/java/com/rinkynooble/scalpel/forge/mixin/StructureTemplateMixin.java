package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.data.StructureFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Cut entities saved inside a structure are not spawned when the structure is placed. */
@Mixin(StructureTemplate.class)
public abstract class StructureTemplateMixin {
    @Inject(method = "createEntityIgnoreException", at = @At("HEAD"), cancellable = true)
    private static void scalpel$skipCutEntity(ServerLevelAccessor level, CompoundTag tag, CallbackInfoReturnable<Optional<Entity>> cir) {
        if (StructureFilter.isCutEntity(tag)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}

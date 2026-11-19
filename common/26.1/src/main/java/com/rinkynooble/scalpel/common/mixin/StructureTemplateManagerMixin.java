package com.rinkynooble.scalpel.common.mixin;

import com.rinkynooble.scalpel.common.data.StructureFilter;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Remembers which structure is being read, so replaced blocks can be reported against it. */
@Mixin(StructureTemplateManager.class)
public abstract class StructureTemplateManagerMixin {
    @Inject(method = "tryLoad", at = @At("HEAD"))
    private void scalpel$begin(Identifier id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        StructureFilter.beginTemplate(id);
    }

    @Inject(method = "tryLoad", at = @At("RETURN"))
    private void scalpel$end(Identifier id, CallbackInfoReturnable<Optional<StructureTemplate>> cir) {
        StructureFilter.endTemplate();
    }
}

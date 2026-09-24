package com.rinkynooble.scalpel.forge.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rinkynooble.scalpel.forge.client.ScalpelClient;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

/**
 * Placeholder blocks use only Scalpel's blockstate file; the original mod's file is left out. The bakery loads every
 * model inside its constructor, so the map is filtered as the constructor stores it.
 */
@Mixin(ModelBakery.class)
public abstract class ModelBakeryMixin {
    @WrapOperation(method = "<init>", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/resources/model/ModelBakery;blockStateResources:Ljava/util/Map;"))
    private void scalpel$onlyPlaceholderBlockstates(ModelBakery bakery, Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates,
                                                     Operation<Void> original) {
        original.call(bakery, ScalpelClient.filterBlockstates(blockStates, ModelBakery.LoadedJson::source));
    }
}

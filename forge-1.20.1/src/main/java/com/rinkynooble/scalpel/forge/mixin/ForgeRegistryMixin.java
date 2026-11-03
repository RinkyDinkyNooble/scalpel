package com.rinkynooble.scalpel.forge.mixin;

import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

/** Every registration ends here, including vanilla's own and direct {@code Registry.register} calls. */
@Mixin(value = ForgeRegistry.class, remap = false)
public abstract class ForgeRegistryMixin<V> {
    @Shadow
    @Final
    private RegistryManager stage;

    @Shadow
    public abstract ResourceKey<Registry<V>> getRegistryKey();

    @Shadow
    abstract int add(int id, ResourceLocation key, V value, String owner);

    @Shadow
    @Final
    private Map<V, Holder.Reference<V>> delegatesByValue;

    /** A refused object has no delegate of its own, so Forge's lookup finds what stands in for it. */
    @Inject(method = "getDelegate(Ljava/lang/Object;)Ljava/util/Optional;", at = @At("RETURN"), cancellable = true)
    private void scalpel$standInDelegate(V value, CallbackInfoReturnable<Optional<Holder.Reference<V>>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            Holder.Reference<V> delegate = delegatesByValue.get(RegistryCutter.standInFor(value));
            if (delegate != null) {
                cir.setReturnValue(Optional.of(delegate));
            }
        }
    }

    @Inject(method = "add(ILnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;Ljava/lang/String;)I",
            at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void scalpel$beforeAdd(int id, ResourceLocation key, V value, String owner, CallbackInfoReturnable<Integer> cir) {
        if (stage != RegistryManager.ACTIVE) {
            return;
        }
        RegistryCutter.Cut cut = RegistryCutter.beforeAdd(getRegistryKey(), key, value, owner);
        if (cut == null) {
            return;
        }
        cir.setReturnValue(cut.skip() ? -1 : add(id, key, (V) cut.replacement(), owner));
    }
}

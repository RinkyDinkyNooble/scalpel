package com.rinkynooble.scalpel.fabric.mixin.client;

import com.google.common.collect.ImmutableSet;
import com.rinkynooble.scalpel.common.client.PlaceholderPack;
import com.rinkynooble.scalpel.common.client.Placeholders;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The client's resource packs get Scalpel's placeholder pack: required, so always on, and fixed at the top, the way the
 * Forge port adds it through its pack finder event.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void scalpel$addPlaceholders(RepositorySource[] given, CallbackInfo ci) {
        boolean clientResources = false;
        for (RepositorySource source : given) {
            clientResources |= source instanceof ClientPackSource;
        }
        if (!clientResources) {
            return;
        }
        Set<RepositorySource> all = new LinkedHashSet<>(sources);
        all.add(consumer -> {
            PlaceholderPack placeholders = Placeholders.pack();
            if (placeholders.isEmpty()) {
                return;
            }
            Pack created = Pack.readMetaAndCreate(PlaceholderPack.ID, Component.literal("Scalpel placeholders"), true,
                    id -> placeholders, PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
            if (created != null) {
                consumer.accept(created);
            }
        });
        sources = ImmutableSet.copyOf(all);
    }
}

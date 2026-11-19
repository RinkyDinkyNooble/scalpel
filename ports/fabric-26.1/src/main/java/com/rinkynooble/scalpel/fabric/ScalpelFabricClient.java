package com.rinkynooble.scalpel.fabric;

import com.rinkynooble.scalpel.common.client.Placeholders;
import com.rinkynooble.scalpel.common.client.RedactedEntityRenderer;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Client side on Fabric: the label renderer for placeholder entities, tooltips, the handshake reply. */
public final class ScalpelFabricClient implements ClientModInitializer {
    @Override
    @SuppressWarnings("unchecked")
    public void onInitializeClient() {
        // Mods register their content in their own initializers, which all run before this one.
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (RegistryCutter.isPlaceholderEntityType(type)) {
                EntityRendererRegistry.register((EntityType<Entity>) type, RedactedEntityRenderer::new);
            }
        }
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Component line = Placeholders.tooltipLine(stack.getItem());
            if (line != null) {
                lines.add(line);
            }
        });
        FabricHandshake.registerClient();
    }
}

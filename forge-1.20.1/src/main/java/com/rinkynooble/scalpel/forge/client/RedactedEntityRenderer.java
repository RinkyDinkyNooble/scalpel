package com.rinkynooble.scalpel.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Draws a redacted entity as a floating label naming what it stands in for. */
public class RedactedEntityRenderer extends EntityRenderer<Entity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("scalpel", "textures/block/redacted.png");

    public RedactedEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Entity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Component label = Component.translatable("entity.scalpel.redacted")
                .append(" (" + EntityType.getKey(entity.getType()) + ")");
        renderNameTag(entity, label, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Entity entity) {
        return TEXTURE;
    }
}

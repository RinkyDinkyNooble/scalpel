package com.rinkynooble.scalpel.common.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Draws a redacted entity as a floating label naming what it stands in for. Since 1.21.2 renderers draw from a render
 * state; the base renderer draws the state's name tag, so this only supplies the label and always shows it.
 */
public class RedactedEntityRenderer extends EntityRenderer<Entity, EntityRenderState> {
    public RedactedEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    protected boolean shouldShowName(Entity entity, double distanceSquared) {
        return true;
    }

    @Override
    protected Component getNameTag(Entity entity) {
        return Component.translatable("entity.scalpel.redacted").append(" (" + EntityType.getKey(entity.getType()) + ")");
    }
}

package com.rinkynooble.scalpel.common.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Stands in for a redacted entity: no AI, not saved, cannot be summoned, rendered as a label. */
public class RedactedEntity extends Entity {
    public RedactedEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A placeholder entity type, built with the original's id (1.21.2: entity types need their key to build). */
    public static EntityType<RedactedEntity> createType(Identifier originalId) {
        return EntityType.Builder.<RedactedEntity>of(RedactedEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .noSummon()
                .noSave()
                .clientTrackingRange(4)
                .build(ResourceKey.create(Registries.ENTITY_TYPE, originalId));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected Component getTypeName() {
        return Component.translatable("entity.scalpel.redacted");
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}

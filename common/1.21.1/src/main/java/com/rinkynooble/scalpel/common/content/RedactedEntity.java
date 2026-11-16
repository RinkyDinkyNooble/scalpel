package com.rinkynooble.scalpel.common.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;

/** Stands in for a redacted entity: no AI, not saved, cannot be summoned, rendered as a label. */
public class RedactedEntity extends Entity {
    public RedactedEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** A placeholder entity type. The id is only used for the data fixer lookup, which {@code noSave} skips. */
    public static EntityType<RedactedEntity> createType(ResourceLocation originalId) {
        return EntityType.Builder.<RedactedEntity>of(RedactedEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .noSummon()
                .noSave()
                .clientTrackingRange(4)
                .build(originalId.toString());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
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

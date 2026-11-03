package com.rinkynooble.scalpel.forge.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * The ghost of a removed enchantment: never registered, handed to mod code that still asks for it. With no id, its
 * level on any item reads 0, so a mod that checks for it finds nothing.
 */
public class RemovedEnchantment extends Enchantment implements Redacted {
    private final ResourceLocation originalId;

    public RemovedEnchantment(ResourceLocation originalId) {
        super(Rarity.VERY_RARE, EnchantmentCategory.BREAKABLE, new EquipmentSlot[0]);
        this.originalId = originalId;
    }

    @Override
    public ResourceLocation originalId() {
        return originalId;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return false;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }
}

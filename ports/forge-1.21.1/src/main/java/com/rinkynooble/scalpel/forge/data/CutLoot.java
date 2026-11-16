package com.rinkynooble.scalpel.forge.data;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.data.DataFilter;
import com.rinkynooble.scalpel.common.data.GeneratedLoot;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code scalpel:strip_cut_items}: a global loot modifier that takes cut items out of generated loot.
 * The loot table filter works on item ids in the JSON, but loot functions can turn one item into another while it
 * drops (vanilla enchants {@code minecraft:book} into {@code minecraft:enchanted_book}), so this catches the result.
 * It is listed in {@code forge:global_loot_modifiers} and moved to the end of Forge's map after every load (Forge 52
 * applies them in the order of a hash map, not the index), so it sees what the other global loot modifiers added.
 * LootJS is not one: it applies its actions after {@code ForgeHooks.modifyLoot} has run every modifier, so what a
 * LootJS script adds is not checked.
 * It also takes cut enchantments off whatever drops, and an enchanted book left with none is dropped.
 */
public final class CutLoot extends LootModifier {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Scalpel.MOD_ID, "strip_cut_items");

    private static final Supplier<MapCodec<CutLoot>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, CutLoot::new)));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Scalpel.MOD_ID);

    static {
        SERIALIZERS.register(ID.getPath(), CODEC);
    }

    private CutLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        GeneratedLoot.strip(generatedLoot, String.valueOf(context.getQueriedLootTableId()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    /**
     * Forge 52 applies global loot modifiers in the order of the map it builds from the scanned files, with no priority
     * setting, so this one would run wherever its id happens to hash. Called after Forge decodes them: moves it to the end.
     */
    public static Map<ResourceLocation, IGlobalLootModifier> runLast(Map<ResourceLocation, IGlobalLootModifier> modifiers) {
        ScalpelCore core = Scalpel.core();
        IGlobalLootModifier ours = modifiers.get(ID);
        if (ours == null) {
            if (DataFilter.anyRegistryCut(core.resolver())) {
                core.warn("loot modifier " + ID + " is not loaded (a data pack or script took it out of the list), "
                        + "so cut items that loot functions create can still drop");
            }
            return modifiers;
        }
        ImmutableMap.Builder<ResourceLocation, IGlobalLootModifier> ordered = ImmutableMap.builder();
        modifiers.forEach((id, modifier) -> {
            if (!id.equals(ID)) {
                ordered.put(id, modifier);
            }
        });
        ordered.put(ID, ours);
        if (DataFilter.anyRegistryCut(core.resolver())) {
            int others = modifiers.size() - 1;
            DataFilter.change(core, "loot modifier added", ID.toString(), others == 0
                    ? "the only global loot modifier" : "runs after the other " + others + " global loot modifiers");
        }
        return ordered.build();
    }
}

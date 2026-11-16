package com.rinkynooble.scalpel.neoforge.data;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.data.DataFilter;
import com.rinkynooble.scalpel.common.data.GeneratedLoot;
import com.rinkynooble.scalpel.core.ScalpelCore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Map;

/**
 * {@code scalpel:strip_cut_items}: a global loot modifier that takes cut items out of generated loot.
 * The loot table filter works on item ids in the JSON, but loot functions can turn one item into another while it
 * drops (vanilla enchants {@code minecraft:book} into {@code minecraft:enchanted_book}), so this catches the result.
 * It is listed in {@code neoforge:global_loot_modifiers} and moved to the end of NeoForge's list after every load, so
 * it sees what the other global loot modifiers added. It also takes cut enchantments off whatever drops, and an
 * enchanted book left with none is dropped.
 */
public final class CutLoot extends LootModifier {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Scalpel.MOD_ID, "strip_cut_items");

    private static final MapCodec<CutLoot> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, CutLoot::new));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Scalpel.MOD_ID);

    static {
        SERIALIZERS.register(ID.getPath(), () -> CODEC);
    }

    private CutLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        GeneratedLoot.strip(generatedLoot, String.valueOf(context.getQueriedLootTableId()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    /**
     * NeoForge applies global loot modifiers in the order its index lists them, with no priority setting, so this one
     * would run wherever Scalpel's data happens to fall. Called after NeoForge reads the index: moves it to the end.
     */
    public static Map<Identifier, IGlobalLootModifier> runLast(Map<Identifier, IGlobalLootModifier> modifiers) {
        ScalpelCore core = Scalpel.core();
        IGlobalLootModifier ours = modifiers.get(ID);
        if (ours == null) {
            if (DataFilter.anyRegistryCut(core.resolver())) {
                core.warn("loot modifier " + ID + " is not loaded (a data pack or script took it out of the list), "
                        + "so cut items that loot functions create can still drop");
            }
            return modifiers;
        }
        ImmutableMap.Builder<Identifier, IGlobalLootModifier> ordered = ImmutableMap.builder();
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

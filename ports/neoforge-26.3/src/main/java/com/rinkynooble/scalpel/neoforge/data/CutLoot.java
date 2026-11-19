package com.rinkynooble.scalpel.neoforge.data;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code scalpel:strip_cut_items}: a global loot modifier that takes cut items out of generated loot.
 * The loot table filter works on item ids in the JSON, but loot functions can turn one item into another while it
 * drops (vanilla enchants {@code minecraft:book} into {@code minecraft:enchanted_book}), so this catches the result.
 * It runs after every other global loot modifier, so it sees what they added. It also takes cut enchantments off
 * whatever drops, and an enchanted book left with none is dropped.
 *
 * <p>NeoForge changed how modifiers are ordered inside the 26.1 line: up to 26.1.2.19 (the 26.1 and 26.1.1 betas)
 * the {@code neoforge:global_loot_modifiers} index lists them in order; from 26.1.2.20 every file has a
 * {@code priority} (higher first) and the index is gone, and {@code LootModifier}'s constructor changed with it. So this
 * class implements the interface itself (no {@code LootModifier} constructor to link against), its file asks for the
 * lowest priority, and {@code LootModifierManagerMixin} puts it last on either system (on the betas it adds it, since
 * no index lists it).
 */
public final class CutLoot implements IGlobalLootModifier {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Scalpel.MOD_ID, "strip_cut_items");

    /** The file's {@code conditions} (always empty) are left unread; only the priority is read. */
    private static final MapCodec<CutLoot> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.optionalFieldOf("priority", Integer.MIN_VALUE).forGetter(CutLoot::priority)
    ).apply(inst, CutLoot::new));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Scalpel.MOD_ID);

    static {
        SERIALIZERS.register(ID.getPath(), () -> CODEC);
    }

    private final int priority;

    private CutLoot(int priority) {
        this.priority = priority;
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        GeneratedLoot.strip(generatedLoot, String.valueOf(context.getQueriedLootTableId()));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    /** NeoForge 26.1.2.20+: higher runs first. Not called on the betas. */
    @Override
    public int priority() {
        return priority;
    }

    /**
     * Priority system (26.1.2.20+): equal priorities run in no set order, so after NeoForge sorted them this one is
     * moved to the end of the sorted list.
     */
    public static List<IGlobalLootModifier> runLast(Map<Identifier, IGlobalLootModifier> modifiers, List<IGlobalLootModifier> sorted) {
        ScalpelCore core = Scalpel.core();
        IGlobalLootModifier ours = modifiers.get(ID);
        if (ours == null) {
            if (DataFilter.anyRegistryCut(core.resolver())) {
                core.warn("loot modifier " + ID + " is not loaded (a data pack or script took it out), "
                        + "so cut items that loot functions create can still drop");
            }
            return sorted;
        }
        List<IGlobalLootModifier> ordered = new ArrayList<>(sorted.size());
        for (IGlobalLootModifier modifier : sorted) {
            if (modifier != ours) {
                ordered.add(modifier);
            }
        }
        ordered.add(ours);
        report(core, ordered.size() - 1);
        return List.copyOf(ordered);
    }

    /**
     * Index system (the 26.1 and 26.1.2 betas before 26.1.2.20): modifiers run in the index's order. Scalpel's isn't
     * in the index (an index file would fail to load as a modifier on newer NeoForge), so it is added here, at the end.
     */
    public static Map<Identifier, IGlobalLootModifier> runLastIndexed(Map<Identifier, IGlobalLootModifier> modifiers) {
        ImmutableMap.Builder<Identifier, IGlobalLootModifier> ordered = ImmutableMap.builder();
        modifiers.forEach((id, modifier) -> {
            if (!id.equals(ID)) {
                ordered.put(id, modifier);
            }
        });
        ordered.put(ID, new CutLoot(Integer.MIN_VALUE));
        report(Scalpel.core(), modifiers.size() - (modifiers.containsKey(ID) ? 1 : 0));
        return ordered.build();
    }

    private static void report(ScalpelCore core, int others) {
        if (DataFilter.anyRegistryCut(core.resolver())) {
            DataFilter.change(core, "loot modifier added", ID.toString(), others == 0
                    ? "the only global loot modifier" : "runs after the other " + others + " global loot modifiers");
        }
    }
}

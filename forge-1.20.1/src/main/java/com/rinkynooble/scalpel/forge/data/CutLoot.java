package com.rinkynooble.scalpel.forge.data;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.registry.RegistryCutter;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code scalpel:strip_cut_items}: a global loot modifier that takes cut items out of generated loot.
 * The loot table filter works on item ids in the JSON, but loot functions can turn one item into another while it
 * drops (vanilla enchants {@code minecraft:book} into {@code minecraft:enchanted_book}), so this catches the result.
 * It is listed in {@code forge:global_loot_modifiers} and moved to the end of Forge's list after every load, so it
 * sees what the other global loot modifiers added. LootJS is not one: it applies its actions after
 * {@code ForgeHooks.modifyLoot} has run every modifier, so what a LootJS script adds is not checked.
 */
public final class CutLoot extends LootModifier {
    public static final ResourceLocation ID = new ResourceLocation(Scalpel.MOD_ID, "strip_cut_items");

    private static final Supplier<Codec<CutLoot>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, CutLoot::new)));
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Scalpel.MOD_ID);

    /** Loot table and item pairs already in the report, so a drop is recorded once rather than every time. */
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    static {
        SERIALIZERS.register(ID.getPath(), CODEC);
    }

    private CutLoot(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    static void reset() {
        reported.clear();
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ScalpelCore core = Scalpel.core();
        if (!core.applies()) {
            return generatedLoot;
        }
        generatedLoot.removeIf(stack -> {
            if (stack.isEmpty() || !RegistryCutter.isCutItem(stack.getItem())) {
                return false;
            }
            String table = String.valueOf(context.getQueriedLootTableId());
            String id = RegistryCutter.itemId(stack.getItem());
            if (reported.add(table + "|" + id)) {
                DataFilter.change(core, "generated loot removed", id, "made by a loot function or modifier in " + table);
                core.writeReport();
            }
            return true;
        });
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }

    /**
     * Forge applies global loot modifiers in the order its index lists them, with no priority setting, so this one
     * would run wherever Scalpel's data happens to fall. Called after Forge reads the index: moves it to the end.
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

package com.rinkynooble.scalpel.common.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.registry.RegistryCutter;
import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.json.JsonScrub;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchantments are data since 1.21: each is a file in the {@code minecraft:enchantment} registry, loaded when a world
 * loads. The rules are decided per file as that registry loads, with the same meaning as on 1.20.1:
 * <ul>
 *   <li>redact: the entry stays, so items, loot conditions and mods that name it still load, but it has no effects,
 *       its name reads "Redacted", and it is taken out of every enchantment tag (so the table, trades and random loot
 *       never offer it). {@code EnchantmentHelperMixin} also makes its level read 0 for code that asks.</li>
 *   <li>remove: the file is never loaded. Tags, loot tables, other enchantments' lists and enchantment providers lose
 *       their references to it.</li>
 * </ul>
 * Vanilla enchantments are only ever redacted (the core's guard), since vanilla code names some of them directly.
 */
public final class CutEnchantments {
    private static final JsonScrub.Policy KEEP = new JsonScrub.Policy(JsonScrub.EmptyArrays.KEEP, Set.of());
    private static final String NOTHING_PROVIDED = "{\"type\":\"minecraft:by_cost\",\"enchantments\":[],\"cost\":0}";
    private static final List<DataComponentType<ItemEnchantments>> COMPONENTS =
            List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS);

    /** Applied redactions (not filled in a dry run), read on every level lookup, so it stays empty unless a rule needs it. */
    private static final Set<String> inert = ConcurrentHashMap.newKeySet();
    /** Applied removals: enchantment ids left out of the registry. */
    private static final Set<String> removed = ConcurrentHashMap.newKeySet();

    private CutEnchantments() {
    }

    // ------------------------------------------------------------------ registry load

    /** True for the registries this class filters: enchantments and enchantment providers. */
    public static boolean handles(Identifier registry) {
        return registry.equals(Registries.ENCHANTMENT.identifier()) || registry.equals(Registries.ENCHANTMENT_PROVIDER.identifier());
    }

    /**
     * Called with the files of a datapack registry before any is read. For the enchantment registry every id is decided
     * here, so the other files can be checked against the full list, and removed ones are left out.
     */
    public static Map<Identifier, Resource> beforeRegistryLoad(ResourceKey<? extends Registry<?>> registry, FileToIdConverter converter,
                                                                     Map<Identifier, Resource> files) {
        if (!registry.equals(Registries.ENCHANTMENT)) {
            return files;
        }
        ScalpelCore core = Scalpel.core();
        if (core.rules().isEmpty()) {
            return files;
        }
        Map<Identifier, Resource> kept = null;
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            Identifier id = converter.fileToId(file.getKey());
            Decision decision = RegistryCutter.decideData(ContentType.ENCHANTMENT, id);
            if (!core.applies() || !decision.isCut()) {
                continue;
            }
            if (decision.action() == Decision.Action.REMOVE) {
                removed.add(id.toString());
                if (kept == null) {
                    kept = new LinkedHashMap<>(files);
                }
                kept.remove(file.getKey());
            } else {
                inert.add(id.toString());
            }
        }
        if (kept != null) {
            core.log().detail("enchantments left out of the registry: " + removed);
        }
        return kept == null ? files : kept;
    }

    /** One enchantment or enchantment provider file, before it is decoded. */
    public static JsonElement filter(Identifier registry, Identifier id, JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return json;
        }
        ScalpelCore core = Scalpel.core();
        if (core.rules().isEmpty() || !core.applies()) {
            return json;
        }
        boolean enchantment = registry.equals(Registries.ENCHANTMENT.identifier());
        if (enchantment) {
            // A client reading the server's list from its own copy of a pack never saw the file list.
            Decision decision = RegistryCutter.decideData(ContentType.ENCHANTMENT, id);
            if (decision.isCut() && decision.action() != Decision.Action.REMOVE) {
                inert.add(id.toString());
            }
        }
        JsonElement work = json;
        if (enchantment && inert.contains(id.toString())) {
            JsonObject redacted = json.getAsJsonObject().deepCopy();
            redacted.add("effects", new JsonObject());
            redacted.add("description", JsonParser.parseString("{\"translate\":\"enchantment.scalpel.redacted\",\"color\":\"dark_gray\"}"));
            work = redacted;
        }
        // Removed enchantments, and removed items an enchantment lists as the ones it applies to.
        Resolver resolver = core.resolver();
        java.util.function.Predicate<String> gone = ref -> removed.contains(ref) || isRemovedItem(resolver, ref);
        if ((removed.isEmpty() && !anyRemovedItem(resolver)) || !JsonScrub.references(work, gone)) {
            return work;
        }
        String directory = enchantment ? "enchantment" : "enchantment_provider";
        JsonElement scrubbed = work.deepCopy();
        JsonScrub.Result result = JsonScrub.scrub(scrubbed, gone, KEEP);
        String hits = String.join(", ", result.hits());
        if (!result.dropWhole() && !JsonScrub.references(scrubbed, gone)) {
            DataFilter.change(core, "enchantment references removed", id.toString(), directory + ": " + result.removed() + " for " + hits);
            return scrubbed;
        }
        if (!enchantment) {
            DataFilter.change(core, "enchantment provider emptied", id.toString(), "gave removed " + hits);
            return JsonParser.parseString(NOTHING_PROVIDED);
        }
        core.warn("enchantment " + id + " names removed " + hits + " where it can't be taken out. The world may fail to load;"
                + " keep " + hits + " or redact it instead.");
        return work;
    }

    private static boolean isRemovedItem(Resolver resolver, String id) {
        Decision decision = resolver.cutState(ContentType.ITEM, id);
        return decision != null && decision.action() == Decision.Action.REMOVE;
    }

    private static boolean anyRemovedItem(Resolver resolver) {
        return resolver.cutEntries(ContentType.ITEM).values().stream().anyMatch(d -> d.action() == Decision.Action.REMOVE);
    }

    /** True if a rule took this enchantment out of the registry (and rules apply). */
    public static boolean isRemoved(String id) {
        return !removed.isEmpty() && removed.contains(id);
    }

    // ------------------------------------------------------------------ items

    /** True for a redacted enchantment: its level reads 0. */
    public static boolean isInert(Holder<Enchantment> enchantment) {
        return !inert.isEmpty() && enchantment.unwrapKey().map(k -> inert.contains(k.identifier().toString())).orElse(false);
    }

    private static boolean anyCut(Resolver resolver) {
        return !resolver.cutIds(ContentType.ENCHANTMENT).isEmpty();
    }

    private static String idOf(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    /** The first cut enchantment on a stack (enchanted or stored on a book), or null. */
    static String firstCut(ItemStack stack) {
        ScalpelCore core = Scalpel.core();
        if (!core.applies() || stack.isEmpty() || !anyCut(core.resolver())) {
            return null;
        }
        for (DataComponentType<ItemEnchantments> type : COMPONENTS) {
            ItemEnchantments enchantments = stack.get(type);
            if (enchantments == null) {
                continue;
            }
            for (Holder<Enchantment> holder : enchantments.keySet()) {
                String id = idOf(holder);
                if (core.resolver().isCut(ContentType.ENCHANTMENT, id)) {
                    return id;
                }
            }
        }
        return null;
    }

    /** Takes cut enchantments off a stack and returns their ids. */
    static List<String> strip(ItemStack stack) {
        List<String> stripped = new ArrayList<>();
        if (firstCut(stack) == null) {
            return stripped;
        }
        Resolver resolver = Scalpel.core().resolver();
        for (DataComponentType<ItemEnchantments> type : COMPONENTS) {
            ItemEnchantments enchantments = stack.get(type);
            if (enchantments == null || enchantments.isEmpty()) {
                continue;
            }
            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(enchantments);
            mutable.removeIf(holder -> {
                String id = idOf(holder);
                if (resolver.isCut(ContentType.ENCHANTMENT, id)) {
                    stripped.add(id);
                    return true;
                }
                return false;
            });
            stack.set(type, mutable.toImmutable());
        }
        return stripped;
    }

    /** An enchanted book with nothing on it, which {@link #strip} can leave behind. */
    static boolean isEmptyBook(ItemStack stack) {
        return anyCut(Scalpel.core().resolver()) && stack.is(Items.ENCHANTED_BOOK)
                && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    /** True for an enchanted book whose enchantments are all cut or hidden, and not in a dry run. */
    public static boolean isUnlistedBook(ItemStack stack) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        if (!core.applies() || !stack.is(Items.ENCHANTED_BOOK)
                || (!anyCut(resolver) && resolver.hiddenEntries(ContentType.ENCHANTMENT).isEmpty())) {
            return false;
        }
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            String id = idOf(holder);
            if (!resolver.isCut(ContentType.ENCHANTMENT, id) && !resolver.isHidden(ContentType.ENCHANTMENT, id)) {
                return false;
            }
        }
        return !enchantments.isEmpty();
    }
}

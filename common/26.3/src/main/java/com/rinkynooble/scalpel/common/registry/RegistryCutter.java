package com.rinkynooble.scalpel.common.registry;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Decision.Action;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.common.Scalpel;
import com.rinkynooble.scalpel.common.content.Redacted;
import com.rinkynooble.scalpel.common.content.RedactedBlock;
import com.rinkynooble.scalpel.common.content.RedactedBlockItem;
import com.rinkynooble.scalpel.common.content.RedactedEntity;
import com.rinkynooble.scalpel.common.content.RedactedItem;
import com.rinkynooble.scalpel.common.content.RemovedBlock;
import com.rinkynooble.scalpel.common.content.RemovedItem;
import com.rinkynooble.scalpel.common.data.CutEnchantments;
import com.rinkynooble.scalpel.common.mixin.BlockItemAccessor;
import com.rinkynooble.scalpel.common.mixin.MappedRegistryAccessor;
import com.rinkynooble.scalpel.common.mixin.DataComponentInitializersAccessor;
import com.rinkynooble.scalpel.common.mixin.InitializerEntryAccessor;
import com.rinkynooble.scalpel.common.mixin.StandingAndWallBlockItemAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Decides, at registration time, whether an item, block or entity type is kept, hidden, redacted or removed.
 *
 * <p>Two entry points, both called from mixins:
 * <ul>
 *   <li>{@link #beforeLazyRegister}: {@code RegisterEvent#register} with a supplier (what DeferredRegister uses).
 *       Removed ids, redacted entity types and the items of redacted blocks are cut here, so the mod's object is
 *       never even constructed.</li>
 *   <li>{@link #beforeAdd}: where every registration ends up ({@code ForgeRegistry#add} on Forge, vanilla's registry on
 *       Fabric), vanilla bootstrap included,
 *       direct {@code Registry.register} calls). The object already exists here, so links such as a block item's
 *       block or a spawn egg's entity can be checked. Redacted blocks and items are swapped here too: a placeholder
 *       block copies the original's block states, and the report can name a mod's own class.</li>
 * </ul>
 */
public final class RegistryCutter {
    /** What a hook should do: skip the registration, or register {@code replacement} instead. Null means carry on. */
    public record Cut(boolean skip, Object replacement) {
        static final Cut SKIP = new Cut(true, null);
    }

    private static final Set<String> processed = ConcurrentHashMap.newKeySet();
    /** Objects that were built and then refused, so later links (a block item's block) can still be named. */
    private static final Set<Object> placeholderEntityTypes = Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));
    private static final Map<Object, String> refused = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Redacted originals and the placeholders registered in their place. */
    private static final Map<Object, Object> standIns = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Unregistered stand-ins for removed ids, handed to mod code that still asks for them. Keyed by type + id. */
    private static final Map<String, Object> ghosts = new ConcurrentHashMap<>();
    /**
     * Blocks and items a mod built and Scalpel refused (redacted or removed), keyed by type + id. A loader's holder
     * (NeoForge's DeferredHolder, Forge's RegistryObject) answers with these rather than the placeholder, because mods
     * cast what their holders give them to their own classes (AE2 collects its blocks into typed arrays).
     */
    private static final Map<String, Object> originals = new ConcurrentHashMap<>();

    private RegistryCutter() {
    }

    public static ContentType typeOf(ResourceKey<?> registryKey) {
        if (registryKey.equals(Registries.ITEM)) {
            return ContentType.ITEM;
        }
        if (registryKey.equals(Registries.BLOCK)) {
            return ContentType.BLOCK;
        }
        if (registryKey.equals(Registries.ENTITY_TYPE)) {
            return ContentType.ENTITY;
        }
        // Enchantments are data since 1.21 (CutEnchantments decides them as their registry loads).
        return null;
    }

    /**
     * Decides an id of a registry type that loads from data (enchantments since 1.21), once per id: records the cut or
     * hide in the resolver and the report. Returns the decision, also when it was made earlier.
     */
    public static Decision decideData(ContentType type, Identifier name) {
        ScalpelCore core = Scalpel.core();
        Resolver resolver = core.resolver();
        String id = name.toString();
        Decision earlier = resolver.cutState(type, id);
        if (earlier == null) {
            earlier = resolver.hiddenState(type, id);
        }
        if (earlier != null) {
            return earlier;
        }
        Decision decision = resolver.decide(type, id);
        if (!processed.add(key(type, id))) {
            return decision;
        }
        if (decision.action() == Action.PROTECTED) {
            core.report().protectedHit(type, id, name.getNamespace(), decision);
        }
        if (decision.isHidden() || decision.isCut()) {
            record(core, type, id, name.getNamespace(), decision);
        }
        return decision;
    }

    /**
     * A supplier built by {@code RegisterEvent#register(key, Consumer)} wraps an object that already exists.
     * Those are left to {@link #beforeAdd}, which can clean up after the object properly.
     */
    public static boolean wrapsBuiltObject(Supplier<?> supplier) {
        String name = supplier.getClass().getName();
        return name.startsWith("net.minecraftforge.registries.RegisterEvent") || name.startsWith("net.neoforged.neoforge.registries.RegisterEvent");
    }

    public static Cut beforeLazyRegister(ResourceKey<?> registryKey, Identifier name, Supplier<?> supplier) {
        ContentType type = typeOf(registryKey);
        if (type == null || name.getNamespace().equals(Scalpel.MOD_ID)) {
            return null;
        }
        ScalpelCore core = Scalpel.core();
        if (!core.applies()) {
            return null;
        }
        String id = name.toString();
        if (processed.contains(key(type, id))) {
            return null;
        }
        Resolver resolver = core.resolver();
        Decision decision = switch (type) {
            case BLOCK -> resolver.decideBlock(id);
            case ITEM -> resolver.decideItem(id, List.of(id), null);
            case ENTITY -> resolver.decideEntity(id);
            default -> Decision.NONE;
        };
        if (!decision.isCut()) {
            return null;
        }
        if (decision.action() == Action.REMOVE && (type == ContentType.BLOCK || type == ContentType.ITEM)
                && !holdsBuiltObject(registryKey, supplier)) {
            // Built, then refused in beforeAdd. The mod's holder resolves to its own object, because mods cast what
            // their holders give them (AE2 casts its charger block to ChargerBlock and collects blocks into typed arrays).
            return buildRemoved(registryKey, type, name, decision, supplier);
        }
        boolean placesPlaceholder = type == ContentType.ITEM && Scalpel.platform().registeredValue(Registries.BLOCK, name) instanceof RedactedBlock;
        if (decision.action() != Action.REMOVE && type != ContentType.ENTITY && !placesPlaceholder) {
            // Built, then swapped in beforeAdd: a block's states and an item's class are only known once it exists.
            return buildRedacted(registryKey, type, name, decision, supplier);
        }
        if (holdsBuiltObject(registryKey, supplier)) {
            // The mod built the object already and only hands it back (GlitchCore registers Biomes O' Plenty this
            // way). Skipping here would leave its intrusive holder behind and the registry would refuse to freeze;
            // beforeAdd sees the object and releases it.
            return null;
        }
        // The item of a redacted block is never built: its block is already the placeholder, and mods hand the
        // block to the item's constructor cast to their own class (Mekanism's BlockDeferredRegister).
        processed.add(key(type, id));
        record(core, type, id, name.getNamespace(), decision);
        if (decision.action() == Action.REMOVE) {
            makeGhost(registryKey, type, name);
            return Cut.SKIP;
        }
        return new Cut(false, placeholder(type, name, null));
    }

    /**
     * Builds a redacted block or item for {@link #beforeAdd} to swap. It is built only to read its block states and
     * class, so a constructor that fails on another cut entry it needs costs only that: the entry gets a placeholder
     * that was never built, with a warning, and whatever the failed constructor left in the registry is released.
     */
    private static Cut buildRedacted(ResourceKey<?> registryKey, ContentType type, Identifier name, Decision decision, Supplier<?> supplier) {
        Set<Object> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(intrusiveHolders(registryKey).keySet());
        try {
            return new Cut(false, supplier.get());
        } catch (RuntimeException e) {
            intrusiveHolders(registryKey).keySet().removeIf(obj -> !before.contains(obj));
            ScalpelCore core = Scalpel.core();
            String id = name.toString();
            processed.add(key(type, id));
            record(core, type, id, name.getNamespace(), decision);
            core.warn(type.keyword() + " " + id + " failed to build (" + e.getClass().getSimpleName() + "), so its placeholder was made"
                    + " without it" + (type == ContentType.BLOCK ? " and has no block states" : "") + ". scalpel.log has the error.");
            core.log().detail("building redacted " + type.keyword() + " " + id + " failed: " + e);
            forgetComponents(registryKey, name);
            return new Cut(false, placeholder(type, name, null));
        }
    }

    /**
     * Builds a removed block or item for {@link #beforeAdd} to refuse; the built object becomes what the mod's holder
     * answers. If the constructor fails, the entry is skipped with a ghost, as before.
     */
    private static Cut buildRemoved(ResourceKey<?> registryKey, ContentType type, Identifier name, Decision decision, Supplier<?> supplier) {
        Set<Object> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(intrusiveHolders(registryKey).keySet());
        try {
            return new Cut(false, supplier.get());
        } catch (RuntimeException e) {
            intrusiveHolders(registryKey).keySet().removeIf(obj -> !before.contains(obj));
            ScalpelCore core = Scalpel.core();
            String id = name.toString();
            processed.add(key(type, id));
            record(core, type, id, name.getNamespace(), decision);
            core.log().detail("building removed " + type.keyword() + " " + id + " failed, skipped instead: " + e);
            forgetComponents(registryKey, name);
            makeGhost(registryKey, type, name);
            return Cut.SKIP;
        }
    }

    public static Cut beforeAdd(ResourceKey<?> registryKey, Identifier name, Object value, String owner) {
        ContentType type = typeOf(registryKey);
        if (type == null || value instanceof Redacted || name.getNamespace().equals(Scalpel.MOD_ID)) {
            return null;
        }
        String id = name.toString();
        if (!processed.add(key(type, id))) {
            return null;
        }
        ScalpelCore core = Scalpel.core();
        if (core.rules().isEmpty()) {
            return null;
        }
        Resolver resolver = core.resolver();
        String modId = owner == null ? name.getNamespace() : owner;

        List<String> placedBlocks = List.of();
        Decision decision;
        switch (type) {
            case BLOCK -> decision = resolver.decideBlock(id);
            case ITEM -> {
                placedBlocks = placedBlockIds(value);
                decision = resolver.decideItem(id, placedBlocks, spawnedEntityId(value, name));
            }
            case ENTITY -> decision = resolver.decideEntity(id);
            default -> decision = Decision.NONE;
        }

        if (decision.action() == Action.PROTECTED) {
            core.report().protectedHit(type, id, modId, decision);
            core.log().detail("protected " + type.keyword() + " " + id + ": " + decision.describe());
        }
        if (decision.isHidden()) {
            // Hidden content registers as itself; only recipe viewers and creative tabs leave it out.
            record(core, type, id, modId, decision);
        }
        if (type == ContentType.ITEM && !decision.isCut()) {
            for (String blockId : placedBlocks) {
                if (resolver.isCut(ContentType.BLOCK, blockId)) {
                    core.warn("item " + id + " is not cut but places cut block " + blockId
                            + ". Placing it will fail or crash; cut the item too.");
                }
            }
        }
        if (!decision.isCut()) {
            return null;
        }
        if (decision.action() == Action.REDACT && name.getNamespace().equals("minecraft")) {
            // Vanilla code relies on its own classes and block states (beds, for example), so vanilla content is
            // left out of every list and filtered out of all data, but the object itself stays registered.
            decision = new Decision(Action.REDACT, decision.rule(), decision.matched(),
                    (decision.note() == null ? "" : decision.note() + "; ") + "vanilla: filtered out, not replaced");
            record(core, type, id, modId, decision);
            return null;
        }
        record(core, type, id, modId, decision);
        if (decision.action() == Action.REDACT) {
            String modClass = modClassOf(type, value);
            if (modClass != null) {
                core.report().modClass(type, id, modId, modClass);
            }
        }
        if (!core.applies()) {
            return null;
        }
        release(registryKey, value);
        forgetComponents(registryKey, name);
        refused.put(value, id);
        if (type == ContentType.BLOCK || type == ContentType.ITEM) {
            originals.put(key(type, id), value);
        }
        if (decision.action() == Action.REMOVE) {
            forgetRefused(value, null);
            if (type == ContentType.BLOCK || type == ContentType.ITEM) {
                // The mod keeps using its own object (it may cast it), so its holder answers with that.
                ghosts.put(key(type, id), value);
            } else {
                makeGhost(registryKey, type, name);
            }
            return Cut.SKIP;
        }
        Object replacement = placeholder(type, name, value);
        forgetRefused(value, replacement);
        standIns.put(value, replacement);
        return new Cut(false, replacement);
    }

    /**
     * The original's class when it is a mod's own (TaCZ's {@code GunSmithTableItem}), as opposed to a vanilla or
     * Forge class such as {@code Item}, {@code BlockItem}, {@code Block} or {@code StairBlock}. A mod's code may cast
     * its entry to that class, which a placeholder can't satisfy. Null for vanilla and Forge classes.
     */
    private static String modClassOf(ContentType type, Object value) {
        if (type != ContentType.ITEM && type != ContentType.BLOCK) {
            return null;
        }
        Class<?> c = value.getClass();
        while (c.isAnonymousClass()) {
            c = c.getSuperclass();
        }
        return c.getName().startsWith("net.minecraft") ? null : c.getName();
    }

    /**
     * Takes a refused object out of the lists its own constructor put it in. A loader's spawn egg class may keep its own
     * egg list (vanilla's has none since 26.1: eggs are found through their ENTITY_DATA component). A flower pot built
     * with the old constructor adds itself to the empty pot's plants, and potting the plant would place the
     * unregistered block. A refused item's default components are forgotten (see {@link #forgetComponents}).
     */
    private static void forgetRefused(Object original, Object replacement) {
        if (original instanceof SpawnEggItem egg) {
            Scalpel.platform().forgetSpawnEgg(egg);
        }
        if (original instanceof FlowerPotBlock pot && pot != Blocks.FLOWER_POT) {
            Scalpel.platform().forgetFlowerPot(pot, replacement instanceof Block block ? block : null);
        }
    }

    /**
     * A removed id gets a ghost: a placeholder object that is never registered. Mods commonly look up every entry
     * they own during startup (attributes, creative tabs, render layers); with a ghost those lookups succeed and
     * do nothing, instead of crashing. The ghost is built while the registry still accepts new objects, and its
     * intrusive holder is dropped right away so the registry can freeze.
     */
    private static void makeGhost(ResourceKey<?> registryKey, ContentType type, Identifier name) {
        Object ghost = type == ContentType.BLOCK ? new RemovedBlock(name) : placeholder(type, name, null);
        release(registryKey, ghost);
        forgetComponents(registryKey, name);
        refused.put(ghost, name.toString());
        ghosts.put(key(type, name.toString()), ghost);
    }

    /**
     * What Forge's delegate lookup should find for an object that was refused, or null. Mods that build their content
     * themselves keep the original in a static field and go on using it (AE2 sets render layers on its blocks and
     * builds stacks of its items), and Forge throws "No delegate exists for value" for anything unregistered. A
     * redacted original resolves to its placeholder, a removed block or item to the shared {@code scalpel:removed}.
     */
    /** The id a refused object had before it was refused, or null (1.21.11: BlockHolderMixin keys its holder by it). */
    public static String refusedId(Object value) {
        return refused.get(value);
    }

    public static Object standInFor(Object value) {
        Object standIn = standIns.get(value);
        if (standIn != null || !refused.containsKey(value)) {
            return standIn;
        }
        if (value instanceof Block) {
            return RemovedItem.blockOrNull();
        }
        return value instanceof Item ? RemovedItem.getOrNull() : null;
    }

    /**
     * The item a refused block should report from {@code asItem()}, or null when the block wasn't refused. Fabric mods
     * keep their own blocks and fill their creative tabs with {@code block.asItem()}; for a refused block vanilla answers
     * air, and a tab with an air stack fails to build. A redacted block gives its placeholder's item, a removed one
     * {@code scalpel:removed} (which the tab filter then takes out).
     */
    public static Item itemForRefusedBlock(Block block) {
        if (!refused.containsKey(block)) {
            return null;
        }
        if (standIns.get(block) instanceof Block placeholder) {
            Item item = placeholder.asItem();
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return item;
            }
        }
        return RemovedItem.getOrNull();
    }

    /** The ghost for a removed id, or null. Called by {@code RegistryObject#get()} when the entry is missing. */
    public static Object ghostFor(Identifier registry, Identifier name) {
        if (ghosts.isEmpty()) {
            return null;
        }
        ContentType type = typeOf(ResourceKey.createRegistryKey(registry));
        if (type == null) {
            return null;
        }
        Object ghost = ghosts.get(key(type, name.toString()));
        if (ghost instanceof Redacted && type == ContentType.ITEM) {
            // An item that was never built: removed items without an object of their own share one hidden registered
            // item, since an item must be registered to go into an ItemStack. A refused original answers for itself;
            // the registry's stand-in lookups make it read as scalpel:removed.
            return RemovedItem.getOrNull();
        }
        return ghost;
    }

    /**
     * The mod's own object for a refused block or item, or null. What a loader's holder for that id should answer once
     * the entry is decided; registry lookups by id still find the placeholder.
     */
    public static Object originalFor(Identifier registry, Identifier name) {
        if (originals.isEmpty()) {
            return null;
        }
        ContentType type = typeOf(ResourceKey.createRegistryKey(registry));
        return type == null ? null : originals.get(key(type, name.toString()));
    }

    /** True for entity types Scalpel created as placeholders. */
    public static boolean isPlaceholderEntityType(Object type) {
        return placeholderEntityTypes.contains(type);
    }

    /** The id an item stands for: its original id for placeholders and ghosts, else its registry key. */
    public static String itemId(Item item) {
        String id = idOf(Registries.ITEM, item);
        return id == null ? "minecraft:air" : id;
    }

    /** True for items that were cut: placeholders, ghosts, refused originals and redacted vanilla items. */
    public static boolean isCutItem(Item item) {
        String id = itemId(item);
        return id.equals(RemovedItem.ID) || Scalpel.core().resolver().isCut(ContentType.ITEM, id);
    }

    /** True for items a {@code hide} rule matched. They are not cut: recipes, loot and tags keep them. */
    public static boolean isHiddenItem(Item item) {
        return Scalpel.core().resolver().isHidden(ContentType.ITEM, itemId(item));
    }

    /** True for stacks left out of recipe viewers and creative tabs: their item, or a book with only cut enchantments. */
    public static boolean isUnlisted(ItemStack stack) {
        return isUnlisted(stack.getItem()) || CutEnchantments.isUnlistedBook(stack);
    }

    /** True for items left out of recipe viewers and creative tabs: cut or hidden, and not in a dry run. */
    public static boolean isUnlisted(Item item) {
        return Scalpel.core().applies() && (isCutItem(item) || isHiddenItem(item));
    }

    private static void record(ScalpelCore core, ContentType type, String id, String modId, Decision decision) {
        if (decision.isHidden()) {
            core.resolver().markHidden(type, id, decision);
            core.report().hidden(type, id, modId, decision);
        } else {
            core.resolver().markCut(type, id, decision);
            core.report().cut(type, id, modId, decision);
        }
        core.log().detail((core.applies() ? "" : "[dry run] ") + decision.action().name().toLowerCase(java.util.Locale.ROOT) + " "
                + type.keyword() + " " + id + " (mod " + modId + ")" + ruleSuffix(decision));
    }

    private static String ruleSuffix(Decision decision) {
        Rule rule = decision.rule();
        String text = rule == null ? "" : ", rule " + rule.location();
        return decision.note() == null ? text : text + ", " + decision.note();
    }

    private static Object placeholder(ContentType type, Identifier name, Object original) {
        return switch (type) {
            case BLOCK -> original instanceof Block block ? RedactedBlock.copying(name, block) : new RedactedBlock(name);
            case ITEM -> {
                RedactedBlock block = redactedBlockFor(name, original);
                yield block != null ? new RedactedBlockItem(block, name) : new RedactedItem(name);
            }
            case ENTITY -> {
                EntityType<RedactedEntity> placeholder = RedactedEntity.createType(name);
                placeholderEntityTypes.add(placeholder);
                yield placeholder;
            }
            default -> throw new IllegalArgumentException(type.toString());
        };
    }

    /** The placeholder block a redacted item should place: its own block's, or the one with the same id. */
    private static RedactedBlock redactedBlockFor(Identifier name, Object original) {
        if (original instanceof BlockItem blockItem) {
            Object placed = ((BlockItemAccessor) blockItem).scalpel$block();
            Object standIn = standIns.get(placed);
            if ((standIn != null ? standIn : placed) instanceof RedactedBlock block) {
                return block;
            }
        }
        Object sameId = Scalpel.platform().registeredValue(Registries.BLOCK, name);
        return sameId instanceof RedactedBlock block ? block : null;
    }

    /**
     * True if the supplier holds (as a captured value or field) an object that is built and waiting to be registered,
     * that is, one with an unregistered intrusive holder. A supplier that builds its object holds none. Fields that
     * can't be read count as not holding one.
     */
    private static boolean holdsBuiltObject(ResourceKey<?> registryKey, Supplier<?> supplier) {
        Map<Object, ?> holders = intrusiveHolders(registryKey);
        if (holders.isEmpty()) {
            return false;
        }
        for (java.lang.reflect.Field field : supplier.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object held = field.get(supplier);
                if (held != null && holders.containsKey(held)) {
                    return true;
                }
            } catch (RuntimeException | IllegalAccessException e) {
                // Not readable: treated as a supplier that builds its object.
            }
        }
        return false;
    }

    /**
     * 26.1: an item's constructor queues its default components under its id in
     * {@code BuiltInRegistries.DATA_COMPONENT_INITIALIZERS}, applied once registries are loaded. A refused item's must
     * go: for a removed id nothing is registered under it (the game would stop at "Missing element"), and for a redacted
     * one the placeholder would also get the original's components (a sword's damage, a food's nutrition). Called
     * before the placeholder is built, so only the placeholder's own stay.
     */
    private static void forgetComponents(ResourceKey<?> registryKey, Identifier name) {
        if (!registryKey.equals(Registries.ITEM)) {
            return;
        }
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, name);
        List<Object> pending = ((DataComponentInitializersAccessor) BuiltInRegistries.DATA_COMPONENT_INITIALIZERS).scalpel$initializers();
        synchronized (pending) {
            pending.removeIf(entry -> key.equals(((InitializerEntryAccessor) entry).scalpel$key()));
        }
    }

    /** Removes the intrusive holder the refused object created, or Forge refuses to freeze the registry. */
    private static void release(ResourceKey<?> registryKey, Object value) {
        intrusiveHolders(registryKey).remove(value);
    }

    /** Objects built for a registry and not registered yet. Empty when the registry keeps none. */
    @SuppressWarnings("unchecked")
    private static Map<Object, ?> intrusiveHolders(ResourceKey<?> registryKey) {
        Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(registryKey.identifier());
        if (registry instanceof MappedRegistryAccessor<?> accessor) {
            Map<Object, ?> holders = ((MappedRegistryAccessor<Object>) accessor).scalpel$unregisteredIntrusiveHolders();
            if (holders != null) {
                return holders;
            }
        }
        return new IdentityHashMap<>();
    }

    private static List<String> placedBlockIds(Object value) {
        if (!(value instanceof BlockItem blockItem)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(2);
        // The field, not getBlock(): that asks Forge for the block's delegate, which a refused block doesn't have.
        addBlockId(ids, ((BlockItemAccessor) blockItem).scalpel$block());
        if (value instanceof StandingAndWallBlockItem wall) {
            addBlockId(ids, ((StandingAndWallBlockItemAccessor) wall).scalpel$wallBlock());
        }
        return ids;
    }

    private static void addBlockId(List<String> ids, Block block) {
        String id = idOf(Registries.BLOCK, block);
        if (id != null && !ids.contains(id)) {
            ids.add(id);
        }
    }

    private static String spawnedEntityId(Object value, Identifier name) {
        try {
            if (value instanceof SpawnEggItem egg) {
                String loaderEgg = Scalpel.platform().spawnEggEntityId(egg);
                if (loaderEgg != null) {
                    return loaderEgg;
                }
                // 1.21.5: the egg's entity is its ENTITY_DATA component, set from its properties. Since 26.1 an item's
                // components are bound only after registries load, so at registration the pending initializer is read.
                TypedEntityData<EntityType<?>> data = pendingComponents(name).get(DataComponents.ENTITY_DATA);
                return data == null ? null : idOf(Registries.ENTITY_TYPE, data.type());
            }
        } catch (RuntimeException e) {
            // The egg's entity cannot be resolved yet; the item is judged by its own rules only.
        }
        return null;
    }

    /**
     * The default components an item queued in its constructor, built early with no data registries: a component
     * that needs one (a tag, an enchantment) stops its initializer there, so only plain values such as a spawn egg's
     * ENTITY_DATA are dependable. Never bound to the item; the real ones are built after registries load.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static DataComponentMap pendingComponents(Identifier name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, name);
        List<Object> mine = new ArrayList<>();
        List<Object> pending = ((DataComponentInitializersAccessor) BuiltInRegistries.DATA_COMPONENT_INITIALIZERS).scalpel$initializers();
        synchronized (pending) {
            for (Object entry : pending) {
                if (key.equals(((InitializerEntryAccessor) entry).scalpel$key())) {
                    mine.add(entry);
                }
            }
        }
        DataComponentMap.Builder components = DataComponentMap.builder();
        HolderLookup.Provider noRegistries = HolderLookup.Provider.create(Stream.empty());
        for (Object entry : mine) {
            try {
                ((DataComponentInitializers.Initializer) ((InitializerEntryAccessor) entry).scalpel$initializer()).run(components, noRegistries, key);
            } catch (RuntimeException e) {
                // Needs a data registry; whatever was set before that stays in the builder.
            }
        }
        try {
            return components.build();
        } catch (RuntimeException e) {
            return DataComponentMap.EMPTY;
        }
    }

    /** The id of a registry value: its key, the id it stands in for, or the id it had before it was refused. */
    private static <T> String idOf(ResourceKey<Registry<T>> registryKey, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Redacted redacted) {
            return redacted.originalId().toString();
        }
        String before = refused.get(value);
        if (before != null) {
            return before;
        }
        Identifier key = Scalpel.platform().registeredKey(registryKey, value);
        return key == null ? null : key.toString();
    }

    private static String key(ContentType type, String id) {
        return type.ordinal() + id;
    }
}

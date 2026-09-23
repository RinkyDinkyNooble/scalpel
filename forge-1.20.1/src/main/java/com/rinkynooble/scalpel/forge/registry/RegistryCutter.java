package com.rinkynooble.scalpel.forge.registry;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.Decision;
import com.rinkynooble.scalpel.core.Decision.Action;
import com.rinkynooble.scalpel.core.Resolver;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.core.rules.Rule;
import com.rinkynooble.scalpel.forge.Scalpel;
import com.rinkynooble.scalpel.forge.content.Redacted;
import com.rinkynooble.scalpel.forge.content.RedactedBlock;
import com.rinkynooble.scalpel.forge.content.RedactedBlockItem;
import com.rinkynooble.scalpel.forge.content.RedactedEntity;
import com.rinkynooble.scalpel.forge.content.RedactedItem;
import com.rinkynooble.scalpel.forge.content.RemovedItem;
import com.rinkynooble.scalpel.forge.mixin.ForgeSpawnEggItemAccessor;
import com.rinkynooble.scalpel.forge.mixin.MappedRegistryAccessor;
import com.rinkynooble.scalpel.forge.mixin.SpawnEggItemAccessor;
import com.rinkynooble.scalpel.forge.mixin.StandingAndWallBlockItemAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Decides, at registration time, whether an item, block or entity type is kept, redacted or removed.
 *
 * <p>Two entry points, both called from mixins:
 * <ul>
 *   <li>{@link #beforeLazyRegister}: {@code RegisterEvent#register} with a supplier (what DeferredRegister uses).
 *       Cutting here means the mod's object is never even constructed.</li>
 *   <li>{@link #beforeAdd}: {@code ForgeRegistry#add}, where every registration ends up (vanilla bootstrap,
 *       direct {@code Registry.register} calls). The object already exists here, so links such as a block item's
 *       block or a spawn egg's entity can be checked.</li>
 * </ul>
 */
public final class RegistryCutter {
    /** What a hook should do: skip the registration, or register {@code replacement} instead. Null means carry on. */
    public record Cut(boolean skip, Object replacement) {
        static final Cut SKIP = new Cut(true, null);
    }

    private static final Set<String> processed = ConcurrentHashMap.newKeySet();
    /** Objects that were built and then refused, so later links (a block item's block) can still be named. */
    private static final Map<Object, String> refused = Collections.synchronizedMap(new IdentityHashMap<>());
    /** Unregistered stand-ins for removed ids, handed to mod code that still asks for them. Keyed by type + id. */
    private static final Map<String, Object> ghosts = new ConcurrentHashMap<>();

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
        return null;
    }

    /**
     * A supplier built by {@code RegisterEvent#register(key, Consumer)} wraps an object that already exists.
     * Those are left to {@link #beforeAdd}, which can clean up after the object properly.
     */
    public static boolean wrapsBuiltObject(Supplier<?> supplier) {
        return supplier.getClass().getName().startsWith("net.minecraftforge.registries.RegisterEvent");
    }

    public static Cut beforeLazyRegister(ResourceKey<?> registryKey, ResourceLocation name) {
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
        processed.add(key(type, id));
        record(core, type, id, name.getNamespace(), decision);
        if (decision.action() == Action.REMOVE) {
            makeGhost(registryKey, type, name);
            return Cut.SKIP;
        }
        return new Cut(false, placeholder(type, name, null));
    }

    public static Cut beforeAdd(ResourceKey<?> registryKey, ResourceLocation name, Object value, String owner) {
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
                decision = resolver.decideItem(id, placedBlocks, spawnedEntityId(value));
            }
            case ENTITY -> decision = resolver.decideEntity(id);
            default -> decision = Decision.NONE;
        }

        if (decision.action() == Action.PROTECTED) {
            core.report().protectedHit(type, id, modId, decision);
            core.log().detail("protected " + type.keyword() + " " + id + ": " + decision.describe());
        }
        if (type == ContentType.ITEM && !decision.isCut()) {
            for (String blockId : placedBlocks) {
                if (resolver.isCut(ContentType.BLOCK, blockId)) {
                    core.warn("item " + id + " is " + decision.action().name().toLowerCase(java.util.Locale.ROOT) + " but places cut block "
                            + blockId + ". Placing it will fail or crash; cut the item too.");
                }
            }
        }
        if (!decision.isCut()) {
            return null;
        }
        if (decision.action() == Action.REDACT && name.getNamespace().equals("minecraft")) {
            // Vanilla code relies on its own classes and block states (beds, for example), so vanilla content is
            // hidden and filtered out of all data, but the object itself stays registered.
            decision = new Decision(Action.REDACT, decision.rule(), decision.matched(),
                    (decision.note() == null ? "" : decision.note() + "; ") + "vanilla: hidden, not replaced");
            record(core, type, id, modId, decision);
            return null;
        }
        record(core, type, id, modId, decision);
        if (!core.applies()) {
            return null;
        }
        release(registryKey, value);
        refused.put(value, id);
        if (decision.action() == Action.REMOVE) {
            makeGhost(registryKey, type, name);
            return Cut.SKIP;
        }
        return new Cut(false, placeholder(type, name, value));
    }

    /**
     * A removed id gets a ghost: a placeholder object that is never registered. Mods commonly look up every entry
     * they own during startup (attributes, creative tabs, render layers); with a ghost those lookups succeed and
     * do nothing, instead of crashing. The ghost is built while the registry still accepts new objects, and its
     * intrusive holder is dropped right away so the registry can freeze.
     */
    private static void makeGhost(ResourceKey<?> registryKey, ContentType type, ResourceLocation name) {
        Object ghost = placeholder(type, name, null);
        release(registryKey, ghost);
        refused.put(ghost, name.toString());
        ghosts.put(key(type, name.toString()), ghost);
    }

    /** The ghost for a removed id, or null. Called by {@code RegistryObject#get()} when the entry is missing. */
    public static Object ghostFor(ResourceLocation registry, ResourceLocation name) {
        if (ghosts.isEmpty()) {
            return null;
        }
        ContentType type = typeOf(ResourceKey.createRegistryKey(registry));
        if (type == null) {
            return null;
        }
        Object ghost = ghosts.get(key(type, name.toString()));
        if (ghost != null && type == ContentType.ITEM) {
            // An item must be registered to go into an ItemStack, so removed items share one hidden registered item.
            return RemovedItem.getOrNull();
        }
        return ghost;
    }

    /** The id an item stands for: its original id for placeholders and ghosts, else its registry key. */
    public static String itemId(net.minecraft.world.item.Item item) {
        String id = idOf(Registries.ITEM, item);
        return id == null ? "minecraft:air" : id;
    }

    /** True for items that were cut: placeholders, ghosts, refused originals and hidden vanilla items. */
    public static boolean isCutItem(net.minecraft.world.item.Item item) {
        String id = itemId(item);
        return id.equals(RemovedItem.ID) || Scalpel.core().resolver().isCut(ContentType.ITEM, id);
    }

    private static void record(ScalpelCore core, ContentType type, String id, String modId, Decision decision) {
        core.resolver().markCut(type, id, decision);
        core.report().cut(type, id, modId, decision);
        core.log().detail((core.applies() ? "" : "[dry run] ") + decision.action().name().toLowerCase(java.util.Locale.ROOT) + " "
                + type.keyword() + " " + id + " (mod " + modId + ")" + ruleSuffix(decision));
    }

    private static String ruleSuffix(Decision decision) {
        Rule rule = decision.rule();
        String text = rule == null ? "" : ", rule " + rule.location();
        return decision.note() == null ? text : text + ", " + decision.note();
    }

    private static Object placeholder(ContentType type, ResourceLocation name, Object original) {
        return switch (type) {
            case BLOCK -> new RedactedBlock(name);
            case ITEM -> {
                RedactedBlock block = redactedBlockFor(name, original);
                yield block != null ? new RedactedBlockItem(block, name) : new RedactedItem(name);
            }
            case ENTITY -> RedactedEntity.createType(name);
            default -> throw new IllegalArgumentException(type.toString());
        };
    }

    /** The placeholder block a redacted item should place: its own block's, or the one with the same id. */
    private static RedactedBlock redactedBlockFor(ResourceLocation name, Object original) {
        if (original instanceof BlockItem blockItem && blockItem.getBlock() instanceof RedactedBlock block) {
            return block;
        }
        Block sameId = forgeRegistry(Registries.BLOCK).getValue(name);
        return sameId instanceof RedactedBlock block ? block : null;
    }

    /** Removes the intrusive holder the refused object created, or Forge refuses to freeze the registry. */
    @SuppressWarnings("unchecked")
    private static void release(ResourceKey<?> registryKey, Object value) {
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(registryKey.location());
        if (registry instanceof MappedRegistryAccessor<?> accessor) {
            Map<Object, ?> holders = ((MappedRegistryAccessor<Object>) accessor).scalpel$unregisteredIntrusiveHolders();
            if (holders != null) {
                holders.remove(value);
            }
        }
    }

    private static List<String> placedBlockIds(Object value) {
        if (!(value instanceof BlockItem blockItem)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(2);
        addBlockId(ids, blockItem.getBlock());
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

    private static String spawnedEntityId(Object value) {
        try {
            if (value instanceof ForgeSpawnEggItem egg) {
                Supplier<? extends EntityType<?>> supplier = ((ForgeSpawnEggItemAccessor) egg).scalpel$typeSupplier();
                if (supplier instanceof RegistryObject<?> registryObject) {
                    return registryObject.getId().toString();
                }
                return idOf(Registries.ENTITY_TYPE, supplier.get());
            }
            if (value instanceof SpawnEggItem egg) {
                return idOf(Registries.ENTITY_TYPE, ((SpawnEggItemAccessor) egg).scalpel$defaultType());
            }
        } catch (RuntimeException e) {
            // The egg's entity cannot be resolved yet; the item is judged by its own rules only.
        }
        return null;
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
        @SuppressWarnings("unchecked")
        ForgeRegistry<T> registry = forgeRegistry(registryKey);
        return registry.getResourceKey((T) value).map(k -> k.location().toString()).orElse(null);
    }

    private static <T> ForgeRegistry<T> forgeRegistry(ResourceKey<Registry<T>> key) {
        return RegistryManager.ACTIVE.getRegistry(key);
    }

    private static String key(ContentType type, String id) {
        return type.ordinal() + id;
    }
}

package com.rinkynooble.scalpel.forge.data;

import com.rinkynooble.scalpel.core.ContentType;
import com.rinkynooble.scalpel.core.ScalpelCore;
import com.rinkynooble.scalpel.forge.Scalpel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure templates (.nbt) name their blocks and entities by id. Cut blocks become air and cut entities are
 * left out, in both modes, and each one is logged once per structure.
 */
public final class StructureFilter {
    private static final ThreadLocal<ResourceLocation> loading = new ThreadLocal<>();
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private StructureFilter() {
    }

    public static void beginTemplate(ResourceLocation id) {
        loading.set(id);
    }

    public static void endTemplate() {
        loading.remove();
    }

    /** True when a block state tag names a cut block, which should then be read as air. */
    public static boolean isCutBlock(CompoundTag stateTag) {
        return isCut(ContentType.BLOCK, stateTag.getString("Name"), "block");
    }

    /** True when an entity tag in a structure names a cut entity, which should then be skipped. */
    public static boolean isCutEntity(CompoundTag entityTag) {
        return isCut(ContentType.ENTITY, entityTag.getString("id"), "entity");
    }

    private static boolean isCut(ContentType type, String id, String what) {
        if (id.isEmpty()) {
            return false;
        }
        ScalpelCore core = Scalpel.core();
        if (core.resolver().cutIds(type).isEmpty() || !core.resolver().isCut(type, id)) {
            return false;
        }
        ResourceLocation structure = loading.get();
        String target = structure == null ? "unknown structure" : structure.toString();
        if (reported.add(target + "|" + id)) {
            DataFilter.change(core, "structure " + what + " removed", target, id + (type == ContentType.BLOCK ? " replaced with air" : " skipped"));
        }
        return core.applies();
    }
}

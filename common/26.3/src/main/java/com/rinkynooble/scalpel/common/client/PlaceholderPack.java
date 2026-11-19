package com.rinkynooble.scalpel.common.client;

import com.rinkynooble.scalpel.common.content.Redacted;
import com.rinkynooble.scalpel.common.content.RedactedBlock;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A resource pack built in memory at the top of the pack list. For every redacted id it provides a blockstate and an
 * item model definition ({@code items/<id>.json}, 1.21.4: an item's model is named by its id) pointing at Scalpel's
 * shared models, so the original mod's models are never loaded or baked.
 */
public final class PlaceholderPack implements PackResources {
    public static final String ID = "scalpel_placeholders";

    private static final byte[] BLOCKSTATE = "{\"variants\":{\"\":{\"model\":\"scalpel:block/redacted\"}}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ITEM_MODEL = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"scalpel:item/redacted\"}}"
            .getBytes(StandardCharsets.UTF_8);

    private final Map<Identifier, byte[]> files = new HashMap<>();
    private final Set<String> namespaces = new HashSet<>();

    public PlaceholderPack() {
        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (entry.getValue() instanceof RedactedBlock) {
                Identifier id = entry.getKey().identifier();
                add(Identifier.fromNamespaceAndPath(id.getNamespace(), "blockstates/" + id.getPath() + ".json"), BLOCKSTATE);
            }
        }
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            if (entry.getValue() instanceof Redacted) {
                Identifier id = entry.getKey().identifier();
                add(Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json"), ITEM_MODEL);
            }
        }
    }

    private void add(Identifier location, byte[] data) {
        files.put(location, data);
        namespaces.add(location.getNamespace());
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    /** True if this pack provides the blockstate file at {@code location} (a placeholder block). */
    public boolean providesBlockstate(Identifier location) {
        return files.containsKey(location);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        byte[] data = files.get(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<Identifier, byte[]> file : files.entrySet()) {
            Identifier location = file.getKey();
            if (location.getNamespace().equals(namespace) && location.getPath().startsWith(prefix)) {
                byte[] data = file.getValue();
                output.accept(location, () -> new ByteArrayInputStream(data));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? namespaces : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionType<T> type) {
        if ("pack".equals(type.name())) {
            PackFormat version = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
            return (T) new PackMetadataSection(Component.literal("Scalpel placeholders"), new InclusiveRange<>(version, version));
        }
        return null;
    }

    /** Where the pack says it comes from: built in, named "Scalpel placeholders". */
    public static final PackLocationInfo LOCATION = new PackLocationInfo(ID, Component.literal("Scalpel placeholders"), PackSource.BUILT_IN, Optional.empty());

    @Override
    public PackLocationInfo location() {
        return LOCATION;
    }


    @Override
    public void close() {
    }
}

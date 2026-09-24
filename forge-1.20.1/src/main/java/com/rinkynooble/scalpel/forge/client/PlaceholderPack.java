package com.rinkynooble.scalpel.forge.client;

import com.rinkynooble.scalpel.forge.content.Redacted;
import com.rinkynooble.scalpel.forge.content.RedactedBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A resource pack built in memory at the top of the pack list. For every redacted id it provides a blockstate and an
 * item model pointing at Scalpel's shared model, so the original mod's models are never loaded or baked.
 */
public final class PlaceholderPack implements PackResources {
    public static final String ID = "scalpel_placeholders";

    private static final byte[] BLOCKSTATE = "{\"variants\":{\"\":{\"model\":\"scalpel:block/redacted\"}}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ITEM_MODEL = "{\"parent\":\"scalpel:item/redacted\"}".getBytes(StandardCharsets.UTF_8);

    private final Map<ResourceLocation, byte[]> files = new HashMap<>();
    private final Set<String> namespaces = new HashSet<>();

    public PlaceholderPack() {
        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (entry.getValue() instanceof RedactedBlock) {
                ResourceLocation id = entry.getKey().location();
                add(new ResourceLocation(id.getNamespace(), "blockstates/" + id.getPath() + ".json"), BLOCKSTATE);
            }
        }
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
            if (entry.getValue() instanceof Redacted) {
                ResourceLocation id = entry.getKey().location();
                add(new ResourceLocation(id.getNamespace(), "models/item/" + id.getPath() + ".json"), ITEM_MODEL);
            }
        }
    }

    private void add(ResourceLocation location, byte[] data) {
        files.put(location, data);
        namespaces.add(location.getNamespace());
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    /** True if this pack provides the blockstate file at {@code location} (a placeholder block). */
    public boolean providesBlockstate(ResourceLocation location) {
        return files.containsKey(location);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
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
        for (Map.Entry<ResourceLocation, byte[]> file : files.entrySet()) {
            ResourceLocation location = file.getKey();
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
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("Scalpel placeholders"), 15);
        }
        return null;
    }

    @Override
    public String packId() {
        return ID;
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public void close() {
    }
}

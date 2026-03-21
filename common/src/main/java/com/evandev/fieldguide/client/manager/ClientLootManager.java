package com.evandev.fieldguide.client.manager;

import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.GuideEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ClientLootManager {
    private static final ClientLootManager INSTANCE = new ClientLootManager();

    private final Map<Object, List<ItemStack>> dropCache = new HashMap<>();

    private ClientLootManager() {
    }

    public static ClientLootManager getInstance() {
        return INSTANCE;
    }

    public void updateLootCache(Map<ResourceLocation, List<ItemStack>> lootCache, boolean clearCache) {
        if (clearCache) {
            this.dropCache.clear();
        }

        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : lootCache.entrySet()) {
            ResourceLocation id = entry.getKey();
            List<ItemStack> drops = entry.getValue();

            String namespace = id.getNamespace();
            if (namespace.equals("item") || namespace.equals("entity") || namespace.equals("block") || namespace.equals("fieldguide") || namespace.equals("cobblemon")) {
                dropCache.put(id, drops);
            }
        }
    }

    public List<ItemStack> getDrops(Object entry) {
        List<ItemStack> rawDrops = new ArrayList<>();
        if (entry instanceof GuideEntry ge && ge.isComposite()) {
            Set<Object> uniqueComponents = new HashSet<>();
            if (ge.displayId() != null) {
                BuiltInRegistries.BLOCK.getOptional(ge.displayId()).ifPresent(uniqueComponents::add);
                BuiltInRegistries.ITEM.getOptional(ge.displayId()).ifPresent(uniqueComponents::add);
                BuiltInRegistries.ENTITY_TYPE.getOptional(ge.displayId()).ifPresent(uniqueComponents::add);
            }
            if (ge.childEntries() != null) {
                for (ResourceLocation compId : ge.childEntries()) {
                    BuiltInRegistries.BLOCK.getOptional(compId).ifPresent(uniqueComponents::add);
                    BuiltInRegistries.ITEM.getOptional(compId).ifPresent(uniqueComponents::add);
                    BuiltInRegistries.ENTITY_TYPE.getOptional(compId).ifPresent(uniqueComponents::add);
                }
            }
            for (Object comp : uniqueComponents) {
                ResourceLocation id = AutoPopulateRegistry.getEntryId(comp, true);
                rawDrops.addAll(dropCache.getOrDefault(id, Collections.emptyList()));
            }
        } else {
            ResourceLocation id = AutoPopulateRegistry.getEntryId(entry, true);
            rawDrops.addAll(dropCache.getOrDefault(id, Collections.emptyList()));
        }

        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack stack : rawDrops) {
            if (distinct.stream().noneMatch(s -> isSameLootItem(s, stack))) {
                distinct.add(stack);
            }
        }
        return distinct;
    }

    private boolean isSameLootItem(ItemStack a, ItemStack b) {
        if (!ItemStack.isSameItem(a, b)) return false;
        if (a.getTag() == b.getTag()) return true;
        if (a.getTag() == null || b.getTag() == null) return false;

        CompoundTag tagA = a.getTag().copy();
        tagA.remove("FieldGuideDropChance");
        tagA.remove("FieldGuideMin");
        tagA.remove("FieldGuideMax");

        CompoundTag tagB = b.getTag().copy();
        tagB.remove("FieldGuideDropChance");
        tagB.remove("FieldGuideMin");
        tagB.remove("FieldGuideMax");

        return tagA.equals(tagB);
    }

    public Map<Object, List<ItemStack>> getDropCache() {
        return dropCache;
    }
}

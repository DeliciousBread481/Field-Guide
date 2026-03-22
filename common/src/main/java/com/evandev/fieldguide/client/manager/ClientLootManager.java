package com.evandev.fieldguide.client.manager;

import com.evandev.fieldguide.ModDataComponents;
import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.GuideEntry;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.network.RequestLootPacket;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ClientLootManager {
    private static final ClientLootManager INSTANCE = new ClientLootManager();

    private final Map<Object, List<ItemStack>> dropCache = new HashMap<>();
    private final Set<ResourceLocation> requestedIds = new HashSet<>();

    private ClientLootManager() {
    }

    public static ClientLootManager getInstance() {
        return INSTANCE;
    }

    public void clear() {
        this.dropCache.clear();
        this.requestedIds.clear();
    }

    public void updateLootCache(Map<ResourceLocation, List<ItemStack>> lootCache, boolean clearCache) {
        if (clearCache) {
            this.clear();
        }

        if (lootCache != null) {
            for (Map.Entry<ResourceLocation, List<ItemStack>> entry : lootCache.entrySet()) {
                ResourceLocation id = entry.getKey();
                List<ItemStack> drops = entry.getValue();
                dropCache.put(id, drops);
            }
        }
    }

    public void requestLoot(ResourceLocation entryId) {
        if (entryId != null && !requestedIds.contains(entryId)) {
            requestedIds.add(entryId);
            Services.NETWORK.sendToServer(new RequestLootPacket(entryId));
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
                if (id != null) {
                    if (dropCache.containsKey(id)) {
                        rawDrops.addAll(dropCache.get(id));
                    } else {
                        requestLoot(id);
                    }
                }
            }
        } else {
            ResourceLocation id = AutoPopulateRegistry.getEntryId(entry, true);
            if (id != null) {
                if (dropCache.containsKey(id)) {
                    rawDrops.addAll(dropCache.get(id));
                } else {
                    requestLoot(id);
                }
            }
        }

        if (Services.PLATFORM.isModLoaded("cobblemon")) {
            rawDrops.addAll(FieldGuideCobblemonCompat.getCobblemonDrops(entry));
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

        ItemStack copyA = a.copy();
        copyA.remove(ModDataComponents.DROP_CHANCE.get());
        copyA.remove(ModDataComponents.MIN_DROP.get());
        copyA.remove(ModDataComponents.MAX_DROP.get());

        ItemStack copyB = b.copy();
        copyB.remove(ModDataComponents.DROP_CHANCE.get());
        copyB.remove(ModDataComponents.MIN_DROP.get());
        copyB.remove(ModDataComponents.MAX_DROP.get());

        return ItemStack.isSameItemSameComponents(copyA, copyB);
    }

    public Map<Object, List<ItemStack>> getDropCache() {
        return dropCache;
    }
}

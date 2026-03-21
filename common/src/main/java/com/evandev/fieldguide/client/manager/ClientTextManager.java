package com.evandev.fieldguide.client.manager;

import com.evandev.fieldguide.api.GuideEntry;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.entry.EntryResolver;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ClientTextManager {
    private static final ClientTextManager INSTANCE = new ClientTextManager();

    private ClientTextManager() {
    }

    public static ClientTextManager getInstance() {
        return INSTANCE;
    }

    public String getEntryDescription(Object entry) {
        ResourceLocation id = EntryResolver.getEntryId(entry, false);
        if (id == null) return "";
        String custom = ProgressManager.getInstance().getCustomDescription(entry);
        if (custom != null) return custom;

        if (id.getNamespace().equals("fieldguide") && id.getPath().startsWith("cobblemon/")) {
            String species = id.getPath().substring("cobblemon/".length());
            int underscore = species.lastIndexOf('_');
            if (underscore != -1) species = species.substring(0, underscore);

            String descKey = "cobblemon.species." + species + ".desc";
            if (I18n.exists(descKey)) return I18n.get(descKey);
        }

        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";
        if (I18n.exists(overrideKey)) return I18n.get(overrideKey);

        Object coreEntry = EntryResolver.resolveCoreEntry(entry);

        // Entity Descriptions
        if (coreEntry instanceof EntityType) {
            String entityKey = "entity." + id.getNamespace() + "." + id.getPath() + ".description";
            if (I18n.exists(entityKey)) return I18n.get(entityKey);
        }

        // Quark JEI Hint
        String quarkJeiKey = "quark.jei.hint." + id.getPath();
        if (id.getNamespace().equals("quark") && I18n.exists(quarkJeiKey)) {
            return I18n.get(quarkJeiKey);
        }

        // Item Descriptions
        String loreKey = "lore." + id.getNamespace() + "." + id.getPath();
        if (I18n.exists(loreKey)) return I18n.get(loreKey);

        String fallbackKey = (coreEntry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        if (coreEntry instanceof Item) {
            fallbackKey = "item." + id.getNamespace() + "." + id.getPath() + ".description";
        }
        return I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing");
    }

    public Component getDefaultNameComponent(Object entry) {
        ResourceLocation id = EntryResolver.getEntryId(entry, false);
        if (id != null) {
            if (id.getNamespace().equals("fieldguide") && id.getPath().startsWith("cobblemon/")) {
                String species = id.getPath().substring("cobblemon/".length());
                int underscore = species.lastIndexOf('_');
                if (underscore != -1) species = species.substring(0, underscore);

                String nameKey = "cobblemon.species." + species + ".name";
                if (I18n.exists(nameKey)) {
                    return Component.translatable(nameKey);
                }
            }

            String overrideKey = "fieldguide.name." + id.getNamespace() + "." + id.getPath();
            if (I18n.exists(overrideKey)) {
                return Component.translatable(overrideKey);
            }
        }

        Object coreEntry = EntryResolver.resolveCoreEntry(entry);

        if (entry instanceof GuideEntry ge && ge.isStructure() && id != null && id.getPath().endsWith("_tree")) {
            if (coreEntry instanceof Block block) {
                String saplingName = block.getName().getString();
                return Component.literal(saplingName.replace("Sapling", "Tree"));
            }
        }

        if (coreEntry instanceof EntityType<?> type) return type.getDescription();
        if (coreEntry instanceof Block block) return block.getName();
        if (coreEntry instanceof Item item) return item.getDescription();

        return Component.translatable("fieldguide.unknown");
    }

    public void setCustomDescription(Object entry, String desc) {
        ProgressManager.getInstance().setCustomDescription(entry, desc);
    }

    public void setCustomName(Object entry, String name) {
        ProgressManager.getInstance().setCustomName(entry, name);
    }

    public Component getEntryName(Object entry) {
        String custom = ProgressManager.getInstance().getCustomName(entry);
        if (custom != null) return Component.literal(custom);

        return getDefaultNameComponent(entry);
    }

    public String getDefaultName(Object entry) {
        return getDefaultNameComponent(entry).getString();
    }

    public String getJournalTitle() {
        return ProgressManager.getInstance().getJournalTitle();
    }

    public void setJournalTitle(String title) {
        ProgressManager.getInstance().setJournalTitle(title);
    }
}

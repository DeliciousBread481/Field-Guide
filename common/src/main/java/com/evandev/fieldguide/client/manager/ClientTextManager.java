package com.evandev.fieldguide.client.manager;

import com.evandev.fieldguide.api.CompositeFieldGuideEntry;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.util.EntryResolver;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class ClientTextManager {
    private static final ClientTextManager INSTANCE = new ClientTextManager();

    private ClientTextManager() {
    }

    public static ClientTextManager getInstance() {
        return INSTANCE;
    }

    public String getEntryDescription(Object entry) {
        ResourceLocation id = EntryResolver.getEntryId(entry);
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

        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;
        String fallbackKey = (coreEntry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(overrideKey) ? I18n.get(overrideKey) : (I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing"));
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

    public Component getDefaultNameComponent(Object entry) {
        ResourceLocation id = EntryResolver.getEntryId(entry);
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

        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if (entry instanceof CompositeFieldGuideEntry && id != null && id.getPath().endsWith("_tree")) {
            if (coreEntry instanceof Block block) {
                String saplingName = block.getName().getString();
                return Component.literal(saplingName.replace("Sapling", "Tree"));
            }
        }

        if (coreEntry instanceof EntityType<?> type) return type.getDescription();
        if (coreEntry instanceof Block block) return block.getName();

        return Component.translatable("fieldguide.unknown");
    }

    public String getJournalTitle() {
        return ProgressManager.getInstance().getJournalTitle();
    }

    public void setJournalTitle(String title) {
        ProgressManager.getInstance().setJournalTitle(title);
    }
}

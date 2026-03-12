package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.data.CategoryType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncCategoriesPacket implements CustomPacketPayload {

    public static final Type<SyncCategoriesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_categories"));

    public static final StreamCodec<FriendlyByteBuf, SyncCategoriesPacket> CODEC = StreamCodec.ofMember(
            SyncCategoriesPacket::encode,
            SyncCategoriesPacket::new
    );

    private final List<Category> categories;
    private final List<String> biomeAdditions;
    private final List<String> biomeRemovals;
    private final List<String> lootAdditions;
    private final List<String> lootRemovals;
    private final Map<ResourceLocation, ResourceLocation> redirects;
    private final boolean clearCache;
    private final boolean resolveEntries;

    public SyncCategoriesPacket(
            List<Category> categories,
            List<String> biomeAdditions,
            List<String> biomeRemovals,
            List<String> lootAdditions,
            List<String> lootRemovals,
            Map<ResourceLocation, ResourceLocation> redirects,
            boolean clearCache,
            boolean resolveEntries
    ) {
        this.categories = categories;
        this.biomeAdditions = biomeAdditions;
        this.biomeRemovals = biomeRemovals;
        this.lootAdditions = lootAdditions;
        this.lootRemovals = lootRemovals;
        this.redirects = redirects;
        this.clearCache = clearCache;
        this.resolveEntries = resolveEntries;
    }

    public SyncCategoriesPacket(FriendlyByteBuf buf) {
        this.categories = buf.readList(b -> {
            ResourceLocation id = b.readResourceLocation();
            Category cat = new Category(id);
            cat.setSortIndex(b.readInt());
            b.readOptional(FriendlyByteBuf::readResourceLocation).ifPresent(cat::setIcon);
            cat.setGroupByQueries(b.readList(FriendlyByteBuf::readUtf));

            List<CategoryEntry> entries = b.readList(eb -> new CategoryEntry(
                    eb.readEnum(CategoryType.Type.class),
                    eb.readNullable(FriendlyByteBuf::readResourceLocation),
                    eb.readNullable(FriendlyByteBuf::readResourceLocation),
                    eb.readNullable(FriendlyByteBuf::readUtf),
                    eb.readNullable(nb -> nb.readList(FriendlyByteBuf::readResourceLocation)),
                    eb.readNullable(FriendlyByteBuf::readResourceLocation),
                    eb.readNullable(nb -> nb.readList(FriendlyByteBuf::readUtf))
            ));
            entries.forEach(cat::addEntry);
            return cat;
        });

        this.biomeAdditions = buf.readList(FriendlyByteBuf::readUtf);
        this.biomeRemovals = buf.readList(FriendlyByteBuf::readUtf);
        this.lootAdditions = buf.readList(FriendlyByteBuf::readUtf);
        this.lootRemovals = buf.readList(FriendlyByteBuf::readUtf);
        this.redirects = buf.readMap(HashMap::new, b -> b.readResourceLocation(), b -> b.readResourceLocation());
        this.clearCache = buf.readBoolean();
        this.resolveEntries = buf.readBoolean();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(categories, (b, cat) -> {
            b.writeResourceLocation(cat.getId());
            b.writeInt(cat.getSortIndex());
            b.writeNullable(cat.getIcon(), FriendlyByteBuf::writeResourceLocation);
            b.writeCollection(cat.getGroupByQueries() != null ? cat.getGroupByQueries() : List.of(), FriendlyByteBuf::writeUtf);

            b.writeCollection(cat.getEntries() != null ? cat.getEntries() : List.of(), (eb, entry) -> {
                eb.writeEnum(entry.type());
                eb.writeNullable(entry.id(), FriendlyByteBuf::writeResourceLocation);
                eb.writeNullable(entry.displayId(), FriendlyByteBuf::writeResourceLocation);
                eb.writeNullable(entry.strategy(), FriendlyByteBuf::writeUtf);
                eb.writeNullable(entry.components(), (nb, comps) -> nb.writeCollection(comps, FriendlyByteBuf::writeResourceLocation));
                eb.writeNullable(entry.structureNbt(), FriendlyByteBuf::writeResourceLocation);
                eb.writeNullable(entry.stackedBlocks(), (nb, blocks) -> nb.writeCollection(blocks, FriendlyByteBuf::writeUtf));
            });
        });

        buf.writeCollection(biomeAdditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(biomeRemovals, FriendlyByteBuf::writeUtf);
        buf.writeCollection(lootAdditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(lootRemovals, FriendlyByteBuf::writeUtf);
        buf.writeMap(redirects, (b, k) -> b.writeResourceLocation(k), (b, v) -> b.writeResourceLocation(v));
        buf.writeBoolean(clearCache);
        buf.writeBoolean(resolveEntries);
    }

    public List<Category> getCategories() {
        return categories;
    }

    public List<String> getBiomeAdditions() {
        return biomeAdditions;
    }

    public List<String> getBiomeRemovals() {
        return biomeRemovals;
    }

    public List<String> getLootAdditions() {
        return lootAdditions;
    }

    public List<String> getLootRemovals() {
        return lootRemovals;
    }

    public Map<ResourceLocation, ResourceLocation> getRedirects() {
        return redirects;
    }

    public boolean shouldClearCache() {
        return clearCache;
    }

    public boolean shouldResolveEntries() {
        return resolveEntries;
    }
}
package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.api.Category;
import com.evandev.fieldguide.api.GuideEntry;
import com.evandev.fieldguide.api.variant.DatapackVariant;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncCategoriesPacket implements CustomPacketPayload {

    public static final Type<SyncCategoriesPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_categories"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCategoriesPacket> CODEC = StreamCodec.ofMember(
            SyncCategoriesPacket::encode,
            SyncCategoriesPacket::new
    );

    private final List<Category> categories;
    private final List<GuideEntry> entries;
    private final List<String> biomeAdditions;
    private final List<String> biomeRemovals;
    private final List<String> lootAdditions;
    private final List<String> lootRemovals;
    private final Map<Identifier, Identifier> redirects;
    private final Map<Identifier, List<DatapackVariant>> variants;
    private final boolean clearCache;
    private final boolean resolveEntries;

    public SyncCategoriesPacket(
            List<Category> categories,
            List<GuideEntry> entries,
            List<String> biomeAdditions,
            List<String> biomeRemovals,
            List<String> lootAdditions,
            List<String> lootRemovals,
            Map<Identifier, Identifier> redirects,
            Map<Identifier, List<DatapackVariant>> variants,
            boolean clearCache,
            boolean resolveEntries
    ) {
        this.categories = categories;
        this.entries = entries;
        this.biomeAdditions = biomeAdditions;
        this.biomeRemovals = biomeRemovals;
        this.lootAdditions = lootAdditions;
        this.lootRemovals = lootRemovals;
        this.redirects = redirects;
        this.variants = variants;
        this.clearCache = clearCache;
        this.resolveEntries = resolveEntries;
    }

    public SyncCategoriesPacket(RegistryFriendlyByteBuf buf) {
        this.categories = Category.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
        this.entries = GuideEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
        this.biomeAdditions = buf.readList(FriendlyByteBuf::readUtf);
        this.biomeRemovals = buf.readList(FriendlyByteBuf::readUtf);
        this.lootAdditions = buf.readList(FriendlyByteBuf::readUtf);
        this.lootRemovals = buf.readList(FriendlyByteBuf::readUtf);
        this.redirects = buf.readMap(HashMap::new, FriendlyByteBuf::readIdentifier, FriendlyByteBuf::readIdentifier);
        this.variants = buf.readMap(HashMap::new, FriendlyByteBuf::readIdentifier, b -> b.readList(vb -> new DatapackVariant(vb.readUtf(), vb.readNbt())));
        this.clearCache = buf.readBoolean();
        this.resolveEntries = buf.readBoolean();
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        Category.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, categories);
        GuideEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, entries);

        buf.writeCollection(biomeAdditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(biomeRemovals, FriendlyByteBuf::writeUtf);
        buf.writeCollection(lootAdditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(lootRemovals, FriendlyByteBuf::writeUtf);
        buf.writeMap(redirects, FriendlyByteBuf::writeIdentifier, FriendlyByteBuf::writeIdentifier);
        buf.writeMap(variants, FriendlyByteBuf::writeIdentifier, (b, list) -> b.writeCollection(list, (vb, v) -> {
            vb.writeUtf(v.id());
            vb.writeNbt(v.nbt());
        }));
        buf.writeBoolean(clearCache);
        buf.writeBoolean(resolveEntries);
    }

    public List<Category> getCategories() {
        return categories;
    }

    public List<GuideEntry> getEntries() {
        return entries;
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

    public Map<Identifier, Identifier> getRedirects() {
        return redirects;
    }

    public Map<Identifier, List<DatapackVariant>> getVariants() {
        return variants;
    }

    public boolean shouldClearCache() {
        return clearCache;
    }

    public boolean shouldResolveEntries() {
        return resolveEntries;
    }
}
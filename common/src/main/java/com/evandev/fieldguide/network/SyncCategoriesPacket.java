package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.Category;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncCategoriesPacket(
        List<Category> categories,
        List<String> biomeAdditions,
        List<String> biomeRemovals,
        List<String> lootAdditions,
        List<String> lootRemovals,
        Map<ResourceLocation, ResourceLocation> redirects,
        boolean clearCache,
        boolean isLast
) implements CustomPacketPayload {

    public static final Type<SyncCategoriesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_categories"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCategoriesPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                Category.CODEC.apply(ByteBufCodecs.list()).encode(buf, p.categories());

                ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).encode(buf, p.biomeAdditions());
                ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).encode(buf, p.biomeRemovals());
                ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).encode(buf, p.lootAdditions());
                ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).encode(buf, p.lootRemovals());

                ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceLocation, ResourceLocation, Map<ResourceLocation, ResourceLocation>>map(
                        HashMap::new, ResourceLocation.STREAM_CODEC, ResourceLocation.STREAM_CODEC
                ).encode(buf, p.redirects());

                buf.writeBoolean(p.clearCache());
                buf.writeBoolean(p.isLast());
            },
            buf -> new SyncCategoriesPacket(
                    Category.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceLocation, ResourceLocation, Map<ResourceLocation, ResourceLocation>>map(
                            HashMap::new, ResourceLocation.STREAM_CODEC, ResourceLocation.STREAM_CODEC
                    ).decode(buf),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
package com.evandev.fieldguide.api;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record CategoryEntry(CategoryType.Type type, ResourceLocation id, ResourceLocation displayId, String strategy,
                            String virtualType, ResourceLocation icon,
                            List<ResourceLocation> components,
                            ResourceLocation structureNbt,
                            List<String> stackedBlocks,
                            EntryUnlockData unlockData) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CategoryEntry> CODEC = StreamCodec.of(
            (buf, entry) -> {
                CategoryType.Type.CODEC.encode(buf, entry.type());
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, Optional.ofNullable(entry.id()));
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, Optional.ofNullable(entry.displayId()));
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, Optional.ofNullable(entry.strategy()));
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).encode(buf, Optional.ofNullable(entry.virtualType()));
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, Optional.ofNullable(entry.icon()));

                buf.writeBoolean(entry.components() != null);
                if (entry.components() != null) {
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, entry.components());
                }

                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, Optional.ofNullable(entry.structureNbt()));

                buf.writeBoolean(entry.stackedBlocks() != null);
                if (entry.stackedBlocks() != null) {
                    ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).encode(buf, entry.stackedBlocks());
                }

                EntryUnlockData.STREAM_CODEC.encode(buf, entry.unlockData() != null ? entry.unlockData() : EntryUnlockData.DEFAULT);
            },
            buf -> new CategoryEntry(
                    CategoryType.Type.CODEC.decode(buf),
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf).orElse(null),
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf).orElse(null),
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf).orElse(null),
                    ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8).decode(buf).orElse(null),
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf).orElse(null),
                    buf.readBoolean() ? ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf) : null,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf).orElse(null),
                    buf.readBoolean() ? ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).decode(buf) : null,
                    EntryUnlockData.STREAM_CODEC.decode(buf)
            )
    );
}
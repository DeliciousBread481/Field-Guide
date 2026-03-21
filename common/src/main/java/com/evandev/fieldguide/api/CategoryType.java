package com.evandev.fieldguide.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

public class CategoryType {
    public enum Type {
        ENTRY, AUTO_POPULATE, COMPOSITE, VIRTUAL;

        private static final IntFunction<Type> BY_ID = ByIdMap.continuous(
                Type::ordinal,
                Type.values(),
                ByIdMap.OutOfBoundsStrategy.ZERO
        );

        public static final StreamCodec<ByteBuf, Type> CODEC = ByteBufCodecs.idMapper(
                BY_ID,
                Type::ordinal
        );
    }
}

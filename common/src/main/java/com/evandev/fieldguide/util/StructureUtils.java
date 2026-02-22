package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.mixin.accessor.StructureTemplateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StructureUtils {
    public static Map<BlockPos, BlockState> getStructureBlocks(CompositeFieldGuideEntry entry) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();

        if (entry.structureNbt() != null) {
            ResourceLocation nbtLocation = entry.structureNbt();
            ResourceLocation path = new ResourceLocation(nbtLocation.getNamespace(), "structures/" + nbtLocation.getPath() + ".nbt");

            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(path);
                if (res.isPresent()) {
                    CompoundTag tag = NbtIo.readCompressed(res.get().open());
                    StructureTemplate template = new StructureTemplate();
                    template.load(BuiltInRegistries.BLOCK.asLookup(), tag);

                    List<StructureTemplate.Palette> palettes = ((StructureTemplateAccessor) template).getPalettes();

                    if (!palettes.isEmpty()) {
                        for (StructureTemplate.StructureBlockInfo info : palettes.get(0).blocks()) {
                            blocks.put(info.pos(), info.state());
                        }
                    }
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to load structure NBT: {}", path, e);
            }
        }

        return blocks;
    }

    public static Map<BlockPos, BlockState> getStackedBlocks(List<String> stackedBlocks) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        int y = 0;
        for (String blockStr : stackedBlocks) {
            String[] parts = blockStr.split("\\|");
            ResourceLocation id = new ResourceLocation(parts[0]);
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != net.minecraft.world.level.block.Blocks.AIR) {
                BlockState state = block.defaultBlockState();
                if (parts.length > 1) {
                    String[] props = parts[1].split(",");
                    for (String propStr : props) {
                        String[] kv = propStr.split("=");
                        if (kv.length == 2) {
                            state = setProperty(state, kv[0], kv[1]);
                        }
                    }
                }
                blocks.put(new BlockPos(0, y, 0), state);
            }
            y++;
        }
        return blocks;
    }

    private static BlockState setProperty(BlockState state, String key, String value) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(key)) {
                return parseAndSet(state, prop, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState parseAndSet(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }
}
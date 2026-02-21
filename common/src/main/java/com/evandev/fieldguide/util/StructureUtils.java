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
import net.minecraft.world.level.block.state.BlockState;
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
}

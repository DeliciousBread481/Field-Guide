package com.evandev.fieldguide.server.command;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.network.ExportContentPacket;
import com.evandev.fieldguide.network.GrantContentPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public class FieldGuideCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fieldguide")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("export")
                        .then(Commands.literal("names").executes(ctx -> export(ctx.getSource(), "names")))
                        .then(Commands.literal("descriptions").executes(ctx -> export(ctx.getSource(), "descriptions")))
                        .then(Commands.literal("all").executes(ctx -> export(ctx.getSource(), "all")))
                        .then(Commands.literal("feature")
                                .then(Commands.argument("feature", ResourceLocationArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                ctx.getSource().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).keySet(), builder))

                                        .executes(ctx -> exportFeature(ctx.getSource(), ResourceLocationArgument.getId(ctx, "feature"), Blocks.DIRT))

                                        .then(Commands.argument("base_block", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        BuiltInRegistries.BLOCK.keySet(), builder))
                                                .executes(ctx -> exportFeature(
                                                        ctx.getSource(),
                                                        ResourceLocationArgument.getId(ctx, "feature"),
                                                        BuiltInRegistries.BLOCK.get(ResourceLocationArgument.getId(ctx, "base_block"))
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("everything")
                                        .executes(ctx -> grantEverything(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))
                                )
                                .then(Commands.literal("category")
                                        .then(Commands.argument("category", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ServerFieldGuideManager.getInstance().getCategories().keySet(), builder))
                                                .executes(ctx -> grantCategory(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), ResourceLocationArgument.getId(ctx, "category")))
                                        )
                                )
                                .then(Commands.literal("only")
                                        .then(Commands.argument("entry", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        Iterables.concat(BuiltInRegistries.ENTITY_TYPE.keySet(), BuiltInRegistries.BLOCK.keySet()),
                                                        builder))
                                                .executes(ctx -> grantEntry(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), ResourceLocationArgument.getId(ctx, "entry")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("everything")
                                        .executes(ctx -> revokeEverything(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))
                                )
                                .then(Commands.literal("category")
                                        .then(Commands.argument("category", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(ServerFieldGuideManager.getInstance().getCategories().keySet(), builder))
                                                .executes(ctx -> revokeCategory(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), ResourceLocationArgument.getId(ctx, "category")))
                                        )
                                )
                                .then(Commands.literal("only")
                                        .then(Commands.argument("entry", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        Iterables.concat(BuiltInRegistries.ENTITY_TYPE.keySet(), BuiltInRegistries.BLOCK.keySet()),
                                                        builder))
                                                .executes(ctx -> revokeEntry(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"), ResourceLocationArgument.getId(ctx, "entry")))
                                        )
                                )
                        )
                )
        );
    }

    private static int grantEverything(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.Type.EVERYTHING, null), player);
        }
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.grant.everything.success", targets.size()), true);
        return targets.size();
    }

    private static int grantCategory(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation categoryId) {
        Category category = ServerFieldGuideManager.getInstance().getCategories().get(categoryId);
        if (category == null) {
            source.sendFailure(Component.translatable("commands.fieldguide.category.not_found", categoryId));
            return 0;
        }
        for (ServerPlayer player : targets) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.Type.CATEGORY, categoryId), player);
        }
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.grant.category.success", categoryId, targets.size()), true);
        return targets.size();
    }

    private static int export(CommandSourceStack source, String type) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Services.NETWORK.sendToPlayer(new ExportContentPacket(type), player);
            source.sendSuccess(() -> Component.literal("Triggering export on client..."), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("This command must be run by an in-game player."));
            return 0;
        }
    }

    private static int exportFeature(CommandSourceStack source, ResourceLocation featureId, net.minecraft.world.level.block.Block baseBlock) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command must be run by an in-game player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        int y = Math.min(player.getBlockY() + 50, level.getMaxBuildHeight() - 40);
        BlockPos origin = new BlockPos(player.getBlockX(), y, player.getBlockZ());

        Registry<ConfiguredFeature<?, ?>> registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> feature = registry.get(featureId);

        if (feature == null) {
            source.sendFailure(Component.literal("Feature not found: " + featureId));
            return 0;
        }

        int radiusH = 16;
        int radiusV = 32;

        for (int dx = -radiusH; dx <= radiusH; dx++) {
            for (int dy = -2; dy <= radiusV; dy++) {
                for (int dz = -radiusH; dz <= radiusH; dz++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        level.setBlock(origin.below(), baseBlock.defaultBlockState(), 3);

        feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), origin);

        BlockPos min = null;
        BlockPos max = null;

        for (int dx = -radiusH; dx <= radiusH; dx++) {
            for (int dy = -2; dy <= radiusV; dy++) {
                for (int dz = -radiusH; dz <= radiusH; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).isAir() && !pos.equals(origin.below())) {
                        if (min == null) {
                            min = pos;
                            max = pos;
                        } else {
                            min = new BlockPos(Math.min(min.getX(), pos.getX()), Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
                            max = new BlockPos(Math.max(max.getX(), pos.getX()), Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
                        }
                    }
                }
            }
        }

        if (min == null) {
            source.sendFailure(Component.literal("Feature generated no blocks! It might require a different base block."));
        } else {
            StructureTemplate template = new StructureTemplate();
            BlockPos size = max.subtract(min).offset(1, 1, 1);
            template.fillFromWorld(level, min, size, false, Blocks.AIR);

            try {
                Path exportDir = Services.PLATFORM.getConfigDirectory().getParent()
                        .resolve("fieldguide_exports")
                        .resolve("assets")
                        .resolve(Constants.MOD_ID)
                        .resolve("structures");
                Path filePath = exportDir.resolve(featureId.getPath() + ".nbt");
                Files.createDirectories(filePath.getParent());

                try (OutputStream out = Files.newOutputStream(filePath)) {
                    CompoundTag tag = template.save(new CompoundTag());
                    NbtIo.writeCompressed(tag, out);
                    source.sendSuccess(() -> Component.literal("§aExported feature to " + filePath.toAbsolutePath()), true);
                }
            } catch (Exception e) {
                source.sendFailure(Component.literal("Failed to save feature: " + e.getMessage()));
            }
        }

        for (int dx = -radiusH; dx <= radiusH; dx++) {
            for (int dy = -2; dy <= radiusV; dy++) {
                for (int dz = -radiusH; dz <= radiusH; dz++) {
                    level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        return 1;
    }

    private static int reload(CommandSourceStack source) {
        ServerFieldGuideManager.getInstance().reload(source.getServer());
        source.sendSuccess(() -> Component.literal("FieldGuide configuration and caches reloaded!"), true);
        return 1;
    }

    private static int grantEntry(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation entryId) {
        for (ServerPlayer player : targets) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.Type.ENTRY, entryId), player);
        }
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.grant.entry.success", entryId), true);
        return targets.size();
    }

    private static int revokeEverything(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.REVOKE, GrantContentPacket.Type.EVERYTHING, null), player);
        }
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.revoke.everything.success"), true);
        return targets.size();
    }

    private static int revokeCategory(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation categoryId) {
        for (ServerPlayer player : targets) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.REVOKE, GrantContentPacket.Type.CATEGORY, categoryId), player);
        }
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.revoke.category.success", categoryId, targets.size()), true);
        return targets.size();
    }

    private static int revokeEntry(CommandSourceStack source, Collection<ServerPlayer> targets, ResourceLocation entryId) {
        for (ServerPlayer player : targets) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.REVOKE, GrantContentPacket.Type.ENTRY, entryId), player);
        }
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.revoke.entry.success", entryId), true);
        return targets.size();
    }
}
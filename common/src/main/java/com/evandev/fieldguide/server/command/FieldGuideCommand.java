package com.evandev.fieldguide.server.command;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class FieldGuideCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fieldguide")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("export")
                        .then(Commands.literal("names").executes(ctx -> export(ctx.getSource(), "names")))
                        .then(Commands.literal("descriptions").executes(ctx -> export(ctx.getSource(), "descriptions")))
                        .then(Commands.literal("all").executes(ctx -> export(ctx.getSource(), "all")))
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
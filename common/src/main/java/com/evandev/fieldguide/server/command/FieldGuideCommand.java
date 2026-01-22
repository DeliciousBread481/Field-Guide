package com.evandev.fieldguide.server.command;

import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

public class FieldGuideCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fieldguide")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("everything")
                                        .executes(ctx -> grantEverything(ctx.getSource()))
                                )
                                .then(Commands.literal("category")
                                        .then(Commands.argument("category", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(FieldGuideDataManager.getCategories().keySet(), builder))
                                                .executes(ctx -> grantCategory(ctx.getSource(), ResourceLocationArgument.getId(ctx, "category")))
                                        )
                                )
                                .then(Commands.literal("only")
                                        .then(Commands.argument("entry", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        FieldGuideDataManager.getValidEntries().stream().map(FieldGuideDataManager::getEntryId),
                                                        builder))
                                                .executes(ctx -> grantEntry(ctx.getSource(), ResourceLocationArgument.getId(ctx, "entry")))
                                        )
                                )
                        )
                )
                .then(Commands.literal("revoke")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.literal("everything")
                                        .executes(ctx -> revokeEverything(ctx.getSource()))
                                )
                                .then(Commands.literal("category")
                                        .then(Commands.argument("category", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(FieldGuideDataManager.getCategories().keySet(), builder))
                                                .executes(ctx -> revokeCategory(ctx.getSource(), ResourceLocationArgument.getId(ctx, "category")))
                                        )
                                )
                                .then(Commands.literal("only")
                                        .then(Commands.argument("entry", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        FieldGuideDataManager.getValidEntries().stream().map(FieldGuideDataManager::getEntryId),
                                                        builder))
                                                .executes(ctx -> revokeEntry(ctx.getSource(), ResourceLocationArgument.getId(ctx, "entry")))
                                        )
                                )
                        )
                )
        );
    }

    private static int grantEverything(CommandSourceStack source) {
        FieldGuideDataManager manager = FieldGuideDataManager.getInstance();
        int count = 0;
        for (Object entry : FieldGuideDataManager.getValidEntries()) {
            manager.unlock(entry, false);
            count++;
        }
        int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.grant.everything.success", finalCount), true);
        return count;
    }

    private static int grantCategory(CommandSourceStack source, ResourceLocation categoryId) {
        Category category = FieldGuideDataManager.getCategories().get(categoryId);
        if (category == null) {
            source.sendFailure(Component.translatable("commands.fieldguide.category.not_found", categoryId));
            return 0;
        }

        int count = 0;
        for (Object entry : category.getEntries()) {
            FieldGuideDataManager.getInstance().unlock(entry, false);
            count++;
        }
        int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.grant.category.success", categoryId, finalCount), true);
        return count;
    }

    private static int grantEntry(CommandSourceStack source, ResourceLocation entryId) {
        Object entry = resolveEntry(entryId);
        if (entry != null) {
            FieldGuideDataManager.getInstance().unlock(entry, true);
            source.sendSuccess(() -> Component.translatable("commands.fieldguide.grant.entry.success", entryId), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.fieldguide.entry.not_found", entryId));
            return 0;
        }
    }

    private static int revokeEverything(CommandSourceStack source) {
        FieldGuideDataManager.getInstance().revokeAll();
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.revoke.everything.success"), true);
        return 1;
    }

    private static int revokeCategory(CommandSourceStack source, ResourceLocation categoryId) {
        Category category = FieldGuideDataManager.getCategories().get(categoryId);
        if (category == null) {
            source.sendFailure(Component.translatable("commands.fieldguide.category.not_found", categoryId));
            return 0;
        }

        int count = 0;
        for (Object entry : category.getEntries()) {
            FieldGuideDataManager.getInstance().revoke(entry);
            count++;
        }
        int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.fieldguide.revoke.category.success", categoryId, finalCount), true);
        return count;
    }

    private static int revokeEntry(CommandSourceStack source, ResourceLocation entryId) {
        Object entry = resolveEntry(entryId);
        if (entry != null) {
            FieldGuideDataManager.getInstance().revoke(entry);
            source.sendSuccess(() -> Component.translatable("commands.fieldguide.revoke.entry.success", entryId), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.fieldguide.entry.not_found", entryId));
            return 0;
        }
    }

    private static Object resolveEntry(ResourceLocation id) {
        Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (entityType.isPresent()) return entityType.get();

        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        return block.orElse(null);
    }
}
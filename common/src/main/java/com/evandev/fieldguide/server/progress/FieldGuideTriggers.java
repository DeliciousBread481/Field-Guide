package com.evandev.fieldguide.server.progress;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.mixin.accessor.CriteriaTriggersAccessor;
import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class FieldGuideTriggers {
    public static final EntryUnlockedTrigger ENTRY_UNLOCKED =
            CriteriaTriggersAccessor.callRegister(new EntryUnlockedTrigger());

    public static final CategoryCompletedTrigger CATEGORY_COMPLETED =
            CriteriaTriggersAccessor.callRegister(new CategoryCompletedTrigger());

    public static void init() {
    }

    public static class EntryUnlockedTrigger extends SimpleCriterionTrigger<EntryUnlockedTrigger.Instance> {
        private static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "entry_unlocked");

        @Override
        public @NotNull ResourceLocation getId() {
            return ID;
        }

        @Override
        protected @NotNull Instance createInstance(JsonObject json, @NotNull ContextAwarePredicate playerPredicate, @NotNull DeserializationContext context) {
            ResourceLocation entryId = json.has("entry") ? new ResourceLocation(json.get("entry").getAsString()) : null;
            return new Instance(playerPredicate, entryId);
        }

        public void trigger(ServerPlayer player, ResourceLocation entryId) {
            this.trigger(player, instance -> instance.matches(entryId));
        }

        public static class Instance extends AbstractCriterionTriggerInstance {
            private final ResourceLocation entryId;

            public Instance(ContextAwarePredicate playerPredicate, ResourceLocation entryId) {
                super(ID, playerPredicate);
                this.entryId = entryId;
            }

            @Override
            public @NotNull JsonObject serializeToJson(@NotNull SerializationContext context) {
                JsonObject json = super.serializeToJson(context);
                if (this.entryId != null) {
                    json.addProperty("entry", this.entryId.toString());
                }
                return json;
            }

            public boolean matches(ResourceLocation entryId) {
                return this.entryId == null || this.entryId.equals(entryId);
            }
        }
    }

    public static class CategoryCompletedTrigger extends SimpleCriterionTrigger<CategoryCompletedTrigger.Instance> {
        private static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "category_completed");

        @Override
        public @NotNull ResourceLocation getId() {
            return ID;
        }

        @Override
        protected @NotNull Instance createInstance(JsonObject json, @NotNull ContextAwarePredicate playerPredicate, @NotNull DeserializationContext context) {
            ResourceLocation categoryId = json.has("category") ? new ResourceLocation(json.get("category").getAsString()) : null;
            return new Instance(playerPredicate, categoryId);
        }

        public void trigger(ServerPlayer player, ResourceLocation categoryId) {
            this.trigger(player, instance -> instance.matches(categoryId));
        }

        public static class Instance extends AbstractCriterionTriggerInstance {
            private final ResourceLocation categoryId;

            public Instance(ContextAwarePredicate playerPredicate, ResourceLocation categoryId) {
                super(ID, playerPredicate);
                this.categoryId = categoryId;
            }

            @Override
            public @NotNull JsonObject serializeToJson(@NotNull SerializationContext context) {
                JsonObject json = super.serializeToJson(context);
                if (this.categoryId != null) {
                    json.addProperty("category", this.categoryId.toString());
                }
                return json;
            }

            public boolean matches(ResourceLocation categoryId) {
                return this.categoryId == null || this.categoryId.equals(categoryId);
            }
        }
    }
}

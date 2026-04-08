package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class ItemFrameRecipeOverlay {

    private static float fadeProgress = 0.0f;
    private static float prevFadeProgress = 0.0f;
    private static float targetFade = 0.0f;
    private static ItemStack targetFoodItem = ItemStack.EMPTY;
    private static final List<List<ItemStack>> cachedIngredients = new ArrayList<>();

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ItemFrameRecipeOverlay::onClientTick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("tweaksdelight", "item_frame_recipe_overlay"), ItemFrameRecipeOverlay::onRenderGui);
    }

    private static void onClientTick(Minecraft mc) {
        if (!TweaksDelightConfig.CLIENT.enableItemFrameRecipeOverlay) {
            targetFade = 0.0f;
            fadeProgress = 0.0f;
            prevFadeProgress = 0.0f;
            targetFoodItem = ItemStack.EMPTY;
            return;
        }

        prevFadeProgress = fadeProgress;
        if (mc.level == null || mc.player == null) return;

        Entity crosshairTarget = mc.crosshairPickEntity;
        boolean lookingAtValidFrame = false;

        if (crosshairTarget instanceof ItemFrame itemFrame) {
            ItemStack framedItem = itemFrame.getItem();
            if (!framedItem.isEmpty() && isFoodItem(framedItem)) {
                if (!ItemStack.matches(framedItem, targetFoodItem)) {
                    targetFoodItem = framedItem.copy();
                    updateCachedRecipe(mc, framedItem);
                }
                if (!cachedIngredients.isEmpty()) {
                    lookingAtValidFrame = true;
                }
            }
        }

        if (lookingAtValidFrame) {
            targetFade = 1.0f;
        } else {
            targetFade = 0.0f;
        }

        if (targetFade > 0.0f) {
            fadeProgress = Math.min(1.0f, fadeProgress + 0.15f);
        } else {
            fadeProgress = Math.max(0.0f, fadeProgress - 0.15f);
            if (fadeProgress <= 0.0f) {
                targetFoodItem = ItemStack.EMPTY;
                cachedIngredients.clear();
            }
        }
    }

    private static void updateCachedRecipe(Minecraft mc, ItemStack target) {
        cachedIngredients.clear();

        if (mc.getSingleplayerServer() == null) return;
        RecipeManager rm = mc.getSingleplayerServer().getRecipeManager();

        List<RecipeHolder<?>> matchingRecipes = new ArrayList<>();

        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();

            List<RecipeDisplay> displays = recipe.display();
            if (displays.isEmpty()) continue;

            SlotDisplay resultDisplay = displays.get(0).result();
            List<ItemStack> resultStacks = resultDisplay.resolveForStacks(SlotDisplayContext.fromLevel(mc.level));

            if (!resultStacks.isEmpty()) {
                ItemStack result = resultStacks.get(0);
                if (result != null && !result.isEmpty() && ItemStack.isSameItem(result, target)) {
                    matchingRecipes.add(holder);
                }
            }
        }

        if (matchingRecipes.isEmpty()) return;

        RecipeHolder<?> selected = null;
        for (RecipeHolder<?> h : matchingRecipes) {
            String typeStr = h.value().getType().toString();
            if (typeStr.contains("cooking") || typeStr.contains("farmersdelight:cooking")) {
                selected = h;
                break;
            }
        }

        if (selected == null) {
            for (RecipeHolder<?> h : matchingRecipes) {
                String typeStr = h.value().getType().toString();
                if (typeStr.contains("cutting") || typeStr.contains("farmersdelight:cutting")) {
                    selected = h;
                    break;
                }
            }
        }

        if (selected == null) {
            selected = matchingRecipes.get(0);
        }

        for (Ingredient ing : selected.value().placementInfo().ingredients()) {
            if (ing.isEmpty()) continue;

            List<ItemStack> matchingStacks = new ArrayList<>();

            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack testStack = new ItemStack(item);
                if (ing.test(testStack)) {
                    matchingStacks.add(testStack);
                }
            }

            if (!matchingStacks.isEmpty()) {
                cachedIngredients.add(matchingStacks);
            }
        }
    }

    private static void onRenderGui(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!TweaksDelightConfig.CLIENT.enableItemFrameRecipeOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        float partialTicks = tickCounter.getGameTimeDeltaTicks();

        float lerpedFade = Mth.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || cachedIngredients.isEmpty() || targetFoodItem.isEmpty()) return;

        int itemsPerRow = Math.min(cachedIngredients.size(), 3);
        int rows = (int) Math.ceil((double) cachedIngredients.size() / itemsPerRow);

        int baseItemsWidth = itemsPerRow * 18;
        int outputWidth = 46;
        int boxWidth = Math.max(70, baseItemsWidth + outputWidth);
        int boxHeight = Math.max(40, rows * 18 + 22);

        int x = mc.getWindow().getGuiScaledWidth() / 2 + 15;
        int y = mc.getWindow().getGuiScaledHeight() / 2 - 20;

        context.pose().pushMatrix();

        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);

        context.pose().translate(translateX, translateY);
        context.pose().scale(scale, scale);
        context.pose().translate(-translateX, -translateY);

        int alpha = (int)(180 * lerpedFade);
        int bgColor = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        context.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha = (int)(255 * lerpedFade);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        context.text(mc.font, Component.translatable("gui.tweaksdelight.recipe"), x + 4, y + 4, textColor, false);

        int gridStartX = x + 4;
        int gridStartY = y + 17;

        for (int i = 0; i < cachedIngredients.size(); i++) {
            List<ItemStack> stacks = cachedIngredients.get(i);
            if (!stacks.isEmpty()) {
                int index = (int) ((mc.level.getGameTime() / 20) % stacks.size());
                int col = i % itemsPerRow;
                int row = i / itemsPerRow;
                context.item(stacks.get(index), gridStartX + col * 18, gridStartY + row * 18);
            }
        }

        int afterGridX = gridStartX + baseItemsWidth + 4;
        int centerY = gridStartY + (rows * 18) / 2 - 9;

        ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS);
        context.item(equalsItem, afterGridX, centerY);

        afterGridX += 20;
        context.item(targetFoodItem, afterGridX, centerY);
        context.pose().popMatrix();
    }

    private static boolean isFoodItem(ItemStack stack) {
        if (stack.has(DataComponents.FOOD)) return true;

        String id = stack.getItem().toString().toLowerCase();
        return id.contains("pie") || id.contains("stew") || id.contains("soup") ||
                id.contains("feast") || id.contains("cake") || id.contains("meal") ||
                id.contains("salad") || id.contains("potage") || id.contains("roast");
    }
}
package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class ItemFrameRecipeOverlay {

    private static float fadeProgress = 0.0f;
    private static float prevFadeProgress = 0.0f;
    private static float targetFade = 0.0f;
    private static ItemStack targetFoodItem = ItemStack.EMPTY;
    private static final List<Ingredient> cachedIngredients = new ArrayList<>();

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(ItemFrameRecipeOverlay::onClientTick);
        HudRenderCallback.EVENT.register(ItemFrameRecipeOverlay::onRenderGui);
    }

    private static void onClientTick(MinecraftClient mc) {
        if (!TweaksDelightConfig.CLIENT.enableItemFrameRecipeOverlay) {
            targetFade = 0.0f;
            fadeProgress = 0.0f;
            prevFadeProgress = 0.0f;
            targetFoodItem = ItemStack.EMPTY;
            return;
        }

        prevFadeProgress = fadeProgress;
        if (mc.world == null || mc.player == null) return;

        Entity crosshairEntity = mc.targetedEntity;
        boolean lookingAtValidFrame = false;
        ItemStack framedItem = ItemStack.EMPTY;

        if (crosshairEntity instanceof ItemFrameEntity itemFrame) {
            framedItem = itemFrame.getHeldItemStack();
        } else if (mc.crosshairTarget instanceof BlockHitResult blockHitResult) {
            BlockEntity blockEntity = mc.world.getBlockEntity(blockHitResult.getBlockPos());
            if (blockEntity != null && blockEntity.getClass().getName().contains("fastitemframes")) {
                try {
                    java.lang.reflect.Method getItemMethod = blockEntity.getClass().getMethod("getItem");
                    Object result = getItemMethod.invoke(blockEntity);
                    if (result instanceof ItemStack stack) {
                        framedItem = stack;
                    }
                } catch (Exception e) {
                    // Ignore reflection errors
                }
            }
        }

        if (!framedItem.isEmpty() && isFoodItem(framedItem)) {
            if (!ItemStack.areEqual(framedItem, targetFoodItem)) {
                targetFoodItem = framedItem.copy();
                updateCachedRecipe(mc, framedItem);
            }
            if (!cachedIngredients.isEmpty()) {
                lookingAtValidFrame = true;
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

    private static void updateCachedRecipe(MinecraftClient mc, ItemStack target) {
        cachedIngredients.clear();
        RecipeManager rm = mc.world.getRecipeManager();
        if (rm == null) return;

        List<RecipeEntry<?>> matchingRecipes = new ArrayList<>();

        for (RecipeEntry<?> holder : rm.values()) {
            ItemStack result = holder.value().getResult(mc.world.getRegistryManager());
            if (result != null && !result.isEmpty() && ItemStack.areItemsEqual(result, target)) {
                matchingRecipes.add(holder);
            }
        }

        if (matchingRecipes.isEmpty()) return;

        RecipeEntry<?> selected = null;
        for (RecipeEntry<?> h : matchingRecipes) {
            String typeStr = h.value().getType().toString();
            if (typeStr.contains("cooking") || typeStr.contains("farmersdelight:cooking")) {
                selected = h;
                break;
            }
        }

        if (selected == null) {
            for (RecipeEntry<?> h : matchingRecipes) {
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

        for (Ingredient ing : selected.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            cachedIngredients.add(ing);
        }
    }

    private static void onRenderGui(DrawContext context, RenderTickCounter tickCounter) {
        if (!TweaksDelightConfig.CLIENT.enableItemFrameRecipeOverlay) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        float partialTicks = tickCounter.getTickDelta(true);

        float lerpedFade = MathHelper.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || cachedIngredients.isEmpty() || targetFoodItem.isEmpty()) return;

        int itemsPerRow = Math.min(cachedIngredients.size(), 3);
        int rows = (int) Math.ceil((double) cachedIngredients.size() / itemsPerRow);

        int baseItemsWidth = itemsPerRow * 18;
        int outputWidth = 46;
        int boxWidth = Math.max(70, baseItemsWidth + outputWidth);
        int boxHeight = Math.max(40, rows * 18 + 22);

        int x = mc.getWindow().getScaledWidth() / 2 + 15;
        int y = mc.getWindow().getScaledHeight() / 2 - 20;

        context.getMatrices().push();

        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);

        context.getMatrices().translate(translateX, translateY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-translateX, -translateY, 0);

        int alpha = (int)(180 * lerpedFade);
        int bgColor = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        context.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha = (int)(255 * lerpedFade);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        context.drawText(mc.textRenderer, Text.translatable("gui.tweaksdelight.recipe"), x + 4, y + 4, textColor, false);

        int gridStartX = x + 4;
        int gridStartY = y + 17;

        for (int i = 0; i < cachedIngredients.size(); i++) {
            ItemStack[] stacks = cachedIngredients.get(i).getMatchingStacks();
            if (stacks.length > 0) {
                int index = (int) ((mc.world.getTime() / 20) % stacks.length);
                int col = i % itemsPerRow;
                int row = i / itemsPerRow;
                context.drawItem(stacks[index], gridStartX + col * 18, gridStartY + row * 18);
            }
        }

        int afterGridX = gridStartX + baseItemsWidth + 4;
        int centerY = gridStartY + (rows * 18) / 2 - 9;

        ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS);
        context.drawItem(equalsItem, afterGridX, centerY);

        afterGridX += 20;
        context.drawItem(targetFoodItem, afterGridX, centerY);
        context.getMatrices().pop();
    }

    private static boolean isFoodItem(ItemStack stack) {
        if (stack.contains(DataComponentTypes.FOOD)) return true;

        String id = stack.getItem().toString().toLowerCase();
        return id.contains("pie") || id.contains("stew") || id.contains("soup") ||
                id.contains("feast") || id.contains("cake") || id.contains("meal") ||
                id.contains("salad") || id.contains("potage") || id.contains("roast");
    }
}
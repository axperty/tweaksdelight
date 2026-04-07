package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.mixin.HandledScreenAccessor;
import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.List;

public class SmartIngredientHighlighting {

    private static Slot lastHoveredSlot = null;
    private static long hoverStartTime = 0;
    private static final List<ItemStack> requiredIngredients = new ArrayList<>();
    private static boolean highlightActive = false;
    private static ItemStack trackingItem = ItemStack.EMPTY;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, tickCounter) -> {
                    onScreenRender(handledScreen, context, mouseX, mouseY);
                });
            }
        });
    }

    private static void onScreenRender(HandledScreen<?> screen, DrawContext context, int mouseX, int mouseY) {
        if (!TweaksDelightConfig.CLIENT.enableSmartIngredientHighlighting) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        int guiLeft = ((HandledScreenAccessor) screen).getX();
        int guiTop = ((HandledScreenAccessor) screen).getY();

        Slot currentlyHovered = null;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (mouseX >= guiLeft + slot.x && mouseX < guiLeft + slot.x + 16 &&
                    mouseY >= guiTop + slot.y && mouseY < guiTop + slot.y + 16) {
                currentlyHovered = slot;
                break;
            }
        }

        if (currentlyHovered != null && currentlyHovered.hasStack() && Screen.hasShiftDown()) {
            ItemStack stack = currentlyHovered.getStack();
            if (stack.getItem().isFood()) {
                if (lastHoveredSlot != currentlyHovered || !ItemStack.areEqual(stack, trackingItem)) {
                    lastHoveredSlot = currentlyHovered;
                    trackingItem = stack.copy();
                    hoverStartTime = System.currentTimeMillis();
                    highlightActive = false;
                } else if (!highlightActive && System.currentTimeMillis() - hoverStartTime > TweaksDelightConfig.CLIENT.smartIngredientHighlightingDelay) {
                    highlightActive = true;
                    loadIngredients(mc, trackingItem);
                }
            } else {
                resetState();
            }
        } else {
            resetState();
        }

        if (highlightActive && !requiredIngredients.isEmpty()) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);

            for (Slot slot : screen.getScreenHandler().slots) {
                if (slot == currentlyHovered) continue;

                int x = guiLeft + slot.x;
                int y = guiTop + slot.y;

                if (!slot.hasStack()) {
                    context.fill(x, y, x + 16, y + 16, 0x88C6C6C6);
                    continue;
                }

                ItemStack slotItem = slot.getStack();
                boolean isIngredient = false;
                for (ItemStack req : requiredIngredients) {
                    if (ItemStack.areItemsEqual(slotItem, req)) {
                        isIngredient = true;
                        break;
                    }
                }

                if (!isIngredient) {
                    context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);
                }
            }

            context.getMatrices().pop();
        }
    }

    private static void resetState() {
        lastHoveredSlot = null;
        highlightActive = false;
        trackingItem = ItemStack.EMPTY;
        requiredIngredients.clear();
    }

    private static void loadIngredients(MinecraftClient mc, ItemStack target) {
        requiredIngredients.clear();
        RecipeManager rm = mc.world.getRecipeManager();
        if (rm == null) return;

        List<Recipe<?>> matchingRecipes = new ArrayList<>();

        for (Recipe<?> holder : rm.values()) {
            ItemStack result = holder.getOutput(mc.world.getRegistryManager());
            if (result != null && !result.isEmpty() && ItemStack.areItemsEqual(result, target)) {
                matchingRecipes.add(holder);
            }
        }

        if (matchingRecipes.isEmpty()) return;

        Recipe<?> selected = null;
        for (Recipe<?> h : matchingRecipes) {
            String typeStr = h.getType().toString();
            if (typeStr.contains("cooking") || typeStr.contains("farmersdelight:cooking")) {
                selected = h;
                break;
            }
        }

        if (selected == null) {
            for (Recipe<?> h : matchingRecipes) {
                String typeStr = h.getType().toString();
                if (typeStr.contains("cutting") || typeStr.contains("farmersdelight:cutting")) {
                    selected = h;
                    break;
                }
            }
        }

        if (selected == null) {
            selected = matchingRecipes.get(0);
        }

        for (Ingredient ing : selected.getIngredients()) {
            if (ing.isEmpty()) continue;
            for (ItemStack option : ing.getMatchingStacks()) {
                if (!option.isEmpty()) {
                    requiredIngredients.add(option.copy());
                }
            }
        }
    }
}
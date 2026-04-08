package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.mixin.AbstractContainerScreenAccessor;
import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.util.Mth;

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
            if (screen instanceof AbstractContainerScreen<?> handledScreen) {
                ScreenEvents.afterExtract(screen).register((s, context, mouseX, mouseY, tickCounter) -> {
                    onScreenRender(handledScreen, context, mouseX, mouseY);
                });
            }
        });
    }

    private static void onScreenRender(AbstractContainerScreen<?> screen, GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!TweaksDelightConfig.CLIENT.enableSmartIngredientHighlighting) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int guiLeft = ((AbstractContainerScreenAccessor) screen).getLeftPos();
        int guiTop = ((AbstractContainerScreenAccessor) screen).getTopPos();

        Slot currentlyHovered = null;
        for (Slot slot : screen.getMenu().slots) {
            if (mouseX >= guiLeft + slot.x && mouseX < guiLeft + slot.x + 16 &&
                    mouseY >= guiTop + slot.y && mouseY < guiTop + slot.y + 16) {
                currentlyHovered = slot;
                break;
            }
        }

        if (currentlyHovered != null && currentlyHovered.hasItem() && mc.hasShiftDown()) {
            ItemStack stack = currentlyHovered.getItem();
            if (stack.has(DataComponents.FOOD)) {
                if (lastHoveredSlot != currentlyHovered || !ItemStack.matches(stack, trackingItem)) {
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
            context.pose().pushMatrix();
            context.pose().translate(0.0f, 0.0f);

            for (Slot slot : screen.getMenu().slots) {
                if (slot == currentlyHovered) continue;

                int x = guiLeft + slot.x;
                int y = guiTop + slot.y;

                if (!slot.hasItem()) {
                    context.fill(x, y, x + 16, y + 16, 0x88C6C6C6);
                    continue;
                }

                ItemStack slotItem = slot.getItem();
                boolean isIngredient = false;
                for (ItemStack req : requiredIngredients) {
                    if (ItemStack.isSameItem(slotItem, req)) {
                        isIngredient = true;
                        break;
                    }
                }

                if (!isIngredient) {
                    context.fill(x, y, x + 16, y + 16, 0xAA8B8B8B);
                }
            }

            context.pose().popMatrix();
        }
    }

    private static void resetState() {
        lastHoveredSlot = null;
        highlightActive = false;
        trackingItem = ItemStack.EMPTY;
        requiredIngredients.clear();
    }

    private static void loadIngredients(Minecraft mc, ItemStack target) {
        requiredIngredients.clear();

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

            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack testStack = new ItemStack(item);
                if (ing.test(testStack)) {
                    requiredIngredients.add(testStack.copy());
                }
            }
        }
    }
}
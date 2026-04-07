package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.TweaksDelight;
import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = TweaksDelight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SmartIngredientHighlighting {

    private static Slot lastHoveredSlot = null;
    private static long hoverStartTime = 0;
    private static final List<ItemStack> requiredIngredients = new ArrayList<>();
    private static boolean highlightActive = false;
    private static ItemStack trackingItem = ItemStack.EMPTY;

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!TweaksDelightConfig.CLIENT.enableSmartIngredientHighlighting.get()) return;

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            resetState();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int guiLeft = screen.getGuiLeft();
        int guiTop = screen.getGuiTop();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        Slot currentlyHovered = null;
        for (Slot slot : screen.getMenu().slots) {
            if (mouseX >= guiLeft + slot.x && mouseX < guiLeft + slot.x + 16 &&
                mouseY >= guiTop + slot.y && mouseY < guiTop + slot.y + 16) {
                currentlyHovered = slot;
                break;
            }
        }

        if (currentlyHovered != null && currentlyHovered.hasItem() && Screen.hasShiftDown()) {
            ItemStack stack = currentlyHovered.getItem();
            if (stack.getItem().isEdible()) {
                if (lastHoveredSlot != currentlyHovered || !ItemStack.isSameItemSameTags(stack, trackingItem)) {
                    lastHoveredSlot = currentlyHovered;
                    trackingItem = stack.copy();
                    hoverStartTime = System.currentTimeMillis();
                    highlightActive = false;
                } else if (!highlightActive && System.currentTimeMillis() - hoverStartTime > TweaksDelightConfig.CLIENT.smartIngredientHighlightingDelay.get()) {
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
            GuiGraphics graphics = event.getGuiGraphics();
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);

            for (Slot slot : screen.getMenu().slots) {
                if (slot == currentlyHovered) continue;

                int x = guiLeft + slot.x;
                int y = guiTop + slot.y;

                if (!slot.hasItem()) {
                    graphics.fill(x, y, x + 16, y + 16, 0x88C6C6C6);
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
                    graphics.fill(x, y, x + 16, y + 16, 0xAA8B8B8B); 
                }
            }

            graphics.pose().popPose();
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
        RecipeManager rm = mc.level.getRecipeManager();
        if (rm == null) return;

        List<Recipe<?>> matchingRecipes = new ArrayList<>();
        
        for (Recipe<?> recipe : rm.getRecipes()) {
            ItemStack result = recipe.getResultItem(mc.level.registryAccess());
            if (result != null && !result.isEmpty() && ItemStack.isSameItem(result, target)) {
                matchingRecipes.add(recipe);
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
            for (ItemStack option : ing.getItems()) {
                if (!option.isEmpty()) {
                    requiredIngredients.add(option.copy());
                }
            }
        }
    }
}

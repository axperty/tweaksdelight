package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.TweaksDelight;
import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TweaksDelight.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class ItemFrameRecipeOverlay {

    private static float fadeProgress = 0.0f;
    private static float prevFadeProgress = 0.0f;
    private static float targetFade = 0.0f;
    private static ItemStack targetFoodItem = ItemStack.EMPTY;
    private static final List<Ingredient> cachedIngredients = new ArrayList<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!TweaksDelightConfig.CLIENT.enableItemFrameRecipeOverlay.get()) {
            targetFade = 0.0f;
            fadeProgress = 0.0f;
            prevFadeProgress = 0.0f;
            targetFoodItem = ItemStack.EMPTY;
            return;
        }

        prevFadeProgress = fadeProgress;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity crosshairTarget = mc.crosshairPickEntity;
        boolean lookingAtValidFrame = false;

        if (crosshairTarget instanceof ItemFrame itemFrame) {
            ItemStack framedItem = itemFrame.getItem();
            if (!framedItem.isEmpty() && framedItem.has(DataComponents.FOOD)) {
                lookingAtValidFrame = true;
                if (!ItemStack.isSameItemSameComponents(framedItem, targetFoodItem)) {
                    targetFoodItem = framedItem.copy();
                    updateCachedRecipe(mc, framedItem);
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
        RecipeManager rm = mc.level.getRecipeManager();
        if (rm == null) return;

        List<RecipeHolder<?>> matchingRecipes = new ArrayList<>();
        
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            ItemStack result = holder.value().getResultItem(mc.level.registryAccess());
            if (result != null && !result.isEmpty() && ItemStack.isSameItem(result, target)) {
                matchingRecipes.add(holder);
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

        for (Ingredient ing : selected.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            cachedIngredients.add(ing);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!TweaksDelightConfig.CLIENT.enableItemFrameRecipeOverlay.get()) return;

        Minecraft mc = Minecraft.getInstance();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        
        float lerpedFade = Mth.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || cachedIngredients.isEmpty() || targetFoodItem.isEmpty()) return;

        GuiGraphics graphics = event.getGuiGraphics();

        int baseItemsWidth = cachedIngredients.size() * 18;
        int outputWidth = 46;
        int boxWidth = Math.max(70, baseItemsWidth + outputWidth); 
        int boxHeight = 40; 
        
        int x = mc.getWindow().getGuiScaledWidth() / 2 + 15;
        int y = mc.getWindow().getGuiScaledHeight() / 2 - boxHeight / 2;

        graphics.pose().pushPose();
        
        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);
        
        graphics.pose().translate(translateX, translateY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-translateX, -translateY, 0);

        int alpha = (int)(180 * lerpedFade);
        int bgColor = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        graphics.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha = (int)(255 * lerpedFade);
        int textColor = (textAlpha << 24) | 0xFFFFFF; 
        graphics.drawString(mc.font, Component.translatable("gui.tweaksdelight.recipe"), x + 4, y + 4, textColor, false);

        int currentX = x + 4;
        int itemY = y + 17;
        
        for (int i = 0; i < cachedIngredients.size(); i++) {
            ItemStack[] stacks = cachedIngredients.get(i).getItems();
            if (stacks.length > 0) {
                int index = (int) ((mc.level.getGameTime() / 20) % stacks.length);
                graphics.renderItem(stacks[index], currentX, itemY);
            }
            currentX += 18;
        }

        currentX += 4;
        ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS.get());
        graphics.renderItem(equalsItem, currentX, itemY);
        
        currentX += 20;
        graphics.renderItem(targetFoodItem, currentX, itemY);
        graphics.pose().popPose();
    }
}

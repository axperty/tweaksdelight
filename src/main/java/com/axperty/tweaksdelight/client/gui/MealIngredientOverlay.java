package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

public class MealIngredientOverlay {

    private static float fadeProgress = 0.0f;
    private static float prevFadeProgress = 0.0f;
    private static float targetFade = 0.0f;
    private static ItemStack heldMeal = ItemStack.EMPTY;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(MealIngredientOverlay::onClientTick);
        HudRenderCallback.EVENT.register(MealIngredientOverlay::onRenderGui);
    }

    private static void onClientTick(MinecraftClient mc) {
        if (!TweaksDelightConfig.CLIENT.enableMealIngredientOverlay) {
            targetFade = 0.0f;
            fadeProgress = 0.0f;
            prevFadeProgress = 0.0f;
            heldMeal = ItemStack.EMPTY;
            return;
        }

        prevFadeProgress = fadeProgress;
        if (mc.world == null || mc.player == null) return;

        boolean shouldShow = false;

        if (mc.player.isSneaking()
                && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {

            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
            BlockEntity be = mc.world.getBlockEntity(blockHit.getBlockPos());

            if (be instanceof Inventory) {
                ItemStack held = mc.player.getMainHandStack();
                if (!isFoodItem(held)) held = mc.player.getOffHandStack();
                if (!isFoodItem(held)) held = ItemStack.EMPTY;
                if (!held.isEmpty()) {
                    heldMeal = held;
                    shouldShow = true;
                }
            }
        }

        if (shouldShow) {
            targetFade = 1.0f;
        } else {
            targetFade = 0.0f;
            if (targetFade <= 0.0f) heldMeal = ItemStack.EMPTY;
        }

        if (targetFade > 0.0f) {
            fadeProgress = Math.min(1.0f, fadeProgress + 0.15f);
        } else {
            fadeProgress = Math.max(0.0f, fadeProgress - 0.15f);
        }
    }

    private static boolean isFoodItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.contains(DataComponentTypes.FOOD)) return true;
        String id = stack.getItem().toString().toLowerCase();
        return id.contains("pie") || id.contains("stew") || id.contains("soup") ||
                id.contains("feast") || id.contains("cake") || id.contains("meal") ||
                id.contains("salad") || id.contains("potage") || id.contains("roast");
    }

    private static void onRenderGui(DrawContext context, RenderTickCounter tickCounter) {
        if (!TweaksDelightConfig.CLIENT.enableMealIngredientOverlay) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        float partialTicks = tickCounter.getDynamicDeltaTicks();
        float lerpedFade = MathHelper.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || heldMeal.isEmpty()) return;

        Text line1 = Text.translatable("gui.tweaksdelight.meal_grab");
        Text line2 = heldMeal.getName();

        int line1Width = mc.textRenderer.getWidth(line1);
        int line2Width = mc.textRenderer.getWidth(line2);
        int boxWidth   = Math.max(line1Width, line2Width) + 12;
        int boxHeight  = 28;

        int x = mc.getWindow().getScaledWidth() / 2 + 15;
        int y = mc.getWindow().getScaledHeight() / 2 - 20;

        context.getMatrices().pushMatrix();

        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);

        context.getMatrices().translate(translateX, translateY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-translateX, -translateY);

        int alpha       = (int)(180 * lerpedFade);
        int bgColor     = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        context.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha  = (int)(255 * lerpedFade);
        int grabColor  = (textAlpha << 24) | 0xFF8800;
        int nameColor  = (textAlpha << 24) | 0xFFFFFF;

        context.drawText(mc.textRenderer, line1, x + 6, y + 4,  grabColor, false);
        context.drawText(mc.textRenderer, line2, x + 6, y + 15, nameColor, false);

        context.getMatrices().popMatrix();
    }
}
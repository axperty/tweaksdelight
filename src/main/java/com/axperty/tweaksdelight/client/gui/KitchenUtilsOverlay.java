package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class KitchenUtilsOverlay {

    private static float fadeProgress = 0.0f;
    private static float prevFadeProgress = 0.0f;
    private static float targetFade = 0.0f;
    private static BlockPos targetBlockPos = null;
    private static final List<ItemStack> potIngredients = new ArrayList<>();
    private static int currentCookTime = 0;
    private static int totalCookTime = 0;
    private static boolean isSkillet = false;
    private static ItemStack predictedOutput = ItemStack.EMPTY;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(KitchenUtilsOverlay::onClientTick);
        HudRenderCallback.EVENT.register(KitchenUtilsOverlay::onRenderGui);
    }

    private static void onClientTick(MinecraftClient mc) {
        prevFadeProgress = fadeProgress;
        if (mc.world == null || mc.player == null) return;

        boolean lookingAtPot = false;

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = mc.world.getBlockEntity(pos);

            if (be != null) {
                Identifier beId = Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
                if (beId != null) {
                    String id = beId.toString();
                    if (id.equals("farmersdelight:cooking_pot") && TweaksDelightConfig.CLIENT.enableCookingPotOverlay ||
                            id.equals("farmersdelight:skillet") && TweaksDelightConfig.CLIENT.enableSkilletOverlay) {
                        lookingAtPot = true;
                        isSkillet = id.equals("farmersdelight:skillet");
                        if (targetBlockPos == null || !targetBlockPos.equals(pos) || mc.world.getTime() % 5 == 0) {
                            targetBlockPos = pos;
                            updatePotContents(mc, pos, be);
                        }
                    }
                }
            }
        }

        if (lookingAtPot) {
            targetFade = 1.0f;
        } else {
            targetFade = 0.0f;
        }

        if (targetFade > 0.0f) {
            fadeProgress = Math.min(1.0f, fadeProgress + 0.15f);
        } else {
            fadeProgress = Math.max(0.0f, fadeProgress - 0.15f);
            if (fadeProgress <= 0.0f) {
                targetBlockPos = null;
                potIngredients.clear();
                currentCookTime = 0;
                totalCookTime = 0;
                predictedOutput = ItemStack.EMPTY;
            }
        }
    }

    private static void updatePotContents(MinecraftClient mc, BlockPos pos, BlockEntity be) {
        potIngredients.clear();

        if (be instanceof Inventory container) {
            int limit = Math.min(6, container.size());
            for (int i = 0; i < limit; i++) {
                ItemStack stack = container.getStack(i);
                if (!stack.isEmpty()) {
                    potIngredients.add(stack.copy());
                }
            }
        }

        if (potIngredients.isEmpty()) {
            Direction[] dirs = new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            for (Direction d : dirs) {
                Storage<ItemVariant> storage = ItemStorage.SIDED.find(mc.world, pos, d);
                if (storage != null) {
                    int count = 0;
                    for (StorageView<ItemVariant> view : storage) {
                        if (!view.isResourceBlank()) {
                            potIngredients.add(view.getResource().toStack((int) view.getAmount()));
                        }
                        if (++count >= 6) break;
                    }
                    if (!potIngredients.isEmpty()) break;
                }
            }
        }

        try {
            NbtCompound nbt = be.createNbt();

            if (potIngredients.isEmpty() && nbt.contains("Inventory")) {
                NbtCompound invTag = nbt.getCompound("Inventory");
                if (invTag.contains("Items")) {
                    NbtList itemList = invTag.getList("Items", NbtElement.COMPOUND_TYPE);
                    for (int i = 0; i < itemList.size(); i++) {
                        NbtCompound itemTag = itemList.getCompound(i);
                        ItemStack parsed = ItemStack.fromNbt(itemTag);
                        if (!parsed.isEmpty()) potIngredients.add(parsed.copy());
                    }
                }
            }
            if (potIngredients.isEmpty() && isSkillet && nbt.contains("Ingredient")) {
                ItemStack parsed = ItemStack.fromNbt(nbt.getCompound("Ingredient"));
                if (!parsed.isEmpty()) potIngredients.add(parsed.copy());
            }

            if (nbt.contains("CookTime")) {
                currentCookTime = nbt.getInt("CookTime");
            }
            if (nbt.contains("CookTimeTotal")) {
                totalCookTime = nbt.getInt("CookTimeTotal");
            }
        } catch (Exception ignored) {
        }

        predictOutput(mc);
    }

    private static void predictOutput(MinecraftClient mc) {
        if (potIngredients.isEmpty()) {
            predictedOutput = ItemStack.EMPTY;
            return;
        }
        RecipeManager rm = mc.world.getRecipeManager();
        if (rm == null) return;

        for (Recipe<?> holder : rm.values()) {
            String typeStr = holder.getType().toString();
            if (isSkillet) {
                if (!typeStr.contains("campfire_cooking")) continue;
            } else {
                if (!typeStr.contains("farmersdelight:cooking")) continue;
            }

            boolean matches = true;
            for (Ingredient ing : holder.getIngredients()) {
                if (ing.isEmpty()) continue;
                boolean found = false;
                for (ItemStack in : potIngredients) {
                    if (ing.test(in)) { found = true; break; }
                }
                if (!found) { matches = false; break; }
            }

            if (matches) {
                predictedOutput = holder.getOutput(mc.world.getRegistryManager());
                return;
            }
        }
        predictedOutput = ItemStack.EMPTY;
    }

    private static void onRenderGui(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float partialTicks = tickDelta;
        float lerpedFade = MathHelper.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || potIngredients.isEmpty()) return;
        if (isSkillet && !TweaksDelightConfig.CLIENT.enableSkilletOverlay) return;
        if (!isSkillet && !TweaksDelightConfig.CLIENT.enableCookingPotOverlay) return;

        boolean hasOutput = !predictedOutput.isEmpty();
        int baseItemsWidth = potIngredients.size() * 18;
        int outputWidth = hasOutput ? 40 : 8;
        int boxWidth = Math.max(70, baseItemsWidth + outputWidth);
        int boxHeight = isSkillet ? 38 : 44;

        int x = mc.getWindow().getScaledWidth() / 2 - boxWidth / 2;
        int y = mc.getWindow().getScaledHeight() / 2 + 15;

        context.getMatrices().push();

        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);

        context.getMatrices().translate(translateX, translateY, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(-translateX, -translateY, 0);

        int alpha = (int)(200 * lerpedFade);
        int bgColor = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        context.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha = (int)(255 * lerpedFade);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        Text header = isSkillet ? Text.translatable("gui.tweaksdelight.skillet") : Text.translatable("gui.tweaksdelight.cooking_pot");

        context.drawText(mc.textRenderer, header, x + 4, y + 4, textColor, false);

        int currentX = x + 4;
        int itemY = y + 17;
        for (ItemStack ingredient : potIngredients) {
            context.drawItem(ingredient, currentX, itemY);
            currentX += 18;
        }

        if (hasOutput) {
            currentX += 1;

            ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS);
            context.drawItem(equalsItem, currentX, itemY);

            currentX += 20;
            context.drawItem(predictedOutput, currentX, itemY);
        }

        if (!isSkillet && totalCookTime > 0) {
            float progress = (float) currentCookTime / (float) totalCookTime;
            progress = MathHelper.clamp(progress, 0.0f, 1.0f);
            int barWidth = boxWidth - 8;
            int filledWidth = (int) (barWidth * progress);

            context.fill(x + 4, y + 37, x + 4 + barWidth, y + 39, (alpha << 24) | 0x333333);
            context.fill(x + 4, y + 37, x + 4 + filledWidth, y + 39, (alpha << 24) | 0xFF8800);
        }

        context.getMatrices().pop();
    }
}
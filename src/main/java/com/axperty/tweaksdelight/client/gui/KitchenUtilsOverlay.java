package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KitchenUtilsOverlay {

    private static float fadeProgress = 0.0f;
    private static float prevFadeProgress = 0.0f;
    private static float targetFade = 0.0f;
    private static BlockPos targetBlockPos = null;
    private static final List<ItemStack> potIngredients = new ArrayList<>();
    private static int currentCookTime = 0;
    private static int totalCookTime = 0;
    private static boolean isSkillet = false;
    private static ItemStack finishedOutput = ItemStack.EMPTY;
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
            BlockState state = mc.world.getBlockState(pos);
            Identifier blockId = Registries.BLOCK.getId(state.getBlock());

            if (blockId != null) {
                String id = blockId.toString();
                if ((id.equals("farmersdelight:cooking_pot") && TweaksDelightConfig.CLIENT.enableCookingPotOverlay) ||
                        (id.equals("farmersdelight:skillet") && TweaksDelightConfig.CLIENT.enableSkilletOverlay)) {

                    lookingAtPot = true;
                    isSkillet = id.equals("farmersdelight:skillet");

                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be != null && (targetBlockPos == null || !targetBlockPos.equals(pos) || mc.world.getTime() % 5 == 0)) {
                        targetBlockPos = pos;
                        updatePotContents(mc, pos, be);
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
                finishedOutput = ItemStack.EMPTY;
                predictedOutput = ItemStack.EMPTY;
            }
        }
    }

    private static void updatePotContents(MinecraftClient mc, BlockPos pos, BlockEntity be) {
        potIngredients.clear();
        finishedOutput = ItemStack.EMPTY;
        predictedOutput = ItemStack.EMPTY;

        if (be instanceof Inventory container) {
            int limit = Math.min(6, container.size());
            for (int i = 0; i < limit; i++) {
                ItemStack stack = container.getStack(i);
                if (!stack.isEmpty()) {
                    potIngredients.add(stack.copy());
                }
            }

            if (!isSkillet && container.size() >= 7) {
                ItemStack outputStack = container.getStack(6);
                if (!outputStack.isEmpty()) {
                    finishedOutput = outputStack.copy();
                }
            }
        }

        try {
            NbtCompound nbt = be.createNbt(mc.world.getRegistryManager());
            RegistryOps<NbtElement> ops = mc.world.getRegistryManager().getOps(NbtOps.INSTANCE);

            if (nbt.contains("Inventory")) {
                Optional<NbtCompound> invTagOpt = nbt.getCompound("Inventory");
                if (invTagOpt.isPresent()) {
                    Optional<NbtList> itemListOpt = invTagOpt.get().getList("Items");
                    if (itemListOpt.isPresent()) {
                        NbtList itemList = itemListOpt.get();
                        if (!itemList.isEmpty()) {
                            List<ItemStack> nbtIngredients = new ArrayList<>();
                            ItemStack nbtOutput = ItemStack.EMPTY;

                            for (int i = 0; i < itemList.size(); i++) {
                                Optional<NbtCompound> itemTagOpt = itemList.getCompound(i);
                                if (itemTagOpt.isPresent()) {
                                    NbtCompound itemTag = itemTagOpt.get();
                                    byte slot = itemTag.getByte("Slot", (byte) 0);
                                    ItemStack parsed = ItemStack.CODEC.parse(ops, itemTag).result().orElse(ItemStack.EMPTY);

                                    if (!parsed.isEmpty()) {
                                        if (isSkillet) {
                                            nbtIngredients.add(parsed.copy());
                                        } else {
                                            if (slot < 6) {
                                                nbtIngredients.add(parsed.copy());
                                            } else if (slot == 6) {
                                                nbtOutput = parsed.copy();
                                            }
                                        }
                                    }
                                }
                            }
                            potIngredients.clear();
                            potIngredients.addAll(nbtIngredients);
                            finishedOutput = nbtOutput;
                        }
                    }
                }
            }

            if (nbt.contains("CookTime")) {
                currentCookTime = nbt.getInt("CookTime", 0);
            }
            if (nbt.contains("CookTimeTotal")) {
                totalCookTime = nbt.getInt("CookTimeTotal", 0);
            }

        } catch (Exception ignored) {
        }

        if (finishedOutput.isEmpty() && !potIngredients.isEmpty()) {
            tryPredictRecipe(mc);
        }
    }

    private static void tryPredictRecipe(MinecraftClient mc) {
        if (mc.getServer() == null) return;

        ServerRecipeManager rm = mc.getServer().getRecipeManager();

        for (RecipeEntry<?> holder : rm.values()) {
            Recipe<?> recipe = holder.value();
            String typeStr = recipe.getType().toString();

            if (isSkillet) {
                if (!typeStr.contains("campfire_cooking")) continue;
            } else {
                if (!typeStr.contains("farmersdelight:cooking")) continue;
            }

            List<?> recipeIngredientsRaw = recipe.getIngredientPlacement().getIngredients();
            List<Ingredient> validRecipeIngredients = new ArrayList<>();

            for (Object ingObj : recipeIngredientsRaw) {
                Ingredient ing = null;
                if (ingObj instanceof Optional<?> opt) {
                    if (opt.isPresent() && opt.get() instanceof Ingredient) {
                        ing = (Ingredient) opt.get();
                    }
                } else if (ingObj instanceof Ingredient) {
                    ing = (Ingredient) ingObj;
                }

                if (ing != null && !ing.isEmpty()) {
                    validRecipeIngredients.add(ing);
                }
            }

            if (validRecipeIngredients.size() != potIngredients.size()) {
                continue;
            }

            boolean matches = true;
            List<ItemStack> availablePotItems = new ArrayList<>(potIngredients);

            for (Ingredient ing : validRecipeIngredients) {
                boolean found = false;
                for (int i = 0; i < availablePotItems.size(); i++) {
                    if (ing.test(availablePotItems.get(i))) {
                        availablePotItems.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    matches = false;
                    break;
                }
            }

            if (matches && availablePotItems.isEmpty()) {
                List<RecipeDisplay> displays = recipe.getDisplays();
                if (!displays.isEmpty()) {
                    List<ItemStack> results = displays.get(0).result().getStacks(SlotDisplayContexts.createParameters(mc.world));
                    if (!results.isEmpty()) {
                        predictedOutput = results.get(0).copy();
                        return;
                    }
                }
            }
        }
    }

    private static void onRenderGui(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float partialTicks = tickCounter.getDynamicDeltaTicks();
        float lerpedFade = MathHelper.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || potIngredients.isEmpty()) return;
        if (isSkillet && !TweaksDelightConfig.CLIENT.enableSkilletOverlay) return;
        if (!isSkillet && !TweaksDelightConfig.CLIENT.enableCookingPotOverlay) return;

        boolean hasFinalOutput = !finishedOutput.isEmpty();
        boolean hasPredictedOutput = !predictedOutput.isEmpty();
        boolean showOutputSection = hasFinalOutput || hasPredictedOutput;

        int baseItemsWidth = potIngredients.size() * 18;
        int outputWidth = showOutputSection ? 40 : 8;
        int boxWidth = Math.max(70, baseItemsWidth + outputWidth);
        int boxHeight = isSkillet ? 38 : 44;

        int x = mc.getWindow().getScaledWidth() / 2 - boxWidth / 2;
        int y = mc.getWindow().getScaledHeight() / 2 + 15;

        context.getMatrices().pushMatrix();

        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);

        context.getMatrices().translate(translateX, translateY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-translateX, -translateY);

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

        if (showOutputSection) {
            currentX += 1;
            ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS);
            context.drawItem(equalsItem, currentX, itemY);
            currentX += 20;

            ItemStack outputToRender = hasFinalOutput ? finishedOutput : predictedOutput;
            context.drawItem(outputToRender, currentX, itemY);

            if (outputToRender.getCount() > 1) {
                String countStr = String.valueOf(outputToRender.getCount());
                context.drawText(mc.textRenderer, countStr, currentX + 17 - mc.textRenderer.getWidth(countStr), itemY + 9, 0xFFFFFF, true);
            }
        }

        if (!isSkillet && totalCookTime > 0) {
            float progress = (float) currentCookTime / (float) totalCookTime;
            progress = MathHelper.clamp(progress, 0.0f, 1.0f);
            int barWidth = boxWidth - 8;
            int filledWidth = (int) (barWidth * progress);

            context.fill(x + 4, y + 37, x + 4 + barWidth, y + 39, (alpha << 24) | 0x333333);
            context.fill(x + 4, y + 37, x + 4 + filledWidth, y + 39, (alpha << 24) | 0xFF8800);
        }

        context.getMatrices().popMatrix();
    }
}
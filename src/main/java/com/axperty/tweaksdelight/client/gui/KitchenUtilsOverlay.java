package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

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
    private static int tickCount = 0;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(KitchenUtilsOverlay::onClientTick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("tweaksdelight", "kitchen_utils_overlay"), KitchenUtilsOverlay::onRenderGui);
    }

    private static void onClientTick(Minecraft mc) {
        tickCount++;
        prevFadeProgress = fadeProgress;
        if (mc.level == null || mc.player == null) return;

        boolean lookingAtPot = false;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());

            if (blockId != null) {
                String id = blockId.toString();
                if ((id.equals("farmersdelight:cooking_pot") && TweaksDelightConfig.CLIENT.enableCookingPotOverlay) ||
                        (id.equals("farmersdelight:skillet") && TweaksDelightConfig.CLIENT.enableSkilletOverlay)) {

                    lookingAtPot = true;
                    isSkillet = id.equals("farmersdelight:skillet");

                    BlockEntity be = mc.level.getBlockEntity(pos);
                    if (be != null && (targetBlockPos == null || !targetBlockPos.equals(pos) || tickCount % 5 == 0)) {
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

    private static void updatePotContents(Minecraft mc, BlockPos pos, BlockEntity be) {
        potIngredients.clear();
        finishedOutput = ItemStack.EMPTY;
        predictedOutput = ItemStack.EMPTY;

        if (be instanceof Container container) {
            int limit = Math.min(6, container.getContainerSize());
            for (int i = 0; i < limit; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    potIngredients.add(stack.copy());
                }
            }

            if (!isSkillet && container.getContainerSize() >= 7) {
                ItemStack outputStack = container.getItem(6);
                if (!outputStack.isEmpty()) {
                    finishedOutput = outputStack.copy();
                }
            }
        }

        try {
            CompoundTag nbt = be.saveWithoutMetadata(mc.level.registryAccess());
            RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);

            if (nbt.contains("Inventory")) {
                Optional<CompoundTag> invTagOpt = nbt.getCompound("Inventory");
                if (invTagOpt.isPresent()) {
                    Optional<ListTag> itemListOpt = invTagOpt.get().getList("Items");
                    if (itemListOpt.isPresent()) {
                        ListTag itemList = itemListOpt.get();
                        if (!itemList.isEmpty()) {
                            List<ItemStack> nbtIngredients = new ArrayList<>();
                            ItemStack nbtOutput = ItemStack.EMPTY;

                            for (int i = 0; i < itemList.size(); i++) {
                                Optional<CompoundTag> itemTagOpt = itemList.getCompound(i);
                                if (itemTagOpt.isPresent()) {
                                    CompoundTag itemTag = itemTagOpt.get();
                                    byte slot = itemTag.getByteOr("Slot", (byte) 0);
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
                currentCookTime = nbt.getIntOr("CookTime", 0);
            }
            if (nbt.contains("CookTimeTotal")) {
                totalCookTime = nbt.getIntOr("CookTimeTotal", 0);
            }

        } catch (Exception ignored) {
        }

        if (finishedOutput.isEmpty() && !potIngredients.isEmpty()) {
            tryPredictRecipe(mc);
        }
    }

    private static void tryPredictRecipe(Minecraft mc) {
        if (mc.getSingleplayerServer() == null) return;

        RecipeManager rm = mc.getSingleplayerServer().getRecipeManager();

        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            String typeStr = recipe.getType().toString();

            if (isSkillet) {
                if (!typeStr.contains("campfire_cooking")) continue;
            } else {
                if (!typeStr.contains("farmersdelight:cooking")) continue;
            }

            List<?> recipeIngredientsRaw = recipe.placementInfo().ingredients();
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
                List<RecipeDisplay> displays = recipe.display();
                if (!displays.isEmpty()) {
                    List<ItemStack> results = displays.get(0).result().resolveForStacks(SlotDisplayContext.fromLevel(mc.level));
                    if (!results.isEmpty()) {
                        predictedOutput = results.get(0).copy();
                        return;
                    }
                }
            }
        }
    }

    private static void onRenderGui(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        float partialTicks = tickCounter.getGameTimeDeltaTicks();
        float lerpedFade = Mth.lerp(partialTicks, prevFadeProgress, fadeProgress);

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

        int x = mc.getWindow().getGuiScaledWidth() / 2 - boxWidth / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 15;

        context.pose().pushMatrix();

        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);

        context.pose().translate(translateX, translateY);
        context.pose().scale(scale, scale);
        context.pose().translate(-translateX, -translateY);

        int alpha = (int)(200 * lerpedFade);
        int bgColor = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        context.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        context.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha = (int)(255 * lerpedFade);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        Component header = isSkillet ? Component.translatable("gui.tweaksdelight.skillet") : Component.translatable("gui.tweaksdelight.cooking_pot");

        context.text(mc.font, header, x + 4, y + 4, textColor, false);

        int currentX = x + 4;
        int itemY = y + 17;

        for (ItemStack ingredient : potIngredients) {
            context.item(ingredient, currentX, itemY);
            currentX += 18;
        }

        if (showOutputSection) {
            currentX += 1;
            ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS);
            context.item(equalsItem, currentX, itemY);
            currentX += 20;

            ItemStack outputToRender = hasFinalOutput ? finishedOutput : predictedOutput;
            context.item(outputToRender, currentX, itemY);

            if (outputToRender.getCount() > 1) {
                String countStr = String.valueOf(outputToRender.getCount());
                context.text(mc.font, countStr, currentX + 17 - mc.font.width(countStr), itemY + 9, 0xFFFFFF, true);
            }
        }

        if (!isSkillet && totalCookTime > 0) {
            float progress = (float) currentCookTime / (float) totalCookTime;
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            int barWidth = boxWidth - 8;
            int filledWidth = (int) (barWidth * progress);

            context.fill(x + 4, y + 37, x + 4 + barWidth, y + 39, (alpha << 24) | 0x333333);
            context.fill(x + 4, y + 37, x + 4 + filledWidth, y + 39, (alpha << 24) | 0xFF8800);
        }

        context.pose().popMatrix();
    }
}
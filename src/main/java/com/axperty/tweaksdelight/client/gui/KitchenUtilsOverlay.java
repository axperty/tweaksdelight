package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.TweaksDelight;
import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = TweaksDelight.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
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

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        prevFadeProgress = fadeProgress;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        boolean lookingAtPot = false;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
            BlockPos pos = blockHit.getBlockPos();
            BlockEntity be = mc.level.getBlockEntity(pos);

            if (be != null) {
                String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
                if (id.equals("farmersdelight:cooking_pot") && TweaksDelightConfig.CLIENT.enableCookingPotOverlay.get() ||
                    id.equals("farmersdelight:skillet") && TweaksDelightConfig.CLIENT.enableSkilletOverlay.get()) {
                    lookingAtPot = true;
                    isSkillet = id.equals("farmersdelight:skillet");
                    if (targetBlockPos == null || !targetBlockPos.equals(pos) || mc.level.getGameTime() % 5 == 0) {
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
                predictedOutput = ItemStack.EMPTY;
            }
        }
    }

    private static void updatePotContents(Minecraft mc, BlockPos pos, BlockEntity be) {
        potIngredients.clear();
        
        if (be instanceof Container container) {
            int limit = Math.min(6, container.getContainerSize());
            for (int i = 0; i < limit; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    potIngredients.add(stack.copy());
                }
            }
        }
        
        if (potIngredients.isEmpty()) {
            Direction[] dirs = new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            for (Direction d : dirs) {
                IItemHandler handler = mc.level.getCapability(Capabilities.ItemHandler.BLOCK, pos, mc.level.getBlockState(pos), be, d);
                if (handler != null) {
                    int limit = Math.min(6, handler.getSlots());
                    for (int i = 0; i < limit; i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!stack.isEmpty()) potIngredients.add(stack.copy());
                    }
                    if (!potIngredients.isEmpty()) break;
                }
            }
        }

        try {
            CompoundTag nbt = be.saveCustomOnly(mc.level.registryAccess());
            
            // Client-side NBT sync fallback since Capabilities may not exist client-side for dynamic blocks
            if (potIngredients.isEmpty() && nbt.contains("Inventory")) {
                CompoundTag invTag = nbt.getCompound("Inventory");
                if (invTag.contains("Items")) {
                    ListTag itemList = invTag.getList("Items", 10); // 10 is CompoundTag ID
                    for (int i = 0; i < itemList.size(); i++) {
                        CompoundTag itemTag = itemList.getCompound(i);
                        ItemStack parsed = ItemStack.parseOptional(mc.level.registryAccess(), itemTag);
                        if (!parsed.isEmpty()) potIngredients.add(parsed.copy());
                    }
                }
            }
            // Skillet specific fast check if not inside standard Inventory
            if (potIngredients.isEmpty() && isSkillet && nbt.contains("Ingredient")) {
                ItemStack parsed = ItemStack.parseOptional(mc.level.registryAccess(), nbt.getCompound("Ingredient"));
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

    private static void predictOutput(Minecraft mc) {
        if (potIngredients.isEmpty()) {
            predictedOutput = ItemStack.EMPTY;
            return;
        }
        RecipeManager rm = mc.level.getRecipeManager();
        if (rm == null) return;

        for (RecipeHolder<?> holder : rm.getRecipes()) {
            String typeStr = holder.value().getType().toString();
            if (isSkillet) {
                if (!typeStr.contains("campfire_cooking")) continue;
            } else {
                if (!typeStr.contains("farmersdelight:cooking")) continue;
            }

            boolean matches = true;
            for (Ingredient ing : holder.value().getIngredients()) {
                if (ing.isEmpty()) continue;
                boolean found = false;
                for (ItemStack in : potIngredients) {
                    if (ing.test(in)) { found = true; break; }
                }
                if (!found) { matches = false; break; }
            }
            
            if (matches) {
                predictedOutput = holder.value().getResultItem(mc.level.registryAccess());
                return;
            }
        }
        predictedOutput = ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        float partialTicks = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float lerpedFade = Mth.lerp(partialTicks, prevFadeProgress, fadeProgress);

        if (lerpedFade <= 0.01f || potIngredients.isEmpty()) return;
        if (isSkillet && !TweaksDelightConfig.CLIENT.enableSkilletOverlay.get()) return;
        if (!isSkillet && !TweaksDelightConfig.CLIENT.enableCookingPotOverlay.get()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        
        boolean hasOutput = !predictedOutput.isEmpty();
        int baseItemsWidth = potIngredients.size() * 18;
        int outputWidth = hasOutput ? 40 : 8; 
        int boxWidth = Math.max(70, baseItemsWidth + outputWidth);
        int boxHeight = isSkillet ? 38 : 44; 
        
        int x = mc.getWindow().getGuiScaledWidth() / 2 - boxWidth / 2;
        int y = mc.getWindow().getGuiScaledHeight() / 2 + 15;

        graphics.pose().pushPose();
        
        float scale = 0.85f + (0.15f * lerpedFade);
        float translateX = x + (boxWidth / 2.0f);
        float translateY = y + (boxHeight / 2.0f);
        
        graphics.pose().translate(translateX, translateY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-translateX, -translateY, 0);

        int alpha = (int)(200 * lerpedFade);
        int bgColor = (alpha << 24) | 0x111111;
        int borderColor = (alpha << 24) | 0x2A2A2A;

        graphics.fill(x - 2, y - 2, x + boxWidth + 2, y + boxHeight + 2, borderColor);
        graphics.fill(x, y, x + boxWidth, y + boxHeight, bgColor);

        int textAlpha = (int)(255 * lerpedFade);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        Component header = isSkillet ? Component.translatable("gui.tweaksdelight.skillet") : Component.translatable("gui.tweaksdelight.cooking_pot");
        
        graphics.drawString(mc.font, header, x + 4, y + 4, textColor, false);

        int currentX = x + 4;
        int itemY = y + 17;
        for (ItemStack ingredient : potIngredients) {
            graphics.renderItem(ingredient, currentX, itemY);
            currentX += 18;
        }

        if (hasOutput) {
            currentX += 1;
            
            ItemStack equalsItem = new ItemStack(ItemRegistry.EQUALS.get());
            graphics.renderItem(equalsItem, currentX, itemY);
            
            currentX += 20; 
            graphics.renderItem(predictedOutput, currentX, itemY);
        }

        if (!isSkillet && totalCookTime > 0) {
            float progress = (float) currentCookTime / (float) totalCookTime;
            progress = Mth.clamp(progress, 0.0f, 1.0f);
            int barWidth = boxWidth - 8;
            int filledWidth = (int) (barWidth * progress);
            
            graphics.fill(x + 4, y + 37, x + 4 + barWidth, y + 39, (alpha << 24) | 0x333333);
            graphics.fill(x + 4, y + 37, x + 4 + filledWidth, y + 39, (alpha << 24) | 0xFF8800); 
        }

        graphics.pose().popPose();
    }
}

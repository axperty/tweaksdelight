package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class MealIngredientGrabber {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {

            if (!TweaksDelightConfig.CLIENT.enableMealIngredientGrabber) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;
            BlockEntity be = world.getBlockEntity(hitResult.getBlockPos());
            if (!(be instanceof Inventory)) return ActionResult.PASS;
            ItemStack heldStack = player.getStackInHand(hand);
            if (heldStack.isEmpty()) return ActionResult.PASS;
            if (!isFoodItem(heldStack)) return ActionResult.PASS;
            if (world.isClient()) return ActionResult.SUCCESS;

            Inventory container = (Inventory) be;
            List<Ingredient> recipeIngredients = getRecipeIngredients(world, heldStack);
            if (recipeIngredients.isEmpty()) return ActionResult.PASS;

            int[] claimedFromSlot = new int[container.size()];
            List<int[]> transfers = new ArrayList<>();
            int missingIngredientCount = 0;
            List<Text> missingNames = new ArrayList<>();

            for (Ingredient ing : recipeIngredients) {
                if (ing.isEmpty()) continue;
                boolean found = false;
                for (int i = 0; i < container.size(); i++) {
                    ItemStack slotStack = container.getStack(i);
                    int available = slotStack.getCount() - claimedFromSlot[i];
                    if (available > 0 && ing.test(slotStack)) {
                        claimedFromSlot[i]++;
                        transfers.add(new int[]{i});
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    boolean playerHasIt = false;
                    for (int i = 0; i < player.getInventory().size(); i++) {
                        if (ing.test(player.getInventory().getStack(i))) {
                            playerHasIt = true;
                            break;
                        }
                    }
                    if (!playerHasIt) {
                        missingIngredientCount++;
                        ItemStack matching = getFirstMatchingStack(ing);
                        if (!matching.isEmpty()) {
                            missingNames.add(matching.getName());
                        }
                    }
                }
            }

            if (transfers.isEmpty() && missingIngredientCount == 0) return ActionResult.PASS;

            if (!transfers.isEmpty()) {
                List<ItemStack> stacksToGive = new ArrayList<>();
                for (int[] t : transfers) {
                    stacksToGive.add(container.getStack(t[0]).copyWithCount(1));
                }

                PlayerInventory inventory = player.getInventory();
                if (!hasRoom(inventory, stacksToGive)) {
                    player.sendMessage(
                            Text.translatable("message.tweaksdelight.meal_dispenser.no_space"),
                            true);
                    return ActionResult.SUCCESS;
                }

                for (int[] t : transfers) {
                    ItemStack taken = container.removeStack(t[0], 1);
                    if (!taken.isEmpty()) {
                        if (!player.getInventory().insertStack(taken)) {
                            player.dropItem(taken, false);
                        }
                    }
                }

                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.4f);
            }

            if (missingIngredientCount > 0) {
                MutableText msg = Text.translatable("message.tweaksdelight.meal_dispenser.missing");
                if (!missingNames.isEmpty()) {
                    for (int i = 0; i < missingNames.size(); i++) {
                        if (i > 0) msg.append(Text.literal(", "));
                        msg.append(missingNames.get(i));
                    }
                } else {
                    msg.append(Text.literal(missingIngredientCount + " ingredient(s)"));
                }
                player.sendMessage(msg, true);
            }

            return ActionResult.SUCCESS;
        });
    }

    private static List<Ingredient> getRecipeIngredients(World world, ItemStack meal) {
        if (!(world.getRecipeManager() instanceof ServerRecipeManager rm)) return List.of();
        RecipeEntry<?> selected = null;

        for (RecipeEntry<?> entry : rm.values()) {
            List<RecipeDisplay> displays = entry.value().getDisplays();
            if (displays.isEmpty()) continue;

            SlotDisplay resultDisplay = displays.get(0).result();
            List<ItemStack> resultStacks = resultDisplay.getStacks(SlotDisplayContexts.createParameters(world));

            if (!resultStacks.isEmpty()) {
                ItemStack result = resultStacks.get(0);
                if (result != null && !result.isEmpty() && ItemStack.areItemsEqual(result, meal)) {
                    String type = entry.value().getType().toString();
                    if (type.contains("cooking") || type.contains("cutting")) {
                        selected = entry;
                        break;
                    }
                    if (selected == null) selected = entry;
                }
            }
        }

        if (selected == null) return List.of();

        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ing : selected.value().getIngredientPlacement().getIngredients()) {
            if (!ing.isEmpty()) ingredients.add(ing);
        }
        return ingredients;
    }

    private static boolean isFoodItem(ItemStack stack) {
        if (stack.contains(net.minecraft.component.DataComponentTypes.FOOD)) return true;
        String id = stack.getItem().toString().toLowerCase();
        return id.contains("pie") || id.contains("stew") || id.contains("soup") ||
                id.contains("feast") || id.contains("cake") || id.contains("meal") ||
                id.contains("salad") || id.contains("potage") || id.contains("roast");
    }

    private static boolean hasRoom(PlayerInventory inventory, List<ItemStack> items) {
        int freeSlots = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) freeSlots++;
        }

        for (ItemStack item : items) {
            boolean canStack = false;
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack existing = inventory.getStack(i);
                if (!existing.isEmpty()
                        && ItemStack.areItemsEqual(existing, item)
                        && existing.getCount() < existing.getMaxCount()) {
                    canStack = true;
                    break;
                }
            }
            if (!canStack) {
                if (freeSlots <= 0) return false;
                freeSlots--;
            }
        }
        return true;
    }

    private static ItemStack getFirstMatchingStack(Ingredient ing) {
        for (Item item : Registries.ITEM) {
            ItemStack testStack = new ItemStack(item);
            if (ing.test(testStack)) {
                return testStack;
            }
        }
        return ItemStack.EMPTY;
    }
}
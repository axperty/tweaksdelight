package com.axperty.tweaksdelight.client.gui;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.collection.DefaultedList;
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
                        ItemStack[] matching = ing.getMatchingStacks();
                        if (matching.length > 0 && !matching[0].isEmpty()) {
                            missingNames.add(matching[0].getName());
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
                            player.dropStack(taken);
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
        RecipeManager rm = world.getRecipeManager();
        RecipeEntry<?> selected = null;

        for (RecipeEntry<?> entry : rm.values()) {
            ItemStack result = entry.value().getResult(world.getRegistryManager());
            if (result != null && !result.isEmpty() && ItemStack.areItemsEqual(result, meal)) {
                String type = entry.value().getType().toString();
                if (type.contains("cooking") || type.contains("cutting")) {
                    selected = entry;
                    break;
                }
                if (selected == null) selected = entry;
            }
        }

        if (selected == null) return List.of();

        List<Ingredient> result = new ArrayList<>();
        for (Ingredient ing : selected.value().getIngredients()) {
            if (!ing.isEmpty()) result.add(ing);
        }
        return result;
    }

    private static boolean isFoodItem(ItemStack stack) {
        if (stack.contains(net.minecraft.component.DataComponentTypes.FOOD)) return true;
        String id = stack.getItem().toString().toLowerCase();
        return id.contains("pie") || id.contains("stew") || id.contains("soup") ||
                id.contains("feast") || id.contains("cake") || id.contains("meal") ||
                id.contains("salad") || id.contains("potage") || id.contains("roast");
    }

    private static boolean hasRoom(PlayerInventory inventory, List<ItemStack> items) {
        DefaultedList<ItemStack> simulatedInv = DefaultedList.ofSize(inventory.main.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.main.size(); i++) {
            simulatedInv.set(i, inventory.main.get(i).copy());
        }

        for (ItemStack item : items) {
            if (item.isEmpty()) continue;

            int remaining = item.getCount();

            for (int i = 0; i < simulatedInv.size(); i++) {
                if (remaining <= 0) break;

                ItemStack existing = simulatedInv.get(i);
                if (!existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, item)) {
                    int space = existing.getMaxCount() - existing.getCount();
                    if (space > 0) {
                        int toAdd = Math.min(space, remaining);
                        existing.increment(toAdd);
                        remaining -= toAdd;
                    }
                }
            }

            for (int i = 0; i < simulatedInv.size(); i++) {
                if (remaining <= 0) break;

                ItemStack existing = simulatedInv.get(i);
                if (existing.isEmpty()) {
                    int toAdd = Math.min(item.getMaxCount(), remaining);
                    simulatedInv.set(i, item.copyWithCount(toAdd));
                    remaining -= toAdd;
                }
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }
}

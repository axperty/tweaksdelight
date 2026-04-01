package com.axperty.tweaksdelight.registry;

import com.axperty.tweaksdelight.TweaksDelight;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ItemRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TweaksDelight.MOD_ID);

    public static final DeferredItem<Item> EQUALS = ITEMS.register("equals",
            () -> new Item(new Item.Properties()));
}

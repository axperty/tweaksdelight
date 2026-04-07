package com.axperty.tweaksdelight.registry;

import com.axperty.tweaksdelight.TweaksDelight;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;

public class ItemRegistry {
    public static final Item EQUALS = new Item(new Item.Settings());

    public static void register() {
        Registry.register(Registries.ITEM, new Identifier(TweaksDelight.MOD_ID, "equals"), EQUALS);
    }
}

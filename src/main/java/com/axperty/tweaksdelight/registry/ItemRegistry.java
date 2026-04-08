package com.axperty.tweaksdelight.registry;

import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

public class ItemRegistry {

    public static final Item EQUALS = registerItem("equals", new Item.Properties());

    public static void register() {

    }

    private static Item registerItem(String name, Item.Properties settings) {
        Identifier id = Identifier.fromNamespaceAndPath("tweaksdelight", name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(settings.setId(key)));
    }
}
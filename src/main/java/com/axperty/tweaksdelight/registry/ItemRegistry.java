package com.axperty.tweaksdelight.registry;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ItemRegistry {

    public static final Item EQUALS = registerItem("equals", new Item.Settings());

    public static void register() {

    }

    private static Item registerItem(String name, Item.Settings settings) {
        Identifier id = Identifier.of("tweaksdelight", name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return Registry.register(Registries.ITEM, key, new Item(settings.registryKey(key)));
    }
}
package com.axperty.tweaksdelight.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class TweaksDelightConfig {
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<Client, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class Client {
        public final ForgeConfigSpec.BooleanValue enableItemFrameRecipeOverlay;
        public final ForgeConfigSpec.BooleanValue enableSmartIngredientHighlighting;
        public final ForgeConfigSpec.IntValue smartIngredientHighlightingDelay;
        public final ForgeConfigSpec.BooleanValue enableCookingPotOverlay;
        public final ForgeConfigSpec.BooleanValue enableSkilletOverlay;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("Tweaks Delight Configuration");

            enableItemFrameRecipeOverlay = builder
                    .comment("Enable the Item Frame Recipe Overlay")
                    .define("enableItemFrameRecipeOverlay", true);

            enableSmartIngredientHighlighting = builder
                    .comment("Enable the Smart Ingredient Highlighting overlay in containers")
                    .define("enableSmartIngredientHighlighting", true);

            smartIngredientHighlightingDelay = builder
                    .comment("Delay (in milliseconds) before items are highlighted when holding the Shift key")
                    .defineInRange("smartIngredientHighlightingDelay", 500, 0, 5000);

            enableCookingPotOverlay = builder
                    .comment("Enable the quick HUD when looking at a Cooking Pot")
                    .define("enableCookingPotOverlay", true);

            enableSkilletOverlay = builder
                    .comment("Enable the quick HUD when looking at a Skillet")
                    .define("enableSkilletOverlay", true);

            builder.pop();
        }
    }
}

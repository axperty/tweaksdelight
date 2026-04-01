package com.axperty.tweaksdelight.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class TweaksDelightConfig {
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        final Pair<Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue enableItemFrameRecipeOverlay;
        public final ModConfigSpec.BooleanValue enableSmartIngredientHighlighting;
        public final ModConfigSpec.IntValue smartIngredientHighlightingDelay;
        public final ModConfigSpec.BooleanValue enableCookingPotOverlay;
        public final ModConfigSpec.BooleanValue enableSkilletOverlay;

        public Client(ModConfigSpec.Builder builder) {
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

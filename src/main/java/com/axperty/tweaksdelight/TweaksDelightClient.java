package com.axperty.tweaksdelight;

import com.axperty.tweaksdelight.client.gui.ItemFrameRecipeOverlay;
import com.axperty.tweaksdelight.client.gui.KitchenUtilsOverlay;
import com.axperty.tweaksdelight.client.gui.MealIngredientGrabber;
import com.axperty.tweaksdelight.client.gui.MealIngredientOverlay;
import com.axperty.tweaksdelight.client.gui.SmartIngredientHighlighting;
import net.fabricmc.api.ClientModInitializer;

public class TweaksDelightClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ItemFrameRecipeOverlay.register();
        KitchenUtilsOverlay.register();
        SmartIngredientHighlighting.register();
        MealIngredientGrabber.register();
        MealIngredientOverlay.register();
    }
}
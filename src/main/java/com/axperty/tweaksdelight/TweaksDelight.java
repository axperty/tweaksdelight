package com.axperty.tweaksdelight;

import com.axperty.tweaksdelight.config.TweaksDelightConfig;
import com.axperty.tweaksdelight.registry.ItemRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TweaksDelight implements ModInitializer {
    public static final String MOD_ID = "tweaksdelight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TweaksDelightConfig.init();
        ItemRegistry.register();
    }
}

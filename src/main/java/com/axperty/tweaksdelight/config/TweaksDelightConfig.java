package com.axperty.tweaksdelight.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TweaksDelightConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("tweaksdelight.json").toFile();
    }

    public static Client CLIENT = new Client();

    public static void init() {
        load();
    }

    public static void load() {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                CLIENT = GSON.fromJson(reader, Client.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(getConfigFile())) {
            GSON.toJson(CLIENT, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Client {
        public boolean enableItemFrameRecipeOverlay = true;
        public boolean enableSmartIngredientHighlighting = true;
        public int smartIngredientHighlightingDelay = 500;
        public boolean enableCookingPotOverlay = true;
        public boolean enableSkilletOverlay = true;
        public boolean enableMealIngredientGrabber = true;
        public boolean enableMealIngredientOverlay = true;
    }
}

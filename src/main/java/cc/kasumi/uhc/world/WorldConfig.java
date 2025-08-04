package cc.kasumi.uhc.world;

import cc.kasumi.uhc.UHC;
import lombok.Getter;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Configuration management for WorldManager
 */
@Getter
public class WorldConfig {

    private final UHC plugin;
    private final Logger logger;
    private final File configFile;
    private FileConfiguration config;

    // World settings
    private String uhcWorldName;
    private String lobbyWorldName;
    private WorldType worldType;
    private boolean generateStructures;
    private boolean biomeSwapEnabled;
    private boolean giantCavesEnabled;
    private long worldSeed;
    private boolean useCustomSeed;

    // Cave settings
    private boolean caveEnabled;
    private int caveCutoff;
    private int caveMinY;
    private int caveMaxY;
    private int caveHorizontalStretch;
    private int caveVerticalStretch;

    // Generation settings
    private int pregenerateRadius;
    private boolean pregenerateOnStartup;
    private boolean autoResetWorld;

    // Border settings
    private int defaultBorderSize;
    private double borderDamageAmount;
    private double borderDamageBuffer;

    public WorldConfig(UHC plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configFile = new File(plugin.getDataFolder(), "world-config.yml");

        loadConfig();
    }

    /**
     * Load configuration from file
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Load world settings
        uhcWorldName = config.getString("world.uhc-world-name", "uhc");
        lobbyWorldName = config.getString("world.lobby-world-name", "lobby");
        worldType = WorldType.valueOf(config.getString("world.type", "NORMAL"));
        generateStructures = config.getBoolean("world.generate-structures", true);
        biomeSwapEnabled = config.getBoolean("world.biome-swap-enabled", true);
        giantCavesEnabled = config.getBoolean("world.giant-caves-enabled", true);
        useCustomSeed = config.getBoolean("world.use-custom-seed", false);
        worldSeed = config.getLong("world.seed", 0);

        // Load cave settings
        caveEnabled = config.getBoolean("caves.enabled", true);
        caveCutoff = config.getInt("caves.cutoff", 55);
        caveMinY = config.getInt("caves.min-y", 6);
        caveMaxY = config.getInt("caves.max-y", 52);
        caveHorizontalStretch = config.getInt("caves.horizontal-stretch", 16);
        caveVerticalStretch = config.getInt("caves.vertical-stretch", 9);

        // Load generation settings
        pregenerateRadius = config.getInt("generation.pregenerate-radius", 10);
        pregenerateOnStartup = config.getBoolean("generation.pregenerate-on-startup", false);
        autoResetWorld = config.getBoolean("generation.auto-reset-world", false);

        // Load border settings
        defaultBorderSize = config.getInt("border.default-size", 1000);
        borderDamageAmount = config.getDouble("border.damage-amount", 0.2);
        borderDamageBuffer = config.getDouble("border.damage-buffer", 5.0);

        logger.info("World configuration loaded successfully!");
    }

    /**
     * Save current configuration to file
     */
    public void saveConfig() {
        try {
            // World settings
            config.set("world.uhc-world-name", uhcWorldName);
            config.set("world.lobby-world-name", lobbyWorldName);
            config.set("world.type", worldType.name());
            config.set("world.generate-structures", generateStructures);
            config.set("world.biome-swap-enabled", biomeSwapEnabled);
            config.set("world.giant-caves-enabled", giantCavesEnabled);
            config.set("world.use-custom-seed", useCustomSeed);
            config.set("world.seed", worldSeed);

            // Cave settings
            config.set("caves.enabled", caveEnabled);
            config.set("caves.cutoff", caveCutoff);
            config.set("caves.min-y", caveMinY);
            config.set("caves.max-y", caveMaxY);
            config.set("caves.horizontal-stretch", caveHorizontalStretch);
            config.set("caves.vertical-stretch", caveVerticalStretch);

            // Generation settings
            config.set("generation.pregenerate-radius", pregenerateRadius);
            config.set("generation.pregenerate-on-startup", pregenerateOnStartup);
            config.set("generation.auto-reset-world", autoResetWorld);

            // Border settings
            config.set("border.default-size", defaultBorderSize);
            config.set("border.damage-amount", borderDamageAmount);
            config.set("border.damage-buffer", borderDamageBuffer);

            config.save(configFile);
            logger.info("World configuration saved successfully!");

        } catch (IOException e) {
            logger.severe("Failed to save world configuration: " + e.getMessage());
        }
    }

    /**
     * Create default configuration file
     */
    private void createDefaultConfig() {
        try {
            plugin.getDataFolder().mkdirs();
            configFile.createNewFile();

            config = YamlConfiguration.loadConfiguration(configFile);

            // Set default values
            config.set("world.uhc-world-name", "uhc");
            config.set("world.lobby-world-name", "lobby");
            config.set("world.type", "NORMAL");
            config.set("world.generate-structures", true);
            config.set("world.biome-swap-enabled", true);
            config.set("world.giant-caves-enabled", true);
            config.set("world.use-custom-seed", false);
            config.set("world.seed", 0);

            config.set("caves.enabled", true);
            config.set("caves.cutoff", 55);
            config.set("caves.min-y", 6);
            config.set("caves.max-y", 52);
            config.set("caves.horizontal-stretch", 16);
            config.set("caves.vertical-stretch", 9);

            config.set("generation.pregenerate-radius", 10);
            config.set("generation.pregenerate-on-startup", false);
            config.set("generation.auto-reset-world", false);

            config.set("border.default-size", 1000);
            config.set("border.damage-amount", 0.2);
            config.set("border.damage-buffer", 5.0);

            config.save(configFile);
            logger.info("Created default world configuration file!");

        } catch (IOException e) {
            logger.severe("Failed to create default world configuration: " + e.getMessage());
        }
    }

    /**
     * Update a configuration value and save
     */
    public void updateSetting(String path, Object value) {
        config.set(path, value);
        saveConfig();
        loadConfig(); // Reload to update local variables
    }

    /**
     * Validate configuration values
     */
    public boolean validateConfig() {
        boolean valid = true;

        if (caveCutoff < 0 || caveCutoff > 100) {
            logger.warning("Invalid cave cutoff value: " + caveCutoff + " (must be 0-100)");
            valid = false;
        }

        if (caveMinY < 0 || caveMaxY > 255 || caveMinY >= caveMaxY) {
            logger.warning("Invalid cave Y range: " + caveMinY + "-" + caveMaxY);
            valid = false;
        }

        if (pregenerateRadius < 0 || pregenerateRadius > 100) {
            logger.warning("Invalid pregenerate radius: " + pregenerateRadius + " (must be 0-100)");
            valid = false;
        }

        if (defaultBorderSize < 10 || defaultBorderSize > 10000) {
            logger.warning("Invalid default border size: " + defaultBorderSize + " (must be 10-10000)");
            valid = false;
        }

        return valid;
    }

    /**
     * Reset configuration to defaults
     */
    public void resetToDefaults() {
        if (configFile.exists()) {
            configFile.delete();
        }
        createDefaultConfig();
        loadConfig();
        logger.info("World configuration reset to defaults!");
    }

    // Setters with automatic save
    public void setUhcWorldName(String uhcWorldName) {
        this.uhcWorldName = uhcWorldName;
        updateSetting("world.uhc-world-name", uhcWorldName);
    }

    public void setLobbyWorldName(String lobbyWorldName) {
        this.lobbyWorldName = lobbyWorldName;
        updateSetting("world.lobby-world-name", lobbyWorldName);
    }

    public void setWorldType(WorldType worldType) {
        this.worldType = worldType;
        updateSetting("world.type", worldType.name());
    }

    public void setBiomeSwapEnabled(boolean biomeSwapEnabled) {
        this.biomeSwapEnabled = biomeSwapEnabled;
        updateSetting("world.biome-swap-enabled", biomeSwapEnabled);
    }

    public void setGiantCavesEnabled(boolean giantCavesEnabled) {
        this.giantCavesEnabled = giantCavesEnabled;
        updateSetting("world.giant-caves-enabled", giantCavesEnabled);
    }

    public void setCaveSettings(boolean enabled, int cutoff, int minY, int maxY, int hStretch, int vStretch) {
        this.caveEnabled = enabled;
        this.caveCutoff = cutoff;
        this.caveMinY = minY;
        this.caveMaxY = maxY;
        this.caveHorizontalStretch = hStretch;
        this.caveVerticalStretch = vStretch;

        config.set("caves.enabled", enabled);
        config.set("caves.cutoff", cutoff);
        config.set("caves.min-y", minY);
        config.set("caves.max-y", maxY);
        config.set("caves.horizontal-stretch", hStretch);
        config.set("caves.vertical-stretch", vStretch);

        saveConfig();
    }
}
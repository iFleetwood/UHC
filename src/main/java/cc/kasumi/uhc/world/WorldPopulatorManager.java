package cc.kasumi.uhc.world;

import cc.kasumi.uhc.world.custom.CaveSettings;
import cc.kasumi.uhc.world.custom.GiantCave;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages world populators for UHC worlds
 * Integrates with WorldPopulatorListener and provides centralized populator management
 */
@Getter
public class WorldPopulatorManager {

    private final WorldManager worldManager;
    private final Logger logger;
    private final List<BlockPopulator> registeredPopulators;
    private final PopulatorConfig populatorConfig;

    public WorldPopulatorManager(WorldManager worldManager) {
        this.worldManager = worldManager;
        this.logger = worldManager.getPlugin().getLogger();
        this.registeredPopulators = new ArrayList<>();
        this.populatorConfig = new PopulatorConfig();

        registerDefaultPopulators();
    }

    /**
     * Register default populators based on configuration
     */
    private void registerDefaultPopulators() {
        // Giant Cave Populator
        if (worldManager.getSettings().isGiantCavesEnabled()) {
            registerPopulator(new GiantCave(), "Giant Caves");
        }

        // Add more populators here as needed
        // registerPopulator(new CustomOrePopulator(), "Custom Ores");
        // registerPopulator(new UHCStructurePopulator(), "UHC Structures");
    }

    /**
     * Register a populator with name for logging
     */
    public void registerPopulator(BlockPopulator populator, String name) {
        registeredPopulators.add(populator);
        logger.info("Registered populator: " + name);
    }

    /**
     * Add all registered populators to a world
     */
    public void addPopulatorsToWorld(World world) {
        if (registeredPopulators.isEmpty()) {
            logger.info("No populators to add to world: " + world.getName());
            return;
        }

        logger.info("Adding " + registeredPopulators.size() + " populators to world: " + world.getName());

        for (BlockPopulator populator : registeredPopulators) {
            world.getPopulators().add(populator);
            logger.fine("Added populator: " + populator.getClass().getSimpleName());
        }

        logger.info("All populators added successfully to: " + world.getName());
    }

    /**
     * Check if a world should have populators added
     * Called by WorldPopulatorListener
     */
    public boolean shouldAddPopulators(World world) {
        String worldName = world.getName();
        String uhcWorldName = worldManager.getWorldConfig().getUhcWorldName();

        // Only add populators to UHC world
        boolean isUHCWorld = worldName.equalsIgnoreCase(uhcWorldName);

        if (isUHCWorld) {
            logger.info("World " + worldName + " identified as UHC world, populators will be added");
        }

        return isUHCWorld;
    }

    /**
     * Update populator configuration and refresh registered populators
     */
    public void refreshPopulators() {
        logger.info("Refreshing populators based on current configuration...");

        // Clear existing populators
        registeredPopulators.clear();

        // Re-register based on current settings
        registerDefaultPopulators();

        logger.info("Populator refresh completed. " + registeredPopulators.size() + " populators registered.");
    }

    /**
     * Configure cave settings and refresh if needed
     */
    public void updateCaveSettings(boolean enabled, int cutoff, int minY, int maxY, int hStretch, int vStretch) {
        boolean wasEnabled = CaveSettings.CAVE_ENABLED;

        // Update cave settings
        CaveSettings.CAVE_ENABLED = enabled;
        CaveSettings.CAVE_CUTOFF = cutoff;
        CaveSettings.CAVE_MIN_Y = minY;
        CaveSettings.CAVE_MAX_Y = maxY;
        CaveSettings.CAVE_H_STRETCH = hStretch;
        CaveSettings.CAVE_V_STRETCH = vStretch;

        logger.info("Cave settings updated - Enabled: " + enabled +
                ", Cutoff: " + cutoff + ", Y Range: " + minY + "-" + maxY);

        // If cave enabling status changed, refresh populators
        if (wasEnabled != enabled) {
            refreshPopulators();
        }
    }

    /**
     * Get populator statistics
     */
    public PopulatorStats getPopulatorStats() {
        PopulatorStats stats = new PopulatorStats();
        stats.totalRegistered = registeredPopulators.size();
        stats.giantCavesEnabled = CaveSettings.CAVE_ENABLED;
        stats.caveSettings = new CaveStats();
        stats.caveSettings.cutoff = CaveSettings.CAVE_CUTOFF;
        stats.caveSettings.minY = CaveSettings.CAVE_MIN_Y;
        stats.caveSettings.maxY = CaveSettings.CAVE_MAX_Y;
        stats.caveSettings.horizontalStretch = CaveSettings.CAVE_H_STRETCH;
        stats.caveSettings.verticalStretch = CaveSettings.CAVE_V_STRETCH;

        // Count active populators by type
        for (BlockPopulator populator : registeredPopulators) {
            if (populator instanceof GiantCave) {
                stats.activeGiantCaves++;
            }
            // Add other populator type counts here
        }

        return stats;
    }

    /**
     * Remove all populators from a world
     */
    public void removePopulatorsFromWorld(World world) {
        int removed = 0;
        List<BlockPopulator> worldPopulators = new ArrayList<>(world.getPopulators());

        for (BlockPopulator populator : worldPopulators) {
            if (registeredPopulators.contains(populator)) {
                world.getPopulators().remove(populator);
                removed++;
            }
        }

        logger.info("Removed " + removed + " populators from world: " + world.getName());
    }

    /**
     * Configuration for populators
     */
    @Getter
    public static class PopulatorConfig {
        private boolean autoAddToUHCWorlds = true;
        private boolean autoAddToTestWorlds = false;
        private boolean logPopulatorActivity = true;
        private boolean validatePopulatorSettings = true;

        public PopulatorConfig setAutoAddToUHCWorlds(boolean autoAddToUHCWorlds) {
            this.autoAddToUHCWorlds = autoAddToUHCWorlds;
            return this;
        }

        public PopulatorConfig setAutoAddToTestWorlds(boolean autoAddToTestWorlds) {
            this.autoAddToTestWorlds = autoAddToTestWorlds;
            return this;
        }

        public PopulatorConfig setLogPopulatorActivity(boolean logPopulatorActivity) {
            this.logPopulatorActivity = logPopulatorActivity;
            return this;
        }

        public PopulatorConfig setValidatePopulatorSettings(boolean validatePopulatorSettings) {
            this.validatePopulatorSettings = validatePopulatorSettings;
            return this;
        }
    }

    /**
     * Statistics about registered populators
     */
    public static class PopulatorStats {
        public int totalRegistered = 0;
        public int activeGiantCaves = 0;
        public boolean giantCavesEnabled = false;
        public CaveStats caveSettings = new CaveStats();
    }

    /**
     * Cave-specific statistics
     */
    public static class CaveStats {
        public int cutoff = 0;
        public int minY = 0;
        public int maxY = 0;
        public int horizontalStretch = 0;
        public int verticalStretch = 0;
    }
}
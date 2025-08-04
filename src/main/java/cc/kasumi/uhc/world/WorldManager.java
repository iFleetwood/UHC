package cc.kasumi.uhc.world;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.world.custom.CaveSettings;
import cc.kasumi.uhc.world.custom.GiantCave;
import cc.kasumi.uhc.world.generator.BiomeSwap;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages UHC world generation including biome swapping and custom features
 */
@Getter
public class WorldManager {

    private final UHC plugin;
    private final Logger logger;
    private final BiomeSwap biomeSwap;
    private final WorldConfig worldConfig;
    private final WorldPopulatorManager populatorManager;

    private World uhcWorld;
    private World lobbyWorld;
    private boolean worldGenerationInProgress = false;

    // World generation settings
    private WorldGenerationSettings settings;

    public WorldManager(UHC plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.biomeSwap = new BiomeSwap();
        this.worldConfig = new WorldConfig(plugin);
        this.populatorManager = new WorldPopulatorManager(this);
        this.settings = createSettingsFromConfig();
    }

    /**
     * Initialize all worlds needed for UHC
     */
    public void initializeWorlds() {
        logger.info("Initializing UHC worlds...");

        // Validate configuration
        if (!worldConfig.validateConfig()) {
            logger.warning("World configuration has invalid values, some features may not work correctly!");
        }

        // Apply cave settings from config
        applyCaveSettingsFromConfig();

        // Load or create lobby world first
        loadLobbyWorld();

        // Load or create UHC world
        loadUHCWorld();

        // Pregenerate if configured
        if (worldConfig.isPregenerateOnStartup() && uhcWorld != null) {
            logger.info("Starting automatic chunk pregeneration...");
            pregenerateSpawnChunks(uhcWorld, worldConfig.getPregenerateRadius());
        }

        logger.info("World initialization completed!");
    }

    /**
     * Create settings from configuration
     */
    private WorldGenerationSettings createSettingsFromConfig() {
        return new WorldGenerationSettings()
                .setWorldType(worldConfig.getWorldType())
                .setGenerateStructures(worldConfig.isGenerateStructures())
                .setBiomeSwapEnabled(worldConfig.isBiomeSwapEnabled())
                .setGiantCavesEnabled(worldConfig.isGiantCavesEnabled())
                .setSeed(worldConfig.isUseCustomSeed() ? worldConfig.getWorldSeed() : 0);
    }

    /**
     * Apply cave settings from configuration
     */
    private void applyCaveSettingsFromConfig() {
        configureCaveSettings(
                worldConfig.isCaveEnabled(),
                worldConfig.getCaveCutoff(),
                worldConfig.getCaveMinY(),
                worldConfig.getCaveMaxY(),
                worldConfig.getCaveHorizontalStretch(),
                worldConfig.getCaveVerticalStretch()
        );
    }

    /**
     * Get world configuration
     */
    public WorldConfig getWorldConfig() {
        return worldConfig;
    }

    /**
     * Get populator manager
     */
    public WorldPopulatorManager getPopulatorManager() {
        return populatorManager;
    }

    /**
     * Get the plugin instance
     */
    public UHC getPlugin() {
        return plugin;
    }

    /**
     * Create a new UHC world with custom generation
     * Fixed for 1.8.8 - must run on main thread
     */
    public CompletableFuture<World> createNewUHCWorld() {
        CompletableFuture<World> future = new CompletableFuture<>();

        // Schedule on main thread for 1.8.8 compatibility
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                logger.info("Creating new UHC world...");
                worldGenerationInProgress = true;

                String uhcWorldName = worldConfig.getUhcWorldName();

                // Delete existing world if it exists
                deleteExistingWorld(uhcWorldName);

                // Apply biome swaps before world generation
                if (settings.isBiomeSwapEnabled()) {
                    logger.info("Applying biome swaps...");
                    biomeSwap.startWorldGen();
                }

                // Create world with custom settings
                WorldCreator creator = createUHCWorldCreator();
                uhcWorld = plugin.getServer().createWorld(creator);

                if (uhcWorld == null) {
                    throw new RuntimeException("Failed to create UHC world!");
                }

                // Configure world settings
                configureUHCWorld(uhcWorld);

                // Add custom populators through the manager
                populatorManager.addPopulatorsToWorld(uhcWorld);

                logger.info("UHC world created successfully: " + uhcWorld.getName());
                future.complete(uhcWorld);

            } catch (Exception e) {
                logger.severe("Failed to create UHC world: " + e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(new RuntimeException("World generation failed", e));
            } finally {
                worldGenerationInProgress = false;
            }
        });

        return future;
    }

    /**
     * Pregenerate chunks around spawn for better performance
     * Fixed for 1.8.8 - optimized for older versions
     */
    public void pregenerateSpawnChunks(World world, int radius) {
        logger.info("Pregenerating chunks in radius " + radius + "...");

        new BukkitRunnable() {
            private int x = -radius;
            private int z = -radius;
            private int chunksGenerated = 0;
            private final int totalChunks = (radius * 2 + 1) * (radius * 2 + 1);

            @Override
            public void run() {
                int chunksThisTick = 0;

                // Reduced chunk generation per tick for 1.8.8 stability
                while (chunksThisTick < 2 && x <= radius) { // Generate 2 chunks per tick instead of 5
                    try {
                        // Use synchronous chunk loading for 1.8.8
                        Chunk chunk = world.getChunkAt(x, z);

                        if (!chunk.isLoaded()) {
                            chunk.load(true);
                        }

                        chunksGenerated++;
                        chunksThisTick++;

                        z++;
                        if (z > radius) {
                            z = -radius;
                            x++;
                        }
                    } catch (Exception e) {
                        logger.warning("Error generating chunk at " + x + ", " + z + ": " + e.getMessage());
                        // Skip this chunk and continue
                        z++;
                        if (z > radius) {
                            z = -radius;
                            x++;
                        }
                    }
                }

                // Progress reporting
                if (chunksGenerated % 50 == 0 || x > radius) {
                    double progress = (double) chunksGenerated / totalChunks * 100;
                    logger.info("Chunk pregeneration progress: " + (int)progress + "% (" +
                            chunksGenerated + "/" + totalChunks + ")");
                }

                if (x > radius) {
                    logger.info("Chunk pregeneration completed!");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 2); // Run every 2 ticks instead of 1 for better stability
    }

    /**
     * Reset the UHC world completely
     * Fixed for 1.8.8 - must run on main thread
     */
    public CompletableFuture<Void> resetUHCWorld() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Schedule on main thread for 1.8.8 compatibility
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                logger.info("Resetting UHC world...");

                String uhcWorldName = worldConfig.getUhcWorldName();
                String lobbyWorldName = worldConfig.getLobbyWorldName();

                // Teleport all players to lobby
                if (lobbyWorld != null) {
                    Location lobbySpawn = lobbyWorld.getSpawnLocation();
                    if (uhcWorld != null) {
                        uhcWorld.getPlayers().forEach(player ->
                                player.teleport(lobbySpawn));
                    }
                }

                // Unload and delete world
                if (uhcWorld != null) {
                    plugin.getServer().unloadWorld(uhcWorld, false);
                    uhcWorld = null;
                }

                deleteExistingWorld(uhcWorldName);

                // Create new world (this will complete the future)
                createNewUHCWorld().thenAccept(world -> {
                    logger.info("UHC world reset completed!");
                    future.complete(null);
                }).exceptionally(throwable -> {
                    future.completeExceptionally(throwable);
                    return null;
                });

            } catch (Exception e) {
                logger.severe("Failed to reset UHC world: " + e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Configure cave generation settings
     */
    public void configureCaveSettings(boolean enabled, int cutoff, int minY, int maxY,
                                      int horizontalStretch, int verticalStretch) {
        populatorManager.updateCaveSettings(enabled, cutoff, minY, maxY, horizontalStretch, verticalStretch);

        logger.info("Cave settings updated - Enabled: " + enabled +
                ", Cutoff: " + cutoff + ", Y Range: " + minY + "-" + maxY);
    }

    /**
     * Create UHC world synchronously (for when you need immediate access)
     * Use this only when you're already on the main thread
     */
    public World createNewUHCWorldSync() {
        if (!plugin.getServer().isPrimaryThread()) {
            throw new IllegalStateException("createNewUHCWorldSync() must be called from main thread!");
        }

        try {
            logger.info("Creating new UHC world synchronously...");
            worldGenerationInProgress = true;

            String uhcWorldName = worldConfig.getUhcWorldName();

            // Delete existing world if it exists
            deleteExistingWorld(uhcWorldName);

            // Apply biome swaps before world generation
            if (settings.isBiomeSwapEnabled()) {
                logger.info("Applying biome swaps...");
                biomeSwap.startWorldGen();
            }

            // Create world with custom settings
            WorldCreator creator = createUHCWorldCreator();
            uhcWorld = plugin.getServer().createWorld(creator);

            if (uhcWorld == null) {
                throw new RuntimeException("Failed to create UHC world!");
            }

            // Configure world settings
            configureUHCWorld(uhcWorld);

            // Add custom populators through the manager
            populatorManager.addPopulatorsToWorld(uhcWorld);

            logger.info("UHC world created successfully: " + uhcWorld.getName());
            return uhcWorld;

        } catch (Exception e) {
            logger.severe("Failed to create UHC world: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("World generation failed", e);
        } finally {
            worldGenerationInProgress = false;
        }
    }

    /**
     * Get world border information
     */
    public WorldBorderInfo getWorldBorderInfo(World world) {
        WorldBorder border = world.getWorldBorder();
        return new WorldBorderInfo(
                border.getCenter(),
                border.getSize(),
                border.getDamageAmount(),
                border.getDamageBuffer()
        );
    }

    // Private helper methods

    private void loadLobbyWorld() {
        String lobbyWorldName = worldConfig.getLobbyWorldName();
        lobbyWorld = Bukkit.getWorld(lobbyWorldName);

        if (lobbyWorld == null) {
            logger.info("Creating lobby world: " + lobbyWorldName);
            WorldCreator lobbyCreator = new WorldCreator(lobbyWorldName)
                    .type(WorldType.FLAT)
                    .generateStructures(false);

            lobbyWorld = Bukkit.createWorld(lobbyCreator);

            if (lobbyWorld != null) {
                configureLobbyWorld(lobbyWorld);
                logger.info("Lobby world created successfully!");
            } else {
                logger.warning("Failed to create lobby world!");
            }
        } else {
            logger.info("Loaded existing lobby world: " + lobbyWorld.getName());
        }
    }

    private void loadUHCWorld() {
        String uhcWorldName = worldConfig.getUhcWorldName();
        uhcWorld = plugin.getServer().getWorld(uhcWorldName);

        if (uhcWorld == null) {
            logger.info("UHC world not found, creating new one...");
            // For initialization, we don't need to wait for the future
            // The world will be available after the async creation completes
            createNewUHCWorld().thenAccept(world -> {
                logger.info("UHC world creation completed during initialization!");
            }).exceptionally(throwable -> {
                logger.severe("Failed to create UHC world during initialization: " + throwable.getMessage());
                return null;
            });
        } else {
            logger.info("Loaded existing UHC world: " + uhcWorld.getName());
            configureUHCWorld(uhcWorld);
        }
    }

    private WorldCreator createUHCWorldCreator() {
        String uhcWorldName = worldConfig.getUhcWorldName();
        WorldCreator creator = new WorldCreator(uhcWorldName);

        // Basic world settings
        creator.type(settings.getWorldType());
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(settings.isGenerateStructures());

        // Set custom generator if specified
        if (settings.getCustomGenerator() != null) {
            creator.generator(settings.getCustomGenerator());
        }

        // Set seed if specified
        if (settings.getSeed() != 0) {
            creator.seed(settings.getSeed());
            logger.info("Using custom seed: " + settings.getSeed());
        }

        return creator;
    }

    private void configureUHCWorld(World world) {
        // Basic world rules
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("naturalRegeneration", "false");
        world.setGameRuleValue("keepInventory", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("doFireTick", "false");

        // Set time and weather
        world.setTime(6000); // Noon
        world.setStorm(false);
        world.setThundering(false);
        world.setWeatherDuration(0);

        // Set spawn location to 0,0
        Location spawn = new Location(world, 0.5,
                world.getHighestBlockYAt(0, 0) + 1, 0.5);
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());

        // Configure world border (will be set by game)
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(2000); // Default size, will be changed by game
        border.setDamageAmount(0);
        border.setDamageBuffer(1000);

        logger.info("Configured UHC world: " + world.getName());
    }

    private void configureLobbyWorld(World world) {
        // Lobby world settings
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("keepInventory", "true");

        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);

        // Set spawn at a reasonable height for flat world
        world.setSpawnLocation(0, 64, 0);

        logger.info("Configured lobby world: " + world.getName());
    }

    private void deleteExistingWorld(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (worldFolder.exists()) {
            logger.info("Deleting existing world: " + worldName);
            if (!deleteDirectory(worldFolder)) {
                logger.warning("Failed to completely delete world folder: " + worldName);
            }
        }
    }

    /**
     * Update world generation settings
     */
    public void updateWorldSettings(WorldGenerationSettings newSettings) {
        this.settings = newSettings;

        // Update config
        worldConfig.setWorldType(newSettings.getWorldType());
        worldConfig.setBiomeSwapEnabled(newSettings.isBiomeSwapEnabled());
        worldConfig.setGiantCavesEnabled(newSettings.isGiantCavesEnabled());

        logger.info("World generation settings updated!");
    }

    /**
     * Reload configuration and apply changes
     */
    public void reloadConfiguration() {
        worldConfig.loadConfig();
        this.settings = createSettingsFromConfig();
        applyCaveSettingsFromConfig();

        logger.info("World configuration reloaded!");
    }

    /**
     * Check if automatic world reset is enabled
     */
    public boolean shouldAutoResetWorld() {
        return worldConfig.isAutoResetWorld();
    }

    /**
     * Get the UHC world name from config
     */
    public String getUHCWorldName() {
        return worldConfig.getUhcWorldName();
    }

    /**
     * Get the lobby world name from config
     */
    public String getLobbyWorldName() {
        return worldConfig.getLobbyWorldName();
    }

    /**
     * Force chunk loading in a radius around spawn
     */
    public void forceLoadSpawnChunks(World world, int radius) {
        logger.info("Force loading spawn chunks in radius " + radius + "...");

        Location spawn = world.getSpawnLocation();
        int centerX = spawn.getChunk().getX();
        int centerZ = spawn.getChunk().getZ();

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                Chunk chunk = world.getChunkAt(x, z);
                chunk.load(true);
            }
        }

        logger.info("Spawn chunks force loaded!");
    }

    /**
     * Get world statistics
     */
    public WorldStats getWorldStats() {
        WorldStats stats = new WorldStats();

        if (uhcWorld != null) {
            stats.uhcWorldLoaded = true;
            stats.uhcWorldName = uhcWorld.getName();
            stats.uhcWorldPlayers = uhcWorld.getPlayers().size();
            stats.uhcWorldLoadedChunks = uhcWorld.getLoadedChunks().length;
            stats.uhcWorldSpawn = uhcWorld.getSpawnLocation();

            WorldBorder border = uhcWorld.getWorldBorder();
            stats.borderSize = border.getSize();
            stats.borderCenter = border.getCenter();
        }

        if (lobbyWorld != null) {
            stats.lobbyWorldLoaded = true;
            stats.lobbyWorldName = lobbyWorld.getName();
            stats.lobbyWorldPlayers = lobbyWorld.getPlayers().size();
            stats.lobbyWorldSpawn = lobbyWorld.getSpawnLocation();
        }

        stats.generationInProgress = worldGenerationInProgress;
        stats.autoResetEnabled = worldConfig.isAutoResetWorld();

        return stats;
    }

    /**
     * World statistics holder
     */
    public static class WorldStats {
        public boolean uhcWorldLoaded = false;
        public String uhcWorldName = "N/A";
        public int uhcWorldPlayers = 0;
        public int uhcWorldLoadedChunks = 0;
        public Location uhcWorldSpawn = null;
        public double borderSize = 0;
        public Location borderCenter = null;

        public boolean lobbyWorldLoaded = false;
        public String lobbyWorldName = "N/A";
        public int lobbyWorldPlayers = 0;
        public Location lobbyWorldSpawn = null;

        public boolean generationInProgress = false;
        public boolean autoResetEnabled = false;
    }

    private boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        return directory.delete();
    }

    /**
     * World generation settings configuration
     */
    @Getter
    public static class WorldGenerationSettings {
        private WorldType worldType = WorldType.NORMAL;
        private boolean generateStructures = true;
        private boolean biomeSwapEnabled = true;
        private boolean giantCavesEnabled = true;
        private long seed = 0; // 0 = random
        private ChunkGenerator customGenerator = null;

        public WorldGenerationSettings setWorldType(WorldType worldType) {
            this.worldType = worldType;
            return this;
        }

        public WorldGenerationSettings setGenerateStructures(boolean generateStructures) {
            this.generateStructures = generateStructures;
            return this;
        }

        public WorldGenerationSettings setBiomeSwapEnabled(boolean biomeSwapEnabled) {
            this.biomeSwapEnabled = biomeSwapEnabled;
            return this;
        }

        public WorldGenerationSettings setGiantCavesEnabled(boolean giantCavesEnabled) {
            this.giantCavesEnabled = giantCavesEnabled;
            return this;
        }

        public WorldGenerationSettings setSeed(long seed) {
            this.seed = seed;
            return this;
        }

        public WorldGenerationSettings setCustomGenerator(ChunkGenerator customGenerator) {
            this.customGenerator = customGenerator;
            return this;
        }
    }

    /**
     * World border information holder
     */
    public static class WorldBorderInfo {
        public final Location center;
        public final double size;
        public final double damageAmount;
        public final double damageBuffer;

        public WorldBorderInfo(Location center, double size, double damageAmount, double damageBuffer) {
            this.center = center;
            this.size = size;
            this.damageAmount = damageAmount;
            this.damageBuffer = damageBuffer;
        }
    }
}
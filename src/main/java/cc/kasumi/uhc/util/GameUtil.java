package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.Game;
import lombok.NonNull;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Enhanced GameUtil with comprehensive scatter support and safety checks
 */
public class GameUtil {

    private static final Set<Material> UNSAFE_GROUND_MATERIALS = new HashSet<>(Arrays.asList(
        Material.LAVA, Material.STATIONARY_LAVA,
        Material.WATER, Material.STATIONARY_WATER,
        Material.FIRE, Material.CACTUS,
        Material.AIR
    ));
    
    private static final Set<Material> PASSABLE_MATERIALS = new HashSet<>(Arrays.asList(
        Material.AIR, Material.LONG_GRASS, Material.DEAD_BUSH,
        Material.YELLOW_FLOWER, Material.RED_ROSE, Material.BROWN_MUSHROOM,
        Material.RED_MUSHROOM, Material.TORCH, Material.REDSTONE_TORCH_ON,
        Material.REDSTONE_TORCH_OFF, Material.SNOW, Material.VINE,
        Material.WATER_LILY, Material.DOUBLE_PLANT, Material.CROPS,
        Material.CARROT, Material.POTATO, Material.SAPLING
    ));
    
    private static final Set<Material> DANGEROUS_MATERIALS = new HashSet<>(Arrays.asList(
        Material.LAVA, Material.STATIONARY_LAVA,
        Material.WATER, Material.STATIONARY_WATER,
        Material.FIRE, Material.CACTUS,
        Material.WEB, Material.TNT
    ));

    // Track active wall builders to prevent multiple builders for same area
    private static final Map<String, ProgressiveWallBuilder> activeBuilders = new HashMap<>();

    /**
     * Enhanced location safety check for scatter
     */
    public static boolean isLocationSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Check if location is within world bounds
        if (y < 1 || y > 255) {
            return false;
        }

        Block groundBlock = world.getBlockAt(x, y - 1, z);
        Block feetBlock = world.getBlockAt(x, y, z);
        Block headBlock = world.getBlockAt(x, y + 1, z);

        // Debug version - log first few checks at INFO level
        boolean debug = debugLocationSafety();

        // Check ground safety
        if (!isGroundSafe(groundBlock)) {
            if (debug && UHC.getInstance() != null) {
                UHC.getInstance().getLogger().info("DEBUG: Location " + x + "," + y + "," + z + 
                    " failed: unsafe ground (" + groundBlock.getType() + ") at Y=" + (y-1));
            }
            return false;
        }

        // Check if feet and head space are clear
        if (!isPassableMaterial(feetBlock.getType()) || !isPassableMaterial(headBlock.getType())) {
            if (debug && UHC.getInstance() != null) {
                UHC.getInstance().getLogger().info("DEBUG: Location " + x + "," + y + "," + z + 
                    " failed: blocked space (feet: " + feetBlock.getType() + 
                    ", head: " + headBlock.getType() + ")");
            }
            return false;
        }

        // Check for dangerous nearby blocks
        if (hasDangerousNearbyBlocks(world, x, y, z)) {
            if (debug && UHC.getInstance() != null) {
                UHC.getInstance().getLogger().info("DEBUG: Location " + x + "," + y + "," + z + 
                    " failed: dangerous nearby blocks");
            }
            return false;
        }

        // Additional checks for scatter safety
        if (!hasStableGround(world, x, y - 1, z)) {
            if (debug && UHC.getInstance() != null) {
                UHC.getInstance().getLogger().info("DEBUG: Location " + x + "," + y + "," + z + 
                    " failed: unstable ground (less than 5 solid blocks in 3x3 area)");
            }
            return false;
        }

        return true;
    }
    
    private static int debugCounter = 0;
    private static boolean debugLocationSafety() {
        // Only debug first 10 location checks to avoid spam
        if (debugCounter < 10) {
            debugCounter++;
            return true;
        }
        return false;
    }
    
    public static void resetLocationSafetyDebug() {
        debugCounter = 0;
    }
    
    /**
     * Check if the ground is safe for standing
     */
    private static boolean isGroundSafe(Block groundBlock) {
        Material groundMaterial = groundBlock.getType();
        
        // Check for unsafe ground materials
        if (UNSAFE_GROUND_MATERIALS.contains(groundMaterial)) {
            return false;
        }
        
        // Check for falling blocks
        if (groundMaterial == Material.SAND || groundMaterial == Material.GRAVEL) {
            // Check if there's support below
            Block below = groundBlock.getRelative(0, -1, 0);
            if (below.getType() == Material.AIR || below.isLiquid()) {
                return false;
            }
        }
        
        return groundMaterial.isSolid();
    }
    
    /**
     * Check if a material is passable (player can stand in it)
     */
    private static boolean isPassableMaterial(Material material) {
        // Explicitly reject water and lava even though they're not solid
        if (material == Material.WATER || material == Material.STATIONARY_WATER ||
            material == Material.LAVA || material == Material.STATIONARY_LAVA) {
            return false;
        }
        
        return PASSABLE_MATERIALS.contains(material) || !material.isSolid();
    }

    /**
     * Check for dangerous blocks in the vicinity
     */
    private static boolean hasDangerousNearbyBlocks(World world, int x, int y, int z) {
        // Check 3x3x3 area around the location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (DANGEROUS_MATERIALS.contains(block.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Check if the ground is stable (not floating)
     */
    private static boolean hasStableGround(World world, int x, int y, int z) {
        // Check in a 3x3 area to ensure the platform is stable
        int solidBlocks = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(x + dx, y, z + dz);
                if (block.getType().isSolid() && !UNSAFE_GROUND_MATERIALS.contains(block.getType())) {
                    solidBlocks++;
                }
            }
        }
        // At least 3 solid blocks in 3x3 area for stability (reduced from 5)
        // This allows for edges of cliffs and smaller platforms
        return solidBlocks >= 3;
    }
    
    /**
     * Find the nearest safe location from a given point
     */
    public static Location findNearestSafeLocation(Location origin, int maxRadius) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        
        World world = origin.getWorld();
        
        // Check origin first
        if (isLocationSafe(origin)) {
            return origin;
        }
        
        // Search in expanding circles
        for (int radius = 1; radius <= maxRadius; radius++) {
            List<Location> candidates = new ArrayList<>();
            
            // Generate circle points
            int points = radius * 8; // More points for larger circles
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                int x = origin.getBlockX() + (int)(radius * Math.cos(angle));
                int z = origin.getBlockZ() + (int)(radius * Math.sin(angle));
                
                Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
                int y = world.getHighestBlockYAt(candidate);
                candidate.setY(y + 1);
                
                if (isLocationSafe(candidate)) {
                    candidates.add(candidate);
                }
            }
            
            // Return the closest safe location found at this radius
            if (!candidates.isEmpty()) {
                return candidates.stream()
                    .min(Comparator.comparingDouble(loc -> loc.distance(origin)))
                    .orElse(null);
            }
        }
        
        return null;
    }
    
    /**
     * Make a location safe by clearing dangerous blocks and ensuring solid ground
     */
    public static void makeLocationSafe(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        
        // Clear the area
        for (int dy = 0; dy <= 2; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.AIR);
        }
        
        // Ensure solid ground
        Block groundBlock = world.getBlockAt(x, y - 1, z);
        if (!groundBlock.getType().isSolid() || UNSAFE_GROUND_MATERIALS.contains(groundBlock.getType())) {
            groundBlock.setType(Material.STONE);
        }
        
        // Clear dangerous blocks nearby
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (DANGEROUS_MATERIALS.contains(block.getType())) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }
    
    /**
     * Get a safe spawn location for a player within a radius
     */
    public static Location getSafeSpawnLocation(World world, Location center, int radius) {
        Random random = new Random();
        
        // Try random locations
        for (int attempt = 0; attempt < 50; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;
            
            int x = center.getBlockX() + (int)(distance * Math.cos(angle));
            int z = center.getBlockZ() + (int)(distance * Math.sin(angle));
            
            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int y = world.getHighestBlockYAt(candidate);
            candidate.setY(y + 1);
            
            if (isLocationSafe(candidate)) {
                return candidate;
            }
        }
        
        // Fallback to nearest safe location
        Location nearest = findNearestSafeLocation(center, radius);
        if (nearest != null) {
            return nearest;
        }
        
        // Last resort - make center safe
        makeLocationSafe(center);
        return center;
    }
    
    /**
     * Calculate the highest safe Y coordinate at a location
     */
    public static int getHighestSafeY(World world, int x, int z) {
        int maxY = world.getHighestBlockYAt(x, z);
        
        // Start from top and work down to find safe spot
        for (int y = Math.min(maxY + 1, 255); y > 0; y--) {
            Location testLoc = new Location(world, x + 0.5, y, z + 0.5);
            if (isLocationSafe(testLoc)) {
                return y;
            }
        }
        
        // Default to highest block + 1
        return maxY + 1;
    }
    
    /**
     * Check if a chunk is safe for spawning
     */
    public static boolean isChunkSafe(Chunk chunk) {
        int safeLocations = 0;
        
        // Sample several locations in the chunk
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                Location loc = chunk.getBlock(x, 0, z).getLocation();
                loc.setY(chunk.getWorld().getHighestBlockYAt(loc) + 1);
                
                if (isLocationSafe(loc)) {
                    safeLocations++;
                }
            }
        }
        
        // At least 25% of sampled locations should be safe
        return safeLocations >= 4;
    }
    
    /**
     * Get the biome safety rating (0-1, higher is safer)
     */
    public static double getBiomeSafety(Biome biome) {
        switch (biome) {
            case OCEAN:
            case DEEP_OCEAN:
            case FROZEN_OCEAN:
                return 0.1; // Very unsafe - water
            case RIVER:
            case FROZEN_RIVER:
                return 0.2; // Unsafe - water
            case HELL:
                return 0.3; // Dangerous - nether
            case DESERT:
            case MESA:
                return 0.5; // Medium - hot, sparse resources
            case SWAMPLAND:
                return 0.6; // Medium - water patches
            case EXTREME_HILLS:
            case PLAINS:
            case FOREST:
            case BIRCH_FOREST:
            case ROOFED_FOREST:
                return 0.9; // Very good
            case JUNGLE:
            case JUNGLE_HILLS:
                return 0.8; // Good - dense but safe
            default:
                return 0.5; // Unknown - medium safety
        }
    }

    /**
     * Calculate safe border point using game border settings
     */
    public static Location calculateSafeBorderPoint(Entity entity) {
        if (entity == null) {
            UHC.getInstance().getLogger().warning("Cannot calculate border point: null entity");
            return null;
        }

        Game game = UHC.getInstance().getGame();
        if (game == null) {
            UHC.getInstance().getLogger().severe("Game instance is null - cannot calculate border point!");
            return null;
        }

        Location playerLoc = entity.getLocation();
        World world = playerLoc.getWorld();

        // Use game border settings - assuming world center is 0,0
        Location center = new Location(world, 0, 0, 0);
        double radius = game.getEffectiveBorderRadius();

        double deltaX = playerLoc.getX() - center.getX();
        double deltaZ = playerLoc.getZ() - center.getZ();

        // Find which border edge is closest
        double distToLeftRight = Math.min(Math.abs(deltaX + radius), Math.abs(deltaX - radius));
        double distToTopBottom = Math.min(Math.abs(deltaZ + radius), Math.abs(deltaZ - radius));

        double newX, newZ;
        double buffer = 5.0; // Safety buffer

        if (distToLeftRight < distToTopBottom) {
            // Teleport to left or right edge
            if (deltaX > 0) {
                newX = center.getX() + radius - buffer; // Right edge
            } else {
                newX = center.getX() - radius + buffer; // Left edge
            }
            newZ = Math.max(center.getZ() - radius + buffer,
                    Math.min(center.getZ() + radius + buffer, playerLoc.getZ()));
        } else {
            // Teleport to top or bottom edge
            if (deltaZ > 0) {
                newZ = center.getZ() + radius - buffer; // Bottom edge
            } else {
                newZ = center.getZ() - radius + buffer; // Top edge
            }
            newX = Math.max(center.getX() - radius + buffer,
                    Math.min(center.getX() + radius - buffer, playerLoc.getX()));
        }

        // Set safe Y coordinate with enhanced ground detection
        Location teleportLoc = new Location(world, newX, playerLoc.getY(), newZ);

        // Find the highest safe block
        int groundY = world.getHighestBlockYAt(teleportLoc);

        // Ensure we're not in a tree or structure
        for (int checkY = groundY; checkY < groundY + 10 && checkY < 255; checkY++) {
            Material mat = world.getBlockAt((int)newX, checkY, (int)newZ).getType();
            if (mat == Material.LEAVES || mat == Material.LEAVES_2 ||
                    mat == Material.LOG || mat == Material.LOG_2) {
                groundY = checkY + 1;
            }
        }

        teleportLoc.setY(Math.min(groundY + 1, 254));

        // Validate the calculated location
        if (!isLocationSafe(teleportLoc)) {
            // Try to find a nearby safe location
            Location safeLoc = findNearestSafeLocation(teleportLoc, 10);
            if (safeLoc != null) {
                teleportLoc = safeLoc;
            } else {
                UHC.getInstance().getLogger().warning("Could not find safe border teleport location!");
            }
        }

        return teleportLoc;
    }

    /**
     * Start progressive border teleportation with world validation
     */
    public static ProgressiveBorderTeleporter startProgressiveBorderTeleport(World world, CombatLogVillagerManager villagerManager) {
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot start border teleport: world is null!");
            return null;
        }

        ProgressiveBorderTeleporter teleporter = new ProgressiveBorderTeleporter(world.getWorldBorder(), world, villagerManager);
        teleporter.startTeleporting();
        return teleporter;
    }

    /**
     * Build walls with world validation and enhanced error handling
     */
    public static ProgressiveWallBuilder buildWallsProgressive(int radius, int height, World world) {
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot build walls: world is null!");
            return null;
        }

        String key = world.getName() + "_" + radius;

        // Cancel any existing builder for this area
        ProgressiveWallBuilder existingBuilder = activeBuilders.get(key);
        if (existingBuilder != null && !existingBuilder.isCancelled()) {
            existingBuilder.cancel();
            UHC.getInstance().getLogger().info("Cancelled previous wall builder for radius " + radius);
        }

        // Create and start new builder
        ProgressiveWallBuilder builder = new ProgressiveWallBuilder(world, radius, height);
        activeBuilders.put(key, builder);

        // Start building
        builder.startBuilding();

        UHC.getInstance().getLogger().info("Started building walls with radius " + radius + " in world: " + world.getName());

        return builder;
    }

    /**
     * Enhanced border shrinking using game border settings
     */
    public static void shrinkBorder(int size, World world) {
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot shrink border: world is null!");
            return;
        }

        ProgressiveWallBuilder builder = buildWallsProgressive(size, 5, world);

        if (builder == null) {
            UHC.getInstance().getLogger().severe("Failed to create wall builder for border shrink!");
            return;
        }

        // Optional: Add progress logging every few seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (builder.isCancelled()) {
                    cancel();
                    return;
                }

                double progress = builder.getProgress();
                if (progress < 100) {
                    long ticksRemaining = builder.getEstimatedTicksRemaining();
                    long secondsRemaining = ticksRemaining / 20;

                    if (secondsRemaining > 10) { // Only log for longer builds
                        UHC.getInstance().getLogger().info(String.format(
                                "Wall building progress in %s: %.1f%% (%d seconds remaining)",
                                world.getName(), progress, secondsRemaining));
                    }
                } else {
                    UHC.getInstance().getLogger().info("Wall building completed in world: " + world.getName());
                    cancel();
                }
            }
        }.runTaskTimer(UHC.getInstance(), 60, 60); // Every 3 seconds
    }

    /**
     * Cancel all active wall builders (useful for plugin disable)
     */
    public static void cancelAllWallBuilders() {
        for (ProgressiveWallBuilder builder : activeBuilders.values()) {
            if (!builder.isCancelled()) {
                builder.cancel();
            }
        }
        activeBuilders.clear();
        UHC.getInstance().getLogger().info("Cancelled all active wall builders");
    }

    /**
     * Enhanced entity in border check using game border settings
     */
    public static boolean isEntityInBorder(@NonNull Entity entity) {
        if (entity == null) {
            return true; // Assume safe if we can't check
        }

        Game game = UHC.getInstance().getGame();
        if (game == null) {
            UHC.getInstance().getLogger().warning("Game instance is null - assuming entity is in border");
            return true;
        }

        Location entityLocation = entity.getLocation();

        // Use game border settings consistently - assuming center at 0,0
        double radius = game.getEffectiveBorderRadius();

        double deltaX = Math.abs(entityLocation.getX());
        double deltaZ = Math.abs(entityLocation.getZ());

        return deltaX <= radius && deltaZ <= radius;
    }

    /**
     * Check if entity is within game border (same as isEntityInBorder now)
     */
    public static boolean isEntityWithinGameBorder(@NonNull Entity entity) {
        return isEntityInBorder(entity);
    }

    /**
     * Get distance from border center (assuming center at 0,0)
     */
    public static double getDistanceFromBorderCenter(@NonNull Entity entity) {
        if (entity == null) {
            return 0.0;
        }

        Location entityLocation = entity.getLocation();

        double deltaX = entityLocation.getX();
        double deltaZ = entityLocation.getZ();

        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    /**
     * Enhanced teleport to border with better error handling and validation
     */
    public static Location teleportToNearestBorderPoint(@NonNull Entity entity) {
        if (entity == null) {
            UHC.getInstance().getLogger().warning("Cannot teleport: entity is null");
            return null;
        }

        Location calculated = calculateSafeBorderPoint(entity);
        if (calculated == null) {
            UHC.getInstance().getLogger().warning("Failed to calculate safe border point for entity: " + entity.getType());
            return null;
        }

        // Perform the teleportation
        try {
            boolean teleported = entity.teleport(calculated);

            if (!teleported) {
                UHC.getInstance().getLogger().warning("Failed to teleport entity to border point!");
                return null;
            }

            if (entity instanceof Player) {
                Player player = (Player) entity;
                player.sendMessage(ChatColor.RED + "You were teleported due to border shrinking!");
                UHC.getInstance().getLogger().info("Teleported player " + player.getName() + " to border");
            } else {
                UHC.getInstance().getLogger().info("Teleported " + entity.getType() + " to border");
            }

            return calculated;

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error during border teleportation: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Border teleportation error", e);
            return null;
        }
    }

    /**
     * Validate that an entity needs to be teleported (is outside border)
     */
    public static boolean needsBorderTeleport(@NonNull Entity entity) {
        return !isEntityInBorder(entity);
    }

    /**
     * Debug border information
     */
    public static void debugBorderInfo(World world) {
        if (world == null) {
            UHC.getInstance().getLogger().info("Border Debug: World is null");
            return;
        }

        Game game = UHC.getInstance().getGame();

        UHC.getInstance().getLogger().info("=== Border Debug Information ===");
        UHC.getInstance().getLogger().info("World: " + world.getName());

        if (game != null) {
            UHC.getInstance().getLogger().info("Game Border Center: (0, 0)"); // Assuming center at spawn
            UHC.getInstance().getLogger().info("Game Border Radius: " + game.getEffectiveBorderRadius());
            UHC.getInstance().getLogger().info("Game Border Size: " + game.getEffectiveBorderSize());
        } else {
            UHC.getInstance().getLogger().info("Game instance: null");
        }

        UHC.getInstance().getLogger().info("================================");
    }

    /**
     * Get all entities outside the border
     */
    public static List<Entity> getEntitiesOutsideBorder(World world) {
        List<Entity> outsideEntities = new ArrayList<>();

        if (world == null) {
            return outsideEntities;
        }

        for (Entity entity : world.getEntities()) {
            if (!isEntityInBorder(entity)) {
                outsideEntities.add(entity);
            }
        }

        return outsideEntities;
    }

    /**
     * Get all players outside the border
     */
    public static List<Player> getPlayersOutsideBorder(World world) {
        List<Player> outsidePlayers = new ArrayList<>();

        if (world == null) {
            return outsidePlayers;
        }

        for (Player player : world.getPlayers()) {
            if (!isEntityInBorder(player)) {
                outsidePlayers.add(player);
            }
        }

        return outsidePlayers;
    }

    /**
     * Get active wall builders for monitoring
     */
    public static Map<String, ProgressiveWallBuilder> getActiveBuilders() {
        return new HashMap<>(activeBuilders);
    }

    /**
     * Check if any wall builders are currently active
     */
    public static boolean hasActiveBuilders() {
        return activeBuilders.values().stream().anyMatch(builder -> !builder.isCancelled());
    }

    /**
     * Get wall building progress for a specific world
     */
    public static double getWallBuildingProgress(World world, int radius) {
        if (world == null) return 100.0;

        String key = world.getName() + "_" + radius;
        ProgressiveWallBuilder builder = activeBuilders.get(key);

        if (builder == null || builder.isCancelled()) {
            return 100.0;
        }

        return builder.getProgress();
    }

    /**
     * Teleport a player safely to avoid suffocation with chunk loading
     */
    public static void safeTeleport(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }

        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        // Check if near a scatter location and preload appropriately
        try {
            if (ProgressiveScatterManager.isNearScatterLocation(location)) {
                ProgressiveScatterManager.preloadScatterChunks(location);
            } else {
                // Ensure destination chunk is loaded
                Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
            }
        } catch (Exception e) {
            // Fallback to basic chunk loading if scatter manager fails
            Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
            if (!chunk.isLoaded()) {
                chunk.load(true);
            }
        }

        // Calculate safe Y coordinate
        int groundY = world.getHighestBlockYAt(x, z);
        Location teleportLoc = new Location(world, location.getX(), groundY + 1, location.getZ(),
                location.getYaw(), location.getPitch());

        // Ensure Y is within bounds
        teleportLoc.setY(Math.max(1, Math.min(groundY + 1, 254)));

        // Validate the calculated location
        if (!isLocationSafe(teleportLoc)) {
            // Try to find a nearby safe location
            Location safeLoc = findNearestSafeLocation(teleportLoc, 10);
            if (safeLoc != null) {
                teleportLoc = safeLoc;
            } else {
                // Force make it safe
                makeLocationSafe(teleportLoc);
            }
        }

        player.teleport(teleportLoc);
    }
}
package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.world.WorldManager;
import lombok.NonNull;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameUtil {

    // Track active wall builders to prevent multiple builders for same area
    private static final Map<String, ProgressiveWallBuilder> activeBuilders = new HashMap<>();

    /**
     * Get scatter location with world manager integration
     */
    public static Location getScatterLocation(int radius, Game game) {
        World world = game.getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot get scatter location: world is null!");
            return null;
        }

        return getScatterLocation(radius, world);
    }

    /**
     * Get scatter location with specific world
     */
    public static Location getScatterLocation(int radius, World world) {
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot get scatter location: world parameter is null!");
            return null;
        }

        Random random = new Random();

        for (int attempt = 0; attempt < 15; attempt++) { // Increased attempts for better locations
            int x = random.nextInt(radius * 2) - radius;
            int z = random.nextInt(radius * 2) - radius;

            Location location = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(location);
            location.setY(groundY + 1);

            // Enhanced safety checks
            if (isLocationSafe(location) && !isLocationNearWorldBorder(location, world)) {
                return location;
            }
        }

        // Enhanced fallback with world center validation
        Location fallback = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
        if (isLocationSafe(fallback)) {
            return fallback;
        }

        // Last resort - find any safe location
        return findAnySafeLocation(world, 100);
    }

    /**
     * Find any safe location within a radius
     */
    private static Location findAnySafeLocation(World world, int searchRadius) {
        Random random = new Random();

        for (int attempt = 0; attempt < 50; attempt++) {
            int x = random.nextInt(searchRadius * 2) - searchRadius;
            int z = random.nextInt(searchRadius * 2) - searchRadius;

            Location candidate = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);

            if (isLocationSafe(candidate)) {
                cc.kasumi.uhc.UHC.getInstance().getLogger().info("Found safe fallback location at: " +
                        x + ", " + candidate.getBlockY() + ", " + z);
                return candidate;
            }
        }

        // Absolute fallback
        UHC.getInstance().getLogger().warning("Could not find safe location, using world spawn");
        return world.getSpawnLocation();
    }

    /**
     * Check if location is near world border
     */
    private static boolean isLocationNearWorldBorder(Location location, World world) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double size = border.getSize();
        double radius = size / 2;

        double distance = location.distance(center);
        return distance > (radius - 50); // Stay 50 blocks away from border
    }

    /**
     * Enhanced location safety check
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

        Material groundMaterial = world.getBlockAt(x, y - 1, z).getType();
        Material feetMaterial = world.getBlockAt(x, y, z).getType();
        Material headMaterial = world.getBlockAt(x, y + 1, z).getType();

        // Ensure there's solid ground (not air, lava, water, or other unsafe blocks)
        if (groundMaterial == Material.AIR ||
                groundMaterial == Material.LAVA ||
                groundMaterial == Material.STATIONARY_LAVA ||
                groundMaterial == Material.WATER ||
                groundMaterial == Material.STATIONARY_WATER ||
                groundMaterial == Material.FIRE ||
                groundMaterial == Material.CACTUS) {
            return false;
        }

        // Check for unsafe ground materials
        if (groundMaterial == Material.SAND && world.getBlockAt(x, y - 2, z).getType() == Material.AIR) {
            return false; // Avoid floating sand
        }

        // Ensure feet space is clear (air or passable blocks)
        if (!isPassableMaterial(feetMaterial)) {
            return false;
        }

        // Ensure headspace is clear (air or small passable blocks)
        if (!isHeadPassableMaterial(headMaterial)) {
            return false;
        }

        // Additional check for dangerous nearby blocks
        if (hasDangerousNearbyBlocks(world, x, y, z)) {
            return false;
        }

        return true;
    }

    /**
     * Check for dangerous blocks nearby
     */
    private static boolean hasDangerousNearbyBlocks(World world, int x, int y, int z) {
        // Check 3x3 area around the location
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material nearby = world.getBlockAt(x + dx, y, z + dz).getType();
                if (nearby == Material.LAVA || nearby == Material.STATIONARY_LAVA ||
                        nearby == Material.FIRE || nearby == Material.CACTUS) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a material is passable for feet level
     */
    private static boolean isPassableMaterial(Material material) {
        return material == Material.AIR ||
                material == Material.LONG_GRASS ||
                material == Material.YELLOW_FLOWER ||
                material == Material.RED_ROSE ||
                material == Material.BROWN_MUSHROOM ||
                material == Material.RED_MUSHROOM ||
                material == Material.DEAD_BUSH ||
                material == Material.SAPLING ||
                material == Material.SNOW ||
                material == Material.WHEAT ||  // crops
                material == Material.CARROT ||
                material == Material.POTATO;
    }

    /**
     * Check if a material is passable for head level (more restrictive)
     */
    private static boolean isHeadPassableMaterial(Material material) {
        return material == Material.AIR ||
                material == Material.LONG_GRASS ||
                material == Material.YELLOW_FLOWER ||
                material == Material.RED_ROSE ||
                material == Material.SNOW;
    }

    /**
     * Progressive scatter with game integration
     */
    public static ProgressiveScatterManager startProgressiveScatter(Game game, List<UUID> playerUUIDs, int radius) {
        if (!game.isWorldReady()) {
            UHC.getInstance().getLogger().severe("Cannot start scatter: world not ready!");
            return null;
        }

        ProgressiveScatterManager manager = new ProgressiveScatterManager(game, playerUUIDs, radius);
        manager.startScattering();
        return manager;
    }

    /**
     * Calculate safe border point with enhanced world validation
     */
    public static Location calculateSafeBorderPoint(Entity entity, WorldBorder border) {
        if (entity == null || border == null) {
            cc.kasumi.uhc.UHC.getInstance().getLogger().warning("Cannot calculate border point: null entity or border");
            return null;
        }

        Location playerLoc = entity.getLocation();
        Location center = border.getCenter();

        if (playerLoc.getWorld() != center.getWorld()) {
            UHC.getInstance().getLogger().warning("Entity and border are in different worlds!");
            return null;
        }

        double size = border.getSize();
        double radius = size / 2;

        double deltaX = playerLoc.getX() - center.getX();
        double deltaZ = playerLoc.getZ() - center.getZ();

        // Find which border edge is closest
        double distToLeftRight = Math.min(Math.abs(deltaX + radius), Math.abs(deltaX - radius));
        double distToTopBottom = Math.min(Math.abs(deltaZ + radius), Math.abs(deltaZ - radius));

        double newX, newZ;
        double buffer = 5.0; // Increased buffer for safety

        if (distToLeftRight < distToTopBottom) {
            // Teleport to left or right edge
            if (deltaX > 0) {
                newX = center.getX() + radius - buffer; // Right edge
            } else {
                newX = center.getX() - radius + buffer; // Left edge
            }
            newZ = Math.max(center.getZ() - radius + buffer,
                    Math.min(center.getZ() + radius - buffer, playerLoc.getZ()));
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
        World world = playerLoc.getWorld();
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
                cc.kasumi.uhc.UHC.getInstance().getLogger().warning("Could not find safe border teleport location!");
            }
        }

        return teleportLoc;
    }

    /**
     * Find nearest safe location within radius
     */
    private static Location findNearestSafeLocation(Location center, int radius) {
        World world = center.getWorld();
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();

        for (int r = 1; r <= radius; r++) {
            for (int x = centerX - r; x <= centerX + r; x++) {
                for (int z = centerZ - r; z <= centerZ + r; z++) {
                    // Only check the perimeter of the current radius
                    if (Math.abs(x - centerX) == r || Math.abs(z - centerZ) == r) {
                        Location candidate = new Location(world, x + 0.5,
                                world.getHighestBlockYAt(x, z) + 1, z + 0.5);

                        if (isLocationSafe(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Start progressive border teleportation with world validation
     */
    public static ProgressiveBorderTeleporter startProgressiveBorderTeleport(WorldBorder worldBorder, World world, CombatLogVillagerManager villagerManager) {
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot start border teleport: world is null!");
            return null;
        }

        if (worldBorder == null) {
            UHC.getInstance().getLogger().severe("Cannot start border teleport: worldBorder is null!");
            return null;
        }

        ProgressiveBorderTeleporter teleporter = new ProgressiveBorderTeleporter(worldBorder, world, villagerManager);
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
     * Enhanced border shrinking with world validation
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
        cc.kasumi.uhc.UHC.getInstance().getLogger().info("Cancelled all active wall builders");
    }

    /**
     * Enhanced entity in border check with world validation
     */
    public static boolean isEntityInBorder(@NonNull Entity entity, WorldBorder border) {
        if (entity == null || border == null) {
            return true; // Assume safe if we can't check
        }

        Location entityLocation = entity.getLocation();
        Location center = border.getCenter();

        if (entityLocation.getWorld() != center.getWorld()) {
            UHC.getInstance().getLogger().warning("Entity and border in different worlds!");
            return true; // Assume safe if different worlds
        }

        double size = border.getSize();
        double radius = size / 2;

        double deltaX = Math.abs(entityLocation.getX() - center.getX());
        double deltaZ = Math.abs(entityLocation.getZ() - center.getZ());

        return deltaX <= radius && deltaZ <= radius;
    }

    /**
     * Enhanced teleport to border with better error handling and validation
     */
    public static Location teleportToNearestBorderPoint(@NonNull Entity entity, WorldBorder border) {
        if (entity == null || border == null) {
            UHC.getInstance().getLogger().warning("Cannot teleport: entity or border is null");
            return null;
        }

        Location calculated = calculateSafeBorderPoint(entity, border);
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
            e.printStackTrace();
            return null;
        }
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
}
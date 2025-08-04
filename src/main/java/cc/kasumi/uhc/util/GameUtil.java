package cc.kasumi.uhc.util;

import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.Game;
import lombok.NonNull;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameUtil {

    // Track active wall builders to prevent multiple builders for same area
    private static final Map<String, ProgressiveWallBuilder> activeBuilders = new HashMap<>();

    // Add these methods to your existing GameUtil class:

    public static Location getScatterLocation(int radius, String worldName) {
        World world = Bukkit.getWorld(worldName);
        Random random = new Random();

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = random.nextInt(radius * 2) - radius;
            int z = random.nextInt(radius * 2) - radius;

            Location location = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(location);
            location.setY(groundY + 1);

            // Basic safety checks
            if (isLocationSafe(location)) {
                return location;
            }
        }

        // Fallback to center if no safe location found
        return new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
    }

    /**
     * Check if a location is safe for player teleportation
     */
    /*
    public static boolean isLocationSafe(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Check for lava or water at feet level
        Material groundMaterial = world.getBlockAt(x, y - 1, z).getType();
        Material feetMaterial = world.getBlockAt(x, y, z).getType();
        Material headMaterial = world.getBlockAt(x, y + 1, z).getType();

        // Avoid lava, water, and ensure airspace
        if (groundMaterial == Material.LAVA || groundMaterial == Material.STATIONARY_LAVA ||
                groundMaterial == Material.WATER || groundMaterial == Material.STATIONARY_WATER ||
                feetMaterial != Material.AIR || headMaterial != Material.AIR) {
            return false;
        }

        return true;
    }

     */

    public static boolean isLocationSafe(Location location) {
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

        return true;
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
     * Progressive scatter with chunk preloading (replaces old scatterPlayer method)
     */
    public static ProgressiveScatterManager startProgressiveScatter(Game game, List<UUID> playerUUIDs, int radius) {
        ProgressiveScatterManager manager = new ProgressiveScatterManager(game, playerUUIDs, radius);
        manager.startScattering();
        return manager;
    }

    /**
     * Calculate safe border point without immediate teleportation
     */
    public static Location calculateSafeBorderPoint(Entity entity, WorldBorder border) {
        Location playerLoc = entity.getLocation();
        Location center = border.getCenter();
        double size = border.getSize();
        double radius = size / 2;

        double deltaX = playerLoc.getX() - center.getX();
        double deltaZ = playerLoc.getZ() - center.getZ();

        // Find which border edge is closest
        double distToLeftRight = Math.min(Math.abs(deltaX + radius), Math.abs(deltaX - radius));
        double distToTopBottom = Math.min(Math.abs(deltaZ + radius), Math.abs(deltaZ - radius));

        double newX, newZ;

        if (distToLeftRight < distToTopBottom) {
            // Teleport to left or right edge (3 blocks inward)
            if (deltaX > 0) {
                newX = center.getX() + radius - 3; // Right edge, move 3 blocks left
            } else {
                newX = center.getX() - radius + 3; // Left edge, move 3 blocks right
            }
            newZ = Math.max(center.getZ() - radius + 3, Math.min(center.getZ() + radius - 3, playerLoc.getZ()));
        } else {
            // Teleport to top or bottom edge (3 blocks inward)
            if (deltaZ > 0) {
                newZ = center.getZ() + radius - 3; // Bottom edge, move 3 blocks up
            } else {
                newZ = center.getZ() - radius + 3; // Top edge, move 3 blocks down
            }
            newX = Math.max(center.getX() - radius + 3, Math.min(center.getX() + radius - 3, playerLoc.getX()));
        }

        // Set safe Y coordinate
        Location teleportLoc = new Location(playerLoc.getWorld(), newX, playerLoc.getY(), newZ);
        teleportLoc.setY(playerLoc.getWorld().getHighestBlockYAt(teleportLoc) + 1);

        return teleportLoc;
    }

    /**
     * Start progressive border teleportation
     */
    public static ProgressiveBorderTeleporter startProgressiveBorderTeleport(WorldBorder worldBorder, World world, CombatLogVillagerManager villagerManager) {
        ProgressiveBorderTeleporter teleporter = new ProgressiveBorderTeleporter(worldBorder, world, villagerManager);
        teleporter.startTeleporting();
        return teleporter;
    }

    public static void revivePlayer(Player player) {

    }

    /**
     * Builds walls progressively to prevent server lag
     * @param radius The radius of the border
     * @param height The height of the walls
     * @param world The world to build in
     * @return ProgressiveWallBuilder instance for tracking progress
     */
    public static ProgressiveWallBuilder buildWallsProgressive(int radius, int height, World world) {
        String key = world.getName() + "_" + radius;

        // Cancel any existing builder for this area
        ProgressiveWallBuilder existingBuilder = activeBuilders.get(key);
        if (existingBuilder != null && !existingBuilder.isCancelled()) {
            existingBuilder.cancel();
            Bukkit.getLogger().info("Cancelled previous wall builder for radius " + radius);
        }

        // Create and start new builder
        ProgressiveWallBuilder builder = new ProgressiveWallBuilder(world, radius, height);
        activeBuilders.put(key, builder);

        // Start building
        builder.startBuilding();

        return builder;
    }

    /**
     * Legacy method - now uses progressive building
     */
    @Deprecated
    public static void buildWalls(int radius, int height, World world) {
        buildWallsProgressive(radius, height, world);
    }

    /**
     * Build walls with progress callback
     */
    public static void shrinkBorder(int size, World world) {
        ProgressiveWallBuilder builder = buildWallsProgressive(size, 5, world);

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

                    Bukkit.getLogger().info(String.format("Wall building progress: %.1f%% (%d seconds remaining)",
                            progress, secondsRemaining));
                } else {
                    cancel();
                }
            }
        }.runTaskTimer(cc.kasumi.uhc.UHC.getInstance(), 60, 60); // Every 3 seconds
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
        Bukkit.getLogger().info("Cancelled all active wall builders");
    }

    public static boolean isEntityInBorder(@NonNull Entity entity, WorldBorder border) {
        Location entityLocation = entity.getLocation();
        Location center = border.getCenter();

        if (entityLocation.getWorld() != center.getWorld()) {
            return true;
        }
        double size = border.getSize();
        double radius = size / 2;

        double deltaX = Math.abs(entityLocation.getX() - center.getX());
        double deltaZ = Math.abs(entityLocation.getZ() - center.getZ());

        return deltaX <= radius && deltaZ <= radius;
    }

    public static Location teleportToNearestBorderPoint(@NonNull Entity entity, WorldBorder border) {
        Location playerLoc = entity.getLocation();
        Location center = border.getCenter();
        double size = border.getSize();
        double radius = size / 2;

        double deltaX = playerLoc.getX() - center.getX();
        double deltaZ = playerLoc.getZ() - center.getZ();

        // Find which border edge is closest
        double distToLeftRight = Math.min(Math.abs(deltaX + radius), Math.abs(deltaX - radius));
        double distToTopBottom = Math.min(Math.abs(deltaZ + radius), Math.abs(deltaZ - radius));

        double newX, newZ;

        if (distToLeftRight < distToTopBottom) {
            // Teleport to left or right edge (3 blocks inward)
            if (deltaX > 0) {
                newX = center.getX() + radius - 3; // Right edge, move 3 blocks left
            } else {
                newX = center.getX() - radius + 3; // Left edge, move 3 blocks right
            }
            newZ = Math.max(center.getZ() - radius + 3, Math.min(center.getZ() + radius - 3, playerLoc.getZ()));
        } else {
            // Teleport to top or bottom edge (3 blocks inward)
            if (deltaZ > 0) {
                newZ = center.getZ() + radius - 3; // Bottom edge, move 3 blocks up
            } else {
                newZ = center.getZ() - radius + 3; // Top edge, move 3 blocks down
            }
            newX = Math.max(center.getX() - radius + 3, Math.min(center.getX() + radius - 3, playerLoc.getX()));
        }

        // Set safe Y coordinate
        Location teleportLoc = new Location(playerLoc.getWorld(), newX, playerLoc.getY(), newZ);
        teleportLoc.setY(playerLoc.getWorld().getHighestBlockYAt(teleportLoc) + 1);

        entity.teleport(teleportLoc);

        if (entity instanceof Player) {
            entity.sendMessage(ChatColor.RED + "You were teleported due to border shrinking!");
        }

        return teleportLoc;
    }
}
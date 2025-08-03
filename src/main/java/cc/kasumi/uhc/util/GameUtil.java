package cc.kasumi.uhc.util;

import lombok.NonNull;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Random;

public class GameUtil {

    public static Location getScatterLocation(int radius, String worldName) {
        World world = Bukkit.getWorld(worldName);
        Random random = new Random();

        int x = random.nextInt(radius + radius) - radius;
        int z = random.nextInt(radius + radius) - radius;
        double y = world.getHighestBlockYAt(x, z) + 3;

        return new Location(world, x, y, z);
    }

    public static void scatterPlayer(Player player, int radius) {
        player.teleport(getScatterLocation(radius, "world"));
    }

    public static void revivePlayer(Player player) {

    }

    /* Doesn't work
    public static void shrinkBorder(int size, Location start) {
        // Shrink
        for (int y = 0; y < 5; y++) {
            for (double x = start.getX() - size; x <= start.getX() + size; x++) {
                for (double z = start.getZ() - size; z <= start.getZ() + size; z++) {
                    int blockY = start.getWorld().getHighestBlockYAt((int) x, (int) z) + y;
                    Location blockLocation = new Location(start.getWorld(), x, blockY, z);

                    blockLocation.getBlock().setType(Material.BEDROCK);
                }
            }
        }
    }
     */

    /*
    public static void buildWalls(int radius, int height, World world) {
        Location location1 = new Location(world, 0.0D, 59.0D, 0.0D);

        int i = height;

        while (i < height + height) {
            for (int x = location1.getBlockX() - radius; x <= location1.getBlockX() + radius; x++) {
                for (int y = 58; y <= 58; y++) {
                    for (int z = location1.getBlockZ() - radius; z <= location1.getBlockZ() + radius; z++) {
                        if ((x == location1.getBlockX() - radius) || (x == location1.getBlockX() + radius) || (z == location1.getBlockZ() - radius) || (z == location1.getBlockZ() + radius)) {
                            Location location2 = new Location(world, x, y, z);
                            location2.setY(world.getHighestBlockYAt(location2));
                            location2.getBlock().setType(Material.BEDROCK);
                        }
                    }
                }
            }

            i++;
        }
    }

     */

    public static void buildWalls(int radius, int height, World world) {
        Location center = new Location(world, 0.0D, 0.0D, 0.0D);

        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                if ((x == center.getBlockX() - radius) ||
                        (x == center.getBlockX() + radius) ||
                        (z == center.getBlockZ() - radius) ||
                        (z == center.getBlockZ() + radius)) {

                    Location groundLocation = new Location(world, x, 0, z);
                    int groundY = world.getHighestBlockYAt(groundLocation);

                    // Start at groundY instead of groundY + 1
                    for (int y = groundY; y < groundY + height; y++) {
                        Location blockLocation = new Location(world, x, y, z);
                        blockLocation.getBlock().setType(Material.BEDROCK);
                    }
                }
            }
        }
    }

    public static void shrinkBorder(int size, World world) {
        buildWalls(size, 5, world);
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

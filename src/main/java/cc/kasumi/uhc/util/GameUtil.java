package cc.kasumi.uhc.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    public static void shrinkBorder(int size, World world) {
        buildWalls(size, 5, world);
    }
}

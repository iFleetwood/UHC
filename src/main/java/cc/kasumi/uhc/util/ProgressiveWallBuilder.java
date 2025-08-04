package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds walls progressively to prevent server lag
 */
public class ProgressiveWallBuilder extends BukkitRunnable {

    private final World world;
    private final int radius;
    private final int height;
    private final List<Location> wallLocations;
    private int currentIndex = 0;
    private final int blocksPerTick;

    @Getter
    private boolean cancelled = false; // Add this for 1.8.8 compatibility

    public ProgressiveWallBuilder(World world, int radius, int height) {
        this.world = world;
        this.radius = radius;
        this.height = height;
        this.wallLocations = calculateWallLocations();
        this.blocksPerTick = calculateBlocksPerTick(radius);

        Bukkit.getLogger().info("Building " + wallLocations.size() + " wall blocks at " + blocksPerTick + " blocks per tick");
    }

    /**
     * Calculate how many blocks to place per tick based on border size
     * Larger borders = slower placement to prevent lag
     * Smaller borders = faster placement since there are fewer blocks
     */
    private int calculateBlocksPerTick(int radius) {
        if (radius > 1000) return 50;      // Very large: 50 blocks/tick
        if (radius > 500) return 100;      // Large: 100 blocks/tick
        if (radius > 250) return 200;      // Medium: 200 blocks/tick
        if (radius > 100) return 300;      // Small: 300 blocks/tick
        return 500;                        // Very small: 500 blocks/tick
    }

    /**
     * Pre-calculate all wall block locations to avoid doing this during placement
     */
    private List<Location> calculateWallLocations() {
        List<Location> locations = new ArrayList<>();
        Location center = new Location(world, 0, 0, 0);

        // Calculate perimeter blocks
        for (int x = center.getBlockX() - radius; x <= center.getBlockX() + radius; x++) {
            for (int z = center.getBlockZ() - radius; z <= center.getBlockZ() + radius; z++) {
                // Only place blocks on the perimeter (border)
                if ((x == center.getBlockX() - radius) ||
                        (x == center.getBlockX() + radius) ||
                        (z == center.getBlockZ() - radius) ||
                        (z == center.getBlockZ() + radius)) {

                    int groundY = world.getHighestBlockYAt(x, z);

                    // Add blocks for each height level
                    for (int y = groundY; y < groundY + height; y++) {
                        locations.add(new Location(world, x, y, z));
                    }
                }
            }
        }

        return locations;
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        int blocksPlaced = 0;

        // Place blocks in batches
        while (currentIndex < wallLocations.size() && blocksPlaced < blocksPerTick) {
            Location location = wallLocations.get(currentIndex);
            location.getBlock().setType(Material.BEDROCK);

            currentIndex++;
            blocksPlaced++;
        }

        // Check if we're done
        if (currentIndex >= wallLocations.size()) {
            Bukkit.getLogger().info("Wall building completed! Built " + wallLocations.size() + " blocks.");
            cancel();
        }
    }

    /**
     * Start building the walls
     */
    public void startBuilding() {
        // Run every tick for maximum speed while preventing lag
        this.runTaskTimer(UHC.getInstance(), 0, 1);
    }

    /**
     * Get progress as a percentage
     */
    public double getProgress() {
        if (wallLocations.isEmpty()) return 100.0;
        return (double) currentIndex / wallLocations.size() * 100.0;
    }

    /**
     * Get estimated ticks remaining
     */
    public long getEstimatedTicksRemaining() {
        if (currentIndex >= wallLocations.size()) return 0;
        int blocksRemaining = wallLocations.size() - currentIndex;
        return (long) Math.ceil((double) blocksRemaining / blocksPerTick);
    }

    /**
     * Cancel the wall builder (1.8.8 compatibility)
     */
    @Override
    public void cancel() {
        this.cancelled = true;
        super.cancel();
    }
}
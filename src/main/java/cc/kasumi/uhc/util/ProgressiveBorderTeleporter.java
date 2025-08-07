package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.Game;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Fixed ProgressiveBorderTeleporter that uses game border settings
 */
public class ProgressiveBorderTeleporter extends BukkitRunnable {

    private final WorldBorder worldBorder; // Keep for damage settings
    private final World world;
    private final CombatLogVillagerManager villagerManager;
    private final Game game; // FIXED: Add game reference

    // FIXED: Use game border settings instead of world border
    private final Location gameBorderCenter;
    private final double gameBorderRadius;

    // Entities to teleport
    private final List<Player> playersToTeleport = new ArrayList<>();
    private final List<VillagerTeleportData> villagersToTeleport = new ArrayList<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // State tracking
    @Getter
    private TeleportPhase currentPhase = TeleportPhase.FINDING_ENTITIES;
    private int currentEntityIndex = 0;
    private Iterator<Chunk> chunkIterator;

    @Getter
    private boolean cancelled = false;

    // Configuration
    private static final int ENTITIES_CHECKED_PER_TICK = 5;
    private static final int CHUNKS_PER_TICK = 3;
    private static final int TELEPORTS_PER_TICK = 2;

    public enum TeleportPhase {
        FINDING_ENTITIES,
        CALCULATING_DESTINATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_ENTITIES,
        COMPLETED
    }

    private static class VillagerTeleportData {
        final Villager villager;
        final CombatLogPlayer combatLogPlayer;
        Location destination;

        VillagerTeleportData(Villager villager, CombatLogPlayer combatLogPlayer) {
            this.villager = villager;
            this.combatLogPlayer = combatLogPlayer;
        }
    }

    public ProgressiveBorderTeleporter(WorldBorder worldBorder, World world, CombatLogVillagerManager villagerManager) {
        this.worldBorder = worldBorder;
        this.world = world;
        this.villagerManager = villagerManager;

        // FIXED: Get game instance and use its border settings
        this.game = UHC.getInstance().getGame();

        if (game != null) {
            // Use game's border configuration
            this.gameBorderCenter = new Location(world, 0, 0, 0); // UHC always uses 0,0
            this.gameBorderRadius = game.getEffectiveBorderRadius();

            UHC.getInstance().getLogger().info("Border teleporter using game settings - " +
                    "Center: (0, 0), Radius: " + String.format("%.1f", gameBorderRadius) +
                    " (Game border: " + game.getCurrentBorderSize() + ")");
        } else {
            // Fallback to world border if game is not available
            this.gameBorderCenter = worldBorder.getCenter();
            this.gameBorderRadius = worldBorder.getSize() / 2.0;

            // During initialization, game might not be available yet - this is normal
            UHC.getInstance().getLogger().fine("Game instance not yet available, using world border as fallback");
        }
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        switch (currentPhase) {
            case FINDING_ENTITIES:
                findEntitiesOutsideBorder();
                break;
            case CALCULATING_DESTINATIONS:
                calculateDestinations();
                break;
            case PRELOADING_CHUNKS:
                preloadChunks();
                break;
            case TELEPORTING_ENTITIES:
                teleportEntities();
                break;
            case COMPLETED:
                complete();
                break;
        }
    }

    private void findEntitiesOutsideBorder() {
        // FIXED: Find all players outside the GAME border (not world border)
        for (Player player : world.getPlayers()) {
            if (!isEntityWithinGameBorder(player)) {
                playersToTeleport.add(player);
            }
        }

        // FIXED: Find all combat log villagers outside the GAME border
        for (Map.Entry<Villager, CombatLogPlayer> entry : villagerManager.getCombatLogVillagers().entrySet()) {
            Villager villager = entry.getKey();
            CombatLogPlayer combatLogPlayer = entry.getValue();

            if (!isEntityWithinGameBorder(villager)) {
                villagersToTeleport.add(new VillagerTeleportData(villager, combatLogPlayer));
            }
        }

        int totalEntities = playersToTeleport.size() + villagersToTeleport.size();

        if (totalEntities == 0) {
            currentPhase = TeleportPhase.COMPLETED;
            Bukkit.getLogger().info("No entities outside game border, teleportation complete");
        } else {
            currentPhase = TeleportPhase.CALCULATING_DESTINATIONS;
            Bukkit.getLogger().info("Found " + totalEntities + " entities outside game border (" +
                    playersToTeleport.size() + " players, " + villagersToTeleport.size() + " villagers)");
        }
    }

    private void calculateDestinations() {
        int calculationsThisTick = 0;

        // Calculate destinations for players
        while (currentEntityIndex < playersToTeleport.size() && calculationsThisTick < ENTITIES_CHECKED_PER_TICK) {
            Player player = playersToTeleport.get(currentEntityIndex);

            // FIXED: Use game border for calculation instead of world border
            Location destination = calculateSafeGameBorderPoint(player);

            if (destination != null) {
                player.setMetadata("borderTeleportDestination",
                        new org.bukkit.metadata.FixedMetadataValue(UHC.getInstance(), destination));
                chunksToPreload.add(destination.getChunk());
            }

            currentEntityIndex++;
            calculationsThisTick++;
        }

        // Calculate destinations for villagers
        int villagerStartIndex = currentEntityIndex - playersToTeleport.size();
        while (villagerStartIndex >= 0 && villagerStartIndex < villagersToTeleport.size() &&
                calculationsThisTick < ENTITIES_CHECKED_PER_TICK) {
            VillagerTeleportData data = villagersToTeleport.get(villagerStartIndex);

            // FIXED: Use game border for calculation
            data.destination = calculateSafeGameBorderPoint(data.villager);

            if (data.destination != null) {
                chunksToPreload.add(data.destination.getChunk());
            }

            currentEntityIndex++;
            calculationsThisTick++;
            villagerStartIndex++;
        }

        // Check if done calculating
        if (currentEntityIndex >= playersToTeleport.size() + villagersToTeleport.size()) {
            currentPhase = TeleportPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentEntityIndex = 0;

            Bukkit.getLogger().info("Calculated destinations using game border, preloading " +
                    chunksToPreload.size() + " chunks");
        }
    }

    /**
     * FIXED: Calculate safe border point using game border settings instead of world border
     */
    private Location calculateSafeGameBorderPoint(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }

        Location entityLocation = entity.getLocation();

        // Calculate distance from game border center (0, 0)
        double deltaX = entityLocation.getX() - gameBorderCenter.getX();
        double deltaZ = entityLocation.getZ() - gameBorderCenter.getZ();

        double safeRadius = gameBorderRadius - 5.0; // Safety buffer

        // Find which border edge is closest
        double distToLeftRight = Math.min(Math.abs(deltaX + safeRadius), Math.abs(deltaX - safeRadius));
        double distToTopBottom = Math.min(Math.abs(deltaZ + safeRadius), Math.abs(deltaZ - safeRadius));

        double newX, newZ;

        if (distToLeftRight < distToTopBottom) {
            // Teleport to left or right edge
            if (deltaX > 0) {
                newX = gameBorderCenter.getX() + safeRadius; // Right edge
            } else {
                newX = gameBorderCenter.getX() - safeRadius; // Left edge
            }
            newZ = Math.max(gameBorderCenter.getZ() - safeRadius,
                    Math.min(gameBorderCenter.getZ() + safeRadius, entityLocation.getZ()));
        } else {
            // Teleport to top or bottom edge
            if (deltaZ > 0) {
                newZ = gameBorderCenter.getZ() + safeRadius; // Bottom edge
            } else {
                newZ = gameBorderCenter.getZ() - safeRadius; // Top edge
            }
            newX = Math.max(gameBorderCenter.getX() - safeRadius,
                    Math.min(gameBorderCenter.getX() + safeRadius, entityLocation.getX()));
        }

        // Set safe Y coordinate
        Location teleportLoc = new Location(world, newX, entityLocation.getY(), newZ);
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
        if (!GameUtil.isLocationSafe(teleportLoc)) {
            // Try to find a nearby safe location
            Location safeLoc = findNearestSafeLocationInGameBorder(teleportLoc, 10);
            if (safeLoc != null) {
                teleportLoc = safeLoc;
            } else {
                UHC.getInstance().getLogger().warning("Could not find safe game border teleport location!");
            }
        }

        return teleportLoc;
    }

    /**
     * FIXED: Check if entity is within game border instead of world border
     */
    private boolean isEntityWithinGameBorder(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return true;
        }

        Location entityLocation = entity.getLocation();

        // Calculate distance from game border center
        double deltaX = Math.abs(entityLocation.getX() - gameBorderCenter.getX());
        double deltaZ = Math.abs(entityLocation.getZ() - gameBorderCenter.getZ());

        return deltaX <= gameBorderRadius && deltaZ <= gameBorderRadius;
    }

    /**
     * Find nearest safe location within game border
     */
    private Location findNearestSafeLocationInGameBorder(Location center, int radius) {
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();

        for (int r = 1; r <= radius; r++) {
            for (int x = centerX - r; x <= centerX + r; x++) {
                for (int z = centerZ - r; z <= centerZ + r; z++) {
                    // Only check the perimeter of the current radius
                    if (Math.abs(x - centerX) == r || Math.abs(z - centerZ) == r) {
                        Location candidate = new Location(world, x + 0.5,
                                world.getHighestBlockYAt(x, z) + 1, z + 0.5);

                        // Check if within game border and safe
                        if (isLocationWithinGameBorder(candidate) && GameUtil.isLocationSafe(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Check if location is within game border
     */
    private boolean isLocationWithinGameBorder(Location location) {
        if (location == null) {
            return false;
        }

        double deltaX = Math.abs(location.getX() - gameBorderCenter.getX());
        double deltaZ = Math.abs(location.getZ() - gameBorderCenter.getZ());

        return deltaX <= gameBorderRadius && deltaZ <= gameBorderRadius;
    }

    private void preloadChunks() {
        int chunksLoaded = 0;

        while (chunkIterator.hasNext() && chunksLoaded < CHUNKS_PER_TICK) {
            Chunk chunk = chunkIterator.next();

            if (!chunk.isLoaded()) {
                chunk.load(true);
            }

            chunksLoaded++;
        }

        if (!chunkIterator.hasNext()) {
            currentPhase = TeleportPhase.TELEPORTING_ENTITIES;
            Bukkit.getLogger().info("Chunks preloaded, starting entity teleportation");
        }
    }

    private void teleportEntities() {
        int teleportsThisTick = 0;

        // Teleport players first
        while (currentEntityIndex < playersToTeleport.size() && teleportsThisTick < TELEPORTS_PER_TICK) {
            Player player = playersToTeleport.get(currentEntityIndex);

            if (player.isOnline() && player.hasMetadata("borderTeleportDestination")) {
                Location destination = (Location) player.getMetadata("borderTeleportDestination").get(0).value();
                player.teleport(destination);
                player.sendMessage(ChatColor.RED + "You were teleported due to border shrinking!");
                player.removeMetadata("borderTeleportDestination", UHC.getInstance());

                UHC.getInstance().getLogger().info("Teleported player " + player.getName() +
                        " to game border at " + formatLocation(destination));
            }

            currentEntityIndex++;
            teleportsThisTick++;
        }

        // Teleport villagers
        int villagerStartIndex = currentEntityIndex - playersToTeleport.size();
        while (villagerStartIndex >= 0 && villagerStartIndex < villagersToTeleport.size() &&
                teleportsThisTick < TELEPORTS_PER_TICK) {
            VillagerTeleportData data = villagersToTeleport.get(villagerStartIndex);

            if (data.destination != null && data.villager.isValid()) {
                Location oldLocation = data.villager.getLocation().clone();
                data.villager.teleport(data.destination);

                // Update combat log player data
                data.combatLogPlayer.setMoved(true);
                data.combatLogPlayer.setLocation(data.destination);

                // Update chunk tracking
                Chunk oldChunk = oldLocation.getChunk();
                Chunk newChunk = data.destination.getChunk();
                if (!oldChunk.equals(newChunk)) {
                    villagerManager.updateVillagerChunk(oldChunk, newChunk);
                }

                UHC.getInstance().getLogger().info("Teleported villager to game border at " +
                        formatLocation(data.destination));
            }

            currentEntityIndex++;
            teleportsThisTick++;
            villagerStartIndex++;
        }

        // Check if done teleporting
        if (currentEntityIndex >= playersToTeleport.size() + villagersToTeleport.size()) {
            currentPhase = TeleportPhase.COMPLETED;
        }
    }

    private void complete() {
        int totalTeleported = playersToTeleport.size() + villagersToTeleport.size();
        Bukkit.getLogger().info("Game border teleportation completed: " + totalTeleported + " entities relocated");

        // Re-enable world border damage after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                worldBorder.setDamageAmount(0.2);
                worldBorder.setDamageBuffer(5.0);
                UHC.getInstance().getLogger().info("World border damage re-enabled");
            }
        }.runTaskLater(UHC.getInstance(), 3*20);

        cancel();
    }

    public void startTeleporting() {
        // Temporarily disable world border damage during teleportation
        worldBorder.setDamageAmount(0);
        worldBorder.setDamageBuffer(1000);

        this.runTaskTimer(UHC.getInstance(), 0, 1);
        UHC.getInstance().getLogger().info("Started game border teleportation process");
    }

    public double getProgress() {
        int totalEntities = playersToTeleport.size() + villagersToTeleport.size();
        if (totalEntities == 0) return 100;

        switch (currentPhase) {
            case FINDING_ENTITIES:
                return 10;
            case CALCULATING_DESTINATIONS:
                return 10 + ((double) currentEntityIndex / totalEntities * 30);
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 60;
                int chunksProcessed = chunksToPreload.size() - (chunkIterator.hasNext() ? 0 : chunksToPreload.size());
                return 40 + ((double) chunksProcessed / chunksToPreload.size() * 20);
            case TELEPORTING_ENTITIES:
                return 60 + ((double) currentEntityIndex / totalEntities * 40);
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }

    @Override
    public void cancel() {
        // Re-enable border damage when cancelled
        if (worldBorder != null) {
            worldBorder.setDamageAmount(0.2);
            worldBorder.setDamageBuffer(5.0);
        }

        this.cancelled = true;
        super.cancel();
        UHC.getInstance().getLogger().info("Game border teleporter cancelled");
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }
}
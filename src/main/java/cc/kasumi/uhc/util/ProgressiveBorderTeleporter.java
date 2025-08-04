package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles progressive teleportation during border shrinking to prevent lag
 */
public class ProgressiveBorderTeleporter extends BukkitRunnable {

    private final WorldBorder worldBorder;
    private final World world; // Add this field
    private final CombatLogVillagerManager villagerManager;

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
    private static final int ENTITIES_CHECKED_PER_TICK = 5;  // Check 5 entities per tick
    private static final int CHUNKS_PER_TICK = 3;            // Preload 3 chunks per tick
    private static final int TELEPORTS_PER_TICK = 2;         // Teleport 2 entities per tick

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
        this.world = world; // Store the world reference
        this.villagerManager = villagerManager;
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
        // Find all players outside the border
        for (Player player : world.getPlayers()) {
            if (!GameUtil.isEntityInBorder(player, worldBorder)) {
                playersToTeleport.add(player);
            }
        }

        // Find all combat log villagers outside the border
        for (Map.Entry<Villager, CombatLogPlayer> entry : villagerManager.getCombatLogVillagers().entrySet()) {
            Villager villager = entry.getKey();
            CombatLogPlayer combatLogPlayer = entry.getValue();

            if (!GameUtil.isEntityInBorder(villager, worldBorder)) {
                villagersToTeleport.add(new VillagerTeleportData(villager, combatLogPlayer));
            }
        }

        int totalEntities = playersToTeleport.size() + villagersToTeleport.size();

        if (totalEntities == 0) {
            // No entities to teleport, we're done
            currentPhase = TeleportPhase.COMPLETED;
            Bukkit.getLogger().info("No entities outside border, teleportation complete");
        } else {
            currentPhase = TeleportPhase.CALCULATING_DESTINATIONS;
            Bukkit.getLogger().info("Found " + totalEntities + " entities outside border (" +
                    playersToTeleport.size() + " players, " + villagersToTeleport.size() + " villagers)");
        }
    }

    private void calculateDestinations() {
        int calculationsThisTick = 0;

        // Calculate destinations for players
        while (currentEntityIndex < playersToTeleport.size() && calculationsThisTick < ENTITIES_CHECKED_PER_TICK) {
            Player player = playersToTeleport.get(currentEntityIndex);
            Location destination = GameUtil.calculateSafeBorderPoint(player, worldBorder);

            if (destination != null) {
                // Store destination in player metadata or temporary map
                player.setMetadata("borderTeleportDestination", new org.bukkit.metadata.FixedMetadataValue(UHC.getInstance(), destination));
                chunksToPreload.add(destination.getChunk());
            }

            currentEntityIndex++;
            calculationsThisTick++;
        }

        // Calculate destinations for villagers
        int villagerStartIndex = currentEntityIndex - playersToTeleport.size();
        while (villagerStartIndex >= 0 && villagerStartIndex < villagersToTeleport.size() && calculationsThisTick < ENTITIES_CHECKED_PER_TICK) {
            VillagerTeleportData data = villagersToTeleport.get(villagerStartIndex);
            data.destination = GameUtil.calculateSafeBorderPoint(data.villager, worldBorder);

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
            currentEntityIndex = 0; // Reset for teleporting phase

            Bukkit.getLogger().info("Calculated destinations, preloading " + chunksToPreload.size() + " chunks");
        }
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
            }

            currentEntityIndex++;
            teleportsThisTick++;
        }

        // Teleport villagers
        int villagerStartIndex = currentEntityIndex - playersToTeleport.size();
        while (villagerStartIndex >= 0 && villagerStartIndex < villagersToTeleport.size() && teleportsThisTick < TELEPORTS_PER_TICK) {
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
        Bukkit.getLogger().info("Border teleportation completed: " + totalTeleported + " entities relocated");

        cancel();
    }

    public void startTeleporting() {
        this.runTaskTimer(UHC.getInstance(), 0, 1);
    }

    public double getProgress() {
        int totalEntities = playersToTeleport.size() + villagersToTeleport.size();
        if (totalEntities == 0) return 100;

        switch (currentPhase) {
            case FINDING_ENTITIES:
                return 10; // Quick phase
            case CALCULATING_DESTINATIONS:
                return 10 + ((double) currentEntityIndex / totalEntities * 30); // 10-40%
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 60;
                int chunksProcessed = chunksToPreload.size() - (chunkIterator.hasNext() ? 0 : chunksToPreload.size());
                return 40 + ((double) chunksProcessed / chunksToPreload.size() * 20); // 40-60%
            case TELEPORTING_ENTITIES:
                return 60 + ((double) currentEntityIndex / totalEntities * 40); // 60-100%
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        super.cancel();
    }
}
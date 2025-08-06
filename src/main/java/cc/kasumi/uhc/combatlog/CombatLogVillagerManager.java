package cc.kasumi.uhc.combatlog;

import cc.kasumi.commons.util.ItemStackUtil;
import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.task.CombatLogVillagerTask;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.inventory.CachedInventory;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.util.GameUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

@Getter
public class CombatLogVillagerManager {

    private static final int COMBAT_LOG_TIMEOUT_SECONDS = 180; // 3 minutes
    private static final double VILLAGER_MAX_HEALTH = 20.0D;

    private final Map<Villager, CombatLogPlayer> combatLogVillagers = new HashMap<>();
    private final Set<Chunk> combatLogVillagerChunks = new HashSet<>();
    private final Game game;

    @Setter
    private BukkitTask positionCheckTask;

    public CombatLogVillagerManager(Game game) {
        this.game = game;
    }

    /**
     * Spawns a combat log villager when a player disconnects during active gameplay
     */
    public void spawnCombatLogVillager(UUID playerUUID, Player player) {
        Location spawnLocation = player.getLocation().clone();
        Chunk playerChunk = spawnLocation.getChunk();

        Villager villager = createVillager(spawnLocation);

        BukkitTask timeoutTask = createTimeoutTask(playerUUID);
        CombatLogPlayer combatLogPlayer = new CombatLogPlayer(playerUUID, player, spawnLocation, timeoutTask);

        updateVillagerHealthBar(villager, combatLogPlayer, villager.getHealth());

        combatLogVillagers.put(villager, combatLogPlayer);
        combatLogVillagerChunks.add(playerChunk);
    }

    /**
     * Removes combat log villager when player rejoins (without killing)
     */
    public void deSpawnCombatLogVillager(UUID playerUUID) {
        CombatLogEntry entry = findCombatLogEntry(playerUUID);
        if (entry == null) return;

        cleanupCombatLogVillager(entry, false);
    }

    /**
     * Removes and kills combat log villager (drops items, removes from game)
     */
    public void deSpawnAndKillCombatLogVillager(UUID playerUUID) {
        CombatLogEntry entry = findCombatLogEntry(playerUUID);
        if (entry == null) return;

        cleanupCombatLogVillager(entry, true);
        game.removePlayer(playerUUID);
    }

    /**
     * Handles when a combat log villager is killed by another player or entity
     */
    public void killCombatLogVillager(Villager villager, CombatLogPlayer combatLogPlayer) {
        combatLogPlayer.getBukkitTask().cancel();
        dropCombatLogItems(villager, combatLogPlayer);

        combatLogVillagers.remove(villager);
        combatLogVillagerChunks.remove(villager.getLocation().getChunk());
    }

    /**
     * Checks if a villager is a combat log villager
     */
    public boolean isControlledVillager(Villager villager) {
        return combatLogVillagers.containsKey(villager);
    }

    /**
     * Gets the UHC player associated with a combat log villager
     */
    public UHCPlayer getUHCPlayerByVillager(Villager villager) {
        CombatLogPlayer combatLogPlayer = combatLogVillagers.get(villager);
        return combatLogPlayer != null ? game.getUHCPlayer(combatLogPlayer.getUuid()) : null;
    }

    /**
     * Gets the combat log player data for a villager
     */
    public CombatLogPlayer getCombatLogPlayer(Villager villager) {
        return combatLogVillagers.get(villager);
    }

    /**
     * Finds a combat log entry by player UUID
     */
    public CombatLogEntry findCombatLogEntry(UUID playerUUID) {
        return combatLogVillagers.entrySet().stream()
                .filter(entry -> entry.getValue().getUuid().equals(playerUUID))
                .map(entry -> new CombatLogEntry(entry.getKey(), entry.getValue()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets all chunks that contain combat log villagers (for chunk unload prevention)
     */
    public Set<Chunk> getCombatLogVillagerChunks() {
        return Collections.unmodifiableSet(combatLogVillagerChunks);
    }

    /**
     * Updates chunk tracking when a villager moves between chunks
     */
    public void updateVillagerChunk(Chunk oldChunk, Chunk newChunk) {
        combatLogVillagerChunks.remove(oldChunk);
        combatLogVillagerChunks.add(newChunk);
    }

    /**
     * Checks if a chunk contains combat log villagers
     */
    public boolean containsVillagerChunk(Chunk chunk) {
        return combatLogVillagerChunks.contains(chunk);
    }

    /**
     * Handles border shrinking by teleporting any villagers outside the border
     */
    public void handleBorderShrink(WorldBorder worldBorder, World world) {
        // Use progressive teleportation to prevent lag
        GameUtil.startProgressiveBorderTeleport(world, this);
    }

    // Private helper methods

    private Villager createVillager(Location location) {
        World world = location.getWorld();
        Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);

        villager.setHealth(VILLAGER_MAX_HEALTH);
        villager.setProfession(Villager.Profession.FARMER);

        return villager;
    }

    public void updateVillagerHealthBar(Villager villager, CombatLogPlayer combatLogPlayer, double currentHealth) {
        int current = (int) Math.ceil(currentHealth);
        int max = (int) VILLAGER_MAX_HEALTH;

        villager.setCustomName(ChatColor.GRAY + Bukkit.getOfflinePlayer(combatLogPlayer.getUuid()).getName() +
                ChatColor.DARK_RED + " ❤" + ChatColor.RED + current + ChatColor.GRAY + "/" + ChatColor.RED + max);

        villager.setCustomNameVisible(true);
    }

    private BukkitTask createTimeoutTask(UUID playerUUID) {
        return new CombatLogVillagerTask(playerUUID)
                .runTaskLater(UHC.getInstance(), COMBAT_LOG_TIMEOUT_SECONDS * 20L);
    }

    private void cleanupCombatLogVillager(CombatLogEntry entry, boolean dropItems) {
        Villager villager = entry.getVillager();
        CombatLogPlayer combatLogPlayer = entry.getCombatLogPlayer();

        // Cancel timeout task
        combatLogPlayer.getBukkitTask().cancel();

        // Drop items if this is a kill
        if (dropItems) {
            dropCombatLogItems(villager, combatLogPlayer);
        }

        // Remove from tracking
        combatLogVillagerChunks.remove(villager.getLocation().getChunk());
        combatLogVillagers.remove(villager);

        // Remove villager entity
        villager.remove();
    }

    private void dropCombatLogItems(Villager villager, CombatLogPlayer combatLogPlayer) {
        Location dropLocation = villager.getLocation().clone();
        World world = dropLocation.getWorld();
        CachedInventory cachedInventory = combatLogPlayer.getCachedInventory();

        // Drop main inventory items
        Arrays.stream(cachedInventory.getContents())
                .filter(item -> !ItemStackUtil.isNullOrAir(item))
                .forEach(item -> world.dropItemNaturally(dropLocation, item));

        // Drop armor items
        Arrays.stream(cachedInventory.getArmorContents())
                .filter(item -> !ItemStackUtil.isNullOrAir(item))
                .forEach(item -> world.dropItemNaturally(dropLocation, item));
    }

    private void relocateVillagerToBorder(Villager villager, CombatLogPlayer combatLogPlayer, WorldBorder worldBorder) {
        Location oldLocation = villager.getLocation().clone();
        combatLogPlayer.setMoved(true);

        Location newLocation = GameUtil.teleportToNearestBorderPoint(villager);
        combatLogPlayer.setLocation(newLocation);

        Chunk oldChunk = oldLocation.getChunk();
        Chunk newChunk = newLocation.getChunk();

        if (!oldChunk.equals(newChunk)) {
            updateVillagerChunk(oldChunk, newChunk);
        }
    }

    /**
     * Data class to hold villager and combat log player together
     */

    @Getter
    public static class CombatLogEntry {
        private final Villager villager;
        private final CombatLogPlayer combatLogPlayer;

        public CombatLogEntry(Villager villager, CombatLogPlayer combatLogPlayer) {
            this.villager = villager;
            this.combatLogPlayer = combatLogPlayer;
        }
    }
}
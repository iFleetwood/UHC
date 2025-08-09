package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final Game game;
    private static final int CHUNK_LOAD_RADIUS = 2; // Load chunks in 5x5 area around teleport destination

    public PlayerListener(Game game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isDead()) {
            player.spigot().respawn();
        }

        if (game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = new UHCPlayer(uuid);
        game.getPlayers().put(uuid, uhcPlayer);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);

        if (uhcPlayer.getState() == PlayerState.COMBAT_LOG) {
            return;
        }

        UHC.getInstance().getGame().removePlayer(uuid);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
            }
        }.runTaskLater(UHC.getInstance(), 1);
    }

    /**
     * Handle teleportation events to ensure chunks are loaded at destination
     * This fixes the issue where players fall through the world when teleporting to scattered players
     * Modified for 1.8.8 compatibility - uses synchronous chunk loading
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Location destination = event.getTo();
        if (destination == null || destination.getWorld() == null) {
            return;
        }

        // Only handle teleports in the UHC world
        if (game.getWorld() == null || !destination.getWorld().equals(game.getWorld())) {
            return;
        }

        // Skip if this is a scatter teleport (handled by ProgressiveScatterManager)
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN && 
            event.getPlayer().hasMetadata("uhc_scatter_teleport")) {
            return;
        }

        // Cancel the event temporarily to load chunks first
        event.setCancelled(true);
        
        Player player = event.getPlayer();
        Location originalLocation = event.getFrom();
        
        UHC.getInstance().getLogger().info("Pre-loading chunks for teleport: " + 
            player.getName() + " to " + formatLocation(destination));

        // Schedule chunk loading on next tick to avoid cancelling the event in the same tick
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Load chunks in radius around destination (synchronous for 1.8.8)
                    Chunk centerChunk = destination.getChunk();
                    int chunksLoaded = 0;
                    
                    for (int dx = -CHUNK_LOAD_RADIUS; dx <= CHUNK_LOAD_RADIUS; dx++) {
                        for (int dz = -CHUNK_LOAD_RADIUS; dz <= CHUNK_LOAD_RADIUS; dz++) {
                            Chunk chunk = destination.getWorld().getChunkAt(
                                centerChunk.getX() + dx, 
                                centerChunk.getZ() + dz
                            );
                            
                            if (!chunk.isLoaded()) {
                                chunk.load(true); // Synchronous loading in 1.8.8
                                chunksLoaded++;
                            }
                        }
                    }
                    
                    UHC.getInstance().getLogger().info("Loaded " + chunksLoaded + " chunks around teleport destination");
                    
                    // Ensure player is still online and hasn't moved significantly
                    if (player.isOnline() && player.getLocation().distance(originalLocation) < 10) {
                        // Perform the teleport safely
                        player.teleport(destination);
                        UHC.getInstance().getLogger().info("Successfully teleported " + 
                            player.getName() + " to " + formatLocation(destination));
                    } else {
                        UHC.getInstance().getLogger().warning("Teleport cancelled - player moved or went offline");
                    }
                    
                } catch (Exception e) {
                    UHC.getInstance().getLogger().severe("Error during chunk preloading for teleport: " + e.getMessage());
                    e.printStackTrace();
                    
                    // Fallback: try teleport anyway
                    if (player.isOnline()) {
                        player.teleport(destination);
                    }
                }
            }
        }.runTask(UHC.getInstance()); // Run on main thread for 1.8.8 compatibility
    }
    
    /**
     * Format location for logging
     */
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}

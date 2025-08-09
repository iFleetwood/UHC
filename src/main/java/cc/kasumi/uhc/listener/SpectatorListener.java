package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Handles all spectator-related events to prevent spectators from interfering with the game
 */
public class SpectatorListener implements Listener {

    private final Game game;

    public SpectatorListener(Game game) {
        this.game = game;
    }

    /**
     * Prevent spectators from breaking blocks
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot break blocks while spectating!");
        }
    }

    /**
     * Prevent spectators from placing blocks
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot place blocks while spectating!");
        }
    }

    /**
     * Handle player interactions - cancel most except spectator tools
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            // Handle spectator compass usage for teleportation
            if (event.getItem() != null && event.getItem().getType() == Material.COMPASS) {
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    // Open teleportation menu using commons menu system
                    if (game.getSpectatorManager() != null) {
                        game.getSpectatorManager().openTeleportationMenu(player);
                    }
                    event.setCancelled(true);
                } else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    // Teleport to random player
                    if (game.getSpectatorManager() != null) {
                        game.getSpectatorManager().teleportSpectatorToRandomPlayer(player);
                    }
                    event.setCancelled(true);
                }
                return;
            }
            
            // Cancel all other interactions
            event.setCancelled(true);
        }
    }

    /**
     * Prevent spectators from damaging entities
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (isSpectator(damager)) {
                event.setCancelled(true);
                damager.sendMessage("§cYou cannot damage entities while spectating!");
            }
        }
    }

    /**
     * Prevent spectators from taking damage
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isSpectator(player)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevent spectators from dropping items
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot drop items while spectating!");
        }
    }

    /**
     * Prevent spectators from picking up items
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Limit spectator inventory interactions
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (isSpectator(player)) {
                // Allow viewing but not moving items in own inventory
                if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Prevent spectators from getting hungry
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isSpectator(player)) {
                event.setCancelled(true);
                player.setFoodLevel(20);
                player.setSaturation(20f);
            }
        }
    }

    /**
     * Prevent spectators from interacting with entities
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot interact with entities while spectating!");
        }
    }

    /**
     * Handle movement events for spectators
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            // Ensure spectators can fly and don't trigger pressure plates
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            if (!player.isFlying() && player.getLocation().getBlock().getType() != Material.AIR) {
                player.setFlying(true);
            }
        }
    }

    /**
     * Ensure spectators can always fly
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (isSpectator(player)) {
            // Always allow flight for spectators
            if (!event.isFlying()) {
                event.setCancelled(true);
                player.setFlying(true);
            }
        }
    }

    /**
     * Setup spectator state on join if needed
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (!game.containsUHCPlayer(player.getUniqueId())) {
            return;
        }
        
        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer != null && uhcPlayer.isSpectator()) {
            // Re-apply spectator settings using SpectatorManager
            if (game.getSpectatorManager() != null) {
                game.getSpectatorManager().handlePlayerJoin(player);
            } else {
                uhcPlayer.manageSpectator(player);
            }
        }
    }

    /**
     * Handle spectator cleanup on quit
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (!game.containsUHCPlayer(player.getUniqueId())) {
            return;
        }
        
        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer != null && uhcPlayer.isSpectator()) {
            // Cleanup spectator data using SpectatorManager
            if (game.getSpectatorManager() != null) {
                game.getSpectatorManager().handlePlayerQuit(player);
            }
            // Note: player.hidePlayer() cleanup is automatic on quit
        }
    }

    /**
     * Check if a player is a spectator
     */
    private boolean isSpectator(Player player) {
        if (!game.containsUHCPlayer(player.getUniqueId())) {
            return false;
        }
        
        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        return uhcPlayer != null && uhcPlayer.isSpectator();
    }
}
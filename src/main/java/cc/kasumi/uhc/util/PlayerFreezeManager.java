package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages freezing players during scatter using invisible armor stands
 * Compatible with Spigot 1.8.8
 */
public class PlayerFreezeManager implements Listener {
    
    private final Map<UUID, ArmorStand> frozenPlayers = new HashMap<>();
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    private boolean freezeActive = false;
    
    public PlayerFreezeManager() {
        UHC.getInstance().getServer().getPluginManager().registerEvents(this, UHC.getInstance());
    }
    
    /**
     * Freeze a player at their current location
     */
    public void freezePlayer(Player player) {
        if (frozenPlayers.containsKey(player.getUniqueId())) {
            return; // Already frozen
        }
        
        Location loc = player.getLocation();
        originalLocations.put(player.getUniqueId(), loc.clone());
        
        // Ensure player is not flying before freezing
        player.setAllowFlight(false);
        player.setFlying(false);
        
        // Create invisible armor stand
        ArmorStand stand = (ArmorStand) player.getWorld().spawnEntity(
            loc.clone().add(0, -1.0, 0), // Spawn slightly below to align properly
            EntityType.ARMOR_STAND
        );
        
        // Configure armor stand
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCanPickupItems(false);
        stand.setCustomNameVisible(false);
        stand.setSmall(true);
        stand.setMarker(true); // Makes it have no hitbox in 1.8.8
        
        // Set as passenger
        stand.setPassenger(player);
        
        // Apply effects to ensure they can't move
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false));
        // Use a lower jump reduction to avoid issues
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 200, false, false));
        
        // Store reference
        frozenPlayers.put(player.getUniqueId(), stand);
        
        // Send freeze message
        player.sendMessage("§e§lYou are frozen during scatter! Please wait...");
        
        UHC.getInstance().getLogger().info("Froze player " + player.getName() + " at " + formatLocation(loc));
    }
    
    /**
     * Unfreeze a player
     */
    public void unfreezePlayer(Player player) {
        ArmorStand stand = frozenPlayers.remove(player.getUniqueId());
        if (stand != null) {
            // Eject player first
            stand.eject();
            
            // Remove armor stand
            stand.remove();
            
            // Clear ALL potion effects to ensure clean state
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            
            // Get the player's current location
            Location currentLoc = player.getLocation();
            
            // Find a safe ground location
            Location safeLocation = findSafeGroundLocation(currentLoc);
            
            // Teleport player to safe location
            player.teleport(safeLocation);
            
            // Ensure player state is reset properly
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setVelocity(player.getVelocity().zero());
            player.setFallDistance(0);
            
            // Give brief invulnerability to prevent fall damage
            player.setNoDamageTicks(20); // 1 second of invulnerability
            
            // Schedule a delayed check to ensure player is grounded
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        // Double-check flight is disabled
                        player.setAllowFlight(false);
                        player.setFlying(false);
                        
                        // If player is still in air, teleport to ground again
                        if (!player.isOnGround() && player.getFallDistance() > 2) {
                            Location groundLoc = findSafeGroundLocation(player.getLocation());
                            player.teleport(groundLoc);
                            player.setVelocity(player.getVelocity().zero());
                        }
                    }
                }
            }.runTaskLater(UHC.getInstance(), 10L); // Check after 0.5 seconds
            
            // Remove from tracking
            originalLocations.remove(player.getUniqueId());
            
            player.sendMessage("§a§lYou have been unfrozen! The game is starting!");
            
            UHC.getInstance().getLogger().info("Unfroze player " + player.getName() + " at " + formatLocation(safeLocation));
        }
    }
    
    /**
     * Freeze all online players
     */
    public void freezeAllPlayers() {
        freezeActive = true;
        for (Player player : UHC.getInstance().getServer().getOnlinePlayers()) {
            freezePlayer(player);
        }
    }
    
    /**
     * Unfreeze all players
     */
    public void unfreezeAllPlayers() {
        freezeActive = false;
        for (Player player : UHC.getInstance().getServer().getOnlinePlayers()) {
            unfreezePlayer(player);
        }
        
        // Clean up any remaining armor stands
        frozenPlayers.values().forEach(ArmorStand::remove);
        frozenPlayers.clear();
        originalLocations.clear();
        
        // Schedule periodic safety checks for the next few seconds
        new BukkitRunnable() {
            int checks = 0;
            
            @Override
            public void run() {
                if (checks++ >= 10) { // Check for 5 seconds (10 checks at 0.5s intervals)
                    cancel();
                    return;
                }
                
                for (Player player : UHC.getInstance().getServer().getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SURVIVAL) {
                        // Ensure flight is disabled
                        if (player.getAllowFlight() || player.isFlying()) {
                            player.setAllowFlight(false);
                            player.setFlying(false);
                        }
                        
                        // Check if player seems to be floating
                        if (!player.isOnGround() && player.getFallDistance() == 0 && player.getVelocity().getY() > 0.1) {
                            // Force downward velocity to ground them
                            player.setVelocity(player.getVelocity().setY(-0.2));
                        }
                    }
                }
            }
        }.runTaskTimer(UHC.getInstance(), 10L, 10L); // Start after 0.5s, repeat every 0.5s
    }
    
    /**
     * Check if a player is frozen
     */
    public boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }
    
    /**
     * Get the number of frozen players
     */
    public int getFrozenCount() {
        return frozenPlayers.size();
    }
    
    // Event handlers to prevent movement and damage
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (freezeActive && isFrozen(event.getPlayer())) {
            // Allow head movement but not position movement
            if (event.getFrom().getX() != event.getTo().getX() ||
                event.getFrom().getY() != event.getTo().getY() ||
                event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            if (freezeActive && isFrozen(player)) {
                event.setCancelled(true);
                
                // Re-mount them just in case
                ArmorStand stand = frozenPlayers.get(player.getUniqueId());
                if (stand != null && !stand.isDead()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (stand.getPassenger() == null) {
                                stand.setPassenger(player);
                            }
                        }
                    }.runTaskLater(UHC.getInstance(), 1L);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (freezeActive && isFrozen(event.getPlayer())) {
            // Allow teleport but update the armor stand location
            Player player = event.getPlayer();
            ArmorStand stand = frozenPlayers.get(player.getUniqueId());
            
            if (stand != null) {
                Location newLoc = event.getTo();
                
                // Schedule armor stand update
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        stand.eject();
                        stand.teleport(newLoc.clone().add(0, -1.0, 0));
                        stand.setPassenger(player);
                        originalLocations.put(player.getUniqueId(), newLoc.clone());
                    }
                }.runTaskLater(UHC.getInstance(), 1L);
            }
        }
    }
    
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (freezeActive && isFrozen(player)) {
                event.setCancelled(true);
            }
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isFrozen(player)) {
            unfreezePlayer(player);
        }
    }
    
    /**
     * Clean up all armor stands on disable
     */
    public void cleanup() {
        unfreezeAllPlayers();
    }
    
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Find a safe ground location for the player
     */
    private Location findSafeGroundLocation(Location loc) {
        Location checkLoc = loc.clone();
        
        // First check if current location is already on ground
        if (isOnGround(checkLoc)) {
            return checkLoc;
        }
        
        // Check up to 10 blocks down for solid ground
        for (int i = 0; i < 10; i++) {
            checkLoc.subtract(0, 1, 0);
            if (isOnGround(checkLoc)) {
                // Return location 1 block above solid ground
                return checkLoc.add(0, 1, 0);
            }
        }
        
        // If no ground found below, check upwards (in case they're inside blocks)
        checkLoc = loc.clone();
        for (int i = 0; i < 5; i++) {
            checkLoc.add(0, 1, 0);
            if (isOnGround(checkLoc)) {
                return checkLoc.add(0, 1, 0);
            }
        }
        
        // If still no safe location, return original location
        // but set it to the ground level at those coordinates
        Location groundLoc = loc.clone();
        groundLoc.setY(loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1);
        return groundLoc;
    }
    
    /**
     * Check if a location is on solid ground
     */
    private boolean isOnGround(Location loc) {
        Location below = loc.clone().subtract(0, 1, 0);
        return below.getBlock().getType().isSolid() && 
               !loc.getBlock().getType().isSolid() && 
               !loc.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }
}
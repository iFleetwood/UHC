package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerFreezeManager implements Listener {

    private static ProtocolManager protocolManager;
    
    private final Map<UUID, ArmorStand> frozenPlayers = new HashMap<>();
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    private boolean freezeActive = false;
    
    public PlayerFreezeManager() {
        protocolManager = UHC.getProtocolManager();

        UHC.getInstance().getServer().getPluginManager().registerEvents(this, UHC.getInstance());
        // registerFlyingPacketListener();
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

        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(
                loc.clone().add(0, 0.0, 0), // Spawn slightly below to align properly
                EntityType.ARMOR_STAND
        );

        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomNameVisible(false);
        armorStand.setSmall(true);
        armorStand.setMarker(true); // Makes it have no hitbox in 1.8.8

        armorStand.setPassenger(player);

        // Apply effects to ensure they can't move
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255, false, false));
        // Use a lower jump reduction to avoid issues
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 200, false, false));
        
        // Store reference
        frozenPlayers.put(player.getUniqueId(), armorStand);
        
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
            // Eject player
            stand.eject();

            // Remove armor stand
            stand.remove();
        }

        // Clear ALL potion effects to ensure clean state
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Get the player's current location
        Location currentLoc = player.getLocation();

        // Ensure player state is reset properly
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.1F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);

        // Give brief invulnerability to prevent fall damage
        player.setNoDamageTicks(20); // 1 second of invulnerability

        // Remove from tracking
        frozenPlayers.remove(player.getUniqueId());
        originalLocations.remove(player.getUniqueId());

        player.sendMessage("§a§lYou have been unfrozen! The game is starting!");

        UHC.getInstance().getLogger().info("Unfroze player " + player.getName() + " at " + formatLocation(currentLoc));
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
        frozenPlayers.clear();
        originalLocations.clear();
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

    private void sendDestroyPacket(Player to, int entityId) {
        PacketContainer destroy = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroy.getIntegerArrays().write(0, new int[]{entityId});
        protocolManager.sendServerPacket(to, destroy);
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

    /*
    private void registerFlyingPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(UHC.getInstance(), ListenerPriority.HIGHEST, PacketType.Play.Client.FLYING, PacketType.Play.Client.POSITION, PacketType.Play.Client.POSITION_LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (frozenPlayers.containsKey(player.getUniqueId())) {
                    event.getPacket().getBooleans().write(0, true); // Force onGround = true
                    Bukkit.broadcastMessage(player.getName() + "yes");
                }
            }
        });
    }

     */
    
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
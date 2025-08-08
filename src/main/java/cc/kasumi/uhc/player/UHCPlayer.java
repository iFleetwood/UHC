package cc.kasumi.uhc.player;

import cc.kasumi.uhc.inventory.CachedInventory;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

@Getter
public class UHCPlayer {

    private final UUID uuid;

    private int kills = 0;

    @Setter
    private PlayerState state = PlayerState.SPECTATING;
    @Setter
    private CachedInventory cachedInventory;
    
    // Spectator-specific fields
    @Setter
    private Location deathLocation;
    @Setter
    private ItemStack[] deathInventory;
    @Setter
    private ItemStack[] deathArmor;
    @Setter
    private PotionEffect[] deathPotionEffects;
    @Setter
    private boolean wasSpectatorOnDeath = false;

    public UHCPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(this.uuid);
    }

    public void setPlayerStateAndManage(PlayerState state) {
        this.state = state;

        if (state == PlayerState.SPECTATING || state == PlayerState.SPECTATOR) {
            manageSpectator(getPlayer());
        } else if (state == PlayerState.ALIVE) {
            manageAlivePlayer(getPlayer());
        }
    }

    public void manageSpectator(Player player) {
        if (player == null) return;
        
        // Use Creative mode instead of Spectator mode as requested
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.1f);
        player.setWalkSpeed(0.2f);
        
        // Clear inventory and effects
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        
        // Remove potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        
        // Reset health and food
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        
        // Reset fire ticks
        player.setFireTicks(0);
    }

    public void manageAlivePlayer(Player player) {
        if (player == null) return;
        
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFlySpeed(0.1f);
        player.setWalkSpeed(0.2f);
    }
    
    /**
     * Store player's death data for potential revival
     */
    public void storeDeathData(Player player) {
        if (player == null) return;
        
        this.deathLocation = player.getLocation().clone();
        this.deathInventory = player.getInventory().getContents().clone();
        this.deathArmor = player.getInventory().getArmorContents().clone();
        
        // Store potion effects
        PotionEffect[] effects = player.getActivePotionEffects().toArray(new PotionEffect[0]);
        this.deathPotionEffects = effects;
    }
    
    /**
     * Check if player is in any spectator state
     */
    public boolean isSpectator() {
        return state == PlayerState.SPECTATING || state == PlayerState.SPECTATOR;
    }

    public void addKill() {
        kills += 1;
    }
}

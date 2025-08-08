package cc.kasumi.uhc.spectator;

import cc.kasumi.commons.menu.Button;
import cc.kasumi.commons.menu.Menu;
import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all spectator functionality including visibility, teleportation, and state management
 */
@Getter
public class SpectatorManager {

    private final UHC plugin;
    private final Game game;
    private final SpectatorConfiguration config;

    public SpectatorManager(UHC plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        this.config = new SpectatorConfiguration();
    }

    /**
     * Convert a player to spectator mode
     */
    public boolean makeSpectator(Player player) {
        return makeSpectator(player, false);
    }

    /**
     * Convert a player to spectator mode
     * @param player The player to make spectator
     * @param isDeath Whether this is due to death
     */
    public boolean makeSpectator(Player player, boolean isDeath) {
        if (player == null) return false;

        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer == null) return false;

        // Store death data if this is due to death
        if (isDeath) {
            uhcPlayer.storeDeathData(player);
            uhcPlayer.setWasSpectatorOnDeath(true);
        }

        // Set spectator state
        uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);

        // Setup spectator mode
        setupSpectatorMode(player);

        // Hide spectator from alive players
        hideSpectatorFromAlivePlayers(player);

        // Give spectator tools
        giveSpectatorTools(player);

        // Send message
        if (isDeath) {
            player.sendMessage(ChatColor.RED + "You have died and are now spectating the game!");
        } else {
            player.sendMessage(ChatColor.YELLOW + "You are now spectating the game!");
        }

        return true;
    }

    /**
     * Remove spectator status from a player
     */
    public boolean removeSpectator(Player player) {
        if (player == null) return false;

        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer == null || !uhcPlayer.isSpectator()) return false;

        // Change state to alive
        uhcPlayer.setPlayerStateAndManage(PlayerState.ALIVE);

        // Cleanup spectator mode
        cleanupSpectator(player);

        // Show player to all other players
        showPlayerToAllPlayers(player);

        // Send message
        player.sendMessage(ChatColor.GREEN + "You are no longer spectating!");

        return true;
    }

    /**
     * Check if a player is a spectator
     */
    public boolean isSpectator(Player player) {
        if (player == null) return false;
        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        return uhcPlayer != null && uhcPlayer.isSpectator();
    }

    /**
     * Setup all spectator mode settings
     */
    public void setupSpectatorMode(Player player) {
        if (player == null) return;

        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer != null) {
            uhcPlayer.manageSpectator(player);
        }
    }

    /**
     * Cleanup spectator effects and settings
     */
    public void cleanupSpectator(Player player) {
        if (player == null) return;

        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);

        // Reset game mode and flight
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);

        // Show player to all other players
        showPlayerToAllPlayers(player);
    }

    /**
     * Hide spectator from alive players using player.hidePlayer()
     */
    private void hideSpectatorFromAlivePlayers(Player spectator) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(spectator)) continue;

            UHCPlayer uhcPlayer = game.getUHCPlayer(onlinePlayer.getUniqueId());
            if (uhcPlayer != null && uhcPlayer.getState() == PlayerState.ALIVE) {
                // Hide spectator from alive player
                onlinePlayer.hidePlayer(spectator);
            } else if (uhcPlayer != null && uhcPlayer.isSpectator() && config.isSpectatorsCanSeeEachOther()) {
                // Show spectator to other spectators if configured
                onlinePlayer.showPlayer(spectator);
            }
        }
    }

    /**
     * Show player to all other players using player.showPlayer()
     */
    private void showPlayerToAllPlayers(Player player) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (!onlinePlayer.equals(player)) {
                onlinePlayer.showPlayer(player);
            }
        }
    }

    /**
     * Update spectator visibility when a player's state changes
     */
    public void updateSpectatorVisibility(Player player) {
        if (player == null) return;

        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer == null) return;

        if (uhcPlayer.isSpectator()) {
            hideSpectatorFromAlivePlayers(player);
        } else {
            showPlayerToAllPlayers(player);
        }
    }

    /**
     * Give spectator tools (compass for teleportation, etc.)
     */
    private void giveSpectatorTools(Player player) {
        if (!config.isSpectatorTeleportationEnabled()) return;

        // Give compass for teleportation
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Teleport to Player");
        
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Right-click to open teleportation menu");
        lore.add(ChatColor.GRAY + "Left-click to teleport to random alive player");
        meta.setLore(lore);
        
        compass.setItemMeta(meta);
        player.getInventory().setItem(0, compass);
    }

    /**
     * Teleport spectator to a specific player
     */
    public boolean teleportSpectatorToPlayer(Player spectator, Player target) {
        if (spectator == null || target == null) return false;
        if (!isSpectator(spectator)) return false;

        UHCPlayer targetUhcPlayer = game.getUHCPlayer(target.getUniqueId());
        if (targetUhcPlayer == null || targetUhcPlayer.getState() != PlayerState.ALIVE) {
            spectator.sendMessage(ChatColor.RED + "That player is not alive!");
            return false;
        }

        spectator.teleport(target.getLocation());
        spectator.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName());
        return true;
    }

    /**
     * Teleport spectator to a random alive player
     */
    public boolean teleportSpectatorToRandomPlayer(Player spectator) {
        if (spectator == null || !isSpectator(spectator)) return false;

        List<Player> alivePlayers = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UHCPlayer uhcPlayer = game.getUHCPlayer(onlinePlayer.getUniqueId());
            if (uhcPlayer != null && uhcPlayer.getState() == PlayerState.ALIVE) {
                alivePlayers.add(onlinePlayer);
            }
        }

        if (alivePlayers.isEmpty()) {
            spectator.sendMessage(ChatColor.RED + "No alive players to teleport to!");
            return false;
        }

        Player randomPlayer = alivePlayers.get((int) (Math.random() * alivePlayers.size()));
        return teleportSpectatorToPlayer(spectator, randomPlayer);
    }

    /**
     * Get list of alive players for spectator teleportation
     */
    public List<Player> getAlivePlayers() {
        List<Player> alivePlayers = new ArrayList<>();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UHCPlayer uhcPlayer = game.getUHCPlayer(onlinePlayer.getUniqueId());
            if (uhcPlayer != null && uhcPlayer.getState() == PlayerState.ALIVE) {
                alivePlayers.add(onlinePlayer);
            }
        }
        return alivePlayers;
    }

    /**
     * Handle player join for spectators
     */
    public void handlePlayerJoin(Player player) {
        UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());
        if (uhcPlayer != null && uhcPlayer.isSpectator()) {
            setupSpectatorMode(player);
            updateSpectatorVisibility(player);
            giveSpectatorTools(player);
        }
    }

    /**
     * Handle player quit for spectators
     */
    public void handlePlayerQuit(Player player) {
        // player.hidePlayer() cleanup is handled automatically by Bukkit on quit
        // Additional cleanup if needed can be added here
    }

    /**
     * Update all spectator visibilities (useful for when states change)
     */
    public void updateAllSpectatorVisibilities() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateSpectatorVisibility(player);
        }
    }

    /**
     * Send message to all spectators
     */
    public void sendMessageToSpectators(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isSpectator(player)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Get all online spectators
     */
    public List<Player> getOnlineSpectators() {
        List<Player> spectators = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isSpectator(player)) {
                spectators.add(player);
            }
        }
        return spectators;
    }

    /**
     * Open teleportation menu for spectator using commons menu system
     */
    public void openTeleportationMenu(Player spectator) {
        if (!isSpectator(spectator)) return;

        List<Player> alivePlayers = getAlivePlayers();
        if (alivePlayers.isEmpty()) {
            spectator.sendMessage(ChatColor.RED + "No alive players to teleport to!");
            return;
        }

        try {
            Menu menu = new Menu() {
                @Override
                public String getTitle(Player player) {
                    return ChatColor.GOLD + "Teleport to Player";
                }

                @Override
                public Map<Integer, Button> getButtons(Player player) {
                    Map<Integer, Button> buttons = new HashMap<>();
                    
                    int slot = 0;
                    for (Player alivePlayer : alivePlayers) {
                        if (slot >= 54) break; // Max inventory size
                        
                        buttons.put(slot, new Button() {
                            @Override
                            public ItemStack getButtonItem(Player player) {
                                ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                                ItemMeta meta = skull.getItemMeta();
                                meta.setDisplayName(ChatColor.GREEN + alivePlayer.getName());
                                
                                List<String> lore = new ArrayList<>();
                                lore.add(ChatColor.GRAY + "Click to teleport to " + alivePlayer.getName());
                                UHCPlayer uhcPlayer = game.getUHCPlayer(alivePlayer.getUniqueId());
                                if (uhcPlayer != null) {
                                    lore.add(ChatColor.YELLOW + "Kills: " + uhcPlayer.getKills());
                                }
                                meta.setLore(lore);
                                
                                skull.setItemMeta(meta);
                                return skull;
                            }

                            @Override
                            public void clicked(Player player, int slot, ClickType clickType, int hotbarButton) {
                                if (alivePlayer.isOnline()) {
                                    teleportSpectatorToPlayer(spectator, alivePlayer);
                                    player.closeInventory();
                                } else {
                                    player.sendMessage(ChatColor.RED + alivePlayer.getName() + " is no longer online!");
                                    openTeleportationMenu(spectator); // Refresh menu
                                }
                            }
                        });
                        slot++;
                    }
                    
                    return buttons;
                }
            };

            menu.openMenu(spectator);
        } catch (Exception e) {
            // Fallback if commons menu system is not available or has different interface
            spectator.sendMessage(ChatColor.RED + "Teleportation menu is not available. Use /spec tp <player> instead.");
            plugin.getLogger().warning("Could not open teleportation menu: " + e.getMessage());
        }
    }
}
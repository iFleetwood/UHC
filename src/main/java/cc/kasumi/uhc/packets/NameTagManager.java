package cc.kasumi.uhc.packets;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.packets.NameTagCreator;
import cc.kasumi.uhc.team.TeamManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Manages nametag updates throughout the game
 * Integrates with the team system to provide real-time nametag updates
 */
public class NameTagManager implements Listener {

    private final Game game;
    private boolean nameTagsEnabled = false;

    public NameTagManager(Game game) {
        this.game = game;

        // Register this as a listener
        Bukkit.getPluginManager().registerEvents(this, UHC.getInstance());
    }

    /**
     * Enable team-based nametags
     */
    public void enableTeamNameTags() {
        if (nameTagsEnabled) {
            return;
        }

        nameTagsEnabled = true;

        // Update all existing players
        new BukkitRunnable() {
            @Override
            public void run() {
                NameTagCreator.updateAllNameTags();
                UHC.getInstance().getLogger().info("Team nametags enabled and updated for all players");
            }
        }.runTaskLater(UHC.getInstance(), 1L);
    }

    /**
     * Disable team-based nametags
     */
    public void disableTeamNameTags() {
        if (!nameTagsEnabled) {
            return;
        }

        nameTagsEnabled = false;

        // Clear all nametags
        new BukkitRunnable() {
            @Override
            public void run() {
                clearAllNameTags();
                UHC.getInstance().getLogger().info("Team nametags disabled and cleared for all players");
            }
        }.runTaskLater(UHC.getInstance(), 1L);
    }

    /**
     * Called when a player joins a team
     */
    public void onPlayerJoinTeam(Player player) {
        if (!nameTagsEnabled) {
            return;
        }

        // Delay the update to ensure team data is properly set
        new BukkitRunnable() {
            @Override
            public void run() {
                NameTagCreator.updateNameTagsForTeamChange(player);
            }
        }.runTaskLater(UHC.getInstance(), 2L);
    }

    /**
     * Called when a player leaves a team
     */
    public void onPlayerLeaveTeam(Player player) {
        if (!nameTagsEnabled) {
            return;
        }

        // Delay the update to ensure team data is properly updated
        new BukkitRunnable() {
            @Override
            public void run() {
                NameTagCreator.updateNameTagsForTeamChange(player);
            }
        }.runTaskLater(UHC.getInstance(), 2L);
    }

    /**
     * Called when team data changes significantly (like team elimination)
     */
    public void onTeamDataChange() {
        if (!nameTagsEnabled) {
            return;
        }

        // Full refresh for major team changes
        new BukkitRunnable() {
            @Override
            public void run() {
                NameTagCreator.updateAllNameTags();
            }
        }.runTaskLater(UHC.getInstance(), 2L);
    }

    /**
     * Handle player joining
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!nameTagsEnabled) {
            return;
        }

        Player player = event.getPlayer();

        // Delay to ensure player is fully loaded
        new BukkitRunnable() {
            @Override
            public void run() {
                // Step 1: Update nametags for the joining player (how they see others)
                NameTagCreator.updateNameTagsForPlayer(player, game.getTeamManager());

                // Step 2: Update how ALL existing players see the joining player
                // This is crucial - we need to refresh the entire nametag system
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(player)) {
                        try {
                            // Force refresh how this other player sees everyone (including the new joiner)
                            NameTagCreator.updateNameTagsForPlayer(other, game.getTeamManager());
                        } catch (Exception e) {
                            UHC.getInstance().getLogger().warning("Failed to update nametags for " +
                                    other.getName() + " after " + player.getName() + " joined: " + e.getMessage());
                        }
                    }
                }

                UHC.getInstance().getLogger().info("Updated nametags for all players after " +
                        player.getName() + " joined");
            }
        }.runTaskLater(UHC.getInstance(), 5L); // Increased delay to 5 ticks for better stability
    }

    /**
     * Handle player leaving
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!nameTagsEnabled) {
            return;
        }

        Player player = event.getPlayer();

        // Clean up immediately
        NameTagCreator.cleanupPlayer(player);
    }

    /**
     * Clear all nametags for all players
     */
    private void clearAllNameTags() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            NameTagCreator.cleanupPlayer(player);
        }
    }

    /**
     * Check if nametags are currently enabled
     */
    public boolean areNameTagsEnabled() {
        return nameTagsEnabled;
    }

    /**
     * Force refresh all nametags (useful for debugging or after configuration changes)
     */
    public void forceRefresh() {
        if (!nameTagsEnabled) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                NameTagCreator.forceRefreshAll();
            }
        }.runTaskLater(UHC.getInstance(), 1L);
    }

    /**
     * Get debug information about the nametag system
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("NameTag Manager Status:\n");
        info.append("Enabled: ").append(nameTagsEnabled).append("\n");
        info.append("Online Players: ").append(Bukkit.getOnlinePlayers().size()).append("\n");
        info.append("Game Available: ").append(game != null).append("\n");

        if (game != null) {
            TeamManager teamManager = game.getTeamManager();
            info.append("Team Manager Available: ").append(teamManager != null).append("\n");
            if (teamManager != null) {
                info.append("Total Teams: ").append(teamManager.getAllTeams().size()).append("\n");
                info.append("Players on Teams: ").append(getPlayersOnTeamsCount()).append("\n");
            }
        }

        info.append("\n").append(NameTagCreator.getDebugInfo());

        return info.toString();
    }

    /**
     * Get count of players currently on teams
     */
    private int getPlayersOnTeamsCount() {
        int count = 0;
        TeamManager teamManager = game.getTeamManager();

        if (teamManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (teamManager.isPlayerOnTeam(player.getUniqueId())) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Toggle nametags on/off
     */
    public void toggleNameTags() {
        if (nameTagsEnabled) {
            disableTeamNameTags();
        } else {
            enableTeamNameTags();
        }
    }
}
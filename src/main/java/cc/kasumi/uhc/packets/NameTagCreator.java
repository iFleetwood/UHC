package cc.kasumi.uhc.packets;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.team.TeamManager;
import cc.kasumi.uhc.team.UHCTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Enhanced NameTagCreator that supports team-based nametags
 * - Teammates see each other in green
 * - Enemies see each other in red
 * - Players see themselves normally
 */
public class NameTagCreator {

    private static final Map<UUID, Map<UUID, String>> activeTeams = new HashMap<>();
    private static final String TEAM_PREFIX = "t"; // Shortened prefix

    // Team colors
    private static final String TEAMMATE_COLOR = ChatColor.GREEN.toString();
    private static final String ENEMY_COLOR = ChatColor.RED.toString();
    private static final String SELF_COLOR = ChatColor.WHITE.toString();

    /**
     * Update nametags for all players based on current team configuration
     */
    public static void updateAllNameTags() {
        try {
            UHC uhc = UHC.getInstance();
            if (uhc == null || uhc.getGame() == null) {
                return;
            }

            TeamManager teamManager = uhc.getGame().getTeamManager();
            if (teamManager == null) {
                return;
            }

            // Clear existing team configurations
            clearAllTeams();

            // Update nametags for each player
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                try {
                    updateNameTagsForPlayer(viewer, teamManager);
                } catch (Exception e) {
                    UHC.getInstance().getLogger().warning("Failed to update nametags for " + viewer.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Critical error in updateAllNameTags: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update nametags for a specific player (when they join)
     */
    public static void updateNameTagsForPlayer(Player viewer, TeamManager teamManager) {
        UUID viewerUuid = viewer.getUniqueId();
        UHCTeam viewerTeam = teamManager.getPlayerTeam(viewerUuid);

        // Create teams for this viewer
        Map<UUID, String> viewerTeams = new HashMap<>();
        activeTeams.put(viewerUuid, viewerTeams);

        for (Player target : Bukkit.getOnlinePlayers()) {
            UUID targetUuid = target.getUniqueId();
            UHCTeam targetTeam = teamManager.getPlayerTeam(targetUuid);

            String teamName = getTeamNameForViewer(viewerUuid, targetUuid);
            String color = determineColor(viewerTeam, targetTeam, viewerUuid, targetUuid);

            // Create team packet for this viewer (including for themselves)
            createTeamForViewer(viewer, target, teamName, color);
            viewerTeams.put(targetUuid, teamName);
        }
    }

    /**
     * Update nametags when teams change
     */
    public static void updateNameTagsForTeamChange(Player player) {
        try {
            UHC uhc = UHC.getInstance();
            if (uhc == null || uhc.getGame() == null) {
                return;
            }

            TeamManager teamManager = uhc.getGame().getTeamManager();
            if (teamManager == null) {
                return;
            }

            // Clean up old nametag data for this player first
            cleanupPlayer(player);

            // Update how this player sees others (complete refresh)
            updateNameTagsForPlayer(player, teamManager);

            // Update how others see this player (complete refresh for all other players)
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player)) {
                    try {
                        // Clean up and refresh the other player's view
                        UUID otherUuid = other.getUniqueId();
                        UUID playerUuid = player.getUniqueId();

                        // Remove old team relationship if it exists
                        Map<UUID, String> otherTeams = activeTeams.get(otherUuid);
                        if (otherTeams != null && otherTeams.containsKey(playerUuid)) {
                            String oldTeamName = otherTeams.get(playerUuid);
                            removePlayerFromTeam(other, player, oldTeamName);
                            otherTeams.remove(playerUuid);
                        }

                        // Create new team relationship
                        updateSingleNameTag(other, player, teamManager);

                    } catch (Exception e) {
                        UHC.getInstance().getLogger().warning("Failed to update " + other.getName() +
                                "'s view of " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Critical error in updateNameTagsForTeamChange for " +
                    player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update a single nametag relationship between viewer and target
     */
    private static void updateSingleNameTag(Player viewer, Player target, TeamManager teamManager) {
        UUID viewerUuid = viewer.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        UHCTeam viewerTeam = teamManager.getPlayerTeam(viewerUuid);
        UHCTeam targetTeam = teamManager.getPlayerTeam(targetUuid);

        String teamName = getTeamNameForViewer(viewerUuid, targetUuid);
        String color = determineColor(viewerTeam, targetTeam, viewerUuid, targetUuid);

        // Remove old team if exists
        Map<UUID, String> viewerTeams = activeTeams.get(viewerUuid);
        if (viewerTeams != null && viewerTeams.containsKey(targetUuid)) {
            String oldTeamName = viewerTeams.get(targetUuid);
            removePlayerFromTeam(viewer, target, oldTeamName);
        }

        // Create new team
        createTeamForViewer(viewer, target, teamName, color);

        // Update tracking
        if (viewerTeams == null) {
            viewerTeams = new HashMap<>();
            activeTeams.put(viewerUuid, viewerTeams);
        }
        viewerTeams.put(targetUuid, teamName);
    }

    /**
     * Clean up nametags when a player leaves or needs refresh
     */
    public static void cleanupPlayer(Player player) {
        try {
            UUID playerUuid = player.getUniqueId();

            // Remove this player from all other players' teams
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(player) && other.isOnline()) {
                    try {
                        Map<UUID, String> otherTeams = activeTeams.get(other.getUniqueId());
                        if (otherTeams != null && otherTeams.containsKey(playerUuid)) {
                            String teamName = otherTeams.get(playerUuid);
                            removePlayerFromTeam(other, player, teamName);
                            otherTeams.remove(playerUuid);
                        }
                    } catch (Exception e) {
                        // Continue cleanup for other players even if one fails
                        UHC.getInstance().getLogger().warning("Failed to cleanup nametag for " +
                                other.getName() + " -> " + player.getName() + ": " + e.getMessage());
                    }
                }
            }

            // Remove this player's team tracking
            Map<UUID, String> playerTeams = activeTeams.remove(playerUuid);
            if (playerTeams != null) {
                // Clean up any remaining team packets for this player
                for (Map.Entry<UUID, String> entry : playerTeams.entrySet()) {
                    try {
                        Player target = Bukkit.getPlayer(entry.getKey());
                        if (target != null && target.isOnline()) {
                            removePlayerFromTeam(player, target, entry.getValue());
                        }
                    } catch (Exception e) {
                        // Continue cleanup even if individual removals fail
                    }
                }
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().warning("Error during player cleanup for " +
                    player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Determine the color for target based on team relationship
     */
    private static String determineColor(UHCTeam viewerTeam, UHCTeam targetTeam, UUID viewerUuid, UUID targetUuid) {
        // If viewer is viewing themselves, show green if they're on a team
        if (viewerUuid.equals(targetUuid)) {
            return viewerTeam != null ? TEAMMATE_COLOR : SELF_COLOR;
        }

        // If either player is not on a team, show as enemy
        if (viewerTeam == null || targetTeam == null) {
            return ENEMY_COLOR;
        }

        // If they're on the same team, show as teammate
        if (viewerTeam.equals(targetTeam)) {
            return TEAMMATE_COLOR;
        }

        // Different teams = enemies
        return ENEMY_COLOR;
    }

    /**
     * Generate a unique team name for viewer-target relationship
     * Limited to 16 characters for 1.8.8 compatibility
     */
    private static String getTeamNameForViewer(UUID viewerUuid, UUID targetUuid) {
        // Create a shorter deterministic team name based on viewer and target
        // Use hashCode to create shorter, unique identifiers
        int viewerHash = Math.abs(viewerUuid.hashCode()) % 1000;
        int targetHash = Math.abs(targetUuid.hashCode()) % 1000;

        // Format: "u" + 3-digit viewer hash + 3-digit target hash = max 7 chars
        return String.format("u%03d%03d", viewerHash, targetHash);
    }

    /**
     * Create a team packet for a specific viewer-target relationship
     */
    private static void createTeamForViewer(Player viewer, Player target, String teamName, String color) {
        try {
            // Validate team name length for 1.8.8 compatibility
            if (teamName.length() > 16) {
                UHC.getInstance().getLogger().warning("Team name too long: " + teamName + " (length: " + teamName.length() + ")");
                return;
            }

            // Validate color string length
            if (color.length() > 16) {
                UHC.getInstance().getLogger().warning("Color prefix too long: " + color + " (length: " + color.length() + ")");
                color = color.substring(0, 16); // Truncate if too long
            }

            // Create team with target player as member
            List<String> members = Arrays.asList(target.getName());

            PacketWrapper teamPacket = new PacketWrapper(
                    teamName,           // Team name (max 16 chars)
                    color,              // Prefix (color) (max 16 chars)
                    "",                 // Suffix (empty)
                    0,                  // Create team
                    members,            // Members
                    true                // Visible
            );

            // Send only to the viewer
            teamPacket.send(viewer);

        } catch (Exception e) {
            UHC.getInstance().getLogger().warning("Failed to create team packet for " +
                    viewer.getName() + " -> " + target.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Remove a player from a team for a specific viewer
     * Fixed to avoid constructor validation issues
     */
    private static void removePlayerFromTeam(Player viewer, Player target, String teamName) {
        try {
            if (viewer == null || !viewer.isOnline() || target == null || teamName == null || teamName.isEmpty()) {
                return;
            }

            // Just remove the player from the team using param=4 (leave)
            // Don't try to delete the team as that causes constructor validation errors
            List<String> members = Arrays.asList(target.getName());
            PacketWrapper removePacket = new PacketWrapper(teamName, 4, members);
            removePacket.send(viewer);

        } catch (Exception e) {
            // Log the error but don't spam - this is a common occurrence during player disconnects
            if (UHC.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
                UHC.getInstance().getLogger().fine("Failed to remove " +
                        (target != null ? target.getName() : "null") + " from team " + teamName +
                        " for viewer " + (viewer != null ? viewer.getName() : "null") + ": " + e.getMessage());
            }
        }
    }

    /**
     * Clear all active teams - more robust cleanup method
     */
    private static void clearAllTeams() {
        try {
            // Instead of trying to delete individual teams (which causes errors),
            // just clear our tracking and let the client handle cleanup
            for (Map.Entry<UUID, Map<UUID, String>> entry : activeTeams.entrySet()) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer == null || !viewer.isOnline()) {
                    continue;
                }

                // Try to delete each team using the full constructor
                for (String teamName : entry.getValue().values()) {
                    try {
                        PacketWrapper deletePacket = new PacketWrapper(teamName, "", "", 1, new ArrayList<>(), false);
                        deletePacket.send(viewer);
                    } catch (Exception e) {
                        // Ignore delete errors - they're not critical
                    }
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        activeTeams.clear();
    }

    /**
     * Get debug information about active teams
     */
    public static String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Active NameTag Teams:\n");

        for (Map.Entry<UUID, Map<UUID, String>> entry : activeTeams.entrySet()) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            String viewerName = viewer != null ? viewer.getName() : "Unknown";

            info.append("Viewer: ").append(viewerName).append(" (").append(entry.getValue().size()).append(" teams)\n");

            for (Map.Entry<UUID, String> teamEntry : entry.getValue().entrySet()) {
                Player target = Bukkit.getPlayer(teamEntry.getKey());
                String targetName = target != null ? target.getName() : "Unknown";
                info.append("  -> ").append(targetName).append(" (").append(teamEntry.getValue()).append(")\n");
            }
        }

        return info.toString();
    }

    /**
     * Force refresh all nametags (useful for debugging)
     */
    public static void forceRefreshAll() {
        try {
            UHC.getInstance().getLogger().info("Force refreshing all nametags...");

            // Step 1: Clear all existing teams
            clearAllTeams();

            // Step 2: Wait a tick then update
            Bukkit.getScheduler().runTaskLater(UHC.getInstance(), () -> {
                try {
                    updateAllNameTags();
                    UHC.getInstance().getLogger().info("Force refresh completed for " +
                            Bukkit.getOnlinePlayers().size() + " players");
                } catch (Exception e) {
                    UHC.getInstance().getLogger().severe("Error during force refresh: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 2L);

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Critical error in forceRefreshAll: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
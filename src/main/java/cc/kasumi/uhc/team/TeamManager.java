package cc.kasumi.uhc.team;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class TeamManager {

    private final Game game;
    private final Map<UUID, UHCTeam> teams;
    private final Map<UUID, UUID> playerToTeam; // Player UUID -> Team UUID
    private final TeamConfiguration config;

    // Available team colors
    private static final ChatColor[] TEAM_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.AQUA,
            ChatColor.GRAY, ChatColor.LIGHT_PURPLE, ChatColor.DARK_GREEN, ChatColor.DARK_BLUE,
            ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.DARK_AQUA, ChatColor.GOLD
    };

    private int colorIndex = 0;

    public TeamManager(Game game) {
        this.game = game;
        this.teams = new HashMap<>();
        this.playerToTeam = new HashMap<>();
        this.config = new TeamConfiguration();
    }

    /**
     * Create a new team
     */
    public UHCTeam createTeam(String teamName) {
        return createTeam(teamName, getNextAvailableColor());
    }

    /**
     * Create a team with specific color
     */
    public UHCTeam createTeam(String teamName, ChatColor color) {
        // Check if team name already exists
        if (getTeamByName(teamName) != null) {
            return null;
        }

        UHCTeam team = new UHCTeam(teamName, color);
        teams.put(team.getTeamId(), team);

        UHC.getInstance().getLogger().info("Created team: " + teamName + " with color " + color.name());
        return team;
    }

    /**
     * Delete a team
     */
    public boolean deleteTeam(UUID teamId) {
        UHCTeam team = teams.get(teamId);
        if (team == null) {
            return false;
        }

        // Remove all players from the team
        List<UUID> members = new ArrayList<>(team.getMembers());
        for (UUID playerUuid : members) {
            removePlayerFromTeam(playerUuid);
        }

        teams.remove(teamId);
        UHC.getInstance().getLogger().info("Deleted team: " + team.getTeamName());
        return true;
    }

    /**
     * Delete team by name
     */
    public boolean deleteTeam(String teamName) {
        UHCTeam team = getTeamByName(teamName);
        if (team == null) {
            return false;
        }
        return deleteTeam(team.getTeamId());
    }

    /**
     * Add a player to a team
     */
    public boolean addPlayerToTeam(UUID playerUuid, UUID teamId) {
        UHCTeam team = teams.get(teamId);
        if (team == null) {
            return false;
        }

        // Remove player from current team if they have one
        removePlayerFromTeam(playerUuid);

        // Check team size limits
        if (config.isMaxTeamSizeEnabled() && team.getSize() >= config.getMaxTeamSize()) {
            return false;
        }

        // Add to team
        if (team.addMember(playerUuid)) {
            playerToTeam.put(playerUuid, teamId);

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(ChatColor.GREEN + "You joined team " + team.getFormattedName());
                team.sendMessage(player.getName() + " joined the team!");
            }

            return true;
        }

        return false;
    }

    /**
     * Add player to team by name
     */
    public boolean addPlayerToTeam(UUID playerUuid, String teamName) {
        UHCTeam team = getTeamByName(teamName);
        if (team == null) {
            return false;
        }
        return addPlayerToTeam(playerUuid, team.getTeamId());
    }

    /**
     * Remove a player from their current team
     */
    public boolean removePlayerFromTeam(UUID playerUuid) {
        UUID teamId = playerToTeam.get(playerUuid);
        if (teamId == null) {
            return false;
        }

        UHCTeam team = teams.get(teamId);
        if (team == null) {
            playerToTeam.remove(playerUuid);
            return false;
        }

        team.removeMember(playerUuid);
        playerToTeam.remove(playerUuid);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(ChatColor.YELLOW + "You left team " + team.getFormattedName());
            team.sendMessage(player.getName() + " left the team!");
        }

        // Delete team if empty and auto-deletion is enabled
        if (config.isAutoDeleteEmptyTeams() && team.getSize() == 0) {
            teams.remove(teamId);
            UHC.getInstance().getLogger().info("Auto-deleted empty team: " + team.getTeamName());
        }

        return true;
    }

    /**
     * Get a player's team
     */
    public UHCTeam getPlayerTeam(UUID playerUuid) {
        UUID teamId = playerToTeam.get(playerUuid);
        if (teamId == null) {
            return null;
        }
        return teams.get(teamId);
    }

    /**
     * Check if a player is on a team
     */
    public boolean isPlayerOnTeam(UUID playerUuid) {
        return playerToTeam.containsKey(playerUuid);
    }

    /**
     * Check if two players are on the same team
     */
    public boolean arePlayersOnSameTeam(UUID player1, UUID player2) {
        UUID team1 = playerToTeam.get(player1);
        UUID team2 = playerToTeam.get(player2);

        if (team1 == null || team2 == null) {
            return false;
        }

        return team1.equals(team2);
    }

    /**
     * Get team by name (case insensitive)
     */
    public UHCTeam getTeamByName(String teamName) {
        return teams.values().stream()
                .filter(team -> team.getTeamName().equalsIgnoreCase(teamName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get team by ID
     */
    public UHCTeam getTeamById(UUID teamId) {
        return teams.get(teamId);
    }

    /**
     * Get all teams
     */
    public Collection<UHCTeam> getAllTeams() {
        return teams.values();
    }

    /**
     * Get all alive teams (teams with at least one alive member)
     */
    public List<UHCTeam> getAliveTeams() {
        return teams.values().stream()
                .filter(team -> !team.isEliminated())
                .collect(Collectors.toList());
    }

    /**
     * Get eliminated teams
     */
    public List<UHCTeam> getEliminatedTeams() {
        return teams.values().stream()
                .filter(UHCTeam::isEliminated)
                .collect(Collectors.toList());
    }

    /**
     * Handle player death - mark as eliminated
     */
    public void handlePlayerDeath(UUID playerUuid) {
        UHCTeam team = getPlayerTeam(playerUuid);
        if (team != null) {
            team.eliminatePlayer(playerUuid);

            Player player = Bukkit.getPlayer(playerUuid);
            String playerName = player != null ? player.getName() : "Unknown";

            if (team.isEliminated()) {
                // Entire team eliminated
                Bukkit.broadcastMessage(team.getFormattedName() + ChatColor.RED + " has been eliminated!");
                team.sendMessage("Your team has been eliminated!");
            } else {
                // Just this player eliminated
                team.sendMessage(ChatColor.RED + playerName + " has been eliminated!");
            }
        }
    }

    /**
     * Handle player respawn - mark as alive
     */
    public void handlePlayerRespawn(UUID playerUuid) {
        UHCTeam team = getPlayerTeam(playerUuid);
        if (team != null) {
            team.revivePlayer(playerUuid);

            Player player = Bukkit.getPlayer(playerUuid);
            String playerName = player != null ? player.getName() : "Unknown";
            team.sendMessage(ChatColor.GREEN + playerName + " has respawned!");
        }
    }

    /**
     * Auto-assign players to teams
     */
    public void autoAssignTeams(List<UUID> players, int teamSize) {
        if (players.isEmpty()) {
            return;
        }

        List<UUID> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        int teamNumber = 1;
        int currentTeamIndex = 0;
        UHCTeam currentTeam = null;

        for (UUID playerUuid : shuffledPlayers) {
            // Create new team if needed
            if (currentTeam == null || currentTeamIndex >= teamSize) {
                String teamName = "Team " + teamNumber;
                currentTeam = createTeam(teamName);
                teamNumber++;
                currentTeamIndex = 0;

                if (currentTeam == null) {
                    UHC.getInstance().getLogger().warning("Failed to create team: " + teamName);
                    continue;
                }
            }

            // Add player to current team
            addPlayerToTeam(playerUuid, currentTeam.getTeamId());
            currentTeamIndex++;
        }

        UHC.getInstance().getLogger().info("Auto-assigned " + players.size() +
                " players to " + (teamNumber - 1) + " teams");
    }

    /**
     * Balance teams by moving players
     */
    public void balanceTeams() {
        List<UHCTeam> teamsList = new ArrayList<>(teams.values());
        if (teamsList.size() < 2) {
            return;
        }

        // Sort teams by size (smallest first)
        teamsList.sort(Comparator.comparingInt(UHCTeam::getSize));

        boolean changed = true;
        while (changed) {
            changed = false;
            UHCTeam smallest = teamsList.get(0);
            UHCTeam largest = teamsList.get(teamsList.size() - 1);

            // If difference is more than 1, move a player
            if (largest.getSize() - smallest.getSize() > 1) {
                // Find a non-leader to move
                UUID playerToMove = null;
                for (UUID member : largest.getMembers()) {
                    if (!largest.isTeamLeader(member)) {
                        playerToMove = member;
                        break;
                    }
                }

                // If only leader remains, move them too
                if (playerToMove == null && largest.getSize() > 1) {
                    playerToMove = largest.getTeamLeader();
                }

                if (playerToMove != null) {
                    removePlayerFromTeam(playerToMove);
                    addPlayerToTeam(playerToMove, smallest.getTeamId());
                    changed = true;

                    // Re-sort after change
                    teamsList.sort(Comparator.comparingInt(UHCTeam::getSize));
                }
            }
        }

        UHC.getInstance().getLogger().info("Teams balanced");
    }

    /**
     * Clear all teams
     */
    public void clearAllTeams() {
        teams.clear();
        playerToTeam.clear();
        colorIndex = 0;
        UHC.getInstance().getLogger().info("All teams cleared");
    }

    /**
     * Get next available color
     */
    private ChatColor getNextAvailableColor() {
        ChatColor color = TEAM_COLORS[colorIndex % TEAM_COLORS.length];
        colorIndex++;
        return color;
    }

    /**
     * Get team statistics
     */
    public TeamStats getTeamStats() {
        TeamStats stats = new TeamStats();
        stats.totalTeams = teams.size();
        stats.aliveTeams = getAliveTeams().size();
        stats.eliminatedTeams = getEliminatedTeams().size();
        stats.totalPlayers = playerToTeam.size();

        if (!teams.isEmpty()) {
            stats.averageTeamSize = (double) playerToTeam.size() / teams.size();
            stats.largestTeamSize = teams.values().stream().mapToInt(UHCTeam::getSize).max().orElse(0);
            stats.smallestTeamSize = teams.values().stream().mapToInt(UHCTeam::getSize).min().orElse(0);
        }

        return stats;
    }

    /**
     * Check if friendly fire should be prevented
     */
    public boolean shouldPreventFriendlyFire(UUID attacker, UUID victim) {
        if (!arePlayersOnSameTeam(attacker, victim)) {
            return false;
        }

        UHCTeam team = getPlayerTeam(attacker);
        return team != null && !team.isFriendlyFire();
    }

    /**
     * Team statistics holder
     */
    public static class TeamStats {
        public int totalTeams = 0;
        public int aliveTeams = 0;
        public int eliminatedTeams = 0;
        public int totalPlayers = 0;
        public double averageTeamSize = 0.0;
        public int largestTeamSize = 0;
        public int smallestTeamSize = 0;
    }
}
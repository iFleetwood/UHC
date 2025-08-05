package cc.kasumi.uhc.team;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simplified TeamManager that handles both solo and team modes
 * Solo mode = teams of size 1, Team mode = teams of size > 1
 */
@Getter
public class TeamManager {

    private final Game game;
    private final Map<UUID, UHCTeam> teams;
    private final Map<UUID, UUID> playerToTeam; // Player UUID -> Team UUID
    private final TeamConfiguration config;

    // Available team colors
    private static final ChatColor[] TEAM_COLORS = {
            ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW,
            ChatColor.AQUA, ChatColor.GRAY, ChatColor.LIGHT_PURPLE, ChatColor.DARK_GREEN,
            ChatColor.DARK_BLUE, ChatColor.DARK_RED, ChatColor.DARK_PURPLE,
            ChatColor.DARK_AQUA, ChatColor.GOLD
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
                // Only show team join message for actual teams (size > 1)
                if (game.isTeamMode()) {
                    player.sendMessage(ChatColor.GREEN + "You joined team " + team.getFormattedName());
                    team.sendMessage(player.getName() + " joined the team!");
                }
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
        if (player != null && game.isTeamMode()) {
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
     * Handle player respawn - mark as alive
     */
    public void handlePlayerRespawn(UUID playerUuid) {
        UHCTeam team = getPlayerTeam(playerUuid);
        if (team != null) {
            team.revivePlayer(playerUuid);

            Player player = Bukkit.getPlayer(playerUuid);
            String playerName = player != null ? player.getName() : "Unknown";

            if (game.isTeamMode()) {
                team.sendMessage(ChatColor.GREEN + playerName + " has respawned!");
            }
        }
    }

    /**
     * Auto-assign players to teams
     */
    public void autoAssignTeams(List<UUID> players, int teamSize) {
        if (players.isEmpty()) {
            return;
        }

        if (teamSize == 1) {
            // Solo mode - create individual teams
            for (UUID playerUuid : players) {
                Player player = Bukkit.getPlayer(playerUuid);
                String teamName = player != null ? player.getName() : "Player";
                UHCTeam soloTeam = createTeam(teamName);
                if (soloTeam != null) {
                    addPlayerToTeam(playerUuid, soloTeam.getTeamId());
                }
            }
            UHC.getInstance().getLogger().info("Created " + players.size() + " solo teams");
            return;
        }

        // Team mode - group players into teams
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
                " players to " + (teamNumber - 1) + " teams of size " + teamSize);
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

        // In solo mode, players are on different teams so this shouldn't trigger
        if (game.isSoloMode()) {
            return false;
        }

        UHCTeam team = getPlayerTeam(attacker);
        return team != null && !team.isFriendlyFire();
    }

    /**
     * Create solo teams for all online players not on teams
     */
    public void createSoloTeamsForUnassignedPlayers() {
        int soloTeamsCreated = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPlayerOnTeam(player.getUniqueId())) {
                String teamName = player.getName();
                UHCTeam soloTeam = createTeam(teamName);
                if (soloTeam != null) {
                    addPlayerToTeam(player.getUniqueId(), soloTeam.getTeamId());
                    soloTeamsCreated++;
                }
            }
        }

        if (soloTeamsCreated > 0) {
            UHC.getInstance().getLogger().info("Created " + soloTeamsCreated + " solo teams for unassigned players");
        }
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

    // Add to TeamManager.java class

    /**
     * Handle player death - mark as eliminated and announce if needed
     * Enhanced version that provides better death messaging
     */
    public void handlePlayerDeath(UUID playerUuid) {
        UHCTeam team = getPlayerTeam(playerUuid);
        if (team == null) {
            return;
        }

        team.eliminatePlayer(playerUuid);

        Player player = Bukkit.getPlayer(playerUuid);
        String playerName = player != null ? player.getName() : "Unknown";

        // Update kills for the killer if applicable
        if (player != null) {
            handleKillCredit(player);
        }

        if (team.isEliminated()) {
            // Entire team eliminated
            handleTeamElimination(team, playerName);
        } else {
            // Just this player eliminated from team
            if (game.isTeamMode() && team.getSize() > 1) {
                team.sendMessage(ChatColor.RED + playerName + " has been eliminated!");

                // Check if this was the team leader
                if (team.getTeamLeader() != null && !team.getTeamLeader().equals(playerUuid)) {
                    Player newLeader = team.getTeamLeaderPlayer();
                    if (newLeader != null) {
                        team.sendMessage(ChatColor.YELLOW + newLeader.getName() + " is now the team leader!");
                    }
                }
            }
        }
    }

    /**
     * Handle complete team elimination
     */
    private void handleTeamElimination(UHCTeam team, String lastPlayerName) {
        if (game.isTeamMode() && team.getSize() > 1) {
            // Multi-player team eliminated
            Bukkit.broadcastMessage(team.getFormattedName() + ChatColor.RED + " has been eliminated!");
            team.sendMessage(ChatColor.RED + "Your team has been eliminated!");

            // List team members for context
            if (team.getSize() > 2) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "Team members: " + team.getFormattedMemberList());
            }
        } else {
            // Solo team eliminated (already handled by death message)
            // Just update internal state
        }

        UHC.getInstance().getLogger().info("Team eliminated: " + team.getTeamName() +
                " (last player: " + lastPlayerName + ")");
    }

    /**
     * Handle kill credit assignment
     */
    private void handleKillCredit(Player victim) {
        // Check if victim was killed by another player
        if (victim.getKiller() instanceof Player) {
            Player killer = victim.getKiller();
            UHCPlayer uhcKiller = game.getUHCPlayer(killer.getUniqueId());

            if (uhcKiller != null) {
                uhcKiller.addKill();

                // Send kill confirmation to killer
                killer.sendMessage(ChatColor.GREEN + "You killed " + victim.getName() +
                        ChatColor.GRAY + " (Total kills: " + uhcKiller.getKills() + ")");

                // Award kill in team context if applicable
                if (game.isTeamMode()) {
                    UHCTeam killerTeam = getPlayerTeam(killer.getUniqueId());
                    if (killerTeam != null && killerTeam.getSize() > 1) {
                        killerTeam.sendMessage(ChatColor.GREEN + killer.getName() +
                                " killed " + victim.getName() + "!");
                    }
                }
            }
        }
    }

    /**
     * Get elimination order for post-game statistics
     */
    public List<TeamEliminationRecord> getEliminationOrder() {
        return new ArrayList<>(eliminationRecords);
    }

    /**
     * Check if game should end based on team states
     */
    public boolean shouldGameEnd() {
        List<UHCTeam> aliveTeams = getAliveTeams();

        if (aliveTeams.isEmpty()) {
            return true; // No teams left
        }

        if (aliveTeams.size() == 1) {
            return true; // Only one team left
        }

        return false; // Multiple teams still alive
    }

    /**
     * Get detailed team statistics for game end
     */
    public TeamEndGameStats getEndGameStats() {
        List<UHCTeam> aliveTeams = getAliveTeams();
        List<UHCTeam> eliminatedTeams = getEliminatedTeams();

        int totalKills = 0;
        int totalPlayers = 0;

        for (UHCTeam team : getAllTeams()) {
            totalPlayers += team.getSize();
            for (UUID memberUuid : team.getMembers()) {
                UHCPlayer player = game.getUHCPlayer(memberUuid);
                if (player != null) {
                    totalKills += player.getKills();
                }
            }
        }

        return new TeamEndGameStats(
                aliveTeams.size(),
                eliminatedTeams.size(),
                totalPlayers,
                totalKills,
                aliveTeams,
                eliminatedTeams
        );
    }

    /**
     * Force eliminate a team (admin command)
     */
    public boolean forceEliminateTeam(String teamName, String reason) {
        UHCTeam team = getTeamByName(teamName);
        if (team == null) {
            return false;
        }

        // Eliminate all team members
        for (UUID memberUuid : new ArrayList<>(team.getMembers())) {
            team.eliminatePlayer(memberUuid);

            // Set player to spectating if online
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null) {
                UHCPlayer uhcPlayer = game.getUHCPlayer(memberUuid);
                if (uhcPlayer != null) {
                    uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);
                }
            }
        }

        // Announce elimination
        Bukkit.broadcastMessage(team.getFormattedName() + ChatColor.RED + " has been eliminated by admin!");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Reason: " + reason);

        // Check if game should end
        game.checkGameEndCondition();

        return true;
    }

    /**
     * Revive a team (admin command - only works if game hasn't ended)
     */
    public boolean reviveTeam(String teamName) {
        UHCTeam team = getTeamByName(teamName);
        if (team == null) {
            return false;
        }

        if (!(game.getState() instanceof ActiveGameState)) {
            return false; // Can't revive after game end
        }

        // Revive all team members who are online
        int revivedCount = 0;
        for (UUID memberUuid : team.getMembers()) {
            Player player = Bukkit.getPlayer(memberUuid);
            if (player != null) {
                team.revivePlayer(memberUuid);

                UHCPlayer uhcPlayer = game.getUHCPlayer(memberUuid);
                if (uhcPlayer != null) {
                    uhcPlayer.setPlayerStateAndManage(PlayerState.ALIVE);
                    player.setHealth(20.0);
                    player.sendMessage(ChatColor.GREEN + "You have been revived by an admin!");
                }

                revivedCount++;
            }
        }

        if (revivedCount > 0) {
            Bukkit.broadcastMessage(team.getFormattedName() + ChatColor.GREEN + " has been revived by admin!");
            return true;
        }

        return false;
    }

    // Add elimination tracking
    private final List<TeamEliminationRecord> eliminationRecords = new ArrayList<>();

    /**
     * Record when a team is eliminated for statistics
     */
    private void recordTeamElimination(UHCTeam team, String reason) {
        eliminationRecords.add(new TeamEliminationRecord(
                team.getTeamName(),
                team.getFormattedName(),
                System.currentTimeMillis(),
                game.getFormattedGameDuration(),
                reason,
                team.getSize(),
                team.getMembersList()
        ));
    }

    /**
     * Team elimination record for statistics
     */
    public static class TeamEliminationRecord {
        public final String teamName;
        public final String formattedName;
        public final long eliminationTime;
        public final String gameTimeWhenEliminated;
        public final String reason;
        public final int teamSize;
        public final List<UUID> members;

        public TeamEliminationRecord(String teamName, String formattedName, long eliminationTime,
                                     String gameTimeWhenEliminated, String reason, int teamSize, List<UUID> members) {
            this.teamName = teamName;
            this.formattedName = formattedName;
            this.eliminationTime = eliminationTime;
            this.gameTimeWhenEliminated = gameTimeWhenEliminated;
            this.reason = reason;
            this.teamSize = teamSize;
            this.members = new ArrayList<>(members);
        }
    }

    /**
     * End game statistics for teams
     */
    public static class TeamEndGameStats {
        public final int aliveTeams;
        public final int eliminatedTeams;
        public final int totalPlayers;
        public final int totalKills;
        public final List<UHCTeam> aliveTeamsList;
        public final List<UHCTeam> eliminatedTeamsList;

        public TeamEndGameStats(int aliveTeams, int eliminatedTeams, int totalPlayers, int totalKills,
                                List<UHCTeam> aliveTeamsList, List<UHCTeam> eliminatedTeamsList) {
            this.aliveTeams = aliveTeams;
            this.eliminatedTeams = eliminatedTeams;
            this.totalPlayers = totalPlayers;
            this.totalKills = totalKills;
            this.aliveTeamsList = new ArrayList<>(aliveTeamsList);
            this.eliminatedTeamsList = new ArrayList<>(eliminatedTeamsList);
        }
    }
}
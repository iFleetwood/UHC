package cc.kasumi.uhc.team;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

@Getter
public class UHCTeam {

    private final UUID teamId;
    private final String teamName;
    private final ChatColor teamColor;
    private final Set<UUID> members;
    private final Set<UUID> alivemembers;

    @Setter
    private UUID teamLeader;

    @Setter
    private boolean friendlyFire;

    @Setter
    private boolean allowJoining;

    private final long createdTime;

    public UHCTeam(String teamName, ChatColor teamColor) {
        this.teamId = UUID.randomUUID();
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.members = new HashSet<>();
        this.alivemembers = new HashSet<>();
        this.friendlyFire = false;
        this.allowJoining = true;
        this.createdTime = System.currentTimeMillis();
    }

    public UHCTeam(UUID teamId, String teamName, ChatColor teamColor) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor;
        this.members = new HashSet<>();
        this.alivemembers = new HashSet<>();
        this.friendlyFire = false;
        this.allowJoining = true;
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * Add a player to the team
     */
    public boolean addMember(UUID playerUuid) {
        if (!allowJoining && !members.isEmpty()) {
            return false;
        }

        boolean added = members.add(playerUuid);
        if (added) {
            alivemembers.add(playerUuid);

            // Set first member as team leader if no leader exists
            if (teamLeader == null) {
                teamLeader = playerUuid;
            }
        }
        return added;
    }

    /**
     * Remove a player from the team
     */
    public boolean removeMember(UUID playerUuid) {
        boolean removed = members.remove(playerUuid);
        if (removed) {
            alivemembers.remove(playerUuid);

            // If the leader was removed, assign a new one
            if (playerUuid.equals(teamLeader) && !members.isEmpty()) {
                teamLeader = members.iterator().next();
            } else if (members.isEmpty()) {
                teamLeader = null;
            }
        }
        return removed;
    }

    /**
     * Mark a player as eliminated (dead)
     */
    public void eliminatePlayer(UUID playerUuid) {
        alivemembers.remove(playerUuid);

        // If the leader died, assign a new one from alive members
        if (playerUuid.equals(teamLeader) && !alivemembers.isEmpty()) {
            teamLeader = alivemembers.iterator().next();
        } else if (alivemembers.isEmpty()) {
            // Team leader can be null if no alive members
            // But keep the original leader for reference
        }
    }

    /**
     * Revive a player (add back to alive members)
     */
    public void revivePlayer(UUID playerUuid) {
        if (members.contains(playerUuid)) {
            alivemembers.add(playerUuid);
        }
    }

    /**
     * Check if a player is a member of this team
     */
    public boolean isMember(UUID playerUuid) {
        return members.contains(playerUuid);
    }

    /**
     * Check if a player is alive on this team
     */
    public boolean isAliveMember(UUID playerUuid) {
        return alivemembers.contains(playerUuid);
    }

    /**
     * Check if this team is eliminated (no alive members)
     */
    public boolean isEliminated() {
        return alivemembers.isEmpty();
    }

    /**
     * Get online players from this team
     */
    public List<Player> getOnlineMembers() {
        List<Player> onlineMembers = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                onlineMembers.add(player);
            }
        }
        return onlineMembers;
    }

    /**
     * Get online alive players from this team
     */
    public List<Player> getOnlineAliveMembers() {
        List<Player> onlineAlive = new ArrayList<>();
        for (UUID uuid : alivemembers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                onlineAlive.add(player);
            }
        }
        return onlineAlive;
    }

    /**
     * Send message to all team members
     */
    public void sendMessage(String message) {
        String teamMessage = teamColor + "[TEAM] " + ChatColor.WHITE + message;
        for (Player player : getOnlineMembers()) {
            player.sendMessage(teamMessage);
        }
    }

    /**
     * Send message to all alive team members
     */
    public void sendMessageToAlive(String message) {
        String teamMessage = teamColor + "[TEAM] " + ChatColor.WHITE + message;
        for (Player player : getOnlineAliveMembers()) {
            player.sendMessage(teamMessage);
        }
    }

    /**
     * Get team leader player
     */
    public Player getTeamLeaderPlayer() {
        if (teamLeader == null) return null;
        return Bukkit.getPlayer(teamLeader);
    }

    /**
     * Check if a player is the team leader
     */
    public boolean isTeamLeader(UUID playerUuid) {
        return playerUuid.equals(teamLeader);
    }

    /**
     * Get formatted team name with color
     */
    public String getFormattedName() {
        return teamColor + teamName + ChatColor.RESET;
    }

    /**
     * Get team size
     */
    public int getSize() {
        return members.size();
    }

    /**
     * Get alive team size
     */
    public int getAliveSize() {
        return alivemembers.size();
    }

    /**
     * Get team members as list
     */
    public List<UUID> getMembersList() {
        return new ArrayList<>(members);
    }

    /**
     * Get alive team members as list
     */
    public List<UUID> getAliveMembersList() {
        return new ArrayList<>(alivemembers);
    }

    /**
     * Check if team can accept new members
     */
    public boolean canAcceptMembers() {
        return allowJoining;
    }

    /**
     * Get team creation time
     */
    public long getCreatedTime() {
        return createdTime;
    }

    /**
     * Get formatted member list for display
     */
    public String getFormattedMemberList() {
        if (members.isEmpty()) {
            return ChatColor.GRAY + "No members";
        }

        StringBuilder sb = new StringBuilder();
        List<UUID> memberList = new ArrayList<>(members);

        for (int i = 0; i < memberList.size(); i++) {
            UUID uuid = memberList.get(i);
            Player player = Bukkit.getPlayer(uuid);
            String playerName = player != null ? player.getName() : "Unknown";

            // Add color coding
            if (uuid.equals(teamLeader)) {
                sb.append(ChatColor.GOLD).append("★").append(playerName); // Leader
            } else if (alivemembers.contains(uuid)) {
                sb.append(ChatColor.GREEN).append(playerName); // Alive
            } else {
                sb.append(ChatColor.GRAY).append(playerName); // Dead
            }

            if (i < memberList.size() - 1) {
                sb.append(ChatColor.WHITE).append(", ");
            }
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        UHCTeam uhcTeam = (UHCTeam) obj;
        return Objects.equals(teamId, uhcTeam.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId);
    }

    @Override
    public String toString() {
        return "UHCTeam{" +
                "name='" + teamName + '\'' +
                ", color=" + teamColor +
                ", members=" + members.size() +
                ", alive=" + alivemembers.size() +
                ", leader=" + (teamLeader != null ? Bukkit.getOfflinePlayer(teamLeader).getName() : "none") +
                '}';
    }
}
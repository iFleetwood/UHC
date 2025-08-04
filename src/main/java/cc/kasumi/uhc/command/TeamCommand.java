package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.team.TeamManager;
import cc.kasumi.uhc.team.UHCTeam;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.annotation.Optional;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

@CommandAlias("team|teams")
public class TeamCommand extends BaseCommand {

    @Subcommand("create")
    @Description("Create a new team")
    @CommandPermission("uhc.team.create")
    public void onCreate(CommandSender sender, String teamName) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        if (teamManager.getTeamByName(teamName) != null) {
            sender.sendMessage(ChatColor.RED + "A team with that name already exists!");
            return;
        }

        UHCTeam team = teamManager.createTeam(teamName);
        if (team != null) {
            sender.sendMessage(ChatColor.GREEN + "Created team: " + team.getFormattedName());

            // If sender is a player, add them to the team
            if (sender instanceof Player) {
                Player player = (Player) sender;
                teamManager.addPlayerToTeam(player.getUniqueId(), team.getTeamId());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create team!");
        }
    }

    @Subcommand("delete")
    @Description("Delete a team")
    @CommandPermission("uhc.team.admin")
    public void onDelete(CommandSender sender, String teamName) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        if (teamManager.deleteTeam(teamName)) {
            sender.sendMessage(ChatColor.GREEN + "Deleted team: " + teamName);
        } else {
            sender.sendMessage(ChatColor.RED + "Team not found: " + teamName);
        }
    }

    @Subcommand("join")
    @Description("Join a team")
    @CommandPermission("uhc.team.join")
    public void onJoin(Player player, String teamName) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        UHCTeam team = teamManager.getTeamByName(teamName);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Team not found: " + teamName);
            return;
        }

        if (!team.canAcceptMembers()) {
            player.sendMessage(ChatColor.RED + "That team is not accepting new members!");
            return;
        }

        if (teamManager.addPlayerToTeam(player.getUniqueId(), team.getTeamId())) {
            player.sendMessage(ChatColor.GREEN + "Successfully joined " + team.getFormattedName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to join team! The team might be full.");
        }
    }

    @Subcommand("leave")
    @Description("Leave your current team")
    @CommandPermission("uhc.team.leave")
    public void onLeave(Player player) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        if (teamManager.removePlayerFromTeam(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "You left your team!");
        } else {
            player.sendMessage(ChatColor.RED + "You are not on a team!");
        }
    }

    @Subcommand("invite")
    @Description("Invite a player to your team")
    @CommandPermission("uhc.team.invite")
    public void onInvite(Player sender, Player target) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        UHCTeam senderTeam = teamManager.getPlayerTeam(sender.getUniqueId());
        if (senderTeam == null) {
            sender.sendMessage(ChatColor.RED + "You are not on a team!");
            return;
        }

        if (!senderTeam.isTeamLeader(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Only team leaders can invite players!");
            return;
        }

        if (teamManager.isPlayerOnTeam(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is already on a team!");
            return;
        }

        // Send invite message
        target.sendMessage(ChatColor.YELLOW + "You have been invited to join " + senderTeam.getFormattedName());
        target.sendMessage(ChatColor.YELLOW + "Type " + ChatColor.WHITE + "/team accept " + senderTeam.getTeamName() + ChatColor.YELLOW + " to join!");

        sender.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to your team!");
        senderTeam.sendMessage(sender.getName() + " invited " + target.getName() + " to the team!");
    }

    @Subcommand("accept")
    @Description("Accept a team invitation")
    @CommandPermission("uhc.team.accept")
    public void onAccept(Player player, String teamName) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        if (teamManager.isPlayerOnTeam(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are already on a team!");
            return;
        }

        UHCTeam team = teamManager.getTeamByName(teamName);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "Team not found: " + teamName);
            return;
        }

        if (teamManager.addPlayerToTeam(player.getUniqueId(), team.getTeamId())) {
            player.sendMessage(ChatColor.GREEN + "Successfully joined " + team.getFormattedName());
        } else {
            player.sendMessage(ChatColor.RED + "Failed to join team!");
        }
    }

    @Subcommand("kick")
    @Description("Kick a player from your team")
    @CommandPermission("uhc.team.kick")
    public void onKick(Player sender, Player target) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        UHCTeam senderTeam = teamManager.getPlayerTeam(sender.getUniqueId());
        if (senderTeam == null) {
            sender.sendMessage(ChatColor.RED + "You are not on a team!");
            return;
        }

        if (!senderTeam.isTeamLeader(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Only team leaders can kick players!");
            return;
        }

        if (!senderTeam.isMember(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is not on your team!");
            return;
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "You cannot kick yourself! Use /team leave instead.");
            return;
        }

        teamManager.removePlayerFromTeam(target.getUniqueId());
        target.sendMessage(ChatColor.RED + "You have been kicked from " + senderTeam.getFormattedName());
        sender.sendMessage(ChatColor.GREEN + "Kicked " + target.getName() + " from the team!");
        senderTeam.sendMessage(target.getName() + " was kicked from the team!");
    }

    @Subcommand("list")
    @Description("List all teams")
    @CommandPermission("uhc.team.list")
    public void onList(CommandSender sender) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        Collection<UHCTeam> teams = teamManager.getAllTeams();
        if (teams.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No teams exist!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Teams (" + teams.size() + ") ===");

        for (UHCTeam team : teams) {
            String status = team.isEliminated() ? ChatColor.RED + " [ELIMINATED]" :
                    ChatColor.GREEN + " [" + team.getAliveSize() + "/" + team.getSize() + " alive]";

            sender.sendMessage(team.getFormattedName() + status);
            sender.sendMessage(ChatColor.GRAY + "  Members: " + team.getFormattedMemberList());
        }
    }

    @Subcommand("info")
    @Description("Show information about your team or a specific team")
    @CommandPermission("uhc.team.info")
    public void onInfo(CommandSender sender, @Optional String teamName) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        UHCTeam team;
        if (teamName != null) {
            team = teamManager.getTeamByName(teamName);
        } else if (sender instanceof Player) {
            team = teamManager.getPlayerTeam(((Player) sender).getUniqueId());
        } else {
            sender.sendMessage(ChatColor.RED + "Please specify a team name!");
            return;
        }

        if (team == null) {
            sender.sendMessage(ChatColor.RED + "Team not found!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + team.getFormattedName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Team ID: " + ChatColor.WHITE + team.getTeamId().toString().substring(0, 8) + "...");
        sender.sendMessage(ChatColor.YELLOW + "Size: " + ChatColor.WHITE + team.getSize() + " members");
        sender.sendMessage(ChatColor.YELLOW + "Alive: " + ChatColor.WHITE + team.getAliveSize() + " members");
        sender.sendMessage(ChatColor.YELLOW + "Leader: " + ChatColor.WHITE +
                (team.getTeamLeaderPlayer() != null ? team.getTeamLeaderPlayer().getName() : "None"));
        sender.sendMessage(ChatColor.YELLOW + "Friendly Fire: " + ChatColor.WHITE +
                (team.isFriendlyFire() ? "Enabled" : "Disabled"));
        sender.sendMessage(ChatColor.YELLOW + "Accepting Members: " + ChatColor.WHITE +
                (team.isAllowJoining() ? "Yes" : "No"));
        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                (team.isEliminated() ? ChatColor.RED + "Eliminated" : ChatColor.GREEN + "Active"));
        sender.sendMessage(ChatColor.YELLOW + "Members: " + team.getFormattedMemberList());
    }

    @Subcommand("chat")
    @Description("Send a message to your team")
    @CommandPermission("uhc.team.chat")
    public void onChat(Player player, String message) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        UHCTeam team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not on a team!");
            return;
        }

        String teamMessage = team.getTeamColor() + "[TEAM] " + ChatColor.WHITE + player.getName() + ": " + message;
        for (Player member : team.getOnlineMembers()) {
            member.sendMessage(teamMessage);
        }
    }

    @Subcommand("auto")
    @Description("Auto-assign players to teams")
    @CommandPermission("uhc.team.admin")
    public void onAuto(CommandSender sender, int teamSize) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        if (teamSize < 1 || teamSize > 10) {
            sender.sendMessage(ChatColor.RED + "Team size must be between 1 and 10!");
            return;
        }

        // Get all online players without teams
        List<UUID> playersToAssign = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!teamManager.isPlayerOnTeam(player.getUniqueId())) {
                playersToAssign.add(player.getUniqueId());
            }
        }

        if (playersToAssign.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No players available to assign to teams!");
            return;
        }

        teamManager.autoAssignTeams(playersToAssign, teamSize);
        sender.sendMessage(ChatColor.GREEN + "Auto-assigned " + playersToAssign.size() +
                " players to teams of size " + teamSize);
    }

    @Subcommand("balance")
    @Description("Balance team sizes")
    @CommandPermission("uhc.team.admin")
    public void onBalance(CommandSender sender) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        sender.sendMessage(ChatColor.RED + "NOT WORKING RIGHT NOW!");
    }

    @Subcommand("clear")
    @Description("Clear all teams")
    @CommandPermission("uhc.team.admin")
    public void onClear(CommandSender sender) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        teamManager.clearAllTeams();
        sender.sendMessage(ChatColor.GREEN + "All teams have been cleared!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "All teams have been disbanded!");
    }

    @Subcommand("stats")
    @Description("Show team statistics")
    @CommandPermission("uhc.team.stats")
    public void onStats(CommandSender sender) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();
        TeamManager.TeamStats stats = teamManager.getTeamStats();

        sender.sendMessage(ChatColor.GOLD + "=== Team Statistics ===");
        sender.sendMessage(ChatColor.YELLOW + "Total Teams: " + ChatColor.WHITE + stats.totalTeams);
        sender.sendMessage(ChatColor.YELLOW + "Alive Teams: " + ChatColor.WHITE + stats.aliveTeams);
        sender.sendMessage(ChatColor.YELLOW + "Eliminated Teams: " + ChatColor.WHITE + stats.eliminatedTeams);
        sender.sendMessage(ChatColor.YELLOW + "Total Players in Teams: " + ChatColor.WHITE + stats.totalPlayers);

        if (stats.totalTeams > 0) {
            sender.sendMessage(ChatColor.YELLOW + "Average Team Size: " + ChatColor.WHITE +
                    String.format("%.1f", stats.averageTeamSize));
            sender.sendMessage(ChatColor.YELLOW + "Largest Team: " + ChatColor.WHITE + stats.largestTeamSize);
            sender.sendMessage(ChatColor.YELLOW + "Smallest Team: " + ChatColor.WHITE + stats.smallestTeamSize);
        }
    }

    @Subcommand("friendlyfire")
    @Description("Toggle friendly fire for your team")
    @CommandPermission("uhc.team.friendlyfire")
    public void onFriendlyFire(Player player, @Optional Boolean enabled) {
        TeamManager teamManager = UHC.getInstance().getGame().getTeamManager();

        UHCTeam team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You are not on a team!");
            return;
        }

        if (!team.isTeamLeader(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only team leaders can change friendly fire settings!");
            return;
        }

        if (enabled == null) {
            enabled = !team.isFriendlyFire();
        }

        team.setFriendlyFire(enabled);
        String status = enabled ? ChatColor.RED + "enabled" : ChatColor.GREEN + "disabled";
        team.sendMessage("Friendly fire has been " + status + ChatColor.WHITE + " for your team!");
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Team Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/team create <name>" + ChatColor.GRAY + " - Create a new team");
        sender.sendMessage(ChatColor.YELLOW + "/team join <name>" + ChatColor.GRAY + " - Join a team");
        sender.sendMessage(ChatColor.YELLOW + "/team leave" + ChatColor.GRAY + " - Leave your team");
        sender.sendMessage(ChatColor.YELLOW + "/team invite <player>" + ChatColor.GRAY + " - Invite a player");
        sender.sendMessage(ChatColor.YELLOW + "/team accept <team>" + ChatColor.GRAY + " - Accept an invitation");
        sender.sendMessage(ChatColor.YELLOW + "/team kick <player>" + ChatColor.GRAY + " - Kick a player (leader only)");
        sender.sendMessage(ChatColor.YELLOW + "/team list" + ChatColor.GRAY + " - List all teams");
        sender.sendMessage(ChatColor.YELLOW + "/team info [team]" + ChatColor.GRAY + " - Show team information");
        sender.sendMessage(ChatColor.YELLOW + "/team chat <message>" + ChatColor.GRAY + " - Send team message");
        sender.sendMessage(ChatColor.YELLOW + "/team friendlyfire [on/off]" + ChatColor.GRAY + " - Toggle friendly fire");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "Admin Commands:");
        sender.sendMessage(ChatColor.YELLOW + "/team delete <name>" + ChatColor.GRAY + " - Delete a team");
        sender.sendMessage(ChatColor.YELLOW + "/team auto <size>" + ChatColor.GRAY + " - Auto-assign players");
        sender.sendMessage(ChatColor.YELLOW + "/team balance" + ChatColor.GRAY + " - Balance team sizes");
        sender.sendMessage(ChatColor.YELLOW + "/team clear" + ChatColor.GRAY + " - Clear all teams");
        sender.sendMessage(ChatColor.YELLOW + "/team stats" + ChatColor.GRAY + " - Show team statistics");
    }
}
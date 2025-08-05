package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.packets.NameTagManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("nametag|nametags")
@CommandPermission("uhc.admin")
public class NameTagCommand extends BaseCommand {

    @Subcommand("enable")
    @Description("Enable team-based nametags")
    public void onEnable(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        NameTagManager nameTagManager = game.getNameTagManager();
        if (nameTagManager == null) {
            sender.sendMessage(ChatColor.RED + "NameTag manager is not available!");
            return;
        }

        if (nameTagManager.areNameTagsEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Team nametags are already enabled!");
            return;
        }

        nameTagManager.enableTeamNameTags();
        sender.sendMessage(ChatColor.GREEN + "Team nametags enabled! Players will see teammates in green and enemies in red.");
    }

    @Subcommand("disable")
    @Description("Disable team-based nametags")
    public void onDisable(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        NameTagManager nameTagManager = game.getNameTagManager();
        if (nameTagManager == null) {
            sender.sendMessage(ChatColor.RED + "NameTag manager is not available!");
            return;
        }

        if (!nameTagManager.areNameTagsEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Team nametags are already disabled!");
            return;
        }

        nameTagManager.disableTeamNameTags();
        sender.sendMessage(ChatColor.GREEN + "Team nametags disabled! Nametags returned to normal.");
    }

    @Subcommand("toggle")
    @Description("Toggle team-based nametags on/off")
    public void onToggle(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        NameTagManager nameTagManager = game.getNameTagManager();
        if (nameTagManager == null) {
            sender.sendMessage(ChatColor.RED + "NameTag manager is not available!");
            return;
        }

        nameTagManager.toggleNameTags();

        String status = nameTagManager.areNameTagsEnabled() ? "enabled" : "disabled";
        sender.sendMessage(ChatColor.GREEN + "Team nametags " + status + "!");
    }

    @Subcommand("refresh")
    @Description("Force refresh all nametags")
    public void onRefresh(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        NameTagManager nameTagManager = game.getNameTagManager();
        if (nameTagManager == null) {
            sender.sendMessage(ChatColor.RED + "NameTag manager is not available!");
            return;
        }

        if (!nameTagManager.areNameTagsEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Team nametags are not enabled!");
            return;
        }

        nameTagManager.forceRefresh();
        sender.sendMessage(ChatColor.GREEN + "Force refreshed all nametags!");
    }

    @Subcommand("status")
    @Description("Show nametag system status")
    public void onStatus(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        NameTagManager nameTagManager = game.getNameTagManager();
        if (nameTagManager == null) {
            sender.sendMessage(ChatColor.RED + "NameTag manager is not available!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== NameTag System Status ===");

        boolean enabled = nameTagManager.areNameTagsEnabled();
        sender.sendMessage(ChatColor.YELLOW + "Status: " +
                (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

        if (enabled) {
            sender.sendMessage(ChatColor.GRAY + "Teammates appear in " + ChatColor.GREEN + "GREEN");
            sender.sendMessage(ChatColor.GRAY + "Enemies appear in " + ChatColor.RED + "RED");
        }

        // Team info
        if (game.getTeamManager() != null) {
            int totalTeams = game.getTeamManager().getAllTeams().size();
            int playersOnTeams = 0;

            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (game.getTeamManager().isPlayerOnTeam(player.getUniqueId())) {
                    playersOnTeams++;
                }
            }

            sender.sendMessage(ChatColor.YELLOW + "Teams: " + ChatColor.WHITE + totalTeams);
            sender.sendMessage(ChatColor.YELLOW + "Players on teams: " + ChatColor.WHITE +
                    playersOnTeams + "/" + org.bukkit.Bukkit.getOnlinePlayers().size());
        }
    }

    @Subcommand("debug")
    @Description("Show detailed debug information")
    public void onDebug(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        NameTagManager nameTagManager = game.getNameTagManager();
        if (nameTagManager == null) {
            sender.sendMessage(ChatColor.RED + "NameTag manager is not available!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== NameTag Debug Information ===");

        String debugInfo = nameTagManager.getDebugInfo();
        String[] lines = debugInfo.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            sender.sendMessage(ChatColor.GRAY + line);
        }
    }

    @Subcommand("test")
    @Description("Test nametag functionality")
    public void onTest(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return;
        }

        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;

        sender.sendMessage(ChatColor.YELLOW + "Testing nametag system...");

        // Show current team status
        if (game.getTeamManager() != null) {
            cc.kasumi.uhc.team.UHCTeam playerTeam = game.getTeamManager().getPlayerTeam(player.getUniqueId());

            if (playerTeam != null) {
                sender.sendMessage(ChatColor.GREEN + "You are on team: " + playerTeam.getFormattedName());
                sender.sendMessage(ChatColor.GRAY + "Teammates: " + playerTeam.getFormattedMemberList());
            } else {
                sender.sendMessage(ChatColor.YELLOW + "You are not on a team!");
            }
        }

        // Show what colors other players should see
        sender.sendMessage(ChatColor.GRAY + "How other players see you:");
        for (org.bukkit.entity.Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;

            if (game.getTeamManager() != null) {
                boolean sameTeam = game.getTeamManager().arePlayersOnSameTeam(player.getUniqueId(), other.getUniqueId());
                ChatColor color = sameTeam ? ChatColor.GREEN : ChatColor.RED;
                String relationship = sameTeam ? "teammate" : "enemy";

                sender.sendMessage(ChatColor.GRAY + "  " + other.getName() + " sees you as " +
                        color + relationship);
            }
        }
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== NameTag Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/nametag enable" + ChatColor.GRAY + " - Enable team-based nametags");
        sender.sendMessage(ChatColor.YELLOW + "/nametag disable" + ChatColor.GRAY + " - Disable team-based nametags");
        sender.sendMessage(ChatColor.YELLOW + "/nametag toggle" + ChatColor.GRAY + " - Toggle nametags on/off");
        sender.sendMessage(ChatColor.YELLOW + "/nametag refresh" + ChatColor.GRAY + " - Force refresh all nametags");
        sender.sendMessage(ChatColor.YELLOW + "/nametag status" + ChatColor.GRAY + " - Show nametag system status");
        sender.sendMessage(ChatColor.YELLOW + "/nametag debug" + ChatColor.GRAY + " - Show debug information");
        sender.sendMessage(ChatColor.YELLOW + "/nametag test" + ChatColor.GRAY + " - Test nametag functionality");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GREEN + "Team NameTags:");
        sender.sendMessage(ChatColor.GRAY + "- Teammates appear in " + ChatColor.GREEN + "GREEN");
        sender.sendMessage(ChatColor.GRAY + "- Enemies appear in " + ChatColor.RED + "RED");
        sender.sendMessage(ChatColor.GRAY + "- Automatically updates when teams change");
    }
}
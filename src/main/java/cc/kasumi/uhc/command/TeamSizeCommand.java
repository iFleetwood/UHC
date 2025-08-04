package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("teamsize")
@CommandPermission("uhc.admin")
public class TeamSizeCommand extends BaseCommand {

    @Default
    public void onGameMode(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        int maxTeamSize = game.getMaxTeamSize();
        String modeText = maxTeamSize == 1 ? "SOLO" : "TEAMS (max size: " + maxTeamSize + ")";

        sender.sendMessage(ChatColor.YELLOW + "Current game mode: " + ChatColor.WHITE + modeText);

        if (game.isTeamMode()) {
            int totalTeams = game.getTeamManager().getAllTeams().size();
            int aliveTeams = game.getTeamManager().getAliveTeams().size();
            sender.sendMessage(ChatColor.GRAY + "Teams: " + totalTeams + " total, " + aliveTeams + " alive");
        }
    }

    @Subcommand("solo")
    @Description("Set game to solo mode (team size 1)")
    public void onSolo(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Cannot change game mode after game has started!");
            return;
        }

        if (game.isSoloMode()) {
            sender.sendMessage(ChatColor.YELLOW + "Game is already in solo mode!");
            return;
        }

        game.setMaxTeamSize(1);
        sender.sendMessage(ChatColor.GREEN + "Game mode set to SOLO!");
        sender.sendMessage(ChatColor.GRAY + "Players will be automatically assigned to individual teams.");
    }

    @Subcommand("teams|team")
    @Description("Set game to team mode with specified team size")
    public void onTeams(CommandSender sender, @Default("2") int teamSize) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Cannot change game mode after game has started!");
            return;
        }

        if (teamSize < 2 || teamSize > 10) {
            sender.sendMessage(ChatColor.RED + "Team size must be between 2 and 10!");
            return;
        }

        if (game.getMaxTeamSize() == teamSize) {
            sender.sendMessage(ChatColor.YELLOW + "Game is already set to team mode with size " + teamSize + "!");
            return;
        }

        game.setMaxTeamSize(teamSize);
        sender.sendMessage(ChatColor.GREEN + "Game mode set to TEAMS with max size " + teamSize + "!");
        sender.sendMessage(ChatColor.GRAY + "Use /team commands to manage teams or /gamemode auto to auto-assign.");
    }

    @Subcommand("auto")
    @Description("Auto-assign all players to teams based on current team size")
    public void onAuto(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Cannot auto-assign after game has started!");
            return;
        }

        // Clear existing teams first
        game.getTeamManager().clearAllTeams();

        // Auto-assign based on current team size
        game.autoAssignPlayersToTeams();

        int totalTeams = game.getTeamManager().getAllTeams().size();

        if (game.isSoloMode()) {
            sender.sendMessage(ChatColor.GREEN + "Auto-assigned " + totalTeams + " players to solo teams!");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Auto-assigned players to " + totalTeams +
                    " teams with max size " + game.getMaxTeamSize() + "!");
        }
    }

    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Game Mode Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/gamemode" + ChatColor.GRAY + " - Show current game mode");
        sender.sendMessage(ChatColor.YELLOW + "/gamemode solo" + ChatColor.GRAY + " - Set to solo mode");
        sender.sendMessage(ChatColor.YELLOW + "/gamemode teams <size>" + ChatColor.GRAY + " - Set to team mode");
        sender.sendMessage(ChatColor.YELLOW + "/gamemode auto" + ChatColor.GRAY + " - Auto-assign players to teams");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Note: Game mode can only be changed before the game starts.");
        sender.sendMessage(ChatColor.GRAY + "Solo mode = team size 1, Team mode = team size > 1");
    }
}
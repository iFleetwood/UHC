package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("teammode|tm")
@CommandPermission("uhc.admin")
public class TeamModeCommand extends BaseCommand {

    @Default
    public void onTeamMode(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        boolean currentMode = game.isTeamMode();
        sender.sendMessage(ChatColor.YELLOW + "Team mode is currently: " +
                (currentMode ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));

        if (currentMode) {
            int totalTeams = game.getTeamManager().getAllTeams().size();
            int aliveTeams = game.getTeamManager().getAliveTeams().size();
            sender.sendMessage(ChatColor.GRAY + "Teams: " + totalTeams + " total, " + aliveTeams + " alive");
        }
    }

    @Subcommand("enable|on")
    @Description("Enable team mode")
    public void onEnable(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Cannot change team mode after game has started!");
            return;
        }

        if (game.isTeamMode()) {
            sender.sendMessage(ChatColor.YELLOW + "Team mode is already enabled!");
            return;
        }

        game.setTeamMode(true);
        sender.sendMessage(ChatColor.GREEN + "Team mode enabled!");
    }

    @Subcommand("disable|off")
    @Description("Disable team mode")
    public void onDisable(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Cannot change team mode after game has started!");
            return;
        }

        if (!game.isTeamMode()) {
            sender.sendMessage(ChatColor.YELLOW + "Team mode is already disabled!");
            return;
        }

        game.setTeamMode(false);
        sender.sendMessage(ChatColor.GREEN + "Team mode disabled!");
    }

    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Team Mode Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/teammode" + ChatColor.GRAY + " - Show current team mode status");
        sender.sendMessage(ChatColor.YELLOW + "/teammode enable" + ChatColor.GRAY + " - Enable team mode");
        sender.sendMessage(ChatColor.YELLOW + "/teammode disable" + ChatColor.GRAY + " - Disable team mode");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Note: Team mode can only be changed before the game starts.");
        sender.sendMessage(ChatColor.GRAY + "Use /team commands to manage individual teams.");
    }
}
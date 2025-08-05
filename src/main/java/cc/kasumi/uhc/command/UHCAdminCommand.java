package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.game.GameEndResult;
import cc.kasumi.uhc.team.UHCTeam;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("uhcadmin|uhca")
@CommandPermission("uhc.admin")
public class UHCAdminCommand extends BaseCommand {

    @Subcommand("end")
    @Description("Force end the current game")
    public void onEndGame(CommandSender sender, @Optional String reason) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (!game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No game is currently running!");
            return;
        }

        String endReason = reason != null ? reason : "Ended by administrator";

        // Force end the game
        GameEndResult result = GameEndResult.forceEnd(endReason);
        game.endGame(result);

        sender.sendMessage(ChatColor.GREEN + "Game has been forcefully ended!");
        sender.sendMessage(ChatColor.GRAY + "Reason: " + endReason);
    }

    @Subcommand("eliminate team")
    @Description("Force eliminate a team")
    public void onEliminateTeam(CommandSender sender, String teamName, @Optional String reason) {
        Game game = UHC.getInstance().getGame();

        if (game == null || !game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No active game running!");
            return;
        }

        String eliminationReason = reason != null ? reason : "Eliminated by administrator";

        if (game.getTeamManager().forceEliminateTeam(teamName, eliminationReason)) {
            sender.sendMessage(ChatColor.GREEN + "Team " + teamName + " has been eliminated!");
        } else {
            sender.sendMessage(ChatColor.RED + "Team not found: " + teamName);
        }
    }

    @Subcommand("revive team")
    @Description("Revive an eliminated team")
    public void onReviveTeam(CommandSender sender, String teamName) {
        Game game = UHC.getInstance().getGame();

        if (game == null || !game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No active game running!");
            return;
        }

        if (game.getTeamManager().reviveTeam(teamName)) {
            sender.sendMessage(ChatColor.GREEN + "Team " + teamName + " has been revived!");
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to revive team: " + teamName);
            sender.sendMessage(ChatColor.GRAY + "Team might not exist, already be alive, or game might have ended.");
        }
    }

    @Subcommand("stats")
    @Description("Show current game statistics")
    public void onStats(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        if (!game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No game is currently running!");
            return;
        }

        Game.GameStatistics stats = game.getGameStatistics();

        sender.sendMessage(ChatColor.GOLD + "=== Game Statistics ===");
        sender.sendMessage(ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + stats.formattedDuration);
        sender.sendMessage(ChatColor.YELLOW + "Mode: " + ChatColor.WHITE + stats.gameMode);
        sender.sendMessage(ChatColor.YELLOW + "World: " + ChatColor.WHITE + stats.worldName);
        sender.sendMessage(ChatColor.YELLOW + "Total Players: " + ChatColor.WHITE + stats.totalPlayers);
        sender.sendMessage(ChatColor.YELLOW + "Total Teams: " + ChatColor.WHITE + stats.totalTeams);
        sender.sendMessage(ChatColor.YELLOW + "Alive Teams: " + ChatColor.WHITE + stats.aliveTeams);
        sender.sendMessage(ChatColor.YELLOW + "Border: " + ChatColor.WHITE + stats.currentBorderSize +
                ChatColor.GRAY + " (started at " + stats.initialBorderSize + ")");
        sender.sendMessage(ChatColor.YELLOW + "PvP: " + ChatColor.WHITE +
                (stats.pvpEnabled ? "Enabled" : "Disabled"));
    }

    @Subcommand("teams alive")
    @Description("List all alive teams")
    public void onListAliveTeams(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null || !game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No active game running!");
            return;
        }

        var aliveTeams = game.getTeamManager().getAliveTeams();

        if (aliveTeams.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No alive teams remaining!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Alive Teams (" + aliveTeams.size() + ") ===");

        for (UHCTeam team : aliveTeams) {
            sender.sendMessage(team.getFormattedName() + ChatColor.WHITE + " - " +
                    ChatColor.GREEN + team.getAliveSize() + "/" + team.getSize() + " alive");
            sender.sendMessage(ChatColor.GRAY + "  Members: " + team.getFormattedMemberList());
        }
    }

    @Subcommand("teams eliminated")
    @Description("List all eliminated teams")
    public void onListEliminatedTeams(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null || !game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No active game running!");
            return;
        }

        var eliminatedTeams = game.getTeamManager().getEliminatedTeams();

        if (eliminatedTeams.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No teams have been eliminated yet!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Eliminated Teams (" + eliminatedTeams.size() + ") ===");

        for (UHCTeam team : eliminatedTeams) {
            sender.sendMessage(team.getFormattedName() + ChatColor.RED + " [ELIMINATED]");
            sender.sendMessage(ChatColor.GRAY + "  Members: " + team.getFormattedMemberList());
        }
    }

    @Subcommand("check end")
    @Description("Manually check if game should end")
    public void onCheckEnd(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null || !game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "No active game running!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Checking game end conditions...");

        // Show current status
        int aliveTeams = game.getTeamManager().getAliveTeams().size();
        int alivePlayers = game.getAlivePlayers().size();

        sender.sendMessage(ChatColor.GRAY + "Alive teams: " + aliveTeams);
        sender.sendMessage(ChatColor.GRAY + "Alive players: " + alivePlayers);

        // Trigger check
        game.checkGameEndCondition();

        sender.sendMessage(ChatColor.GREEN + "Game end check completed!");
    }

    @Subcommand("tp lobby")
    @Description("Teleport all players to lobby")
    public void onTeleportLobby(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        game.teleportAllPlayersToLobby();
        sender.sendMessage(ChatColor.GREEN + "All players have been teleported to lobby!");
    }

    @Subcommand("reset")
    @Description("Reset the game world")
    public void onReset(CommandSender sender) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Resetting game world...");

        // Teleport players to lobby first
        game.teleportAllPlayersToLobby();

        // Reset the world
        game.resetWorldForNewGame();

        sender.sendMessage(ChatColor.GREEN + "World reset initiated! Check console for progress.");
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== UHC Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin end [reason]" + ChatColor.GRAY + " - Force end the game");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin eliminate team <name> [reason]" + ChatColor.GRAY + " - Eliminate a team");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin revive team <name>" + ChatColor.GRAY + " - Revive a team");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin stats" + ChatColor.GRAY + " - Show game statistics");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin teams alive" + ChatColor.GRAY + " - List alive teams");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin teams eliminated" + ChatColor.GRAY + " - List eliminated teams");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin check end" + ChatColor.GRAY + " - Check game end conditions");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin tp lobby" + ChatColor.GRAY + " - Teleport all to lobby");
        sender.sendMessage(ChatColor.YELLOW + "/uhcadmin reset" + ChatColor.GRAY + " - Reset the game world");
    }
}
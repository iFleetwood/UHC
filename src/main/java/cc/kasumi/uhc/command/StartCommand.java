package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("start")
@CommandPermission("uhc.host")
public class StartCommand extends BaseCommand {

    @Default
    public void onStartCommand(CommandSender sender, @Optional Integer seconds) {
        Game game = UHC.getInstance().getGame();

        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game instance is not available!");
            return;
        }

        // Check if game has already been started
        if (game.isStartCountdownStarted()) {
            sender.sendMessage(ChatColor.RED + "Game countdown has already been started!");
            return;
        }

        // Check if world is ready
        if (!game.isWorldReady()) {
            sender.sendMessage(ChatColor.RED + "Cannot start game: World is not ready!");
            sender.sendMessage(ChatColor.YELLOW + "Please wait for world generation to complete or use /world create");
            return;
        }

        // Validate world has spawn location
        if (game.getSpawnLocation() == null) {
            sender.sendMessage(ChatColor.RED + "Cannot start game: World spawn location is invalid!");
            return;
        }

        // Ensure all players are on teams
        game.autoAssignPlayersToTeams();

        // Check if there are teams to scatter
        if (game.getTeamManager().getAllTeams().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Warning: No teams available to scatter!");
            sender.sendMessage(ChatColor.YELLOW + "Make sure players are online.");
        }

        // Default countdown time
        int countdownTime = (seconds != null) ? seconds : 5;

        // Validate countdown time
        if (countdownTime < 0) {
            sender.sendMessage(ChatColor.RED + "Countdown time cannot be negative!");
            return;
        }

        if (countdownTime > 300) { // 5 minutes max
            sender.sendMessage(ChatColor.RED + "Countdown time cannot exceed 300 seconds!");
            return;
        }

        // Start the countdown
        try {
            game.gameStartRunnable(countdownTime);

            String timeText = countdownTime == 0 ? "immediately" : "in " + countdownTime + " seconds";
            sender.sendMessage(ChatColor.GREEN + "Game countdown started! Scattering will begin " + timeText);

            // Log the start
            UHC.getInstance().getLogger().info("Game countdown started by " + sender.getName() +
                    " with " + countdownTime + " seconds delay");

            // Give additional info to the starter
            sender.sendMessage(ChatColor.GRAY + "World: " + game.getWorldName());
            sender.sendMessage(ChatColor.GRAY + "Teams to scatter: " + game.getTeamManager().getAllTeams().size());
            sender.sendMessage(ChatColor.GRAY + "Game mode: " + (game.isSoloMode() ? "Solo" : "Teams (size " + game.getMaxTeamSize() + ")"));
            sender.sendMessage(ChatColor.GRAY + "Border size: " + game.getInitialBorderSize());

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to start game countdown: " + e.getMessage());
            UHC.getInstance().getLogger().severe("Error starting game countdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
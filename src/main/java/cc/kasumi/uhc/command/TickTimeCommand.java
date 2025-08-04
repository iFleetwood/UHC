package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.command.CommandSender;

@CommandAlias("ticktime")
@CommandPermission("uhc.admin")
public class TickTimeCommand extends BaseCommand {

    @Default
    public void onTickTimeCommand(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        sender.sendMessage("Game Duration: " + game.getFormattedGameDuration());
        sender.sendMessage("Total Ticks: " + game.getGameDurationTicks());
        sender.sendMessage("Current Tick: " + game.getCurrentServerTick());
    }
}

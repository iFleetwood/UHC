package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import org.bukkit.command.CommandSender;

@CommandAlias("start")
@CommandPermission("uhc.host")
public class StartCommand extends BaseCommand {


    @Default
    public void onStartCommand(CommandSender sender, @Optional Integer i) {
        Game game = UHC.getInstance().getGame();

        if (game.isStartCountdownStarted()) {
            sender.sendMessage("Game has already been started!");

            return;
        }

        if (i == null) {
            game.gameStartRunnable(5);

            return;
        }

        game.gameStartRunnable(i);
    }
}

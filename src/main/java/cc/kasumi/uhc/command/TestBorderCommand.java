package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("testborder")
@CommandPermission("uhc.admin")
public class TestBorderCommand extends BaseCommand {

    @Default
    public void onTestBorder(CommandSender sender, int integer) {
        Game game = UHC.getInstance().getGame();
        game.setInitialBorderSize(integer);
        game.setCurrentBorderSize(integer);
        game.buildSetInitialBorder();
    }
}

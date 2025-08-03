package cc.kasumi.uhc.command;

import cc.kasumi.uhc.packets.NameTagCreator;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@CommandAlias("test")
@CommandPermission("uhc.admin")
public class TestCommand extends BaseCommand {

    @Default
    public void onTestCommand(Player player) throws IllegalAccessException {
        NameTagCreator nameTagCreator = new NameTagCreator("test", player, ChatColor.GOLD + "", "");

        nameTagCreator.sendPacket();
    }
}

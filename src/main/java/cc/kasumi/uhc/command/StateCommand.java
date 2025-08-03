package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.command.CommandSender;

@CommandAlias("state")
@CommandPermission("uhc.admin")
public class StateCommand extends BaseCommand {

    @Default
    public void onStateCommand(CommandSender sender) {
        sender.sendMessage(UHC.getInstance().getGame().getState().);
    }
}

package cc.kasumi.uhc.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.entity.Player;

@CommandAlias("scatter")
@CommandPermission("uhc.admin")
public class ScatterCommand extends BaseCommand {

    @Default
    public void onScatterCommand(Player player) {

    }
}

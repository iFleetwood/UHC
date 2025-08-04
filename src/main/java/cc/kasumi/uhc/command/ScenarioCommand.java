package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.scenario.Scenario;
import cc.kasumi.uhc.scenario.ScenarioManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("scenario|scenarios")
@CommandPermission("uhc.admin")
public class ScenarioCommand extends BaseCommand {

    @Default
    @Subcommand("list")
    public void onList(CommandSender sender) {
        ScenarioManager manager = UHC.getInstance().getGame().getScenarioManager();

        sender.sendMessage(ChatColor.GOLD + "=== Available Scenarios ===");
        for (Scenario scenario : manager.getAllScenarios()) {
            String status = scenario.isEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED";
            sender.sendMessage(ChatColor.YELLOW + scenario.getName() + ChatColor.GRAY + " - " + status);
            sender.sendMessage(ChatColor.GRAY + "  " + scenario.getDescription());
        }
    }

    @Subcommand("enable")
    public void onEnable(CommandSender sender, String scenarioName) {
        ScenarioManager manager = UHC.getInstance().getGame().getScenarioManager();

        Scenario scenario = manager.getScenario(scenarioName);
        if (scenario == null) {
            sender.sendMessage(ChatColor.RED + "Scenario '" + scenarioName + "' not found!");
            return;
        }

        if (scenario.isEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Scenario '" + scenario.getName() + "' is already enabled!");
            return;
        }

        manager.enableScenario(scenarioName);
        sender.sendMessage(ChatColor.GREEN + "Enabled scenario: " + scenario.getName());
    }

    @Subcommand("disable")
    public void onDisable(CommandSender sender, String scenarioName) {
        ScenarioManager manager = UHC.getInstance().getGame().getScenarioManager();

        Scenario scenario = manager.getScenario(scenarioName);
        if (scenario == null) {
            sender.sendMessage(ChatColor.RED + "Scenario '" + scenarioName + "' not found!");
            return;
        }

        if (!scenario.isEnabled()) {
            sender.sendMessage(ChatColor.YELLOW + "Scenario '" + scenario.getName() + "' is already disabled!");
            return;
        }

        manager.disableScenario(scenarioName);
        sender.sendMessage(ChatColor.GREEN + "Disabled scenario: " + scenario.getName());
    }
}
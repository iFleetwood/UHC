package cc.kasumi.uhc.scenario;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.scenario.type.CutCleanScenario;
import cc.kasumi.uhc.scenario.type.NoFallScenario;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

import java.util.*;

@Getter
public class ScenarioManager {

    private final Map<String, Scenario> scenarios = new HashMap<>();
    private final Set<Scenario> activeScenarios = new HashSet<>();
    private final Game game;
    private boolean listenersRegistered = false;

    public ScenarioManager(Game game) {
        this.game = game;
        registerScenarios();
    }

    private void registerScenarios() {
        // Register all available scenarios
        addScenario(new CutCleanScenario());
        addScenario(new NoFallScenario());
        // Add more scenarios here as you create them
    }

    private void addScenario(Scenario scenario) {
        scenarios.put(scenario.getName().toLowerCase(), scenario);
    }

    public void enableScenario(String scenarioName) {
        Scenario scenario = scenarios.get(scenarioName.toLowerCase());
        if (scenario != null && !scenario.isEnabled()) {
            scenario.setEnabled(true);
            activeScenarios.add(scenario);
            scenario.onActivate(game);

            // Register listener if scenarios are active
            if (listenersRegistered) {
                Bukkit.getPluginManager().registerEvents(scenario.getListener(), UHC.getInstance());
            }

            Bukkit.getLogger().info("Enabled scenario: " + scenario.getName());
        }
    }

    public void disableScenario(String scenarioName) {
        Scenario scenario = scenarios.get(scenarioName.toLowerCase());
        if (scenario != null && scenario.isEnabled()) {
            scenario.setEnabled(false);
            activeScenarios.remove(scenario);
            scenario.onDeactivate(game);

            // Unregister listener
            HandlerList.unregisterAll(scenario.getListener());

            Bukkit.getLogger().info("Disabled scenario: " + scenario.getName());
        }
    }

    public void registerAllListeners() {
        if (listenersRegistered) return;

        for (Scenario scenario : activeScenarios) {
            Bukkit.getPluginManager().registerEvents(scenario.getListener(), UHC.getInstance());
        }

        listenersRegistered = true;
        Bukkit.getLogger().info("Registered " + activeScenarios.size() + " scenario listeners");
    }

    public void unregisterAllListeners() {
        if (!listenersRegistered) return;

        for (Scenario scenario : activeScenarios) {
            HandlerList.unregisterAll(scenario.getListener());
        }

        listenersRegistered = false;
        Bukkit.getLogger().info("Unregistered all scenario listeners");
    }

    public List<Scenario> getAllScenarios() {
        return new ArrayList<>(scenarios.values());
    }

    public List<Scenario> getEnabledScenarios() {
        return new ArrayList<>(activeScenarios);
    }

    public Scenario getScenario(String name) {
        return scenarios.get(name.toLowerCase());
    }

    public boolean isScenarioEnabled(String name) {
        Scenario scenario = getScenario(name);
        return scenario != null && scenario.isEnabled();
    }
}

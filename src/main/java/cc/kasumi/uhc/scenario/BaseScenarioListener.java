package cc.kasumi.uhc.scenario;

import org.bukkit.event.Listener;

public abstract class BaseScenarioListener implements Listener {

    protected final Scenario scenario;

    public BaseScenarioListener(Scenario scenario) {
        this.scenario = scenario;
    }

    protected boolean isScenarioActive() {
        return scenario.isEnabled();
    }
}
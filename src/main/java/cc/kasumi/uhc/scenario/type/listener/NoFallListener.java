package cc.kasumi.uhc.scenario.type.listener;

import cc.kasumi.uhc.scenario.BaseScenarioListener;
import cc.kasumi.uhc.scenario.Scenario;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

public class NoFallListener extends BaseScenarioListener {

    public NoFallListener(Scenario scenario) {
        super(scenario);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isScenarioActive()) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }
}

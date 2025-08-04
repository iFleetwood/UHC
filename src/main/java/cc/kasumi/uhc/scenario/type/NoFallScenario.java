package cc.kasumi.uhc.scenario.type;

import cc.kasumi.uhc.scenario.AbstractScenario;
import cc.kasumi.uhc.scenario.type.listener.NoFallListener;
import org.bukkit.event.Listener;

public class NoFallScenario extends AbstractScenario {

    public NoFallScenario() {
        super("NoFall", "Players take no fall damage");
    }

    @Override
    protected Listener createListener() {
        return new NoFallListener(this);
    }
}

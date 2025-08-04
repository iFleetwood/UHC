package cc.kasumi.uhc.scenario.type;

import cc.kasumi.uhc.scenario.AbstractScenario;
import cc.kasumi.uhc.scenario.type.listener.CutCleanListener;
import org.bukkit.event.Listener;

public class CutCleanScenario extends AbstractScenario {

    public CutCleanScenario() {
        super("CutClean", "All ores and food are automatically smelted/cooked when mined/killed");
    }

    @Override
    protected Listener createListener() {
        return new CutCleanListener(this);
    }
}

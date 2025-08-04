package cc.kasumi.uhc.scenario;

import cc.kasumi.uhc.game.Game;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Listener;

@Getter
public abstract class AbstractScenario implements Scenario {

    protected final String name;
    protected final String description;

    @Setter
    protected boolean enabled = false;

    protected final Listener listener;

    public AbstractScenario(String name, String description) {
        this.name = name;
        this.description = description;
        this.listener = createListener();
    }

    protected abstract Listener createListener();

    @Override
    public void onActivate(Game game) {
        // Default implementation - can be overridden
    }

    @Override
    public void onDeactivate(Game game) {
        // Default implementation - can be overridden
    }
}

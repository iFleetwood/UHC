package cc.kasumi.uhc.scenario;

import cc.kasumi.uhc.game.Game;
import org.bukkit.event.Listener;

public interface Scenario {
    String getName();
    String getDescription();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    Listener getListener();
    void onActivate(Game game);
    void onDeactivate(Game game);
}

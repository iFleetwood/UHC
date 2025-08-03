package cc.kasumi.uhc.game;

import cc.kasumi.uhc.UHC;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public abstract class GameState implements Listener {

    protected final Game game;

    protected GameState(Game game) {
        this.game = game;
    }

    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, UHC.getInstance());
    }

    public void onDisable() {
        HandlerList.unregisterAll(this);
    }
}

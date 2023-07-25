package cc.kasumi.uhc;

import cc.kasumi.uhc.game.UHCGame;
import cc.kasumi.uhc.listener.GameListener;
import cc.kasumi.uhc.listener.PlayerListener;
import cc.kasumi.uhc.listener.ScenarioListener;
import cc.kasumi.uhc.listener.SpectatorListener;
import cc.kasumi.uhc.mongo.MCollection;
import cc.kasumi.uhc.mongo.MDatabase;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.scenario.Scenario;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

@Getter
public final class UHC extends JavaPlugin {

    @Getter
    private static final Map<UUID, UHCPlayer> players = new HashMap<>();
    @Getter
    private static final Map<String, Scenario> scenarios = new HashMap<>();
    @Getter
    private static UHCGame game;
    @Getter
    private static UHC instance;
    @Getter
    private static MDatabase database;

    @Override
    public void onEnable() {
        instance = this;

        Configuration.init();

        database = new MDatabase("", "minecraft");
        game = new UHCGame();

        registerListeners();
    }

    @Override
    public void onDisable() {
    }

    private void registerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(new PlayerListener(), this);
        pluginManager.registerEvents(new GameListener(), this);
        pluginManager.registerEvents(new ScenarioListener(), this);
        pluginManager.registerEvents(new SpectatorListener(), this);
    }

    public MCollection getPlayerCollection() {
        return new MCollection(database, "uhc-players"); // Maybe store m-collection instead
    }
}

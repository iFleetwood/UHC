package cc.kasumi.uhc;

import cc.kasumi.uhc.command.*;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.listener.PlayerListener;
import cc.kasumi.uhc.util.GameUtil;
import cc.kasumi.uhc.util.TickCounter;
import cc.kasumi.uhc.world.WorldManager; // Add this import
import cc.kasumi.uhc.world.listener.WorldPopulatorListener;
import co.aikar.commands.PaperCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class UHC extends JavaPlugin {

    @Getter
    private static UHC instance;
    @Getter
    private static ProtocolManager protocolManager;

    private Game game;
    private PaperCommandManager paperCommandManager;
    private TickCounter tickCounter;
    private WorldManager worldManager; // Add this field

    @Override
    public void onEnable() {
        instance = this;

        // Initialize tick counter first
        tickCounter = TickCounter.getInstance();

        // Initialize world manager before game
        worldManager = new WorldManager(this);
        worldManager.initializeWorlds();

        game = new Game();

        registerListeners();
        registerManagers();
        registerCommands();
    }

    @Override
    public void onDisable() {
        // Cleanup resources
        if (tickCounter != null) {
            tickCounter.stop();
        }

        // Cancel wall builders to prevent lag on reload
        GameUtil.cancelAllWallBuilders();

        // Cancel any remaining tasks
        Bukkit.getScheduler().cancelTasks(this);
    }

    private void registerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new PlayerListener(game), this);
        pluginManager.registerEvents(new WorldPopulatorListener(), this);
    }

    private void registerManagers() {
        protocolManager = ProtocolLibrary.getProtocolManager();
        paperCommandManager = new PaperCommandManager(this);
    }

    private void registerCommands() {
        paperCommandManager.registerCommand(new StartCommand());
        paperCommandManager.registerCommand(new ScatterCommand());
        paperCommandManager.registerCommand(new TestCommand());
        paperCommandManager.registerCommand(new StateCommand());
        paperCommandManager.registerCommand(new TestBorderCommand());
        paperCommandManager.registerCommand(new TickTimeCommand());
        paperCommandManager.registerCommand(new ScenarioCommand());
        paperCommandManager.registerCommand(new WorldCommand());
    }
}
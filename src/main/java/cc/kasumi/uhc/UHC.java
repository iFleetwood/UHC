package cc.kasumi.uhc;

import cc.kasumi.uhc.command.*;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.listener.PlayerListener;
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

    @Override
    public void onEnable() {
        instance = this;

        game = new Game();

        registerListeners();
        registerManagers();
        registerCommands();
    }

    @Override
    public void onDisable() {

    }

    private void registerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        pluginManager.registerEvents(new PlayerListener(game), this);
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
    }
}

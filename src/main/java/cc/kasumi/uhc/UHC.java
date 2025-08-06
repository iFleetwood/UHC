package cc.kasumi.uhc;

import cc.kasumi.uhc.command.*;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.listener.AsyncPlayerPreLoginListener;
import cc.kasumi.uhc.listener.PlayerListener;
import cc.kasumi.uhc.util.GameUtil;
import cc.kasumi.uhc.util.TickCounter;
import cc.kasumi.uhc.world.WorldManager;
import cc.kasumi.uhc.world.listener.WorldPopulatorListener;
import co.aikar.commands.PaperCommandManager;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

@Getter
public final class UHC extends JavaPlugin {

    @Getter
    private static UHC instance;
    @Getter
    private static ProtocolManager protocolManager;

    private Game game;
    private PaperCommandManager paperCommandManager;
    private TickCounter tickCounter;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Starting UHC Plugin initialization...");

        // Initialize tick counter first (required by other systems)
        tickCounter = TickCounter.getInstance();
        getLogger().info("Tick counter initialized");

        // Initialize world manager before game (critical for world system)
        worldManager = new WorldManager(this);
        getLogger().info("WorldManager initialized");

        // Initialize worlds to prevent startup lag
        initializeWorlds();

        // Initialize game after world manager
        initializeGame();

        // Register other components
        registerListeners();
        registerManagers();
        registerCommands();

        getLogger().info("UHC Plugin initialization completed!");
    }

    /**
     * Initialize worlds asynchronously to prevent server startup lag
     */
    private void initializeWorlds() {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    getLogger().info("Starting world initialization...");
                    worldManager.initializeWorlds();
                    getLogger().info("World initialization completed!");
                } catch (Exception e) {
                    getLogger().severe("Failed to initialize worlds: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskLater(this, 20L); // Run after 1 second to allow server to fully start
    }

    /**
     * Initialize game after world system is ready
     */
    private void initializeGame() {
        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = 60; // 30 seconds maximum wait

            @Override
            public void run() {
                attempts++;

                try {
                    // Check if world manager is ready
                    if (worldManager.getUhcWorld() != null || !worldManager.isWorldGenerationInProgress()) {
                        // Initialize game
                        game = new Game();
                        getLogger().info("Game initialized successfully!");
                        cancel();
                        return;
                    }

                    // If still waiting and not at max attempts, continue
                    if (attempts < maxAttempts) {
                        if (attempts % 10 == 0) {
                            getLogger().info("Waiting for world system... (attempt " + attempts + "/" + maxAttempts + ")");
                        }
                        return;
                    }

                    // Max attempts reached, initialize anyway
                    getLogger().warning("World system not ready after " + maxAttempts + " attempts, initializing game anyway...");
                    game = new Game();

                    getLogger().info("Game initialized with fallback method!");
                    cancel();

                } catch (Exception e) {
                    getLogger().severe("Failed to initialize game: " + e.getMessage());
                    e.printStackTrace();

                    if (attempts >= maxAttempts) {
                        getLogger().severe("Game initialization failed after " + maxAttempts + " attempts!");
                        cancel();
                    }
                }
            }
        }.runTaskTimer(this, 40L, 10L); // Start after 2 seconds, check every 0.5 seconds
    }

    @Override
    public void onDisable() {
        getLogger().info("Starting UHC Plugin shutdown...");

        // Cleanup resources in reverse order of initialization
        cleanupGame();
        cleanupWorldSystem();
        cleanupUtilities();

        // Cancel any remaining tasks
        Bukkit.getScheduler().cancelTasks(this);

        getLogger().info("UHC Plugin shutdown completed!");
    }

    /**
     * Cleanup game-related resources
     */
    private void cleanupGame() {
        if (game != null) {
            try {
                game.getBarAPI().onDisable();

                // Teleport all players to lobby before shutdown
                if (worldManager != null && worldManager.getLobbyWorld() != null) {
                    game.teleportAllPlayersToLobby();
                    getLogger().info("All players teleported to lobby");
                }
            } catch (Exception e) {
                getLogger().warning("Error during player teleportation: " + e.getMessage());
            }
        }
    }

    /**
     * Cleanup world system resources
     */
    private void cleanupWorldSystem() {
        if (worldManager != null) {
            try {
                // Save world configuration
                worldManager.getWorldConfig().saveConfig();
                getLogger().info("World configuration saved");
            } catch (Exception e) {
                getLogger().warning("Error saving world configuration: " + e.getMessage());
            }
        }

        // Cancel wall builders to prevent lag on reload
        try {
            GameUtil.cancelAllWallBuilders();
            getLogger().info("All wall builders cancelled");
        } catch (Exception e) {
            getLogger().warning("Error cancelling wall builders: " + e.getMessage());
        }
    }

    /**
     * Cleanup utility resources
     */
    private void cleanupUtilities() {
        if (tickCounter != null) {
            try {
                tickCounter.stop();
                getLogger().info("Tick counter stopped");
            } catch (Exception e) {
                getLogger().warning("Error stopping tick counter: " + e.getMessage());
            }
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();

        try {
            // Register primary listeners
            pluginManager.registerEvents(new AsyncPlayerPreLoginListener(), this);
            pluginManager.registerEvents(new WorldPopulatorListener(), this);
            getLogger().info("WorldPopulatorListener registered");

            // Register game listener after game is initialized
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (game != null) {
                        pluginManager.registerEvents(new PlayerListener(game), UHC.this);
                        getLogger().info("PlayerListener registered");
                        cancel();
                    }
                }
            }.runTaskTimer(this, 20L, 20L); // Check every second until game is ready

        } catch (Exception e) {
            getLogger().severe("Error registering listeners: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerManagers() {
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            paperCommandManager = new PaperCommandManager(this);
            getLogger().info("Managers registered successfully");
        } catch (Exception e) {
            getLogger().severe("Error registering managers: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        try {
            paperCommandManager.registerCommand(new ScatterDebugCommand());
            paperCommandManager.registerCommand(new StartCommand());
            paperCommandManager.registerCommand(new NameTagCommand());
            paperCommandManager.registerCommand(new StateCommand());
            paperCommandManager.registerCommand(new TestBorderCommand());
            paperCommandManager.registerCommand(new TickTimeCommand());
            paperCommandManager.registerCommand(new ScenarioCommand());
            paperCommandManager.registerCommand(new WorldCommand());
            paperCommandManager.registerCommand(new WorldConfigCommand());
            paperCommandManager.registerCommand(new TeamCommand()); // NEW: Register team command
            paperCommandManager.registerCommand(new TeamSizeCommand()); // NEW: Register team mode command
            getLogger().info("Commands registered successfully");
        } catch (Exception e) {
            getLogger().severe("Error registering commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get game instance with null check
     */
    public Game getGame() {
        if (game == null) {
            getLogger().warning("Game instance is null!");
        }
        return game;
    }

    /**
     * Check if the plugin is fully initialized
     */
    public boolean isFullyInitialized() {
        return game != null && worldManager != null && tickCounter != null;
    }

    public boolean isLoadedFully() {
        if (isFullyInitialized()) {
            return !worldManager.isWorldGenerationInProgress();
        }

        return false;
    }

    /**
     * Get initialization status for debugging
     */
    public String getInitializationStatus() {
        StringBuilder status = new StringBuilder();
        status.append("UHC Plugin Status:\n");
        status.append("- TickCounter: ").append(tickCounter != null ? "✓" : "✗").append("\n");
        status.append("- WorldManager: ").append(worldManager != null ? "✓" : "✗").append("\n");
        status.append("- Game: ").append(game != null ? "✓" : "✗").append("\n");
        status.append("- ProtocolManager: ").append(protocolManager != null ? "✓" : "✗").append("\n");
        status.append("- CommandManager: ").append(paperCommandManager != null ? "✓" : "✗").append("\n");

        if (worldManager != null) {
            status.append("- UHC World: ").append(worldManager.getUhcWorld() != null ? "✓" : "✗").append("\n");
            status.append("- Lobby World: ").append(worldManager.getLobbyWorld() != null ? "✓" : "✗").append("\n");
            status.append("- World Generation: ").append(worldManager.isWorldGenerationInProgress() ? "In Progress" : "Idle").append("\n");
        }

        return status.toString();
    }

    /**
     * Emergency world reset command for admins
     */
    public void emergencyWorldReset() {
        getLogger().warning("Emergency world reset initiated!");

        if (game != null) {
            game.teleportAllPlayersToLobby();
        }

        if (worldManager != null) {
            worldManager.resetUHCWorld().thenRun(() -> {
                getLogger().info("Emergency world reset completed!");

                // Reinitialize game if needed
                if (game != null) {
                    game.refreshWorldReference();
                }
            }).exceptionally(throwable -> {
                getLogger().severe("Emergency world reset failed: " + throwable.getMessage());
                return null;
            });
        }
    }
}
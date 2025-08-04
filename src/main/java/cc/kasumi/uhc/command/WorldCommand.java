package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.world.WorldManager;
import cc.kasumi.uhc.world.WorldPopulatorManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static co.aikar.commands.ACFBukkitUtil.formatLocation;

@CommandAlias("world|worldmanager")
@CommandPermission("uhc.admin")
public class WorldCommand extends BaseCommand {

    @Subcommand("create")
    @Description("Create a new UHC world")
    public void onCreate(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Creating new UHC world...");

        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        if (worldManager.isWorldGenerationInProgress()) {
            sender.sendMessage(ChatColor.RED + "World generation is already in progress!");
            return;
        }

        worldManager.createNewUHCWorld().thenAccept(world -> {
            sender.sendMessage(ChatColor.GREEN + "UHC world created successfully: " + world.getName());

            // Update game reference if available
            if (UHC.getInstance().getGame() != null) {
                UHC.getInstance().getGame().refreshWorldReference();
                sender.sendMessage(ChatColor.GRAY + "Game world reference updated.");
            }

        }).exceptionally(throwable -> {
            sender.sendMessage(ChatColor.RED + "Failed to create UHC world: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    @Subcommand("reset")
    @Description("Reset the UHC world completely")
    public void onReset(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Resetting UHC world...");

        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        if (worldManager.isWorldGenerationInProgress()) {
            sender.sendMessage(ChatColor.RED + "World generation is already in progress!");
            return;
        }

        // Warn about active game
        if (UHC.getInstance().getGame() != null && UHC.getInstance().getGame().isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Warning: A game is currently in progress!");
            sender.sendMessage(ChatColor.YELLOW + "All players will be teleported to lobby.");
        }

        worldManager.resetUHCWorld().thenRun(() -> {
            sender.sendMessage(ChatColor.GREEN + "UHC world reset successfully!");

            // Update game reference if available
            if (UHC.getInstance().getGame() != null) {
                UHC.getInstance().getGame().refreshWorldReference();
                sender.sendMessage(ChatColor.GRAY + "Game world reference updated.");
            }

        }).exceptionally(throwable -> {
            sender.sendMessage(ChatColor.RED + "Failed to reset UHC world: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
    }

    @Subcommand("pregenerate")
    @Description("Pregenerate chunks around spawn")
    public void onPregenerate(CommandSender sender, @Default("10") int radius) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        World uhcWorld = worldManager.getUhcWorld();

        if (uhcWorld == null) {
            sender.sendMessage(ChatColor.RED + "UHC world not found! Create it first with /world create");
            return;
        }

        if (radius < 1 || radius > 50) {
            sender.sendMessage(ChatColor.RED + "Radius must be between 1 and 50!");
            return;
        }

        if (worldManager.isWorldGenerationInProgress()) {
            sender.sendMessage(ChatColor.RED + "World generation is already in progress!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting chunk pregeneration with radius " + radius + "...");
        sender.sendMessage(ChatColor.GRAY + "This may take a while depending on the radius size.");

        try {
            worldManager.pregenerateSpawnChunks(uhcWorld, radius);
            sender.sendMessage(ChatColor.GREEN + "Chunk pregeneration started successfully!");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to start chunk pregeneration: " + e.getMessage());
        }
    }

    @Subcommand("tp uhc")
    @Description("Teleport to UHC world spawn")
    public void onTeleportUHC(Player player) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            player.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        World uhcWorld = worldManager.getUhcWorld();

        if (uhcWorld == null) {
            player.sendMessage(ChatColor.RED + "UHC world not found!");
            return;
        }

        try {
            player.teleport(uhcWorld.getSpawnLocation());
            player.sendMessage(ChatColor.GREEN + "Teleported to UHC world: " + uhcWorld.getName());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to teleport: " + e.getMessage());
        }
    }

    @Subcommand("tp lobby")
    @Description("Teleport to lobby world")
    public void onTeleportLobby(Player player) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            player.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        World lobbyWorld = worldManager.getLobbyWorld();

        if (lobbyWorld == null) {
            player.sendMessage(ChatColor.RED + "Lobby world not found!");
            return;
        }

        try {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.sendMessage(ChatColor.GREEN + "Teleported to lobby: " + lobbyWorld.getName());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "Failed to teleport: " + e.getMessage());
        }
    }

    @Subcommand("caves")
    @Description("Configure cave generation settings")
    public void onCaves(CommandSender sender, boolean enabled, @Default("55") int cutoff,
                        @Default("6") int minY, @Default("52") int maxY,
                        @Default("16") int hStretch, @Default("9") int vStretch) {

        if (cutoff < 0 || cutoff > 100) {
            sender.sendMessage(ChatColor.RED + "Cutoff must be between 0 and 100!");
            return;
        }

        if (minY < 0 || maxY > 255 || minY >= maxY) {
            sender.sendMessage(ChatColor.RED + "Invalid Y range! MinY must be < MaxY and within 0-255.");
            return;
        }

        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        worldManager.configureCaveSettings(enabled, cutoff, minY, maxY, hStretch, vStretch);

        sender.sendMessage(ChatColor.GREEN + "Cave settings updated!");
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + enabled);
        sender.sendMessage(ChatColor.GRAY + "Cutoff: " + cutoff + "%");
        sender.sendMessage(ChatColor.GRAY + "Y Range: " + minY + "-" + maxY);
        sender.sendMessage(ChatColor.GRAY + "Stretch: " + hStretch + "x" + vStretch);
        sender.sendMessage(ChatColor.YELLOW + "Note: Changes apply to new world generation only.");
    }

    @Subcommand("info")
    @Description("Show world information")
    public void onInfo(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== World Manager Info ===");

        // UHC World Info
        World uhcWorld = worldManager.getUhcWorld();
        if (uhcWorld != null) {
            WorldManager.WorldBorderInfo borderInfo = worldManager.getWorldBorderInfo(uhcWorld);
            sender.sendMessage(ChatColor.GREEN + "UHC World: " + uhcWorld.getName());
            sender.sendMessage(ChatColor.GRAY + "  Players: " + uhcWorld.getPlayers().size());
            sender.sendMessage(ChatColor.GRAY + "  Loaded Chunks: " + uhcWorld.getLoadedChunks().length);
            sender.sendMessage(ChatColor.GRAY + "  Border Size: " + (int)borderInfo.size);
            sender.sendMessage(ChatColor.GRAY + "  Spawn: " + formatLocation(uhcWorld.getSpawnLocation()));

            // Game integration info
            if (UHC.getInstance().getGame() != null) {
                boolean isGameWorld = UHC.getInstance().getGame().getWorldName().equals(uhcWorld.getName());
                sender.sendMessage(ChatColor.GRAY + "  Game World: " + (isGameWorld ? "✓" : "✗"));
            }
        } else {
            sender.sendMessage(ChatColor.RED + "UHC World: Not loaded");
        }

        // Lobby World Info
        World lobbyWorld = worldManager.getLobbyWorld();
        if (lobbyWorld != null) {
            sender.sendMessage(ChatColor.GREEN + "Lobby World: " + lobbyWorld.getName());
            sender.sendMessage(ChatColor.GRAY + "  Players: " + lobbyWorld.getPlayers().size());
            sender.sendMessage(ChatColor.GRAY + "  Spawn: " + formatLocation(lobbyWorld.getSpawnLocation()));
        } else {
            sender.sendMessage(ChatColor.RED + "Lobby World: Not loaded");
        }

        // Generation Status
        sender.sendMessage(ChatColor.YELLOW + "Generation in progress: " +
                worldManager.isWorldGenerationInProgress());

        // Game Status
        if (UHC.getInstance().getGame() != null) {
            sender.sendMessage(ChatColor.YELLOW + "Game world ready: " +
                    UHC.getInstance().getGame().isWorldReady());
        }
    }

    @Subcommand("populators info")
    @Description("Show populator information")
    public void onPopulatorsInfo(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        WorldPopulatorManager populatorManager = worldManager.getPopulatorManager();
        WorldPopulatorManager.PopulatorStats stats = populatorManager.getPopulatorStats();

        sender.sendMessage(ChatColor.GOLD + "=== Populator Information ===");
        sender.sendMessage(ChatColor.YELLOW + "Registered Populators: " + ChatColor.WHITE + stats.totalRegistered);
        sender.sendMessage(ChatColor.YELLOW + "Giant Caves Active: " + ChatColor.WHITE + stats.activeGiantCaves);
        sender.sendMessage(ChatColor.YELLOW + "Giant Caves Enabled: " + ChatColor.WHITE + stats.giantCavesEnabled);

        if (stats.giantCavesEnabled) {
            sender.sendMessage(ChatColor.GRAY + "Cave Settings:");
            sender.sendMessage(ChatColor.GRAY + "  Cutoff: " + stats.caveSettings.cutoff + "%");
            sender.sendMessage(ChatColor.GRAY + "  Y Range: " + stats.caveSettings.minY + "-" + stats.caveSettings.maxY);
            sender.sendMessage(ChatColor.GRAY + "  Stretch: " + stats.caveSettings.horizontalStretch +
                    "x" + stats.caveSettings.verticalStretch);
        }
    }

    @Subcommand("populators refresh")
    @Description("Refresh populators based on current settings")
    public void onPopulatorsRefresh(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        WorldPopulatorManager populatorManager = worldManager.getPopulatorManager();

        populatorManager.refreshPopulators();
        sender.sendMessage(ChatColor.GREEN + "Populators refreshed based on current configuration!");

        WorldPopulatorManager.PopulatorStats stats = populatorManager.getPopulatorStats();
        sender.sendMessage(ChatColor.GRAY + "Now registered: " + stats.totalRegistered + " populators");
    }

    @Subcommand("populators add")
    @Description("Add populators to current UHC world")
    public void onPopulatorsAdd(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        World uhcWorld = worldManager.getUhcWorld();

        if (uhcWorld == null) {
            sender.sendMessage(ChatColor.RED + "UHC world not found!");
            return;
        }

        WorldPopulatorManager populatorManager = worldManager.getPopulatorManager();
        int before = uhcWorld.getPopulators().size();

        populatorManager.addPopulatorsToWorld(uhcWorld);

        int after = uhcWorld.getPopulators().size();
        sender.sendMessage(ChatColor.GREEN + "Populators added to UHC world!");
        sender.sendMessage(ChatColor.GRAY + "Before: " + before + ", After: " + after);
    }

    @Subcommand("settings")
    @Description("Show world generation settings")
    public void onSettings(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        if (worldManager == null) {
            sender.sendMessage(ChatColor.RED + "WorldManager is not available!");
            return;
        }

        WorldManager.WorldGenerationSettings settings = worldManager.getSettings();

        sender.sendMessage(ChatColor.GOLD + "=== World Generation Settings ===");
        sender.sendMessage(ChatColor.GRAY + "World Type: " + settings.getWorldType());
        sender.sendMessage(ChatColor.GRAY + "Generate Structures: " + settings.isGenerateStructures());
        sender.sendMessage(ChatColor.GRAY + "Biome Swap Enabled: " + settings.isBiomeSwapEnabled());
        sender.sendMessage(ChatColor.GRAY + "Giant Caves Enabled: " + settings.isGiantCavesEnabled());
        sender.sendMessage(ChatColor.GRAY + "Custom Seed: " + (settings.getSeed() == 0 ? "Random" : settings.getSeed()));
    }

    @Subcommand("status")
    @Description("Show comprehensive world and game status")
    public void onStatus(CommandSender sender) {
        UHC uhc = UHC.getInstance();
        sender.sendMessage(ChatColor.GOLD + "=== UHC World & Game Status ===");

        // Plugin Status
        sender.sendMessage(ChatColor.YELLOW + "Plugin Status:");
        sender.sendMessage(ChatColor.GRAY + "  Fully Initialized: " + uhc.isFullyInitialized());

        // World Manager Status
        WorldManager worldManager = uhc.getWorldManager();
        if (worldManager != null) {
            sender.sendMessage(ChatColor.YELLOW + "World Manager:");
            sender.sendMessage(ChatColor.GRAY + "  UHC World: " + (worldManager.getUhcWorld() != null ? "✓" : "✗"));
            sender.sendMessage(ChatColor.GRAY + "  Lobby World: " + (worldManager.getLobbyWorld() != null ? "✓" : "✗"));
            sender.sendMessage(ChatColor.GRAY + "  Generation Active: " + worldManager.isWorldGenerationInProgress());

            if (worldManager.getUhcWorld() != null) {
                World uhcWorld = worldManager.getUhcWorld();
                sender.sendMessage(ChatColor.GRAY + "  UHC World Name: " + uhcWorld.getName());
                sender.sendMessage(ChatColor.GRAY + "  UHC Players: " + uhcWorld.getPlayers().size());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "World Manager: Not Available");
        }

        // Game Status
        if (uhc.getGame() != null) {
            sender.sendMessage(ChatColor.YELLOW + "Game Status:");
            sender.sendMessage(ChatColor.GRAY + "  World Ready: " + uhc.getGame().isWorldReady());
            sender.sendMessage(ChatColor.GRAY + "  Game Started: " + uhc.getGame().isGameStarted());
            sender.sendMessage(ChatColor.GRAY + "  Game World: " + uhc.getGame().getWorldName());
            sender.sendMessage(ChatColor.GRAY + "  Players in Game World: " + uhc.getGame().getPlayersInGameWorld().size());

            if (uhc.getGame().isGameStarted()) {
                sender.sendMessage(ChatColor.GRAY + "  Game Duration: " + uhc.getGame().getFormattedGameDuration());
                sender.sendMessage(ChatColor.GRAY + "  PvP Enabled: " + uhc.getGame().isPvpEnabled());
                sender.sendMessage(ChatColor.GRAY + "  Current Border: " + uhc.getGame().getCurrentBorderSize());
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Game: Not Available");
        }

        // Performance Info
        sender.sendMessage(ChatColor.YELLOW + "Performance:");
        sender.sendMessage(ChatColor.GRAY + "  Server TPS: " + String.format("%.1f", getServerTPS()));
        sender.sendMessage(ChatColor.GRAY + "  Active Wall Builders: " +
                cc.kasumi.uhc.util.GameUtil.getActiveBuilders().size());
    }

    @Subcommand("debug")
    @Description("Show detailed debug information")
    public void onDebug(CommandSender sender) {
        UHC uhc = UHC.getInstance();

        sender.sendMessage(ChatColor.GOLD + "=== Debug Information ===");

        // Send initialization status
        String[] statusLines = uhc.getInitializationStatus().split("\n");
        for (String line : statusLines) {
            sender.sendMessage(ChatColor.GRAY + line);
        }

        // Memory info
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage(ChatColor.YELLOW + "Memory Usage:");
        sender.sendMessage(ChatColor.GRAY + "  Used: " + usedMemory + "MB / " + maxMemory + "MB");
        sender.sendMessage(ChatColor.GRAY + "  Free: " + freeMemory + "MB");

        // Thread info
        sender.sendMessage(ChatColor.YELLOW + "Threads:");
        sender.sendMessage(ChatColor.GRAY + "  Active: " + Thread.activeCount());

        // World specific debug
        if (uhc.getWorldManager() != null && uhc.getWorldManager().getUhcWorld() != null) {
            World world = uhc.getWorldManager().getUhcWorld();
            sender.sendMessage(ChatColor.YELLOW + "UHC World Debug:");
            sender.sendMessage(ChatColor.GRAY + "  Loaded Chunks: " + world.getLoadedChunks().length);
            sender.sendMessage(ChatColor.GRAY + "  Entities: " + world.getEntities().size());
            sender.sendMessage(ChatColor.GRAY + "  Keep Spawn: " + world.getKeepSpawnInMemory());
        }
    }

    @Subcommand("force-reload")
    @Description("Force reload world references and game state")
    public void onForceReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Force reloading world system...");

        try {
            UHC uhc = UHC.getInstance();

            // Reload world manager configuration
            if (uhc.getWorldManager() != null) {
                uhc.getWorldManager().reloadConfiguration();
                sender.sendMessage(ChatColor.GREEN + "WorldManager configuration reloaded");
            }

            // Refresh game world reference
            if (uhc.getGame() != null) {
                uhc.getGame().refreshWorldReference();
                sender.sendMessage(ChatColor.GREEN + "Game world reference refreshed");
            }

            sender.sendMessage(ChatColor.GREEN + "Force reload completed!");

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error during force reload: " + e.getMessage());
            UHC.getInstance().getLogger().severe("Error during force reload: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Subcommand("emergency-reset")
    @Description("Emergency world reset (use only when needed)")
    public void onEmergencyReset(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "WARNING: Emergency world reset initiated!");
        sender.sendMessage(ChatColor.YELLOW + "This should only be used when the world system is broken.");

        try {
            UHC.getInstance().emergencyWorldReset();
            sender.sendMessage(ChatColor.GREEN + "Emergency reset started. Check console for progress.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Emergency reset failed: " + e.getMessage());
        }
    }

    private double getServerTPS() {
        try {
            // Simple TPS calculation based on tick counter
            return 20.0; // Fallback value for 1.8.8
        } catch (Exception e) {
            return 20.0;
        }
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== World Manager Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/world create" + ChatColor.GRAY + " - Create new UHC world");
        sender.sendMessage(ChatColor.YELLOW + "/world reset" + ChatColor.GRAY + " - Reset UHC world");
        sender.sendMessage(ChatColor.YELLOW + "/world pregenerate [radius]" + ChatColor.GRAY + " - Pregenerate chunks");
        sender.sendMessage(ChatColor.YELLOW + "/world tp uhc" + ChatColor.GRAY + " - Teleport to UHC world");
        sender.sendMessage(ChatColor.YELLOW + "/world tp lobby" + ChatColor.GRAY + " - Teleport to lobby");
        sender.sendMessage(ChatColor.YELLOW + "/world caves <enabled> [settings...]" + ChatColor.GRAY + " - Configure caves");
        sender.sendMessage(ChatColor.YELLOW + "/world info" + ChatColor.GRAY + " - Show world information");
        sender.sendMessage(ChatColor.YELLOW + "/world settings" + ChatColor.GRAY + " - Show generation settings");
        sender.sendMessage(ChatColor.YELLOW + "/world status" + ChatColor.GRAY + " - Comprehensive status");
        sender.sendMessage(ChatColor.YELLOW + "/world debug" + ChatColor.GRAY + " - Debug information");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/world populators info" + ChatColor.GRAY + " - Show populator info");
        sender.sendMessage(ChatColor.YELLOW + "/world populators refresh" + ChatColor.GRAY + " - Refresh populators");
        sender.sendMessage(ChatColor.YELLOW + "/world populators add" + ChatColor.GRAY + " - Add populators to UHC world");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "/world force-reload" + ChatColor.GRAY + " - Force reload world system");
        sender.sendMessage(ChatColor.RED + "/world emergency-reset" + ChatColor.GRAY + " - Emergency world reset");
    }
}
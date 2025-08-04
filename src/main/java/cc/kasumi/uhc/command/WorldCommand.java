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

        if (worldManager.isWorldGenerationInProgress()) {
            sender.sendMessage(ChatColor.RED + "World generation is already in progress!");
            return;
        }

        worldManager.createNewUHCWorld().thenAccept(world -> {
            sender.sendMessage(ChatColor.GREEN + "UHC world created successfully: " + world.getName());
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

        if (worldManager.isWorldGenerationInProgress()) {
            sender.sendMessage(ChatColor.RED + "World generation is already in progress!");
            return;
        }

        worldManager.resetUHCWorld().thenRun(() -> {
            sender.sendMessage(ChatColor.GREEN + "UHC world reset successfully!");
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
        World uhcWorld = worldManager.getUhcWorld();

        if (uhcWorld == null) {
            sender.sendMessage(ChatColor.RED + "UHC world not found! Create it first.");
            return;
        }

        if (radius < 1 || radius > 50) {
            sender.sendMessage(ChatColor.RED + "Radius must be between 1 and 50!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting chunk pregeneration with radius " + radius + "...");
        worldManager.pregenerateSpawnChunks(uhcWorld, radius);
    }

    @Subcommand("tp uhc")
    @Description("Teleport to UHC world spawn")
    public void onTeleportUHC(Player player) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();
        World uhcWorld = worldManager.getUhcWorld();

        if (uhcWorld == null) {
            player.sendMessage(ChatColor.RED + "UHC world not found!");
            return;
        }

        player.teleport(uhcWorld.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to UHC world!");
    }

    @Subcommand("tp lobby")
    @Description("Teleport to lobby world")
    public void onTeleportLobby(Player player) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();
        World lobbyWorld = worldManager.getLobbyWorld();

        if (lobbyWorld == null) {
            player.sendMessage(ChatColor.RED + "Lobby world not found!");
            return;
        }

        player.teleport(lobbyWorld.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to lobby!");
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
        worldManager.configureCaveSettings(enabled, cutoff, minY, maxY, hStretch, vStretch);

        sender.sendMessage(ChatColor.GREEN + "Cave settings updated!");
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + enabled);
        sender.sendMessage(ChatColor.GRAY + "Cutoff: " + cutoff);
        sender.sendMessage(ChatColor.GRAY + "Y Range: " + minY + "-" + maxY);
        sender.sendMessage(ChatColor.GRAY + "Stretch: " + hStretch + "x" + vStretch);
        sender.sendMessage(ChatColor.YELLOW + "Note: Changes apply to new world generation only.");
    }

    @Subcommand("info")
    @Description("Show world information")
    public void onInfo(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

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
    }

    @Subcommand("populators info")
    @Description("Show populator information")
    public void onPopulatorsInfo(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();
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
        WorldManager.WorldGenerationSettings settings = worldManager.getSettings();

        sender.sendMessage(ChatColor.GOLD + "=== World Generation Settings ===");
        sender.sendMessage(ChatColor.GRAY + "World Type: " + settings.getWorldType());
        sender.sendMessage(ChatColor.GRAY + "Generate Structures: " + settings.isGenerateStructures());
        sender.sendMessage(ChatColor.GRAY + "Biome Swap Enabled: " + settings.isBiomeSwapEnabled());
        sender.sendMessage(ChatColor.GRAY + "Giant Caves Enabled: " + settings.isGiantCavesEnabled());
        sender.sendMessage(ChatColor.GRAY + "Custom Seed: " + (settings.getSeed() == 0 ? "Random" : settings.getSeed()));
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
        sender.sendMessage(ChatColor.YELLOW + "/world populators info" + ChatColor.GRAY + " - Show populator info");
        sender.sendMessage(ChatColor.YELLOW + "/world populators refresh" + ChatColor.GRAY + " - Refresh populators");
        sender.sendMessage(ChatColor.YELLOW + "/world populators add" + ChatColor.GRAY + " - Add populators to UHC world");
    }
}
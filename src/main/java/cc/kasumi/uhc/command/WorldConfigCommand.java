package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.world.WorldConfig;
import cc.kasumi.uhc.world.WorldManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;

@CommandAlias("worldconfig|wconfig")
@CommandPermission("uhc.admin")
public class WorldConfigCommand extends BaseCommand {

    @Subcommand("reload")
    @Description("Reload world configuration")
    public void onReload(CommandSender sender) {
        UHC.getInstance().getWorldManager().reloadConfiguration();
        sender.sendMessage(ChatColor.GREEN + "World configuration reloaded!");
    }

    @Subcommand("reset")
    @Description("Reset configuration to defaults")
    public void onReset(CommandSender sender) {
        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
        config.resetToDefaults();
        sender.sendMessage(ChatColor.GREEN + "World configuration reset to defaults!");
    }

    @Subcommand("set worldtype")
    @Description("Set world type")
    public void onSetWorldType(CommandSender sender, String worldType) {
        try {
            WorldType type = WorldType.valueOf(worldType.toUpperCase());
            WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
            config.setWorldType(type);
            sender.sendMessage(ChatColor.GREEN + "World type set to: " + type.name());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid world type! Valid types: NORMAL, FLAT, LARGE_BIOMES, AMPLIFIED");
        }
    }

    @Subcommand("set biomeswap")
    @Description("Enable/disable biome swapping")
    public void onSetBiomeSwap(CommandSender sender, boolean enabled) {
        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
        config.setBiomeSwapEnabled(enabled);
        sender.sendMessage(ChatColor.GREEN + "Biome swapping " + (enabled ? "enabled" : "disabled"));
    }

    @Subcommand("set caves")
    @Description("Enable/disable giant caves")
    public void onSetCaves(CommandSender sender, boolean enabled) {
        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
        config.setGiantCavesEnabled(enabled);
        sender.sendMessage(ChatColor.GREEN + "Giant caves " + (enabled ? "enabled" : "disabled"));
    }

    @Subcommand("set uhcworld")
    @Description("Set UHC world name")
    public void onSetUHCWorld(CommandSender sender, String worldName) {
        if (worldName.length() < 3 || worldName.length() > 16) {
            sender.sendMessage(ChatColor.RED + "World name must be between 3 and 16 characters!");
            return;
        }

        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
        config.setUhcWorldName(worldName);
        sender.sendMessage(ChatColor.GREEN + "UHC world name set to: " + worldName);
        sender.sendMessage(ChatColor.YELLOW + "Note: This will take effect on next world creation.");
    }

    @Subcommand("set lobbyworld")
    @Description("Set lobby world name")
    public void onSetLobbyWorld(CommandSender sender, String worldName) {
        if (worldName.length() < 3 || worldName.length() > 16) {
            sender.sendMessage(ChatColor.RED + "World name must be between 3 and 16 characters!");
            return;
        }

        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
        config.setLobbyWorldName(worldName);
        sender.sendMessage(ChatColor.GREEN + "Lobby world name set to: " + worldName);
        sender.sendMessage(ChatColor.YELLOW + "Note: This will take effect on next world creation.");
    }

    @Subcommand("caves configure")
    @Description("Configure cave generation settings")
    public void onConfigureCaves(CommandSender sender, boolean enabled, @Default("55") int cutoff,
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

        if (hStretch < 1 || hStretch > 100 || vStretch < 1 || vStretch > 100) {
            sender.sendMessage(ChatColor.RED + "Stretch values must be between 1 and 100!");
            return;
        }

        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();
        config.setCaveSettings(enabled, cutoff, minY, maxY, hStretch, vStretch);

        sender.sendMessage(ChatColor.GREEN + "Cave settings updated!");
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + enabled);
        sender.sendMessage(ChatColor.GRAY + "Cutoff: " + cutoff + "%");
        sender.sendMessage(ChatColor.GRAY + "Y Range: " + minY + "-" + maxY);
        sender.sendMessage(ChatColor.GRAY + "Stretch: " + hStretch + "x" + vStretch);
        sender.sendMessage(ChatColor.YELLOW + "Note: Changes apply to new world generation only.");
    }

    @Subcommand("show")
    @Description("Show current configuration")
    public void onShow(CommandSender sender) {
        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();

        sender.sendMessage(ChatColor.GOLD + "=== World Configuration ===");

        // World Settings
        sender.sendMessage(ChatColor.YELLOW + "World Settings:");
        sender.sendMessage(ChatColor.GRAY + "  UHC World: " + config.getUhcWorldName());
        sender.sendMessage(ChatColor.GRAY + "  Lobby World: " + config.getLobbyWorldName());
        sender.sendMessage(ChatColor.GRAY + "  World Type: " + config.getWorldType().name());
        sender.sendMessage(ChatColor.GRAY + "  Generate Structures: " + config.isGenerateStructures());
        sender.sendMessage(ChatColor.GRAY + "  Biome Swap: " + config.isBiomeSwapEnabled());
        sender.sendMessage(ChatColor.GRAY + "  Giant Caves: " + config.isGiantCavesEnabled());
        sender.sendMessage(ChatColor.GRAY + "  Custom Seed: " + config.isUseCustomSeed() +
                (config.isUseCustomSeed() ? " (" + config.getWorldSeed() + ")" : ""));

        // Cave Settings
        sender.sendMessage(ChatColor.YELLOW + "Cave Settings:");
        sender.sendMessage(ChatColor.GRAY + "  Enabled: " + config.isCaveEnabled());
        sender.sendMessage(ChatColor.GRAY + "  Cutoff: " + config.getCaveCutoff() + "%");
        sender.sendMessage(ChatColor.GRAY + "  Y Range: " + config.getCaveMinY() + "-" + config.getCaveMaxY());
        sender.sendMessage(ChatColor.GRAY + "  Stretch: " + config.getCaveHorizontalStretch() +
                "x" + config.getCaveVerticalStretch());

        // Generation Settings
        sender.sendMessage(ChatColor.YELLOW + "Generation Settings:");
        sender.sendMessage(ChatColor.GRAY + "  Pregenerate Radius: " + config.getPregenerateRadius());
        sender.sendMessage(ChatColor.GRAY + "  Pregenerate on Startup: " + config.isPregenerateOnStartup());
        sender.sendMessage(ChatColor.GRAY + "  Auto Reset World: " + config.isAutoResetWorld());

        // Border Settings
        sender.sendMessage(ChatColor.YELLOW + "Border Settings:");
        sender.sendMessage(ChatColor.GRAY + "  Default Size: " + config.getDefaultBorderSize());
        sender.sendMessage(ChatColor.GRAY + "  Damage Amount: " + config.getBorderDamageAmount());
        sender.sendMessage(ChatColor.GRAY + "  Damage Buffer: " + config.getBorderDamageBuffer());
    }

    @Subcommand("stats")
    @Description("Show world statistics")
    public void onStats(CommandSender sender) {
        WorldManager worldManager = UHC.getInstance().getWorldManager();
        WorldManager.WorldStats stats = worldManager.getWorldStats();

        sender.sendMessage(ChatColor.GOLD + "=== World Statistics ===");

        // UHC World Stats
        if (stats.uhcWorldLoaded) {
            sender.sendMessage(ChatColor.GREEN + "UHC World: " + stats.uhcWorldName);
            sender.sendMessage(ChatColor.GRAY + "  Players: " + stats.uhcWorldPlayers);
            sender.sendMessage(ChatColor.GRAY + "  Loaded Chunks: " + stats.uhcWorldLoadedChunks);
            sender.sendMessage(ChatColor.GRAY + "  Border Size: " + (int)stats.borderSize);
            if (stats.uhcWorldSpawn != null) {
                sender.sendMessage(ChatColor.GRAY + "  Spawn: " + formatLocation(stats.uhcWorldSpawn));
            }
        } else {
            sender.sendMessage(ChatColor.RED + "UHC World: Not Loaded");
        }

        // Lobby World Stats
        if (stats.lobbyWorldLoaded) {
            sender.sendMessage(ChatColor.GREEN + "Lobby World: " + stats.lobbyWorldName);
            sender.sendMessage(ChatColor.GRAY + "  Players: " + stats.lobbyWorldPlayers);
            if (stats.lobbyWorldSpawn != null) {
                sender.sendMessage(ChatColor.GRAY + "  Spawn: " + formatLocation(stats.lobbyWorldSpawn));
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Lobby World: Not Loaded");
        }

        // System Stats
        sender.sendMessage(ChatColor.YELLOW + "System:");
        sender.sendMessage(ChatColor.GRAY + "  Generation in Progress: " + stats.generationInProgress);
        sender.sendMessage(ChatColor.GRAY + "  Auto Reset Enabled: " + stats.autoResetEnabled);
    }

    @Subcommand("validate")
    @Description("Validate current configuration")
    public void onValidate(CommandSender sender) {
        WorldConfig config = UHC.getInstance().getWorldManager().getWorldConfig();

        if (config.validateConfig()) {
            sender.sendMessage(ChatColor.GREEN + "Configuration is valid!");
        } else {
            sender.sendMessage(ChatColor.RED + "Configuration has errors! Check console for details.");
        }
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== World Configuration Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig show" + ChatColor.GRAY + " - Show current configuration");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig stats" + ChatColor.GRAY + " - Show world statistics");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig reset" + ChatColor.GRAY + " - Reset to defaults");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig validate" + ChatColor.GRAY + " - Validate configuration");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig set worldtype <type>" + ChatColor.GRAY + " - Set world type");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig set biomeswap <true/false>" + ChatColor.GRAY + " - Toggle biome swap");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig set caves <true/false>" + ChatColor.GRAY + " - Toggle giant caves");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig set uhcworld <name>" + ChatColor.GRAY + " - Set UHC world name");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig set lobbyworld <name>" + ChatColor.GRAY + " - Set lobby world name");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "/wconfig caves configure <enabled> [cutoff] [minY] [maxY] [hStretch] [vStretch]");
        sender.sendMessage(ChatColor.GRAY + "  - Configure detailed cave settings");
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("(%d, %d, %d)",
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
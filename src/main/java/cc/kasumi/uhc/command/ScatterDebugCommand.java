package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.team.UHCTeam;
import cc.kasumi.uhc.util.GameUtil;
import cc.kasumi.uhc.util.ProgressiveScatterManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Random;

@CommandAlias("scatterdebug|sdebug")
@CommandPermission("uhc.admin")
public class ScatterDebugCommand extends BaseCommand {

    @Subcommand("test location")
    @Description("Test if a location is safe for scattering")
    public void onTestLocation(Player player) {
        Location loc = player.getLocation();

        player.sendMessage(ChatColor.GOLD + "=== Location Safety Test ===");
        player.sendMessage(ChatColor.YELLOW + "Location: " + formatLocation(loc));

        boolean isSafe = GameUtil.isLocationSafe(loc);
        player.sendMessage(ChatColor.YELLOW + "Safe: " + (isSafe ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

        if (!isSafe) {
            player.sendMessage(ChatColor.RED + "Reasons location is unsafe:");
            analyzeLocationSafety(player, loc);
        }

        // Test border constraints
        World world = loc.getWorld();
        WorldBorder border = world.getWorldBorder();
        Location borderCenter = border.getCenter();
        double borderRadius = border.getSize() / 2;

        double deltaX = Math.abs(loc.getX() - borderCenter.getX());
        double deltaZ = Math.abs(loc.getZ() - borderCenter.getZ());

        boolean withinBorder = deltaX <= borderRadius && deltaZ <= borderRadius;
        player.sendMessage(ChatColor.YELLOW + "Within Border: " + (withinBorder ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

        if (!withinBorder) {
            player.sendMessage(ChatColor.RED + "Distance from border center: " +
                    String.format("X=%.1f, Z=%.1f (max: %.1f)", deltaX, deltaZ, borderRadius));
        }
    }

    @Subcommand("generate test")
    @Description("Generate and test random scatter locations")
    public void onGenerateTest(CommandSender sender, @Default("10") int count) {
        Game game = UHC.getInstance().getGame();
        if (game == null || game.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Game or world not available!");
            return;
        }

        World world = game.getWorld();
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        Random random = new Random();

        int radius = Math.max(150, (int)(border.getSize() / 2) - 80);

        sender.sendMessage(ChatColor.GOLD + "=== Scatter Location Generation Test ===");
        sender.sendMessage(ChatColor.YELLOW + "Testing " + count + " random locations with radius " + radius);

        int safeLocations = 0;
        int unsafeLocations = 0;

        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);
            candidate.setY(groundY + 1);

            if (GameUtil.isLocationSafe(candidate)) {
                safeLocations++;
            } else {
                unsafeLocations++;
            }
        }

        double successRate = (double) safeLocations / count * 100;
        sender.sendMessage(ChatColor.GREEN + "Safe locations: " + safeLocations + "/" + count +
                " (" + String.format("%.1f", successRate) + "%)");
        sender.sendMessage(ChatColor.RED + "Unsafe locations: " + unsafeLocations);

        if (successRate < 50) {
            sender.sendMessage(ChatColor.RED + "WARNING: Low success rate! Scattering may fail frequently.");
            sender.sendMessage(ChatColor.YELLOW + "Consider:");
            sender.sendMessage(ChatColor.GRAY + "- Increasing world border size");
            sender.sendMessage(ChatColor.GRAY + "- Reducing minimum distance between teams");
            sender.sendMessage(ChatColor.GRAY + "- Using world pregeneration");
        }
    }

    @Subcommand("analyze teams")
    @Description("Analyze teams that need to be scattered")
    public void onAnalyzeTeams(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null) {
            sender.sendMessage(ChatColor.RED + "Game not available!");
            return;
        }

        List<UHCTeam> allTeams = (List<UHCTeam>) game.getTeamManager().getAllTeams();
        int validTeams = 0;
        int emptyTeams = 0;
        int offlineTeams = 0;

        sender.sendMessage(ChatColor.GOLD + "=== Team Analysis ===");

        for (UHCTeam team : allTeams) {
            if (team.getSize() == 0) {
                emptyTeams++;
                continue;
            }

            if (team.getOnlineMembers().isEmpty()) {
                offlineTeams++;
                sender.sendMessage(ChatColor.RED + "Offline team: " + team.getTeamName() +
                        " (size: " + team.getSize() + ")");
                continue;
            }

            validTeams++;
            sender.sendMessage(ChatColor.GREEN + "Valid team: " + team.getTeamName() +
                    " (online: " + team.getOnlineMembers().size() + "/" + team.getSize() + ")");
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.YELLOW + "Summary:");
        sender.sendMessage(ChatColor.GREEN + "Valid teams (will scatter): " + validTeams);
        sender.sendMessage(ChatColor.RED + "Teams with offline members: " + offlineTeams);
        sender.sendMessage(ChatColor.GRAY + "Empty teams (will skip): " + emptyTeams);
        sender.sendMessage(ChatColor.YELLOW + "Total teams: " + allTeams.size());

        if (validTeams == 0) {
            sender.sendMessage(ChatColor.RED + "WARNING: No valid teams to scatter!");
        }
    }

    @Subcommand("world info")
    @Description("Show world information relevant to scattering")
    public void onWorldInfo(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null || game.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Game or world not available!");
            return;
        }

        World world = game.getWorld();
        WorldBorder border = world.getWorldBorder();

        sender.sendMessage(ChatColor.GOLD + "=== World Information ===");
        sender.sendMessage(ChatColor.YELLOW + "World: " + world.getName());
        sender.sendMessage(ChatColor.YELLOW + "Environment: " + world.getEnvironment());
        sender.sendMessage(ChatColor.YELLOW + "Loaded Chunks: " + world.getLoadedChunks().length);
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "World Border:");
        sender.sendMessage(ChatColor.GRAY + "  Center: " + formatLocation(border.getCenter()));
        sender.sendMessage(ChatColor.GRAY + "  Size: " + (int)border.getSize() + " blocks");
        sender.sendMessage(ChatColor.GRAY + "  Radius: " + (int)(border.getSize() / 2) + " blocks");
        sender.sendMessage(ChatColor.GRAY + "  Damage Amount: " + border.getDamageAmount());
        sender.sendMessage(ChatColor.GRAY + "  Damage Buffer: " + border.getDamageBuffer());
        sender.sendMessage("");

        // Calculate recommended scatter radius
        int maxRadius = (int)(border.getSize() / 2) - 80;
        int recommendedRadius = Math.max(150, maxRadius);

        sender.sendMessage(ChatColor.YELLOW + "Scatter Calculations:");
        sender.sendMessage(ChatColor.GRAY + "  Max Safe Radius: " + maxRadius);
        sender.sendMessage(ChatColor.GRAY + "  Recommended Radius: " + recommendedRadius);
        sender.sendMessage(ChatColor.GRAY + "  Safety Buffer: 80 blocks");

        // Calculate theoretical max teams
        double borderArea = Math.PI * Math.pow(recommendedRadius, 2);
        double teamArea = Math.PI * Math.pow(100, 2); // 100 block min distance
        int theoreticalMaxTeams = (int)(borderArea / teamArea);

        sender.sendMessage(ChatColor.GRAY + "  Theoretical Max Teams: " + theoreticalMaxTeams +
                " (with 100 block spacing)");
    }

    @Subcommand("simulate")
    @Description("Simulate a scatter attempt without actually scattering")
    public void onSimulate(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null || game.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Game or world not available!");
            return;
        }

        List<UHCTeam> teams = (List<UHCTeam>) game.getTeamManager().getAllTeams();
        int validTeams = 0;

        for (UHCTeam team : teams) {
            if (team.getSize() > 0 && !team.getOnlineMembers().isEmpty()) {
                validTeams++;
            }
        }

        if (validTeams == 0) {
            sender.sendMessage(ChatColor.RED + "No valid teams to simulate!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Simulating scatter for " + validTeams + " teams...");

        // Create a test scatter manager but don't start it
        int borderSize = game.getInitialBorderSize();
        World world = game.getWorld();
        WorldBorder border = world.getWorldBorder();
        Random random = new Random();

        int successfulLocations = 0;
        int attempts = 0;
        int maxAttempts = validTeams * 50; // 50 attempts per team

        for (int i = 0; i < validTeams && attempts < maxAttempts; i++) {
            boolean found = false;

            for (int attempt = 0; attempt < 50; attempt++) {
                attempts++;

                Location candidate = generateRandomLocation(world, border, random, borderSize);
                if (candidate != null && GameUtil.isLocationSafe(candidate)) {
                    successfulLocations++;
                    found = true;
                    break;
                }
            }

            if (!found) {
                sender.sendMessage(ChatColor.RED + "Failed to find location for team " + (i + 1));
            }
        }

        sender.sendMessage(ChatColor.GREEN + "Simulation Results:");
        sender.sendMessage(ChatColor.YELLOW + "Successful locations: " + successfulLocations + "/" + validTeams);
        sender.sendMessage(ChatColor.YELLOW + "Total attempts: " + attempts);
        sender.sendMessage(ChatColor.YELLOW + "Success rate: " +
                String.format("%.1f", (double)successfulLocations / validTeams * 100) + "%");

        if (successfulLocations < validTeams) {
            sender.sendMessage(ChatColor.RED + "WARNING: Simulation predicts scatter failures!");
            sender.sendMessage(ChatColor.YELLOW + "Consider using /scatterdebug world info for optimization suggestions.");
        }
    }

    @Subcommand("force scatter")
    @Description("Force start improved scatter system")
    public void onForceScatter(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        if (game == null || game.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "Game or world not available!");
            return;
        }

        if (game.isGameStarted()) {
            sender.sendMessage(ChatColor.RED + "Game has already started!");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Force starting improved scatter system...");

        // Ensure all players are on teams
        game.autoAssignPlayersToTeams();

        // Start improved scatter
        ProgressiveScatterManager scatterManager = new ProgressiveScatterManager(game, game.getInitialBorderSize());
        scatterManager.startScattering();

        sender.sendMessage(ChatColor.GREEN + "Improved scatter system started!");
    }

    @Subcommand("chunks")
    @CommandPermission("uhc.admin")
    @Description("Check chunk loading around a location")
    public void onChunks(CommandSender sender, String x, String z) {
        try {
            Game game = UHC.getInstance().getGame();
            if (game == null || game.getWorld() == null) {
                sender.sendMessage(ChatColor.RED + "Game or world not available!");
                return;
            }
            
            World world = game.getWorld();
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "UHC world is not loaded!");
                return;
            }
            
            int blockX = Integer.parseInt(x);
            int blockZ = Integer.parseInt(z);
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            
            sender.sendMessage(ChatColor.YELLOW + "=== Chunk Status at " + x + ", " + z + " ===");
            sender.sendMessage(ChatColor.GRAY + "Chunk coordinates: " + chunkX + ", " + chunkZ);
            
            int radius = 2;
            int loadedCount = 0;
            int totalCount = 0;
            
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                    totalCount++;
                    
                    if (chunk.isLoaded()) {
                        loadedCount++;
                        sender.sendMessage(ChatColor.GREEN + "  ✓ Chunk " + (chunkX + dx) + ", " + (chunkZ + dz) + " - LOADED");
                    } else {
                        sender.sendMessage(ChatColor.RED + "  ✗ Chunk " + (chunkX + dx) + ", " + (chunkZ + dz) + " - NOT LOADED");
                    }
                }
            }
            
            sender.sendMessage(ChatColor.YELLOW + "Summary: " + loadedCount + "/" + totalCount + " chunks loaded");
            
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates! Usage: /scatter chunks <x> <z>");
        }
    }
    
    @Subcommand("loadchunks")
    @CommandPermission("uhc.admin")
    @Description("Force load chunks around a location")
    public void onLoadChunks(CommandSender sender, String x, String z) {
        try {
            Game game = UHC.getInstance().getGame();
            if (game == null || game.getWorld() == null) {
                sender.sendMessage(ChatColor.RED + "Game or world not available!");
                return;
            }
            
            World world = game.getWorld();
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "UHC world is not loaded!");
                return;
            }
            
            int blockX = Integer.parseInt(x);
            int blockZ = Integer.parseInt(z);
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            
            sender.sendMessage(ChatColor.YELLOW + "Force loading chunks around " + x + ", " + z + "...");
            
            int radius = 2;
            int loadedCount = 0;
            
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                    
                    if (!chunk.isLoaded()) {
                        chunk.load(true);
                        loadedCount++;
                    }
                }
            }
            
            sender.sendMessage(ChatColor.GREEN + "Force loaded " + loadedCount + " chunks!");
            
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid coordinates! Usage: /scatter loadchunks <x> <z>");
        }
    }

    @Subcommand("test water safety")
    @Description("Test water safety detection in location safety checks")
    public void onTestWaterSafety(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        
        player.sendMessage(ChatColor.GOLD + "=== Water Safety Test ===");
        player.sendMessage(ChatColor.YELLOW + "Testing location: " + formatLocation(loc));
        
        // Test current location
        boolean isSafe = GameUtil.isLocationSafe(loc);
        player.sendMessage(ChatColor.YELLOW + "Current location safe: " + 
            (isSafe ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        
        // Test blocks around current location
        Block groundBlock = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
        Block feetBlock = world.getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        Block headBlock = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        
        player.sendMessage(ChatColor.YELLOW + "Ground block: " + ChatColor.WHITE + groundBlock.getType());
        player.sendMessage(ChatColor.YELLOW + "Feet block: " + ChatColor.WHITE + feetBlock.getType());
        player.sendMessage(ChatColor.YELLOW + "Head block: " + ChatColor.WHITE + headBlock.getType());
        
        // Check for water nearby
        boolean waterNearby = false;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block block = world.getBlockAt(
                        loc.getBlockX() + dx, 
                        loc.getBlockY() + dy, 
                        loc.getBlockZ() + dz
                    );
                    if (block.getType() == Material.WATER || block.getType() == Material.STATIONARY_WATER) {
                        waterNearby = true;
                        player.sendMessage(ChatColor.BLUE + "Water found at offset (" + dx + "," + dy + "," + dz + ")");
                    }
                }
            }
        }
        
        if (!waterNearby) {
            player.sendMessage(ChatColor.GREEN + "No water found within 5x5x5 area");
        }
        
        // Test what getHighestBlockYAt would return here
        int highestY = world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
        Block highestBlock = world.getBlockAt(loc.getBlockX(), highestY, loc.getBlockZ());
        player.sendMessage(ChatColor.YELLOW + "Highest block Y: " + highestY + 
            " (" + highestBlock.getType() + ")");
        
        // Simulate what scatter would do
        player.sendMessage(ChatColor.GOLD + "--- Scatter Simulation ---");
        player.sendMessage(ChatColor.GRAY + "Testing what scatter would do at this location...");
        
        Location testLoc = new Location(world, loc.getX(), 0, loc.getZ());
        int testHighestY = world.getHighestBlockYAt(testLoc);
        int testY = testHighestY;
        Block testBlock = world.getBlockAt((int)loc.getX(), testY, (int)loc.getZ());
        
        player.sendMessage(ChatColor.GRAY + "Initial highest Y: " + testHighestY + " (" + testBlock.getType() + ")");
        
        // Simulate the improved safe solid block finding
        int iterations = 0;
        while (testY > 0 && iterations < 10) {
            testBlock = world.getBlockAt((int)loc.getX(), testY, (int)loc.getZ());
            boolean isSolidBlock = testBlock.getType().isSolid();
            boolean isWater = testBlock.getType() == Material.WATER || testBlock.getType() == Material.STATIONARY_WATER;
            boolean isLava = testBlock.getType() == Material.LAVA || testBlock.getType() == Material.STATIONARY_LAVA;
            
            player.sendMessage(ChatColor.GRAY + "Y=" + testY + " " + testBlock.getType() + 
                " solid=" + isSolidBlock + " water=" + isWater + " lava=" + isLava);
            
            if (isSolidBlock && !isWater && !isLava && 
                testBlock.getType() != Material.FIRE && testBlock.getType() != Material.CACTUS) {
                player.sendMessage(ChatColor.GREEN + "Found safe solid block at Y=" + testY);
                break;
            }
            
            testY--;
            iterations++;
        }
        
        if (iterations >= 10) {
            player.sendMessage(ChatColor.RED + "No safe solid block found in 10 blocks down!");
        } else {
            testLoc.setY(testY + 1);
            boolean finalSafe = GameUtil.isLocationSafe(testLoc);
            player.sendMessage(ChatColor.YELLOW + "Final scatter location: " + formatLocation(testLoc));
            player.sendMessage(ChatColor.YELLOW + "Final location safe: " + 
                (finalSafe ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        }
    }

    private void analyzeLocationSafety(Player player, Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // Check ground
        if (y < 1 || y > 255) {
            player.sendMessage(ChatColor.RED + "- Invalid Y coordinate: " + y);
        }

        org.bukkit.Material groundMaterial = world.getBlockAt(x, y - 1, z).getType();
        if (groundMaterial == org.bukkit.Material.AIR ||
                groundMaterial == org.bukkit.Material.LAVA ||
                groundMaterial == org.bukkit.Material.STATIONARY_LAVA ||
                groundMaterial == org.bukkit.Material.WATER ||
                groundMaterial == org.bukkit.Material.STATIONARY_WATER) {
            player.sendMessage(ChatColor.RED + "- Unsafe ground: " + groundMaterial);
        }

        // Check feet space
        org.bukkit.Material feetMaterial = world.getBlockAt(x, y, z).getType();
        if (feetMaterial != org.bukkit.Material.AIR &&
                feetMaterial != org.bukkit.Material.LONG_GRASS &&
                feetMaterial != org.bukkit.Material.YELLOW_FLOWER &&
                feetMaterial != org.bukkit.Material.RED_ROSE) {
            player.sendMessage(ChatColor.RED + "- Blocked feet space: " + feetMaterial);
        }

        // Check headspace
        org.bukkit.Material headMaterial = world.getBlockAt(x, y + 1, z).getType();
        if (headMaterial != org.bukkit.Material.AIR) {
            player.sendMessage(ChatColor.RED + "- Blocked head space: " + headMaterial);
        }

        // Check for dangerous nearby blocks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                org.bukkit.Material nearby = world.getBlockAt(x + dx, y, z + dz).getType();
                if (nearby == org.bukkit.Material.LAVA ||
                        nearby == org.bukkit.Material.STATIONARY_LAVA ||
                        nearby == org.bukkit.Material.FIRE ||
                        nearby == org.bukkit.Material.CACTUS) {
                    player.sendMessage(ChatColor.RED + "- Dangerous nearby block: " + nearby +
                            " at offset (" + dx + ", " + dz + ")");
                }
            }
        }
    }

    private Location generateRandomLocation(World world, WorldBorder border, Random random, int radius) {
        try {
            Location center = border.getCenter();

            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);
            candidate.setY(groundY + 1);

            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Scatter Debug Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug test location" + ChatColor.GRAY + " - Test current location safety");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug generate test [count]" + ChatColor.GRAY + " - Test random location generation");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug analyze teams" + ChatColor.GRAY + " - Analyze teams for scattering");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug world info" + ChatColor.GRAY + " - Show world scatter information");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug simulate" + ChatColor.GRAY + " - Simulate scatter without executing");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug force scatter" + ChatColor.GRAY + " - Force start improved scatter");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug chunks <x> <z>" + ChatColor.GRAY + " - Check chunk loading around a location");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug loadchunks <x> <z>" + ChatColor.GRAY + " - Force load chunks around a location");
        sender.sendMessage(ChatColor.YELLOW + "/sdebug test water safety" + ChatColor.GRAY + " - Test water safety detection in location safety checks");
    }
}
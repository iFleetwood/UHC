package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.team.UHCTeam;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Progressive scatter system with chunk preloading and optimized performance
 */
public class ProgressiveScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private final double borderRadius;
    private final double bufferFromBorder;
    private final List<UHCTeam> teamsToScatter;
    private final PlayerFreezeManager freezeManager;
    
    // Scatter locations and chunk management
    private final Map<UUID, Location> teamScatterLocations = new ConcurrentHashMap<>();
    private final Map<UUID, ScatterAttempt> scatterAttempts = new ConcurrentHashMap<>();
    private final Set<ChunkCoordinate> chunksToPreload = ConcurrentHashMap.newKeySet();
    private final Set<ChunkCoordinate> preloadedChunks = ConcurrentHashMap.newKeySet();
    
    // State tracking
    @Getter
    private ScatterPhase currentPhase = ScatterPhase.INITIALIZING;
    private int currentTeamIndex = 0;
    private Iterator<ChunkCoordinate> chunkIterator;
    private boolean cancelled = false;
    
    // Configuration
    private static final int LOCATIONS_PER_TICK = 2; // Generate 2 locations per tick
    private static final int CHUNKS_PER_TICK = 3; // Preload 3 chunks per tick
    private static final int TELEPORTS_PER_TICK = 1; // Teleport 1 team per tick
    private static final int MIN_DISTANCE_BETWEEN_TEAMS = 150; // Minimum distance between teams
    private static final int MIN_DISTANCE_FROM_PLAYERS = 100; // Minimum distance from existing players (reduced for testing)
    private static final int MAX_TEAM_SPREAD = 20; // Maximum spread for team members
    private static final int MAX_ATTEMPTS_PER_LOCATION = 100; // Maximum attempts to find a location
    private static final double BUFFER_PERCENTAGE = 0.05; // 5% buffer from border
    private static final int CHUNK_PRELOAD_RADIUS = 2; // Preload chunks in 5x5 area
    
    // Performance tracking
    private long startTime;
    private int totalChunksToPreload = 0;
    
    public enum ScatterPhase {
        INITIALIZING,
        VALIDATING_TEAMS,
        GENERATING_LOCATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_TEAMS,
        COMPLETED,
        FAILED
    }
    
    private static class ScatterAttempt {
        int attempts = 0;
        boolean successful = false;
        String failureReason = "";
        Location finalLocation = null;
        long timestamp = System.currentTimeMillis();
    }
    
    private static class ChunkCoordinate {
        final int x;
        final int z;
        
        ChunkCoordinate(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkCoordinate that = (ChunkCoordinate) o;
            return x == that.x && z == that.z;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }
    
    public ProgressiveScatterManager(Game game, int borderSize) {
        this.game = game;
        this.world = game.getWorld();
        this.borderRadius = borderSize; // Border size is the radius (e.g., 1000 = ±1000)
        this.bufferFromBorder = borderRadius * BUFFER_PERCENTAGE;
        this.teamsToScatter = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
        this.freezeManager = new PlayerFreezeManager();
        
        UHC.getInstance().getLogger().info("Initializing ProgressiveScatter with border radius: " + borderRadius + 
                " (usable area: ±" + (borderRadius - bufferFromBorder) + " blocks)");
    }
    
    @Override
    public void run() {
        if (cancelled) {
            return;
        }
        
        try {
            switch (currentPhase) {
                case INITIALIZING:
                    initialize();
                    break;
                case VALIDATING_TEAMS:
                    validateTeams();
                    break;
                case GENERATING_LOCATIONS:
                    generateLocations();
                    break;
                case PRELOADING_CHUNKS:
                    preloadChunks();
                    break;
                case TELEPORTING_TEAMS:
                    teleportTeams();
                    break;
                case COMPLETED:
                    complete();
                    break;
                case FAILED:
                    handleFailure();
                    break;
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error in ProgressiveScatter: " + e.getMessage());
            e.printStackTrace();
            currentPhase = ScatterPhase.FAILED;
        }
    }
    
    private void initialize() {
        if (world == null) {
            handleError("World is null!");
            return;
        }
        
        // Reset debug counter
        GameUtil.resetLocationSafetyDebug();
        
        // Log initialization details
        UHC.getInstance().getLogger().info("=== ProgressiveScatter Initialization ===");
        UHC.getInstance().getLogger().info("World: " + world.getName());
        UHC.getInstance().getLogger().info("Border radius: " + borderRadius);
        UHC.getInstance().getLogger().info("Buffer from border: " + bufferFromBorder);
        UHC.getInstance().getLogger().info("Usable radius: " + (borderRadius - bufferFromBorder));
        UHC.getInstance().getLogger().info("Min distance between teams: " + MIN_DISTANCE_BETWEEN_TEAMS);
        UHC.getInstance().getLogger().info("Min distance from players: " + MIN_DISTANCE_FROM_PLAYERS);
        
        // Collect valid teams
        for (UHCTeam team : game.getTeamManager().getAllTeams()) {
            if (team.getSize() > 0 && !team.getOnlineMembers().isEmpty()) {
                teamsToScatter.add(team);
                scatterAttempts.put(team.getTeamId(), new ScatterAttempt());
            }
        }
        
        if (teamsToScatter.isEmpty()) {
            handleError("No valid teams to scatter!");
            return;
        }
        
        UHC.getInstance().getLogger().info("Found " + teamsToScatter.size() + " teams to scatter");
        
        // Log online players
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        UHC.getInstance().getLogger().info("Online players: " + onlinePlayers);
        if (onlinePlayers > 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                UHC.getInstance().getLogger().info("  - " + p.getName() + " at " + formatLocation(p.getLocation()));
            }
        }
        
        // Announce scatter start
        Bukkit.broadcastMessage(ChatColor.GOLD + "§l§m                                                ");
        Bukkit.broadcastMessage(ChatColor.GOLD + "§lSCATTERING PLAYERS!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "You will be frozen after being teleported.");
        Bukkit.broadcastMessage(ChatColor.GOLD + "§l§m                                                ");
        
        currentPhase = ScatterPhase.VALIDATING_TEAMS;
    }
    
    private void validateTeams() {
        // Validate world conditions
        if (!game.isWorldReady()) {
            handleError("World is not ready!");
            return;
        }
        
        // Calculate if we have enough space
        double usableRadius = borderRadius - bufferFromBorder;
        double totalArea = usableRadius * usableRadius * 4; // Square area
        double requiredAreaPerTeam = MIN_DISTANCE_BETWEEN_TEAMS * MIN_DISTANCE_BETWEEN_TEAMS;
        double maxTeams = totalArea / requiredAreaPerTeam;
        
        if (teamsToScatter.size() > maxTeams) {
            UHC.getInstance().getLogger().warning("Warning: " + teamsToScatter.size() + 
                    " teams may be too many for border size (recommended max: " + (int)maxTeams + ")");
        }
        
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Validating " + teamsToScatter.size() + " teams for scatter...");
        currentPhase = ScatterPhase.GENERATING_LOCATIONS;
    }
    
    private void generateLocations() {
        int locationsGenerated = 0;
        Random random = new Random();
        
        while (currentTeamIndex < teamsToScatter.size() && locationsGenerated < LOCATIONS_PER_TICK) {
            UHCTeam team = teamsToScatter.get(currentTeamIndex);
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
            
            if (!attempt.successful && attempt.attempts < MAX_ATTEMPTS_PER_LOCATION) {
                Location location = findValidLocation(team, random);
                
                if (location != null) {
                    teamScatterLocations.put(team.getTeamId(), location);
                    attempt.successful = true;
                    attempt.finalLocation = location;
                    addChunksToPreload(location);
                    
                    UHC.getInstance().getLogger().info("Found location for team " + team.getTeamName() + 
                            " at " + formatLocation(location) + " (attempt " + attempt.attempts + ")");
                } else if (attempt.attempts >= MAX_ATTEMPTS_PER_LOCATION) {
                    attempt.failureReason = "Max attempts reached";
                    UHC.getInstance().getLogger().warning("Failed to find location for team " + 
                            team.getTeamName() + ": " + attempt.failureReason);
                }
            }
            
            if (attempt.successful || attempt.attempts >= MAX_ATTEMPTS_PER_LOCATION) {
                currentTeamIndex++;
                locationsGenerated++;
            }
        }
        
        // Progress update
        if (currentTeamIndex > 0 && currentTeamIndex % 5 == 0) {
            double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Generating locations: " + 
                    String.format("%.0f%%", progress) + " complete");
        }
        
        // Check if done
        if (currentTeamIndex >= teamsToScatter.size()) {
            int successful = (int) scatterAttempts.values().stream()
                    .filter(a -> a.successful)
                    .count();
            
            if (successful == 0) {
                // Try with reduced requirements before giving up
                UHC.getInstance().getLogger().warning("No locations found with standard requirements. Trying with reduced constraints...");
                attemptFallbackScatter();
                return;
            }
            
            Bukkit.broadcastMessage(ChatColor.GREEN + "Generated " + successful + "/" + 
                    teamsToScatter.size() + " team locations. Preloading chunks...");
            
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            totalChunksToPreload = chunksToPreload.size();
        }
    }
    
    private void attemptFallbackScatter() {
        UHC.getInstance().getLogger().info("Attempting fallback scatter with reduced requirements...");
        Random random = new Random();
        int successfulFallbacks = 0;
        
        for (UHCTeam team : teamsToScatter) {
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
            if (!attempt.successful) {
                // Try with progressively reduced requirements
                Location location = findFallbackLocation(team, random);
                if (location != null) {
                    teamScatterLocations.put(team.getTeamId(), location);
                    attempt.successful = true;
                    attempt.finalLocation = location;
                    addChunksToPreload(location);
                    successfulFallbacks++;
                    UHC.getInstance().getLogger().info("Fallback location found for team " + team.getTeamName());
                }
            }
        }
        
        int totalSuccessful = (int) scatterAttempts.values().stream()
                .filter(a -> a.successful)
                .count();
        
        if (totalSuccessful == 0) {
            handleError("Failed to generate any scatter locations even with fallback!");
            return;
        }
        
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Generated " + totalSuccessful + "/" + 
                teamsToScatter.size() + " team locations (including " + successfulFallbacks + " fallbacks). Preloading chunks...");
        
        currentPhase = ScatterPhase.PRELOADING_CHUNKS;
        chunkIterator = chunksToPreload.iterator();
        totalChunksToPreload = chunksToPreload.size();
    }
    
    private Location findFallbackLocation(UHCTeam team, Random random) {
        // Try with reduced player distance
        for (int i = 0; i < 20; i++) {
            Location loc = generateRandomLocation(random, borderRadius - bufferFromBorder);
            if (loc != null && GameUtil.isLocationSafe(loc) && 
                isLocationValidWithReducedRequirements(loc, 50, 50)) {
                return loc;
            }
        }
        
        // Try with even more reduced requirements
        for (int i = 0; i < 20; i++) {
            Location loc = generateRandomLocation(random, borderRadius - bufferFromBorder);
            if (loc != null && GameUtil.isLocationSafe(loc) && 
                isLocationValidWithReducedRequirements(loc, 30, 0)) {
                return loc;
            }
        }
        
        // Last resort - just find any safe location
        for (int i = 0; i < 30; i++) {
            Location loc = generateRandomLocation(random, borderRadius * 0.8);
            if (loc != null && GameUtil.isLocationSafe(loc)) {
                return loc;
            }
        }
        
        return null;
    }
    
    private Location generateRandomLocation(Random random, double maxRadius) {
        double x = (random.nextDouble() * 2 - 1) * maxRadius;
        double z = (random.nextDouble() * 2 - 1) * maxRadius;
        
        Location candidate = new Location(world, x, 0, z);
        // Find the highest block Y coordinate
        int highestY = world.getHighestBlockYAt((int)x, (int)z);
        
        // Find the actual solid ground
        int y = highestY;
        Block currentBlock = world.getBlockAt((int)x, y, (int)z);
        
        // If the highest block is air or non-solid, we need to go down
        while (y > 0 && (!currentBlock.getType().isSolid() || currentBlock.getType() == Material.AIR)) {
            y--;
            currentBlock = world.getBlockAt((int)x, y, (int)z);
        }
        
        // Ensure Y is reasonable
        if (y < 1) {
            y = 64; // Default to sea level if something is wrong
        } else if (y > 250) {
            y = 250; // Cap at reasonable height
        }
        
        // Place player one block above the solid block
        candidate.setY(y + 1);
        
        return candidate;
    }
    
    private boolean isLocationValidWithReducedRequirements(Location location, int minTeamDistance, int minPlayerDistance) {
        // Check distance from border
        double distanceFromCenter = Math.max(
            Math.abs(location.getX()),
            Math.abs(location.getZ())
        );
        if (distanceFromCenter > borderRadius - bufferFromBorder) {
            return false;
        }
        
        // Check distance from other teams with reduced requirement
        if (minTeamDistance > 0) {
            for (Location existingLocation : teamScatterLocations.values()) {
                if (location.distance(existingLocation) < minTeamDistance) {
                    return false;
                }
            }
        }
        
        // Check distance from online players with reduced requirement
        if (minPlayerDistance > 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(world) && 
                    player.getLocation().distance(location) < minPlayerDistance) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private Location findValidLocation(UHCTeam team, Random random) {
        ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
        
        for (int i = 0; i < 10 && attempt.attempts < MAX_ATTEMPTS_PER_LOCATION; i++) {
            attempt.attempts++;
            
            // Generate random location within usable area
            double usableRadius = borderRadius - bufferFromBorder;
            double x = (random.nextDouble() * 2 - 1) * usableRadius;
            double z = (random.nextDouble() * 2 - 1) * usableRadius;
            
            Location candidate = new Location(world, x, 0, z);
            // Find the highest block Y coordinate
            int highestY = world.getHighestBlockYAt((int)x, (int)z);
            
            // Debug initial Y
            if (attempt.attempts <= 5) {
                UHC.getInstance().getLogger().info("DEBUG: At " + (int)x + "," + (int)z + 
                    " world.getHighestBlockYAt returned: " + highestY);
            }
            
            // Find the actual solid ground
            int y = highestY;
            Block currentBlock = world.getBlockAt((int)x, y, (int)z);
            
            // If the highest block is air or non-solid, we need to go down
            while (y > 0 && (!currentBlock.getType().isSolid() || currentBlock.getType() == Material.AIR)) {
                y--;
                currentBlock = world.getBlockAt((int)x, y, (int)z);
            }
            
            // Debug what we found
            if (attempt.attempts <= 5) {
                Block aboveBlock = world.getBlockAt((int)x, y + 1, (int)z);
                Block twoAboveBlock = world.getBlockAt((int)x, y + 2, (int)z);
                UHC.getInstance().getLogger().info("DEBUG: Found solid at Y=" + y + 
                    " block=" + currentBlock.getType() +
                    " above=" + aboveBlock.getType() +
                    " twoAbove=" + twoAboveBlock.getType());
            }
            
            // Ensure Y is reasonable
            if (y < 1) {
                y = 64; // Default to sea level if something is wrong
            } else if (y > 250) {
                y = 250; // Cap at reasonable height
            }
            
            // Debug: Let's see what happens if we don't add 1
            if (attempt.attempts <= 3) {
                UHC.getInstance().getLogger().info("DEBUG: Setting Y to " + (y + 1) + " (solid block at " + y + ")");
                
                // Let's also check what getHighestBlockYAt actually returns
                Block testBlock1 = world.getBlockAt((int)x, highestY, (int)z);
                Block testBlock2 = world.getBlockAt((int)x, highestY + 1, (int)z);
                Block testBlock3 = world.getBlockAt((int)x, highestY - 1, (int)z);
                
                UHC.getInstance().getLogger().info("DEBUG: getHighestBlockYAt=" + highestY + 
                    " block@Y=" + testBlock1.getType() +
                    " block@Y+1=" + testBlock2.getType() +
                    " block@Y-1=" + testBlock3.getType());
            }
            
            // Place player one block above the solid block
            candidate.setY(y + 1);
            
            // Debug logging
            if (attempt.attempts <= 5 || attempt.attempts % 10 == 0) {
                UHC.getInstance().getLogger().info("Attempt " + attempt.attempts + " for team " + team.getTeamName() + 
                        ": Testing location " + formatLocation(candidate) + 
                        " (usable radius: " + String.format("%.1f", usableRadius) + ")");
            }
            
            // Validate location
            if (isLocationValid(candidate)) {
                return candidate;
            }
        }
        
        // Log failure details
        UHC.getInstance().getLogger().warning("Failed to find location for team " + team.getTeamName() + 
                " after " + attempt.attempts + " attempts. Border radius: " + borderRadius + 
                ", buffer: " + bufferFromBorder);
        
        return null;
    }
    
    private boolean isLocationValid(Location location) {
        // Check if location is safe
        if (!GameUtil.isLocationSafe(location)) {
            if (scatterAttempts.values().stream().mapToInt(a -> a.attempts).sum() <= 20) {
                UHC.getInstance().getLogger().info("Location " + formatLocation(location) + " is not safe");
            }
            return false;
        }
        
        // Check distance from border
        double distanceFromCenter = Math.max(
            Math.abs(location.getX()),
            Math.abs(location.getZ())
        );
        if (distanceFromCenter > borderRadius - bufferFromBorder) {
            if (scatterAttempts.values().stream().mapToInt(a -> a.attempts).sum() <= 20) {
                UHC.getInstance().getLogger().fine("Location " + formatLocation(location) + 
                        " too close to border (distance: " + String.format("%.1f", distanceFromCenter) + 
                        ", max: " + String.format("%.1f", borderRadius - bufferFromBorder) + ")");
            }
            return false;
        }
        
        // Check distance from other teams
        for (Location existingLocation : teamScatterLocations.values()) {
            double distance = location.distance(existingLocation);
            if (distance < MIN_DISTANCE_BETWEEN_TEAMS) {
                if (scatterAttempts.values().stream().mapToInt(a -> a.attempts).sum() <= 20) {
                    UHC.getInstance().getLogger().fine("Location " + formatLocation(location) + 
                            " too close to another team (distance: " + String.format("%.1f", distance) + 
                            ", min: " + MIN_DISTANCE_BETWEEN_TEAMS + ")");
                }
                return false;
            }
        }
        
        // Check distance from online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && 
                player.getLocation().distance(location) < MIN_DISTANCE_FROM_PLAYERS) {
                if (scatterAttempts.values().stream().mapToInt(a -> a.attempts).sum() <= 20) {
                    UHC.getInstance().getLogger().fine("Location " + formatLocation(location) + 
                            " too close to player " + player.getName() + " (distance: " + 
                            String.format("%.1f", player.getLocation().distance(location)) + 
                            ", min: " + MIN_DISTANCE_FROM_PLAYERS + ")");
                }
                return false;
            }
        }
        
        return true;
    }
    
    private void addChunksToPreload(Location location) {
        Chunk centerChunk = location.getChunk();
        
        // Add chunks in radius around location
        for (int dx = -CHUNK_PRELOAD_RADIUS; dx <= CHUNK_PRELOAD_RADIUS; dx++) {
            for (int dz = -CHUNK_PRELOAD_RADIUS; dz <= CHUNK_PRELOAD_RADIUS; dz++) {
                chunksToPreload.add(new ChunkCoordinate(
                    centerChunk.getX() + dx,
                    centerChunk.getZ() + dz
                ));
            }
        }
    }
    
    private void preloadChunks() {
        int chunksLoadedThisTick = 0;
        
        while (chunkIterator.hasNext() && chunksLoadedThisTick < CHUNKS_PER_TICK) {
            ChunkCoordinate coord = chunkIterator.next();
            
            if (!preloadedChunks.contains(coord)) {
                // Force load chunk
                world.getChunkAt(coord.x, coord.z).load(true);
                preloadedChunks.add(coord);
                chunksLoadedThisTick++;
            }
        }
        
        // Progress update
        int progress = (int) ((double) preloadedChunks.size() / totalChunksToPreload * 100);
        if (preloadedChunks.size() % 10 == 0) {
            UHC.getInstance().getLogger().info("Chunk preloading: " + progress + "% complete (" + 
                    preloadedChunks.size() + "/" + totalChunksToPreload + ")");
        }
        
        // Check if done
        if (!chunkIterator.hasNext()) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Chunks preloaded. Starting teleportation...");
            currentPhase = ScatterPhase.TELEPORTING_TEAMS;
            currentTeamIndex = 0;
        }
    }
    
    private void teleportTeams() {
        if (currentTeamIndex >= teamsToScatter.size()) {
            currentPhase = ScatterPhase.COMPLETED;
            return;
        }
        
        int teleportsThisTick = 0;
        
        while (currentTeamIndex < teamsToScatter.size() && teleportsThisTick < TELEPORTS_PER_TICK) {
            UHCTeam team = teamsToScatter.get(currentTeamIndex);
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
            
            if (attempt.successful && attempt.finalLocation != null) {
                scatterTeamMembers(team, attempt.finalLocation);
                UHC.getInstance().getLogger().info("Teleported team " + team.getTeamName());
            } else {
                UHC.getInstance().getLogger().warning("Skipping team " + team.getTeamName() + 
                        " - no valid location");
            }
            
            currentTeamIndex++;
            teleportsThisTick++;
        }
        
        // Progress update
        if (currentTeamIndex % 5 == 0) {
            double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Teleporting teams: " + 
                    String.format("%.0f%%", progress) + " complete");
        }
    }
    
    private void scatterTeamMembers(UHCTeam team, Location teamCenter) {
        List<Player> members = team.getOnlineMembers();
        
        if (members.isEmpty()) {
            return;
        }
        
        if (members.size() == 1) {
            // Solo team
            Player player = members.get(0);
            player.teleport(teamCenter);
            
            // Freeze player after teleport
            new BukkitRunnable() {
                @Override
                public void run() {
                    freezeManager.freezePlayer(player);
                    player.sendMessage(ChatColor.GREEN + "You have been scattered!");
                    player.sendMessage(ChatColor.YELLOW + "You are now frozen until all teams are scattered.");
                }
            }.runTaskLater(UHC.getInstance(), 2L); // Small delay to ensure teleport completes
        } else {
            // Multiple members - scatter around center
            List<Location> memberLocations = generateTeamMemberLocations(teamCenter, members.size());
            
            for (int i = 0; i < members.size(); i++) {
                Player player = members.get(i);
                Location loc = i < memberLocations.size() ? memberLocations.get(i) : teamCenter;
                player.teleport(loc);
                
                // Freeze each player after teleport
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        freezeManager.freezePlayer(player);
                        player.sendMessage(ChatColor.GREEN + "You have been scattered with your team!");
                        player.sendMessage(ChatColor.YELLOW + "You are now frozen until all teams are scattered.");
                    }
                }.runTaskLater(UHC.getInstance(), 2L); // Small delay to ensure teleport completes
            }
            
            team.sendMessage(ChatColor.YELLOW + "Your team has been scattered together!");
        }
    }
    
    private List<Location> generateTeamMemberLocations(Location center, int count) {
        List<Location> locations = new ArrayList<>();
        locations.add(center); // Team leader at center
        
        if (count <= 1) {
            return locations;
        }
        
        Random random = new Random();
        double angleStep = 2 * Math.PI / (count - 1);
        
        for (int i = 1; i < count; i++) {
            double angle = angleStep * (i - 1);
            double distance = 5 + random.nextDouble() * (MAX_TEAM_SPREAD - 5);
            
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);
            
            Location memberLoc = new Location(world, x, 0, z);
            memberLoc.setY(world.getHighestBlockYAt(memberLoc) + 1);
            
            // Ensure location is safe
            if (GameUtil.isLocationSafe(memberLoc)) {
                locations.add(memberLoc);
            } else {
                // Try closer to center
                distance = 3 + random.nextDouble() * 5;
                x = center.getX() + distance * Math.cos(angle);
                z = center.getZ() + distance * Math.sin(angle);
                memberLoc = new Location(world, x, 0, z);
                memberLoc.setY(world.getHighestBlockYAt(memberLoc) + 1);
                locations.add(memberLoc);
            }
        }
        
        return locations;
    }
    
    private void complete() {
        long duration = System.currentTimeMillis() - startTime;
        int successful = (int) scatterAttempts.values().stream()
                .filter(a -> a.successful)
                .count();
        
        Bukkit.broadcastMessage(ChatColor.GREEN + "=== Scatter Complete ===");
        Bukkit.broadcastMessage(ChatColor.GREEN + "Teams scattered: " + successful + "/" + teamsToScatter.size());
        Bukkit.broadcastMessage(ChatColor.GREEN + "Time taken: " + (duration / 1000.0) + " seconds");
        Bukkit.broadcastMessage(ChatColor.GREEN + "=======================");
        
        UHC.getInstance().getLogger().info("Scatter completed in " + duration + "ms");
        logScatterStatistics();
        
        // Countdown before unfreezing
        new BukkitRunnable() {
            int countdown = 3;
            
            @Override
            public void run() {
                if (countdown > 0) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "§lUnfreezing in " + countdown + "...");
                    
                    // Play sound for all players
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.NOTE_PLING, 1.0f, 1.0f);
                    }
                    
                    countdown--;
                } else {
                    // Unfreeze all players
                    freezeManager.unfreezeAllPlayers();
                    
                    Bukkit.broadcastMessage(ChatColor.GREEN + "§l§m                                                ");
                    Bukkit.broadcastMessage(ChatColor.GREEN + "§lGAME STARTED!");
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Good luck and have fun!");
                    Bukkit.broadcastMessage(ChatColor.GREEN + "§l§m                                                ");
                    
                    // Play final sound
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.playSound(player.getLocation(), Sound.LEVEL_UP, 1.0f, 1.0f);
                    }
                    
                    // Notify game
                    if (game != null) {
                        game.onScatterCompleted(getScatterStatistics());
                    }
                    
                    // Start game
                    game.startGame();
                    
                    // Cancel this countdown task
                    cancel();
                }
            }
        }.runTaskTimer(UHC.getInstance(), 20L, 20L); // Start after 1 second, run every second
        
        // Cancel the main scatter task
        cancel();
    }
    
    private void handleFailure() {
        Bukkit.broadcastMessage(ChatColor.RED + "Scatter failed! Starting game without scattering...");
        
        // Unfreeze players since scatter failed
        freezeManager.unfreezeAllPlayers();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                game.startGame();
            }
        }.runTaskLater(UHC.getInstance(), 60L);
        
        cancel();
    }
    
    private void handleError(String error) {
        UHC.getInstance().getLogger().severe("Scatter error: " + error);
        currentPhase = ScatterPhase.FAILED;
    }
    
    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
    
    private void logScatterStatistics() {
        UHC.getInstance().getLogger().info("=== Scatter Statistics ===");
        
        // Distance analysis
        List<Double> distances = new ArrayList<>();
        List<Location> locations = new ArrayList<>(teamScatterLocations.values());
        
        for (int i = 0; i < locations.size(); i++) {
            for (int j = i + 1; j < locations.size(); j++) {
                distances.add(locations.get(i).distance(locations.get(j)));
            }
        }
        
        if (!distances.isEmpty()) {
            double minDist = distances.stream().min(Double::compare).orElse(0.0);
            double maxDist = distances.stream().max(Double::compare).orElse(0.0);
            double avgDist = distances.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            
            UHC.getInstance().getLogger().info("Team distances - Min: " + String.format("%.1f", minDist) +
                    ", Max: " + String.format("%.1f", maxDist) +
                    ", Avg: " + String.format("%.1f", avgDist));
        }
        
        // Border usage
        double maxDistFromCenter = locations.stream()
                .mapToDouble(loc -> Math.max(Math.abs(loc.getX()), Math.abs(loc.getZ())))
                .max()
                .orElse(0.0);
        
        UHC.getInstance().getLogger().info("Border usage: " + 
                String.format("%.1f%%", (maxDistFromCenter / borderRadius) * 100));
        UHC.getInstance().getLogger().info("Chunks preloaded: " + preloadedChunks.size());
        UHC.getInstance().getLogger().info("========================");
    }
    
    public void startScattering() {
        this.runTaskTimer(UHC.getInstance(), 0L, 2L); // Run every 2 ticks
    }
    
    @Override
    public void cancel() {
        this.cancelled = true;
        super.cancel();
        
        // Don't clean up freeze manager here - it's handled in the countdown
        // Only clean up if we're cancelling due to error/failure
        if (currentPhase == ScatterPhase.FAILED) {
            if (freezeManager != null) {
                freezeManager.cleanup();
            }
        }
        
        // Keep chunks loaded for a bit longer
        new BukkitRunnable() {
            @Override
            public void run() {
                for (ChunkCoordinate coord : preloadedChunks) {
                    Chunk chunk = world.getChunkAt(coord.x, coord.z);
                    if (chunk.isLoaded() && !chunk.isForceLoaded()) {
                        chunk.unload(true);
                    }
                }
            }
        }.runTaskLater(UHC.getInstance(), 200L); // Unload after 10 seconds
    }
    
    public double getProgress() {
        switch (currentPhase) {
            case INITIALIZING:
                return 0;
            case VALIDATING_TEAMS:
                return 5;
            case GENERATING_LOCATIONS:
                return 5 + (currentTeamIndex / (double) Math.max(1, teamsToScatter.size())) * 30;
            case PRELOADING_CHUNKS:
                return 35 + (preloadedChunks.size() / (double) Math.max(1, totalChunksToPreload)) * 30;
            case TELEPORTING_TEAMS:
                return 65 + (currentTeamIndex / (double) Math.max(1, teamsToScatter.size())) * 35;
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }
    
    public boolean isCancelled() {
        return cancelled;
    }
    
    public String getCurrentPhase() {
        return currentPhase.name();
    }
    
    public ScatterStatistics getScatterStatistics() {
        int successful = (int) scatterAttempts.values().stream()
                .filter(a -> a.successful)
                .count();
        int failed = teamsToScatter.size() - successful;
        int totalAttempts = scatterAttempts.values().stream()
                .mapToInt(a -> a.attempts)
                .sum();
        
        return new ScatterStatistics(
            successful,
            failed,
            totalAttempts,
            failed > 0 ? "See logs for details" : "",
            (int) borderRadius,
            MIN_DISTANCE_BETWEEN_TEAMS
        );
    }
    
    public static class ScatterStatistics {
        public final int successfulTeams;
        public final int failedTeams;
        public final int totalAttempts;
        public final String mostCommonFailureReason;
        public final int usedRadius;
        public final int minDistanceBetweenTeams;
        
        public ScatterStatistics(int successfulTeams, int failedTeams, int totalAttempts,
                                String mostCommonFailureReason, int usedRadius, int minDistanceBetweenTeams) {
            this.successfulTeams = successfulTeams;
            this.failedTeams = failedTeams;
            this.totalAttempts = totalAttempts;
            this.mostCommonFailureReason = mostCommonFailureReason;
            this.usedRadius = usedRadius;
            this.minDistanceBetweenTeams = minDistanceBetweenTeams;
        }
    }
}
package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.team.UHCTeam;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Fixed scatter manager that uses game border settings instead of world border
 */
public class ProgressiveScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private int radius;
    private final List<UHCTeam> teamsToScatter;
    private final Map<UUID, Location> teamScatterLocations = new HashMap<>();
    private final Map<UUID, ScatterAttempt> scatterAttempts = new HashMap<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // Cached game border values - FIXED: Use game values instead of world border
    private final Location borderCenter;
    private final double borderRadius;
    private final double effectiveBorderRadius;

    // State tracking
    @Getter
    private ScatterPhase currentPhase = ScatterPhase.VALIDATING_TEAMS;
    private int currentTeamIndex = 0;
    private Iterator<Chunk> chunkIterator;
    private boolean cancelled = false;

    // Configuration
    private static final int LOCATIONS_PER_TICK = 3;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int TELEPORTS_PER_TICK = 2;
    private static final int MIN_DISTANCE_BETWEEN_TEAMS = 150;
    private static final int MAX_TEAM_SPREAD = 25;
    private static final int MAX_ATTEMPTS_PER_LOCATION = 50;
    private static final int SAFETY_BUFFER_FROM_BORDER = 100;
    private static final int FALLBACK_ATTEMPTS = 10;

    public enum ScatterPhase {
        VALIDATING_TEAMS,
        GENERATING_LOCATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_TEAMS,
        HANDLING_FAILURES,
        COMPLETED
    }

    private static class ScatterAttempt {
        int attempts = 0;
        boolean successful = false;
        String failureReason = "";
        Location lastAttemptLocation = null;
    }

    public ProgressiveScatterManager(Game game, int requestedRadius) {
        this.game = game;
        this.world = game.getWorld();

        // FIXED: Use game's border configuration instead of world border
        this.borderCenter = new Location(world, 0, 0, 0); // UHC always uses 0,0 as center
        this.borderRadius = game.getInitialBorderSize() / 2.0; // Use game's initial border size
        this.effectiveBorderRadius = borderRadius - SAFETY_BUFFER_FROM_BORDER;

        // Use effective border radius for scattering to utilize full border area
        this.radius = (int) effectiveBorderRadius;

        this.teamsToScatter = getValidTeamsToScatter();

        UHC.getInstance().getLogger().info("Starting scatter for " + teamsToScatter.size() +
                " teams using full border area (game border: " + game.getInitialBorderSize() +
                ", border radius: " + (int)borderRadius + ", area: " + 
                String.format("%.0f", borderRadius * 2 * borderRadius * 2) + " blocks²) in world: " +
                (world != null ? world.getName() : "NULL"));
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        try {
            switch (currentPhase) {
                case VALIDATING_TEAMS:
                    validateTeams();
                    break;
                case GENERATING_LOCATIONS:
                    generateTeamLocations();
                    break;
                case PRELOADING_CHUNKS:
                    preloadChunks();
                    break;
                case TELEPORTING_TEAMS:
                    teleportTeams();
                    break;
                case HANDLING_FAILURES:
                    handleScatterFailures();
                    break;
                case COMPLETED:
                    complete();
                    break;
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error in scatter manager: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Scatter manager error", e);
            handleError("Scatter error: " + e.getMessage());
        }
    }

    private void validateTeams() {
        if (world == null) {
            handleError("World is null!");
            return;
        }

        if (!game.isWorldReady()) {
            handleError("World is not ready!");
            return;
        }

        if (teamsToScatter.isEmpty()) {
            handleError("No valid teams to scatter!");
            return;
        }

        // FIXED: Validate using game border settings
        UHC.getInstance().getLogger().info("Border validation: Game border size = " + game.getInitialBorderSize() +
                ", Border radius = " + (int)borderRadius +
                ", Effective radius = " + (int)effectiveBorderRadius +
                ", Using full border area for scattering");

        if (effectiveBorderRadius < 100) {
            UHC.getInstance().getLogger().warning("Game border (" + game.getInitialBorderSize() +
                    ") is very small for effective scattering (effective radius: " + (int)effectiveBorderRadius + ")");
        }

        // Initialize scatter attempts tracking
        for (UHCTeam team : teamsToScatter) {
            scatterAttempts.put(team.getTeamId(), new ScatterAttempt());
        }

        currentPhase = ScatterPhase.GENERATING_LOCATIONS;
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Teams validated. Generating scatter locations...");
        UHC.getInstance().getLogger().info("Team validation completed. Found " + teamsToScatter.size() +
                " valid teams to scatter using full border area (border radius: " + (int)borderRadius + 
                ", area: " + String.format("%.0f", borderRadius * 2 * borderRadius * 2) + " blocks²)");
    }

    private void generateTeamLocations() {
        int locationsGenerated = 0;
        Random random = new Random();

        while (currentTeamIndex < teamsToScatter.size() && locationsGenerated < LOCATIONS_PER_TICK) {
            UHCTeam team = teamsToScatter.get(currentTeamIndex);
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());

            Location teamLocation = findValidTeamLocation(random, team, attempt);

            if (teamLocation != null) {
                teamScatterLocations.put(team.getTeamId(), teamLocation);
                chunksToPreload.add(teamLocation.getChunk());
                addSurroundingChunks(teamLocation);
                attempt.successful = true;

                UHC.getInstance().getLogger().info("Generated location for team: " + team.getTeamName() +
                        " at " + formatLocation(teamLocation) + " (attempt " + attempt.attempts + ")");
            } else {
                UHC.getInstance().getLogger().warning("Failed to find scatter location for team: " +
                        team.getTeamName() + " after " + attempt.attempts + " attempts. Reason: " + attempt.failureReason);
            }

            currentTeamIndex++;
            locationsGenerated++;

            // Progress update
            if (currentTeamIndex % Math.max(1, teamsToScatter.size() / 4) == 0) {
                double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Generating team locations: " + (int)progress + "% complete");
            }
        }

        // Check if done generating
        if (currentTeamIndex >= teamsToScatter.size()) {
            int successfulLocations = teamScatterLocations.size();
            int failedTeams = teamsToScatter.size() - successfulLocations;

            if (failedTeams > 0) {
                UHC.getInstance().getLogger().warning("Failed to generate locations for " + failedTeams + " teams");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Generated " + successfulLocations + "/" +
                        teamsToScatter.size() + " team locations. Handling failures...");
                currentPhase = ScatterPhase.HANDLING_FAILURES;
            } else {
                currentPhase = ScatterPhase.PRELOADING_CHUNKS;
                chunkIterator = chunksToPreload.iterator();
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Generated all " + successfulLocations +
                        " team locations. Preloading chunks...");
            }

            currentTeamIndex = 0; // Reset for next phase
        }
    }

    private void handleScatterFailures() {
        List<UHCTeam> failedTeams = new ArrayList<>();

        for (UHCTeam team : teamsToScatter) {
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
            if (!attempt.successful) {
                failedTeams.add(team);
            }
        }

        if (failedTeams.isEmpty()) {
            // All teams now have locations, proceed
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentTeamIndex = 0;
            return;
        }

        // Try fallback strategies for failed teams
        Random random = new Random();
        boolean anySuccess = false;

        for (UHCTeam team : failedTeams) {
            if (currentTeamIndex >= FALLBACK_ATTEMPTS) break;

            Location fallbackLocation = tryFallbackScatterStrategies(team, random);
            if (fallbackLocation != null) {
                teamScatterLocations.put(team.getTeamId(), fallbackLocation);
                chunksToPreload.add(fallbackLocation.getChunk());
                addSurroundingChunks(fallbackLocation);
                scatterAttempts.get(team.getTeamId()).successful = true;
                anySuccess = true;

                UHC.getInstance().getLogger().info("Fallback scatter successful for team: " + team.getTeamName());
            }

            currentTeamIndex++;
        }

        if (anySuccess || currentTeamIndex >= failedTeams.size()) {
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentTeamIndex = 0;

            int stillFailed = 0;
            for (UHCTeam team : teamsToScatter) {
                if (!scatterAttempts.get(team.getTeamId()).successful) {
                    stillFailed++;
                }
            }

            if (stillFailed > 0) {
                UHC.getInstance().getLogger().warning("Still failed to scatter " + stillFailed + " teams after fallback attempts");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Warning: " + stillFailed +
                        " teams could not be scattered and will remain at spawn");
            }
        }
    }

    private Location tryFallbackScatterStrategies(UHCTeam team, Random random) {
        // Strategy 1: Try closer to center with reduced distance requirements
        Location centerAttempt = findLocationNearCenter(random, 50);
        if (centerAttempt != null) {
            UHC.getInstance().getLogger().info("Fallback strategy 1 (center) worked for: " + team.getTeamName());
            return centerAttempt;
        }

        // Strategy 2: Try with reduced distance requirements
        Location reducedDistanceAttempt = findLocationWithReducedRequirements(random);
        if (reducedDistanceAttempt != null) {
            UHC.getInstance().getLogger().info("Fallback strategy 2 (reduced distance) worked for: " + team.getTeamName());
            return reducedDistanceAttempt;
        }

        // Strategy 3: Try systematic grid approach
        Location gridAttempt = findLocationOnGrid(team.getTeamId());
        if (gridAttempt != null) {
            UHC.getInstance().getLogger().info("Fallback strategy 3 (grid) worked for: " + team.getTeamName());
            return gridAttempt;
        }

        // Strategy 4: Last resort - place near spawn with offset
        Location spawnOffset = createSpawnOffsetLocation(team.getTeamId(), random);
        UHC.getInstance().getLogger().warning("Using last resort spawn offset for: " + team.getTeamName());
        return spawnOffset;
    }

    private Location findLocationNearCenter(Random random, int maxRadius) {
        for (int attempt = 0; attempt < 20; attempt++) {
            // Use a smaller area near center for fallback
            double actualMaxRadius = Math.min(maxRadius, borderRadius / 2);
            
            // Use circular distribution
            double angle = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(random.nextDouble()) * actualMaxRadius;
            
            int x = (int) (borderCenter.getX() + r * Math.cos(angle));
            int z = (int) (borderCenter.getZ() + r * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);
            candidate.setY(groundY + 1);

            if (GameUtil.isLocationSafe(candidate) && isLocationValidWithReducedDistance(candidate, 50)) {
                return candidate;
            }
        }
        return null;
    }

    private Location findLocationWithReducedRequirements(Random random) {
        for (int attempt = 0; attempt < 30; attempt++) {
            // Use circular distribution for the full border area
            double angle = random.nextDouble() * 2 * Math.PI;
            double r = Math.sqrt(random.nextDouble()) * borderRadius;
            
            int x = (int) (borderCenter.getX() + r * Math.cos(angle));
            int z = (int) (borderCenter.getZ() + r * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);
            candidate.setY(groundY + 1);

            if (GameUtil.isLocationSafe(candidate) && isLocationValidWithReducedDistance(candidate, 60)) {
                return candidate;
            }
        }
        return null;
    }

    private Location findLocationOnGrid(UUID teamId) {
        // Create a circular pattern based on team ID hash
        int hash = Math.abs(teamId.hashCode());
        
        // Use hash to determine position in a circular pattern
        double baseAngle = (hash % 360) * Math.PI / 180; // Convert to radians
        double baseRadius = borderRadius * 0.3 + (hash % 100) * (borderRadius * 0.6 / 100); // 30% to 90% of border radius

        // Try multiple positions around the base position
        for (int i = 0; i < 8; i++) {
            double angleOffset = i * (Math.PI / 4); // Try 8 positions in 45-degree increments
            double angle = baseAngle + angleOffset;
            
            for (double radiusMultiplier = 1.0; radiusMultiplier >= 0.5; radiusMultiplier -= 0.25) {
                double r = baseRadius * radiusMultiplier;
                
                int x = (int) (borderCenter.getX() + r * Math.cos(angle));
                int z = (int) (borderCenter.getZ() + r * Math.sin(angle));

                Location candidate = new Location(world, x + 0.5, 0, z + 0.5);

                // Check if within game border
                if (Math.abs(x - borderCenter.getX()) > borderRadius ||
                        Math.abs(z - borderCenter.getZ()) > borderRadius) {
                    continue;
                }

                int groundY = world.getHighestBlockYAt(candidate);
                candidate.setY(groundY + 1);

                if (GameUtil.isLocationSafe(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Location createSpawnOffsetLocation(UUID teamId, Random random) {
        // Create deterministic but varied offset based on team ID
        int hash = Math.abs(teamId.hashCode());
        double angle = (hash % 360) * Math.PI / 180; // Convert to radians
        
        // Try to use more of the border area for last resort placement
        // Start from outer edge and work inward
        double maxDistance = borderRadius * 0.8; // 80% of border radius
        double minDistance = 100; // Minimum 100 blocks from center
        
        // Try different distances starting from far out
        for (double distanceMultiplier = 1.0; distanceMultiplier >= 0.2; distanceMultiplier -= 0.2) {
            double distance = minDistance + (maxDistance - minDistance) * distanceMultiplier;
            
            int x = (int) (borderCenter.getX() + distance * Math.cos(angle));
            int z = (int) (borderCenter.getZ() + distance * Math.sin(angle));

            // Check if within border
            if (Math.abs(x - borderCenter.getX()) > borderRadius ||
                    Math.abs(z - borderCenter.getZ()) > borderRadius) {
                continue;
            }

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);
            candidate.setY(groundY + 1);

            // Make it safe if it isn't
            if (!GameUtil.isLocationSafe(candidate)) {
                // Clear area around the location
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 0; dy <= 2; dy++) {
                            world.getBlockAt(x + dx, groundY + dy, z + dz).setType(Material.AIR);
                        }
                    }
                }
                // Ensure solid ground
                world.getBlockAt(x, groundY - 1, z).setType(Material.STONE);
            }

            return candidate;
        }
        
        // Ultimate fallback - near spawn
        Location spawn = world.getSpawnLocation();
        double spawnDistance = 50 + (hash % 50); // 50-100 blocks from spawn
        int x = (int) (spawn.getX() + spawnDistance * Math.cos(angle));
        int z = (int) (spawn.getZ() + spawnDistance * Math.sin(angle));
        
        Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
        int groundY = world.getHighestBlockYAt(candidate);
        candidate.setY(groundY + 1);
        
        return candidate;
    }

    private boolean isLocationValidWithReducedDistance(Location location, int minDistance) {
        // Check distance from other team locations with reduced requirement
        for (Location existingLocation : teamScatterLocations.values()) {
            if (location.distance(existingLocation) < minDistance) {
                return false;
            }
        }

        // FIXED: Check if within game border instead of world border
        double deltaX = Math.abs(location.getX() - borderCenter.getX());
        double deltaZ = Math.abs(location.getZ() - borderCenter.getZ());

        return deltaX <= effectiveBorderRadius && deltaZ <= effectiveBorderRadius;
    }

    private Location findValidTeamLocation(Random random, UHCTeam team, ScatterAttempt attempt) {
        // Strategy 1: Try improved random distribution
        for (int i = 0; i < MAX_ATTEMPTS_PER_LOCATION / 2 && attempt.attempts < MAX_ATTEMPTS_PER_LOCATION; i++) {
            attempt.attempts++;

            Location candidate = generateRandomLocation(random);
            if (candidate == null) {
                attempt.failureReason = "Could not generate random location";
                continue;
            }

            if (!GameUtil.isLocationSafe(candidate)) {
                attempt.failureReason = "Location not safe";
                attempt.lastAttemptLocation = candidate;
                continue;
            }

            if (!isValidTeamLocation(candidate)) {
                attempt.failureReason = "Location conflicts with other teams or border";
                attempt.lastAttemptLocation = candidate;
                continue;
            }

            return candidate;
        }

        // Strategy 2: Try grid-based approach if random failed
        int teamIndex = new ArrayList<>(teamsToScatter).indexOf(team);
        if (teamIndex >= 0) {
            Location gridCandidate = generateGridBasedLocation(random, teamIndex, teamsToScatter.size());

            if (gridCandidate != null && GameUtil.isLocationSafe(gridCandidate) && isValidTeamLocation(gridCandidate)) {
                UHC.getInstance().getLogger().info("Found valid location using grid strategy for team: " + team.getTeamName());
                return gridCandidate;
            } else {
                attempt.failureReason = "Grid-based location also failed";
            }
        }

        // Strategy 3: Try with reduced distance requirements
        for (int i = 0; i < MAX_ATTEMPTS_PER_LOCATION / 4 && attempt.attempts < MAX_ATTEMPTS_PER_LOCATION; i++) {
            attempt.attempts++;

            Location candidate = generateRandomLocation(random);
            if (candidate != null && GameUtil.isLocationSafe(candidate) &&
                    isLocationValidWithReducedDistance(candidate, 60)) {
                UHC.getInstance().getLogger().info("Found valid location using reduced distance for team: " + team.getTeamName());
                return candidate;
            }
        }

        return null;
    }

    private Location generateRandomLocation(Random random) {
        try {
            // Use the full border radius for scattering
            // Generate random coordinates within the circular border area
            double angle = random.nextDouble() * 2 * Math.PI; // Random angle 0 to 2π
            // Use sqrt for uniform distribution in circular area
            double r = Math.sqrt(random.nextDouble()) * borderRadius;
            
            int x = (int) (borderCenter.getX() + r * Math.cos(angle));
            int z = (int) (borderCenter.getZ() + r * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);

            // Ensure reasonable Y coordinate
            if (groundY < 1) groundY = 64;
            if (groundY > 200) groundY = 100;

            candidate.setY(groundY + 1);
            return candidate;
        } catch (Exception e) {
            UHC.getInstance().getLogger().warning("Error generating random location: " + e.getMessage());
            return null;
        }
    }

    private boolean isValidTeamLocation(Location location) {
        // Check if location is safe
        if (!GameUtil.isLocationSafe(location)) {
            return false;
        }

        // Check distance from other team locations
        for (Location existingLocation : teamScatterLocations.values()) {
            double distance = location.distance(existingLocation);
            if (distance < MIN_DISTANCE_BETWEEN_TEAMS) {
                return false;
            }
        }

        // FIXED: Use game border center and radius instead of world border
        double deltaX = Math.abs(location.getX() - borderCenter.getX());
        double deltaZ = Math.abs(location.getZ() - borderCenter.getZ());

        boolean withinBorder = deltaX <= effectiveBorderRadius && deltaZ <= effectiveBorderRadius;

        if (!withinBorder) {
            UHC.getInstance().getLogger().fine("Location outside game border: deltaX=" +
                    String.format("%.1f", deltaX) + ", deltaZ=" + String.format("%.1f", deltaZ) +
                    ", effectiveRadius=" + String.format("%.1f", effectiveBorderRadius) +
                    " (game border: " + game.getInitialBorderSize() + ")");
        }

        return withinBorder;
    }

    private Location generateGridBasedLocation(Random random, int teamIndex, int totalTeams) {
        try {
            // Use game border center
            double centerX = borderCenter.getX();
            double centerZ = borderCenter.getZ();

            // Create a circular grid pattern that utilizes the full border area
            // Use a spiral pattern for better distribution
            double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0)); // Golden angle in radians
            double angle = teamIndex * goldenAngle;
            
            // Use Fermat's spiral for even distribution
            // Distance increases with square root to maintain uniform density
            double maxRadius = borderRadius * 0.9; // Use 90% of border to ensure we stay within bounds
            double r = maxRadius * Math.sqrt((double)teamIndex / totalTeams);
            
            // Add some randomness to avoid perfect spiral
            double angleOffset = (random.nextDouble() - 0.5) * 0.3; // +/- 0.15 radians
            double radiusOffset = (random.nextDouble() - 0.5) * 20; // +/- 10 blocks
            
            double finalAngle = angle + angleOffset;
            double finalRadius = Math.max(10, r + radiusOffset); // Ensure minimum 10 blocks from center
            
            int x = (int) (centerX + finalRadius * Math.cos(finalAngle));
            int z = (int) (centerZ + finalRadius * Math.sin(finalAngle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);

            if (groundY < 1) groundY = 64;
            if (groundY > 200) groundY = 100;

            candidate.setY(groundY + 1);

            return candidate;

        } catch (Exception e) {
            UHC.getInstance().getLogger().warning("Error generating grid-based location: " + e.getMessage());
            return null;
        }
    }

    private void preloadChunks() {
        int chunksLoaded = 0;

        while (chunkIterator.hasNext() && chunksLoaded < CHUNKS_PER_TICK) {
            Chunk chunk = chunkIterator.next();

            try {
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
                chunksLoaded++;
            } catch (Exception e) {
                UHC.getInstance().getLogger().warning("Failed to load chunk: " + e.getMessage());
                chunksLoaded++;
            }
        }

        if (!chunkIterator.hasNext()) {
            currentPhase = ScatterPhase.TELEPORTING_TEAMS;
            currentTeamIndex = 0;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Chunks preloaded. Starting team teleportation...");
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

            if (attempt.successful) {
                Location teamLocation = teamScatterLocations.get(team.getTeamId());
                if (teamLocation != null) {
                    int scatteredMembers = scatterTeamMembers(team, teamLocation);
                    UHC.getInstance().getLogger().info("Scattered team " + team.getTeamName() + ": " +
                            scatteredMembers + " members at " + formatLocation(teamLocation));
                }
            } else {
                // Team failed to scatter, keep at spawn or use fallback
                List<Player> onlineMembers = team.getOnlineMembers();
                for (Player player : onlineMembers) {
                    player.sendMessage(ChatColor.RED + "Your team could not be scattered and will remain near spawn!");
                }
                UHC.getInstance().getLogger().warning("Team " + team.getTeamName() +
                        " could not be scattered: " + attempt.failureReason);
            }

            currentTeamIndex++;
            teleportsThisTick++;

            // Progress update
            if (currentTeamIndex % Math.max(1, teamsToScatter.size() / 4) == 0) {
                double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Teleporting teams: " + (int)progress + "% complete");
            }
        }
    }

    private void complete() {
        int successfulScatters = teamScatterLocations.size();
        int totalTeams = teamsToScatter.size();
        int failedScatters = totalTeams - successfulScatters;

        if (failedScatters > 0) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Scattering completed with issues! " +
                    successfulScatters + "/" + totalTeams + " teams scattered successfully.");
            Bukkit.broadcastMessage(ChatColor.RED + "" + failedScatters + " teams could not be scattered and remain near spawn.");
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Scattering completed successfully! " +
                    successfulScatters + "/" + totalTeams + " teams scattered.");
        }

        UHC.getInstance().getLogger().info("Scatter completed: " + successfulScatters + "/" + totalTeams +
                " successful, " + failedScatters + " failed");

        // Log scatter distribution for debugging
        logScatterDistribution();

        // Report statistics to game
        if (game != null) {
            game.onScatterCompleted(getScatterStatistics());
        }

        // Start the game after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                game.startGame();
            }
        }.runTaskLater(UHC.getInstance(), 40L); // 2 second delay

        cancel();
    }

    private void handleError(String error) {
        UHC.getInstance().getLogger().severe("Scatter failed: " + error);
        Bukkit.broadcastMessage(ChatColor.RED + "Scatter failed: " + error);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Starting game without scattering...");

        // Start game anyway
        new BukkitRunnable() {
            @Override
            public void run() {
                game.startGame();
            }
        }.runTaskLater(UHC.getInstance(), 60L);

        cancel();
    }

    private List<UHCTeam> getValidTeamsToScatter() {
        List<UHCTeam> teams = new ArrayList<>();

        for (UHCTeam team : game.getTeamManager().getAllTeams()) {
            if (team.getSize() > 0 && hasOnlineMembers(team)) {
                teams.add(team);
            } else {
                UHC.getInstance().getLogger().info("Skipping team with no online members: " + team.getTeamName());
            }
        }

        return teams;
    }

    private boolean hasOnlineMembers(UHCTeam team) {
        return !team.getOnlineMembers().isEmpty();
    }

    private int scatterTeamMembers(UHCTeam team, Location teamCenter) {
        List<Player> onlineMembers = team.getOnlineMembers();
        if (onlineMembers.isEmpty()) {
            return 0;
        }

        if (onlineMembers.size() == 1 || game.isSoloMode()) {
            // Single player (solo team), just teleport to team center
            Player player = onlineMembers.get(0);
            player.teleport(teamCenter);
            player.sendMessage(ChatColor.GREEN + "You have been scattered!");
            return 1;
        }

        // Multiple players - scatter them around the team center
        List<Location> memberLocations = generateTeamMemberLocations(teamCenter, onlineMembers.size());
        int membersScattered = 0;

        for (int i = 0; i < onlineMembers.size(); i++) {
            Player player = onlineMembers.get(i);
            Location memberLocation;

            if (i < memberLocations.size()) {
                memberLocation = memberLocations.get(i);
            } else {
                memberLocation = teamCenter;
            }

            player.teleport(memberLocation);
            player.sendMessage(ChatColor.GREEN + "You have been scattered with your team " + team.getFormattedName());
            membersScattered++;
        }

        // Send team message
        team.sendMessage(ChatColor.YELLOW + "Your team has been scattered! " + membersScattered + " members teleported.");
        return membersScattered;
    }

    /**
     * Generate safe locations for team members around their team center
     */
    private List<Location> generateTeamMemberLocations(Location teamCenter, int memberCount) {
        List<Location> locations = new ArrayList<>();
        Random random = new Random();

        // Always add the team center as the first location (for team leader)
        locations.add(teamCenter.clone());

        // If only one member, return just the center
        if (memberCount <= 1) {
            return locations;
        }

        // Generate locations in a circle pattern around the team center
        for (int i = 1; i < memberCount; i++) {
            Location memberLocation = findSafeLocationAroundCenter(teamCenter, i, memberCount, random);

            if (memberLocation != null) {
                locations.add(memberLocation);
            } else {
                // Fallback to team center if we can't find a safe nearby location
                locations.add(teamCenter.clone());
                UHC.getInstance().getLogger().info("Could not find safe location for team member " + (i + 1) +
                        ", using team center as fallback");
            }
        }

        return locations;
    }

    /**
     * Find a safe location around the team center for a specific team member
     */
    private Location findSafeLocationAroundCenter(Location center, int memberIndex, int totalMembers, Random random) {
        // Strategy 1: Systematic circle placement
        if (memberIndex <= 8) {
            double angle = (memberIndex * 45.0) * Math.PI / 180.0; // 8 directions around circle
            double distance = 8 + random.nextDouble() * 12; // 8-20 blocks from center

            Location systematic = calculateLocationAtAngleAndDistance(center, angle, distance);
            if (systematic != null && GameUtil.isLocationSafe(systematic)) {
                return systematic;
            }
        }

        // Strategy 2: Random placement in expanding rings
        for (int ring = 1; ring <= 3; ring++) { // Try 3 rings around center
            for (int attempt = 0; attempt < 8; attempt++) { // 8 attempts per ring
                double angle = random.nextDouble() * 2 * Math.PI;
                double minDistance = ring * 8; // 8, 16, 24 blocks
                double maxDistance = minDistance + 8;
                double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);

                Location candidate = calculateLocationAtAngleAndDistance(center, angle, distance);
                if (candidate != null && GameUtil.isLocationSafe(candidate)) {
                    return candidate;
                }
            }
        }

        // Strategy 3: Try nearby safe spots with reduced distance
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 5 + random.nextDouble() * 15; // 5-20 blocks

            Location candidate = calculateLocationAtAngleAndDistance(center, angle, distance);
            if (candidate != null && GameUtil.isLocationSafe(candidate)) {
                return candidate;
            }
        }

        // Strategy 4: Grid-based placement as last resort
        int[] xOffsets = {-10, -5, 0, 5, 10, -15, 15, -8, 8, -12, 12};
        int[] zOffsets = {-10, -5, 0, 5, 10, -15, 15, -8, 8, -12, 12};

        for (int xOffset : xOffsets) {
            for (int zOffset : zOffsets) {
                if (xOffset == 0 && zOffset == 0) continue; // Skip center

                Location candidate = center.clone().add(xOffset, 0, zOffset);
                candidate.setY(world.getHighestBlockYAt(candidate) + 1);

                if (GameUtil.isLocationSafe(candidate)) {
                    return candidate;
                }
            }
        }

        return null; // No safe location found
    }

    /**
     * Calculate a location at a specific angle and distance from center
     */
    private Location calculateLocationAtAngleAndDistance(Location center, double angle, double distance) {
        try {
            double x = center.getX() + distance * Math.cos(angle);
            double z = center.getZ() + distance * Math.sin(angle);

            Location candidate = new Location(world, x, 0, z);
            int groundY = world.getHighestBlockYAt(candidate);

            // Ensure reasonable Y coordinate
            if (groundY < 1) groundY = 64;
            if (groundY > 200) groundY = 100;

            candidate.setY(groundY + 1);
            return candidate;
        } catch (Exception e) {
            UHC.getInstance().getLogger().warning("Error calculating location at angle/distance: " + e.getMessage());
            return null;
        }
    }

    private void logScatterDistribution() {
        if (!teamScatterLocations.isEmpty()) {
            UHC.getInstance().getLogger().info("=== Scatter Distribution Analysis ===");

            double totalDistanceFromCenter = 0;
            double minDistance = Double.MAX_VALUE;
            double maxDistance = 0;

            for (Map.Entry<UUID, Location> entry : teamScatterLocations.entrySet()) {
                Location loc = entry.getValue();
                double distanceFromCenter = Math.sqrt(Math.pow(loc.getX() - borderCenter.getX(), 2) +
                        Math.pow(loc.getZ() - borderCenter.getZ(), 2));

                totalDistanceFromCenter += distanceFromCenter;
                minDistance = Math.min(minDistance, distanceFromCenter);
                maxDistance = Math.max(maxDistance, distanceFromCenter);
            }

            double averageDistance = totalDistanceFromCenter / teamScatterLocations.size();

            UHC.getInstance().getLogger().info("Distance from center - Min: " + String.format("%.1f", minDistance) +
                    ", Max: " + String.format("%.1f", maxDistance) +
                    ", Average: " + String.format("%.1f", averageDistance));
            UHC.getInstance().getLogger().info("Game border size: " + game.getInitialBorderSize() +
                    ", Border radius: " + String.format("%.1f", borderRadius) +
                    ", Scatter area: " + String.format("%.0f", borderRadius * 2 * borderRadius * 2) + " blocks²");
            UHC.getInstance().getLogger().info("====================================");
        }
    }

    private void addSurroundingChunks(Location center) {
        Chunk centerChunk = center.getChunk();
        int centerX = centerChunk.getX();
        int centerZ = centerChunk.getZ();

        // Add chunks in a 3x3 area around the team location
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                chunksToPreload.add(world.getChunkAt(x, z));
            }
        }
    }

    private String formatLocation(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void startScattering() {
        this.runTaskTimer(UHC.getInstance(), 0, 2); // Run every 2 ticks
    }

    public double getProgress() {
        switch (currentPhase) {
            case VALIDATING_TEAMS:
                return 5;
            case GENERATING_LOCATIONS:
                return 5 + ((double) currentTeamIndex / teamsToScatter.size() * 25);
            case HANDLING_FAILURES:
                return 30 + ((double) currentTeamIndex / Math.max(1, getFailedTeamCount()) * 15);
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 60;
                int chunksProcessed = chunksToPreload.size() - getChunksRemaining();
                return 45 + ((double) chunksProcessed / chunksToPreload.size() * 15);
            case TELEPORTING_TEAMS:
                return 60 + ((double) currentTeamIndex / teamsToScatter.size() * 40);
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }

    private int getFailedTeamCount() {
        int failed = 0;
        for (UHCTeam team : teamsToScatter) {
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
            if (attempt != null && !attempt.successful) {
                failed++;
            }
        }
        return failed;
    }

    private int getChunksRemaining() {
        if (chunkIterator == null) return 0;
        int remaining = 0;
        Iterator<Chunk> temp = chunksToPreload.iterator();
        while (temp.hasNext()) {
            temp.next();
            remaining++;
        }
        return remaining;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        super.cancel();
        UHC.getInstance().getLogger().info("Scatter manager cancelled");
    }

    public Map<UUID, Location> getTeamScatterLocations() {
        return new HashMap<>(teamScatterLocations);
    }

    public int getSuccessfulScatters() {
        return teamScatterLocations.size();
    }

    public int getTotalTeams() {
        return teamsToScatter.size();
    }

    public String getCurrentPhase() {
        return currentPhase.name();
    }

    public ScatterStatistics getScatterStatistics() {
        int successful = 0;
        int failed = 0;
        int totalAttempts = 0;
        String mostCommonFailure = "";

        Map<String, Integer> failureReasons = new HashMap<>();

        for (ScatterAttempt attempt : scatterAttempts.values()) {
            totalAttempts += attempt.attempts;
            if (attempt.successful) {
                successful++;
            } else {
                failed++;
                failureReasons.merge(attempt.failureReason, 1, Integer::sum);
            }
        }

        if (!failureReasons.isEmpty()) {
            mostCommonFailure = failureReasons.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("");
        }

        return new ScatterStatistics(successful, failed, totalAttempts, mostCommonFailure,
                (int)effectiveBorderRadius, MIN_DISTANCE_BETWEEN_TEAMS);
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
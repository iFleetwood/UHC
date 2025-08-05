package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.team.UHCTeam;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Improved scatter manager with better error handling and fallback systems
 */
public class ProgressiveScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private int radius;
    private final List<UHCTeam> teamsToScatter;
    private final Map<UUID, Location> teamScatterLocations = new HashMap<>();
    private final Map<UUID, ScatterAttempt> scatterAttempts = new HashMap<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // State tracking
    @Getter
    private ScatterPhase currentPhase = ScatterPhase.VALIDATING_TEAMS;
    private int currentTeamIndex = 0;
    private Iterator<Chunk> chunkIterator;
    private boolean cancelled = false;

    // Configuration
    private static final int LOCATIONS_PER_TICK = 3; // Increased from 2
    private static final int CHUNKS_PER_TICK = 2;
    private static final int TELEPORTS_PER_TICK = 2;
    private static final int MIN_DISTANCE_BETWEEN_TEAMS = 100; // Increased from 80
    private static final int MAX_TEAM_SPREAD = 25; // Reduced from 30
    private static final int MAX_ATTEMPTS_PER_LOCATION = 50; // Increased from 30
    private static final int SAFETY_BUFFER_FROM_BORDER = 80; // Increased from 50
    private static final int FALLBACK_ATTEMPTS = 10; // New: fallback attempts per team

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

    public ProgressiveScatterManager(Game game, int radius) {
        this.game = game;
        this.world = game.getWorld();
        this.radius = Math.max(150, radius - SAFETY_BUFFER_FROM_BORDER); // Increased minimum
        this.teamsToScatter = getValidTeamsToScatter();

        UHC.getInstance().getLogger().info("Starting improved scatter for " + teamsToScatter.size() +
                " teams with radius " + this.radius + " in world: " +
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
            UHC.getInstance().getLogger().severe("Error in improved scatter manager: " + e.getMessage());
            e.printStackTrace();
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

        // Validate world border
        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2;

        if (borderSize < radius) {
            UHC.getInstance().getLogger().warning("World border (" + (int)borderSize +
                    ") is smaller than scatter radius (" + radius + "), adjusting...");
            radius = Math.max(100, (int) (borderSize - SAFETY_BUFFER_FROM_BORDER));
        }

        // Initialize scatter attempts tracking
        for (UHCTeam team : teamsToScatter) {
            scatterAttempts.put(team.getTeamId(), new ScatterAttempt());
        }

        currentPhase = ScatterPhase.GENERATING_LOCATIONS;
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Teams validated. Generating scatter locations...");
        UHC.getInstance().getLogger().info("Team validation completed. Found " + teamsToScatter.size() +
                " valid teams to scatter with adjusted radius: " + radius);
    }

    private void generateTeamLocations() {
        int locationsGenerated = 0;
        Random random = new Random();

        while (currentTeamIndex < teamsToScatter.size() && locationsGenerated < LOCATIONS_PER_TICK) {
            UHCTeam team = teamsToScatter.get(currentTeamIndex);
            ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());

            Location teamLocation = findValidTeamLocationWithRetries(random, team, attempt);

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
            if (currentTeamIndex >= FALLBACK_ATTEMPTS) break; // Limit fallback attempts per tick

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
            // Either we fixed some teams or we've tried all fallbacks
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
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * Math.min(maxRadius, radius / 2);

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

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
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

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
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();

        // Create a grid pattern based on team ID hash
        int gridSize = 100;
        int hash = Math.abs(teamId.hashCode());
        int gridX = (hash % 10) - 5; // -5 to 4
        int gridZ = ((hash / 10) % 10) - 5; // -5 to 4

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int x = (int) (center.getX() + (gridX + offsetX) * gridSize);
                int z = (int) (center.getZ() + (gridZ + offsetZ) * gridSize);

                Location candidate = new Location(world, x + 0.5, 0, z + 0.5);

                // Check if within border
                if (Math.abs(x - center.getX()) > radius || Math.abs(z - center.getZ()) > radius) {
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
        Location spawn = world.getSpawnLocation();

        // Create deterministic but varied offset based on team ID
        int hash = Math.abs(teamId.hashCode());
        double angle = (hash % 360) * Math.PI / 180; // Convert to radians
        double distance = 50 + (hash % 100); // 50-150 blocks from spawn

        int x = (int) (spawn.getX() + distance * Math.cos(angle));
        int z = (int) (spawn.getZ() + distance * Math.sin(angle));

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

    private boolean isLocationValidWithReducedDistance(Location location, int minDistance) {
        // Check distance from other team locations with reduced requirement
        for (Location existingLocation : teamScatterLocations.values()) {
            if (location.distance(existingLocation) < minDistance) {
                return false;
            }
        }

        // Check if within world border with safety buffer
        WorldBorder border = world.getWorldBorder();
        Location borderCenter = border.getCenter();
        double borderRadius = border.getSize() / 2 - 30; // Reduced buffer

        double deltaX = Math.abs(location.getX() - borderCenter.getX());
        double deltaZ = Math.abs(location.getZ() - borderCenter.getZ());

        return deltaX <= borderRadius && deltaZ <= borderRadius;
    }

    private Location findValidTeamLocationWithRetries(Random random, UHCTeam team, ScatterAttempt attempt) {
        for (int i = 0; i < MAX_ATTEMPTS_PER_LOCATION && attempt.attempts < MAX_ATTEMPTS_PER_LOCATION; i++) {
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

        return null;
    }

    private Location generateRandomLocation(Random random) {
        try {
            WorldBorder border = world.getWorldBorder();
            Location center = border.getCenter();

            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

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
            if (location.distance(existingLocation) < MIN_DISTANCE_BETWEEN_TEAMS) {
                return false;
            }
        }

        // Check if within world border
        WorldBorder border = world.getWorldBorder();
        Location borderCenter = border.getCenter();
        double borderRadius = border.getSize() / 2 - SAFETY_BUFFER_FROM_BORDER;

        double deltaX = Math.abs(location.getX() - borderCenter.getX());
        double deltaZ = Math.abs(location.getZ() - borderCenter.getZ());

        return deltaX <= borderRadius && deltaZ <= borderRadius;
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
                } else {
                    UHC.getInstance().getLogger().warning("Team " + team.getTeamName() +
                            " marked successful but has no location!");
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
            currentTeamIndex = 0; // Reset for teleporting
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Chunks preloaded. Starting team teleportation...");
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

        // Log failed teams for debugging
        if (failedScatters > 0) {
            for (UHCTeam team : teamsToScatter) {
                ScatterAttempt attempt = scatterAttempts.get(team.getTeamId());
                if (!attempt.successful) {
                    UHC.getInstance().getLogger().warning("Failed team: " + team.getTeamName() +
                            " - " + attempt.failureReason + " (attempts: " + attempt.attempts + ")");
                }
            }
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
        UHC.getInstance().getLogger().severe("Improved scatter failed: " + error);
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

            // Get pre-calculated location or fallback to team center
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
     * This ensures team members are close to each other but not at the exact same spot
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

    /**
     * Enhanced method to ensure team members are spread properly
     * Add this to the ProgressiveScatterManager class
     */
    private void validateTeamScatterSpread(UHCTeam team, List<Location> memberLocations) {
        if (memberLocations.size() <= 1) return;

        // Check that no two locations are too close together
        for (int i = 0; i < memberLocations.size(); i++) {
            for (int j = i + 1; j < memberLocations.size(); j++) {
                Location loc1 = memberLocations.get(i);
                Location loc2 = memberLocations.get(j);

                double distance = loc1.distance(loc2);
                if (distance < 5.0) { // Too close together
                    UHC.getInstance().getLogger().warning("Team " + team.getTeamName() +
                            " members scattered too close together (distance: " + String.format("%.1f", distance) + ")");
                }
            }
        }
    }

    /**
     * Debug method to log team scatter locations
     */
    private void logTeamScatterDebug(UHCTeam team, Location teamCenter, List<Location> memberLocations) {
        if (UHC.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
            UHC.getInstance().getLogger().fine("=== Team Scatter Debug: " + team.getTeamName() + " ===");
            UHC.getInstance().getLogger().fine("Team Center: " + formatLocation(teamCenter));

            for (int i = 0; i < memberLocations.size(); i++) {
                Location memberLoc = memberLocations.get(i);
                double distanceFromCenter = teamCenter.distance(memberLoc);
                UHC.getInstance().getLogger().fine("Member " + (i + 1) + ": " + formatLocation(memberLoc) +
                        " (distance from center: " + String.format("%.1f", distanceFromCenter) + ")");
            }
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
        UHC.getInstance().getLogger().info("Improved scatter manager cancelled");
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
                radius, MIN_DISTANCE_BETWEEN_TEAMS);
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
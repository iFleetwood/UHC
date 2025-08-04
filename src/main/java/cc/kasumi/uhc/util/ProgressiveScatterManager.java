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
 * Unified scatter manager that handles both team and individual scattering
 * Teams with size 1 are treated as "solo" players
 */
public class ProgressiveScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private int radius;
    private final List<UHCTeam> teamsToScatter;
    private final Map<UUID, Location> teamScatterLocations = new HashMap<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // State tracking
    @Getter
    private ScatterPhase currentPhase = ScatterPhase.VALIDATING_TEAMS;
    private int currentTeamIndex = 0;
    private Iterator<Chunk> chunkIterator;
    private boolean cancelled = false;

    // Configuration
    private static final int LOCATIONS_PER_TICK = 2;
    private static final int CHUNKS_PER_TICK = 1;
    private static final int TELEPORTS_PER_TICK = 1;
    private static final int MIN_DISTANCE_BETWEEN_TEAMS = 80;
    private static final int MAX_TEAM_SPREAD = 30;
    private static final int MAX_ATTEMPTS_PER_LOCATION = 30;
    private static final int SAFETY_BUFFER_FROM_BORDER = 50;

    public enum ScatterPhase {
        VALIDATING_TEAMS,
        GENERATING_LOCATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_TEAMS,
        COMPLETED
    }

    public ProgressiveScatterManager(Game game, int radius) {
        this.game = game;
        this.world = game.getWorld();
        this.radius = Math.max(100, radius - SAFETY_BUFFER_FROM_BORDER);
        this.teamsToScatter = getTeamsToScatter();

        UHC.getInstance().getLogger().info("Starting unified scatter for " + teamsToScatter.size() +
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
                case COMPLETED:
                    complete();
                    break;
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error in unified scatter manager: " + e.getMessage());
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
            handleError("No teams to scatter!");
            return;
        }

        // Validate world border
        WorldBorder border = world.getWorldBorder();
        if (border.getSize() < radius * 2) {
            UHC.getInstance().getLogger().warning("World border is smaller than scatter radius, adjusting...");
            radius = (int) (border.getSize() / 2) - SAFETY_BUFFER_FROM_BORDER;
        }

        currentPhase = ScatterPhase.GENERATING_LOCATIONS;
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Teams validated. Generating scatter locations...");
        UHC.getInstance().getLogger().info("Team validation completed. Found " + teamsToScatter.size() + " teams to scatter.");
    }

    private void generateTeamLocations() {
        int locationsGenerated = 0;
        Random random = new Random();

        while (currentTeamIndex < teamsToScatter.size() && locationsGenerated < LOCATIONS_PER_TICK) {
            UHCTeam team = teamsToScatter.get(currentTeamIndex);
            Location teamLocation = findValidTeamLocation(random, team);

            if (teamLocation != null) {
                teamScatterLocations.put(team.getTeamId(), teamLocation);
                chunksToPreload.add(teamLocation.getChunk());
                addSurroundingChunks(teamLocation);

                UHC.getInstance().getLogger().info("Generated location for team: " + team.getTeamName());
            } else {
                UHC.getInstance().getLogger().warning("Could not find scatter location for team: " + team.getTeamName());
            }

            currentTeamIndex++;
            locationsGenerated++;

            // Progress update
            double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
            if (currentTeamIndex % Math.max(1, teamsToScatter.size() / 4) == 0) {
                Bukkit.broadcastMessage(ChatColor.YELLOW + "Generating team locations: " + (int)progress + "% complete");
            }
        }

        // Check if done generating
        if (currentTeamIndex >= teamsToScatter.size()) {
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentTeamIndex = 0; // Reset for teleporting phase

            Bukkit.broadcastMessage(ChatColor.YELLOW + "Generated " + teamScatterLocations.size() +
                    " team locations. Preloading chunks...");
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
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Chunks preloaded. Starting team teleportation...");
        }
    }

    private void teleportTeams() {
        if (currentTeamIndex >= teamsToScatter.size()) {
            currentPhase = ScatterPhase.COMPLETED;
            return;
        }

        UHCTeam team = teamsToScatter.get(currentTeamIndex);
        Location teamLocation = teamScatterLocations.get(team.getTeamId());

        if (teamLocation != null) {
            scatterTeamMembers(team, teamLocation);
        }

        currentTeamIndex++;

        // Progress update
        double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
        if (currentTeamIndex % Math.max(1, teamsToScatter.size() / 4) == 0) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Teleporting teams: " + (int)progress + "% complete");
        }
    }

    private void complete() {
        int successfulScatters = teamScatterLocations.size();
        int totalTeams = teamsToScatter.size();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Scattering completed! " +
                successfulScatters + "/" + totalTeams + " teams scattered successfully.");

        UHC.getInstance().getLogger().info("Scatter completed: " + successfulScatters + "/" + totalTeams + " successful");

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

    private List<UHCTeam> getTeamsToScatter() {
        List<UHCTeam> teams = new ArrayList<>();

        for (UHCTeam team : game.getTeamManager().getAllTeams()) {
            if (team.getSize() > 0 && hasOnlineMembers(team)) {
                teams.add(team);
            }
        }

        return teams;
    }

    private boolean hasOnlineMembers(UHCTeam team) {
        return !team.getOnlineMembers().isEmpty();
    }

    private Location findValidTeamLocation(Random random, UHCTeam team) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();

        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_LOCATION; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(candidate);
            candidate.setY(groundY + 1);

            if (isValidTeamLocation(candidate)) {
                return candidate;
            }
        }

        return null;
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

    private void scatterTeamMembers(UHCTeam team, Location teamCenter) {
        List<Player> onlineMembers = team.getOnlineMembers();
        if (onlineMembers.isEmpty()) {
            return;
        }

        if (onlineMembers.size() == 1) {
            // Single player (solo team), just teleport to team center
            Player player = onlineMembers.get(0);
            player.teleport(teamCenter);
            player.sendMessage(ChatColor.GREEN + "You have been scattered!");
            return;
        }

        // Multiple players, spread them around the team center
        Random random = new Random();
        int membersScattered = 0;

        for (Player player : onlineMembers) {
            Location memberLocation = findNearbyLocation(teamCenter, random);

            if (memberLocation != null) {
                player.teleport(memberLocation);
                player.sendMessage(ChatColor.GREEN + "You have been scattered with your team " + team.getFormattedName());
                membersScattered++;
            } else {
                // Fallback to team center
                player.teleport(teamCenter);
                player.sendMessage(ChatColor.GREEN + "You have been scattered with your team " + team.getFormattedName());
                membersScattered++;
            }
        }

        team.sendMessage(ChatColor.YELLOW + "Your team has been scattered! " + membersScattered + " members teleported.");
        UHC.getInstance().getLogger().info("Scattered team " + team.getTeamName() + ": " + membersScattered + " members");
    }

    private Location findNearbyLocation(Location center, Random random) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * MAX_TEAM_SPREAD;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);

            if (GameUtil.isLocationSafe(candidate)) {
                return candidate;
            }
        }

        return null; // Fallback to team center
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

    public void startScattering() {
        this.runTaskTimer(UHC.getInstance(), 0, 2); // Run every 2 ticks
    }

    public double getProgress() {
        switch (currentPhase) {
            case VALIDATING_TEAMS:
                return 5;
            case GENERATING_LOCATIONS:
                return 5 + ((double) currentTeamIndex / teamsToScatter.size() * 30);
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 60;
                int chunksProcessed = chunksToPreload.size() - (chunkIterator.hasNext() ? getChunksRemaining() : 0);
                return 35 + ((double) chunksProcessed / chunksToPreload.size() * 25);
            case TELEPORTING_TEAMS:
                return 60 + ((double) currentTeamIndex / teamsToScatter.size() * 40);
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }

    private int getChunksRemaining() {
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
        UHC.getInstance().getLogger().info("Unified scatter manager cancelled");
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
}
package cc.kasumi.uhc.team;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.util.GameUtil;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles scattering teams together in the UHC world
 */
@Getter
public class TeamScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private final TeamManager teamManager;
    private int radius;
    private final List<UHCTeam> teamsToScatter;
    private final Map<UUID, Location> teamScatterLocations = new HashMap<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // State tracking
    private ScatterPhase currentPhase = ScatterPhase.VALIDATING_TEAMS;
    private int currentTeamIndex = 0;
    private Iterator<Chunk> chunkIterator;
    private boolean cancelled = false;

    // Configuration
    private static final int LOCATIONS_PER_TICK = 1;  // Generate 1 team location per tick
    private static final int CHUNKS_PER_TICK = 1;     // Preload 1 chunk per tick
    private static final int TELEPORTS_PER_TICK = 1;  // Teleport 1 team per tick
    private static final int MIN_DISTANCE_BETWEEN_TEAMS = 150; // Distance between teams
    private static final int MAX_TEAM_SPREAD = 30;     // Max distance between team members
    private static final int MAX_ATTEMPTS_PER_LOCATION = 50;

    public enum ScatterPhase {
        VALIDATING_TEAMS,
        GENERATING_LOCATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_TEAMS,
        COMPLETED
    }

    public TeamScatterManager(Game game, int radius) {
        this.game = game;
        this.world = game.getWorld();
        this.teamManager = game.getTeamManager();
        this.radius = Math.max(100, radius - 50); // Safety buffer
        this.teamsToScatter = getTeamsToScatter();

        UHC.getInstance().getLogger().info("Starting team scatter for " + teamsToScatter.size() +
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
            UHC.getInstance().getLogger().severe("Error in team scatter manager: " + e.getMessage());
            e.printStackTrace();
            handleError("Team scatter error: " + e.getMessage());
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
            radius = (int) (border.getSize() / 2) - 50;
        }

        currentPhase = ScatterPhase.GENERATING_LOCATIONS;
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Teams validated. Generating scatter locations...");
        UHC.getInstance().getLogger().info("Team validation completed. Found " + teamsToScatter.size() + " teams to scatter.");
    }

    private void generateTeamLocations() {
        if (currentTeamIndex >= teamsToScatter.size()) {
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentTeamIndex = 0; // Reset for teleporting phase

            Bukkit.broadcastMessage(ChatColor.YELLOW + "Generated " + teamScatterLocations.size() +
                    " team locations. Preloading chunks...");
            return;
        }

        UHCTeam team = teamsToScatter.get(currentTeamIndex);
        Location teamLocation = findValidTeamLocation(team);

        if (teamLocation != null) {
            teamScatterLocations.put(team.getTeamId(), teamLocation);
            chunksToPreload.add(teamLocation.getChunk());

            // Add surrounding chunks for team member spreading
            addSurroundingChunks(teamLocation);

            UHC.getInstance().getLogger().info("Generated location for team: " + team.getTeamName());
        } else {
            UHC.getInstance().getLogger().warning("Could not find scatter location for team: " + team.getTeamName());
        }

        currentTeamIndex++;

        // Progress update
        double progress = (double) currentTeamIndex / teamsToScatter.size() * 100;
        if (currentTeamIndex % Math.max(1, teamsToScatter.size() / 4) == 0) {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Generating team locations: " + (int)progress + "% complete");
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

        Bukkit.broadcastMessage(ChatColor.GREEN + "Team scattering completed! " +
                successfulScatters + "/" + totalTeams + " teams scattered successfully.");

        UHC.getInstance().getLogger().info("Team scatter completed: " + successfulScatters + "/" + totalTeams + " successful");

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
        UHC.getInstance().getLogger().severe("Team scatter failed: " + error);
        Bukkit.broadcastMessage(ChatColor.RED + "Team scatter failed: " + error);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Starting game without team scattering...");

        // Fallback to individual player scattering
        List<UUID> allPlayers = new ArrayList<>();
        for (UHCTeam team : teamsToScatter) {
            allPlayers.addAll(team.getMembersList());
        }

        // Use regular scatter for all players
        if (!allPlayers.isEmpty()) {
            game.startScattering(); // This will use the regular scattering system
        } else {
            // Start game anyway
            new BukkitRunnable() {
                @Override
                public void run() {
                    game.startGame();
                }
            }.runTaskLater(UHC.getInstance(), 60L);
        }

        cancel();
    }

    private List<UHCTeam> getTeamsToScatter() {
        List<UHCTeam> teams = new ArrayList<>();

        for (UHCTeam team : teamManager.getAllTeams()) {
            if (team.getSize() > 0 && hasOnlineMembers(team)) {
                teams.add(team);
            }
        }

        return teams;
    }

    private boolean hasOnlineMembers(UHCTeam team) {
        return !team.getOnlineMembers().isEmpty();
    }

    private Location findValidTeamLocation(UHCTeam team) {
        Random random = new Random();
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
        double borderRadius = border.getSize() / 2 - 50; // Safety buffer

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
            // Single player, just teleport to team center
            Player player = onlineMembers.get(0);
            player.teleport(teamCenter);
            player.sendMessage(ChatColor.GREEN + "You have been scattered with your team " + team.getFormattedName());
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
                return 5 + ((double) currentTeamIndex / teamsToScatter.size() * 30); // 5-35%
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 60;
                int chunksProcessed = chunksToPreload.size() - (chunkIterator.hasNext() ? getChunksRemaining() : 0);
                return 35 + ((double) chunksProcessed / chunksToPreload.size() * 25); // 35-60%
            case TELEPORTING_TEAMS:
                return 60 + ((double) currentTeamIndex / teamsToScatter.size() * 40); // 60-100%
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
        UHC.getInstance().getLogger().info("Team scatter manager cancelled");
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
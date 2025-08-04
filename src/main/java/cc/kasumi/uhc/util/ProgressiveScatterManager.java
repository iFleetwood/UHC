package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Enhanced player scattering with chunk preloading and world validation
 */
public class ProgressiveScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private int radius;
    private final List<UUID> playersToScatter;
    private final Map<UUID, Location> scatterLocations = new HashMap<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // State tracking
    private ScatterPhase currentPhase = ScatterPhase.VALIDATING_WORLD;
    private int currentPlayerIndex = 0;
    private Iterator<Chunk> chunkIterator;
    private boolean cancelled = false;

    // Configuration - adjusted for 1.8.8 stability
    private static final int LOCATIONS_PER_TICK = 2;  // Reduced for stability
    private static final int CHUNKS_PER_TICK = 1;     // Reduced for 1.8.8
    private static final int TELEPORTS_PER_TICK = 1;  // Keep at 1 for safety
    private static int MIN_DISTANCE_BETWEEN_PLAYERS = 80; // Reduced for better density
    private static final int MAX_ATTEMPTS_PER_LOCATION = 30; // Reduced attempts
    private static final int SAFETY_BUFFER_FROM_BORDER = 50; // Stay away from border

    public enum ScatterPhase {
        VALIDATING_WORLD,
        GENERATING_LOCATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_PLAYERS,
        COMPLETED
    }

    public ProgressiveScatterManager(Game game, List<UUID> playerUUIDs, int radius) {
        this.game = game;
        this.world = game.getWorld();
        this.radius = Math.max(100, radius - SAFETY_BUFFER_FROM_BORDER); // Ensure safe radius
        this.playersToScatter = new ArrayList<>(playerUUIDs);

        UHC.getInstance().getLogger().info("Starting progressive scatter for " + playerUUIDs.size() +
                " players with radius " + this.radius + " in world: " +
                (world != null ? world.getName() : "NULL"));
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        try {
            switch (currentPhase) {
                case VALIDATING_WORLD:
                    validateWorld();
                    break;
                case GENERATING_LOCATIONS:
                    generateLocations();
                    break;
                case PRELOADING_CHUNKS:
                    preloadChunks();
                    break;
                case TELEPORTING_PLAYERS:
                    teleportPlayers();
                    break;
                case COMPLETED:
                    complete();
                    break;
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error in scatter manager: " + e.getMessage());
            e.printStackTrace();
            handleError("Scatter error: " + e.getMessage());
        }
    }

    private void validateWorld() {
        if (world == null) {
            handleError("World is null!");
            return;
        }

        if (!game.isWorldReady()) {
            handleError("World is not ready!");
            return;
        }

        // Validate world border
        WorldBorder border = world.getWorldBorder();
        if (border.getSize() < radius * 2) {
            UHC.getInstance().getLogger().warning("World border (" + border.getSize() +
                    ") is smaller than scatter radius (" + radius * 2 + ")");
            // Adjust radius to fit border
            radius = (int) (border.getSize() / 2) - SAFETY_BUFFER_FROM_BORDER;
            UHC.getInstance().getLogger().info("Adjusted scatter radius to: " + this.radius);
        }

        // Validate player count vs available space
        double availableArea = Math.PI * radius * radius;
        double requiredSpacePerPlayer = MIN_DISTANCE_BETWEEN_PLAYERS * MIN_DISTANCE_BETWEEN_PLAYERS;
        double maxPlayers = availableArea / requiredSpacePerPlayer;

        if (playersToScatter.size() > maxPlayers) {
            UHC.getInstance().getLogger().warning("Too many players (" + playersToScatter.size() +
                    ") for available space. Reducing minimum distance.");
            // Reduce minimum distance for crowded games
            MIN_DISTANCE_BETWEEN_PLAYERS = Math.max(50, MIN_DISTANCE_BETWEEN_PLAYERS / 2);
        }

        currentPhase = ScatterPhase.GENERATING_LOCATIONS;
        Bukkit.broadcastMessage(ChatColor.YELLOW + "World validated. Generating scatter locations...");
        UHC.getInstance().getLogger().info("World validation completed. Starting location generation.");
    }

    private void generateLocations() {
        int locationsGenerated = 0;
        Random random = new Random();

        while (currentPlayerIndex < playersToScatter.size() && locationsGenerated < LOCATIONS_PER_TICK) {
            UUID playerUUID = playersToScatter.get(currentPlayerIndex);
            Player player = Bukkit.getPlayer(playerUUID);

            // Skip offline players
            if (player == null || !player.isOnline()) {
                UHC.getInstance().getLogger().info("Skipping offline player: " + playerUUID);
                currentPlayerIndex++;
                continue;
            }

            Location location = findValidScatterLocation(random, player);

            if (location != null) {
                scatterLocations.put(playerUUID, location);
                chunksToPreload.add(location.getChunk());
                currentPlayerIndex++;
                locationsGenerated++;

                // Announce progress every 25%
                double progress = (double) currentPlayerIndex / playersToScatter.size() * 100;
                if (currentPlayerIndex % Math.max(1, playersToScatter.size() / 4) == 0) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Generating scatter locations: " + (int)progress + "% complete");
                }
            } else {
                // Couldn't find valid location, try with relaxed constraints
                Location relaxedLocation = findRelaxedScatterLocation(random, player);
                if (relaxedLocation != null) {
                    scatterLocations.put(playerUUID, relaxedLocation);
                    chunksToPreload.add(relaxedLocation.getChunk());
                    UHC.getInstance().getLogger().warning("Used relaxed constraints for player: " + player.getName());
                } else {
                    UHC.getInstance().getLogger().warning("Could not find ANY scatter location for player: " + player.getName());
                }
                currentPlayerIndex++;
            }
        }

        // Check if done generating locations
        if (currentPlayerIndex >= playersToScatter.size()) {
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentPlayerIndex = 0; // Reset for teleporting phase

            Bukkit.broadcastMessage(ChatColor.YELLOW + "Generated " + scatterLocations.size() +
                    " scatter locations. Preloading chunks...");
            UHC.getInstance().getLogger().info("Generated " + scatterLocations.size() +
                    " locations, preloading " + chunksToPreload.size() + " chunks");
        }
    }

    private void preloadChunks() {
        int chunksLoaded = 0;

        while (chunkIterator.hasNext() && chunksLoaded < CHUNKS_PER_TICK) {
            Chunk chunk = chunkIterator.next();

            try {
                // Force load the chunk if not already loaded (1.8.8 compatible)
                if (!chunk.isLoaded()) {
                    chunk.load(true); // Force generation
                }
                chunksLoaded++;
            } catch (Exception e) {
                UHC.getInstance().getLogger().warning("Failed to load chunk at " +
                        chunk.getX() + ", " + chunk.getZ() + ": " + e.getMessage());
                chunksLoaded++; // Count it anyway to avoid infinite loop
            }
        }

        // Check if done preloading chunks
        if (!chunkIterator.hasNext()) {
            currentPhase = ScatterPhase.TELEPORTING_PLAYERS;
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Chunks preloaded. Starting player teleportation...");
            UHC.getInstance().getLogger().info("Finished preloading chunks, starting teleportation");
        }
    }

    private void teleportPlayers() {
        int playersTeleported = 0;

        while (currentPlayerIndex < playersToScatter.size() && playersTeleported < TELEPORTS_PER_TICK) {
            UUID playerUUID = playersToScatter.get(currentPlayerIndex);
            Player player = Bukkit.getPlayer(playerUUID);
            Location location = scatterLocations.get(playerUUID);

            if (player != null && player.isOnline() && location != null) {
                try {
                    // Validate location one more time before teleporting
                    if (!GameUtil.isLocationSafe(location)) {
                        UHC.getInstance().getLogger().warning("Location became unsafe for " + player.getName() +
                                ", finding alternative...");
                        Location alternative = findNearbyAlternative(location);
                        if (alternative != null) {
                            location = alternative;
                        }
                    }

                    // Teleport player
                    boolean success = player.teleport(location);
                    if (success) {
                        player.sendMessage(ChatColor.GREEN + "You have been scattered to: " +
                                location.getBlockX() + ", " + location.getBlockZ());

                        // Announce progress
                        double progress = (double) (currentPlayerIndex + 1) / playersToScatter.size() * 100;
                        if ((currentPlayerIndex + 1) % Math.max(1, playersToScatter.size() / 4) == 0) {
                            Bukkit.broadcastMessage(ChatColor.YELLOW + "Teleporting players: " + (int)progress + "% complete");
                        }
                    } else {
                        UHC.getInstance().getLogger().warning("Failed to teleport player: " + player.getName());
                        player.sendMessage(ChatColor.RED + "Failed to scatter you! Please contact an admin.");
                    }
                } catch (Exception e) {
                    UHC.getInstance().getLogger().severe("Error teleporting player " + player.getName() + ": " + e.getMessage());
                    player.sendMessage(ChatColor.RED + "Error during scattering! Please contact an admin.");
                }
            } else {
                UHC.getInstance().getLogger().info("Skipping invalid teleport: player=" +
                        (player != null ? player.getName() : "null") +
                        ", location=" + (location != null ? "valid" : "null"));
            }

            currentPlayerIndex++;
            playersTeleported++;
        }

        // Check if done teleporting
        if (currentPlayerIndex >= playersToScatter.size()) {
            currentPhase = ScatterPhase.COMPLETED;
        }
    }

    private void complete() {
        int successfulScatters = scatterLocations.size();
        int totalPlayers = playersToScatter.size();

        Bukkit.broadcastMessage(ChatColor.GREEN + "Player scattering completed! " +
                successfulScatters + "/" + totalPlayers + " players scattered successfully.");
        UHC.getInstance().getLogger().info("Scatter completed: " + successfulScatters + "/" + totalPlayers + " successful");

        // Wait a moment for players to load their chunks
        new BukkitRunnable() {
            @Override
            public void run() {
                // Start the game
                game.startGame();
            }
        }.runTaskLater(UHC.getInstance(), 40L); // 2 second delay

        // Clean up and cancel
        cancel();
    }

    private void handleError(String error) {
        UHC.getInstance().getLogger().severe("Scatter failed: " + error);
        Bukkit.broadcastMessage(ChatColor.RED + "Scatter failed: " + error);
        Bukkit.broadcastMessage(ChatColor.YELLOW + "Starting game without scattering...");

        // Start game anyway but teleport players to spawn
        for (UUID playerUUID : playersToScatter) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                Location spawn = game.getSpawnLocation();
                if (spawn != null) {
                    player.teleport(spawn);
                    player.sendMessage(ChatColor.YELLOW + "Scattering failed. You've been placed at spawn.");
                }
            }
        }

        // Start the game after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                game.startGame();
            }
        }.runTaskLater(UHC.getInstance(), 60L);

        cancel();
    }

    private Location findValidScatterLocation(Random random, Player player) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double borderRadius = border.getSize() / 2;

        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_LOCATION; attempt++) {
            // Generate coordinates within the effective radius (considering border and safety buffer)
            double effectiveRadius = Math.min(radius, borderRadius - SAFETY_BUFFER_FROM_BORDER);
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * effectiveRadius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);

            // Get proper ground level
            int groundY = world.getHighestBlockYAt(candidate);

            // Avoid spawning in trees or other structures
            groundY = findSafeGroundLevel(x, z, groundY);
            candidate.setY(groundY + 1);

            // Check if location is valid
            if (isLocationValid(candidate, player)) {
                return candidate;
            }
        }

        return null; // Couldn't find valid location
    }

    private Location findRelaxedScatterLocation(Random random, Player player) {
        // Try with relaxed constraints
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double borderRadius = border.getSize() / 2;

        for (int attempt = 0; attempt < 20; attempt++) {
            double effectiveRadius = Math.min(radius, borderRadius - 25); // Reduced safety buffer
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * effectiveRadius;

            int x = (int) (center.getX() + distance * Math.cos(angle));
            int z = (int) (center.getZ() + distance * Math.sin(angle));

            Location candidate = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);

            if (GameUtil.isLocationSafe(candidate) && hasMinimumDistance(candidate, 40)) { // Reduced distance
                return candidate;
            }
        }

        return null;
    }

    private int findSafeGroundLevel(int x, int z, int startY) {
        int groundY = startY;

        // Avoid spawning in trees or other structures
        for (int checkY = groundY; checkY < groundY + 15 && checkY < 255; checkY++) {
            Material mat = world.getBlockAt(x, checkY, z).getType();
            if (mat == Material.LEAVES || mat == Material.LEAVES_2 ||
                    mat == Material.LOG || mat == Material.LOG_2 ||
                    mat == Material.VINE || mat == Material.WEB) {
                groundY = checkY + 1;
            }
        }

        return Math.min(groundY, 250); // Cap at reasonable height
    }

    private boolean isLocationValid(Location candidate, Player player) {
        // First check if location is safe
        if (!GameUtil.isLocationSafe(candidate)) {
            return false;
        }

        // Check distance from other scatter locations
        if (!hasMinimumDistance(candidate, MIN_DISTANCE_BETWEEN_PLAYERS)) {
            return false;
        }

        // Check if within world border (with buffer)
        WorldBorder border = world.getWorldBorder();
        if (!isWithinBorder(candidate, border, SAFETY_BUFFER_FROM_BORDER)) {
            return false;
        }

        return true;
    }

    private boolean hasMinimumDistance(Location candidate, int minDistance) {
        for (Location existing : scatterLocations.values()) {
            if (candidate.distance(existing) < minDistance) {
                return false;
            }
        }
        return true;
    }

    private boolean isWithinBorder(Location location, WorldBorder border, int buffer) {
        Location center = border.getCenter();
        double radius = (border.getSize() / 2) - buffer;

        double deltaX = Math.abs(location.getX() - center.getX());
        double deltaZ = Math.abs(location.getZ() - center.getZ());

        return deltaX <= radius && deltaZ <= radius;
    }

    private Location findNearbyAlternative(Location original) {
        for (int radius = 5; radius <= 20; radius += 5) {
            for (int attempts = 0; attempts < 8; attempts++) {
                double angle = (attempts * Math.PI * 2) / 8; // 8 directions
                int x = original.getBlockX() + (int)(radius * Math.cos(angle));
                int z = original.getBlockZ() + (int)(radius * Math.sin(angle));

                Location alternative = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);

                if (GameUtil.isLocationSafe(alternative)) {
                    return alternative;
                }
            }
        }
        return null;
    }

    public void startScattering() {
        // Run every 2 ticks for 1.8.8 stability
        this.runTaskTimer(UHC.getInstance(), 0, 2);
    }

    public ScatterPhase getCurrentPhase() {
        return currentPhase;
    }

    public double getProgress() {
        switch (currentPhase) {
            case VALIDATING_WORLD:
                return 5;
            case GENERATING_LOCATIONS:
                return 5 + ((double) currentPlayerIndex / playersToScatter.size() * 30); // 5-35%
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 60;
                int chunksProcessed = chunksToPreload.size() - (chunkIterator.hasNext() ? getChunksRemaining() : 0);
                return 35 + ((double) chunksProcessed / chunksToPreload.size() * 25); // 35-60%
            case TELEPORTING_PLAYERS:
                return 60 + ((double) currentPlayerIndex / playersToScatter.size() * 40); // 60-100%
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
        UHC.getInstance().getLogger().info("Progressive scatter manager cancelled");
    }

    public Map<UUID, Location> getScatterLocations() {
        return new HashMap<>(scatterLocations);
    }

    public int getSuccessfulScatters() {
        return scatterLocations.size();
    }

    public int getTotalPlayers() {
        return playersToScatter.size();
    }
}
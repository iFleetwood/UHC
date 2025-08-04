package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles player scattering with chunk preloading to prevent lag
 */
public class ProgressiveScatterManager extends BukkitRunnable {

    private final Game game;
    private final World world;
    private final int radius;
    private final List<UUID> playersToScatter;
    private final Map<UUID, Location> scatterLocations = new HashMap<>();
    private final Set<Chunk> chunksToPreload = new HashSet<>();

    // State tracking
    private ScatterPhase currentPhase = ScatterPhase.GENERATING_LOCATIONS;
    private int currentPlayerIndex = 0;
    private Iterator<Chunk> chunkIterator;
    private boolean cancelled = false;

    // Configuration
    private static final int LOCATIONS_PER_TICK = 3;  // Generate 3 locations per tick
    private static final int CHUNKS_PER_TICK = 2;     // Preload 2 chunks per tick
    private static final int TELEPORTS_PER_TICK = 1;  // Teleport 1 player per tick
    private static final int MIN_DISTANCE_BETWEEN_PLAYERS = 100; // Minimum blocks apart
    private static final int MAX_ATTEMPTS_PER_LOCATION = 50; // Max tries to find valid location

    public enum ScatterPhase {
        GENERATING_LOCATIONS,
        PRELOADING_CHUNKS,
        TELEPORTING_PLAYERS,
        COMPLETED
    }

    public ProgressiveScatterManager(Game game, List<UUID> playerUUIDs, int radius) {
        this.game = game;
        this.world = Bukkit.getWorld(game.getWorldName());
        this.radius = radius;
        this.playersToScatter = new ArrayList<>(playerUUIDs);

        Bukkit.getLogger().info("Starting progressive scatter for " + playerUUIDs.size() + " players with radius " + radius);
    }

    @Override
    public void run() {
        if (cancelled) {
            return;
        }

        switch (currentPhase) {
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
    }

    private void generateLocations() {
        int locationsGenerated = 0;
        Random random = new Random();

        while (currentPlayerIndex < playersToScatter.size() && locationsGenerated < LOCATIONS_PER_TICK) {
            UUID playerUUID = playersToScatter.get(currentPlayerIndex);
            Location location = findValidScatterLocation(random);

            if (location != null) {
                scatterLocations.put(playerUUID, location);
                chunksToPreload.add(location.getChunk());
                currentPlayerIndex++;
                locationsGenerated++;

                // Announce progress every 25%
                double progress = (double) currentPlayerIndex / playersToScatter.size() * 100;
                if (currentPlayerIndex % Math.max(1, playersToScatter.size() / 4) == 0) {
                    Bukkit.broadcastMessage("Generating scatter locations: " + (int)progress + "% complete");
                }
            } else {
                // Couldn't find valid location, skip this player for now
                currentPlayerIndex++;
                Bukkit.getLogger().warning("Could not find valid scatter location for player " + playerUUID);
            }
        }

        // Check if done generating locations
        if (currentPlayerIndex >= playersToScatter.size()) {
            currentPhase = ScatterPhase.PRELOADING_CHUNKS;
            chunkIterator = chunksToPreload.iterator();
            currentPlayerIndex = 0; // Reset for teleporting phase

            Bukkit.broadcastMessage("Generated " + scatterLocations.size() + " scatter locations. Preloading chunks...");
            Bukkit.getLogger().info("Generated " + scatterLocations.size() + " locations, preloading " + chunksToPreload.size() + " chunks");
        }
    }

    private void preloadChunks() {
        int chunksLoaded = 0;

        while (chunkIterator.hasNext() && chunksLoaded < CHUNKS_PER_TICK) {
            Chunk chunk = chunkIterator.next();

            // Force load the chunk if not already loaded
            if (!chunk.isLoaded()) {
                chunk.load(true); // Force generation
            }

            chunksLoaded++;
        }

        // Check if done preloading chunks
        if (!chunkIterator.hasNext()) {
            currentPhase = ScatterPhase.TELEPORTING_PLAYERS;
            Bukkit.broadcastMessage("Chunks preloaded. Starting player teleportation...");
            Bukkit.getLogger().info("Finished preloading chunks, starting teleportation");
        }
    }

    private void teleportPlayers() {
        int playersTeleported = 0;

        while (currentPlayerIndex < playersToScatter.size() && playersTeleported < TELEPORTS_PER_TICK) {
            UUID playerUUID = playersToScatter.get(currentPlayerIndex);
            Player player = Bukkit.getPlayer(playerUUID);
            Location location = scatterLocations.get(playerUUID);

            if (player != null && player.isOnline() && location != null) {
                // Teleport player
                player.teleport(location);
                player.sendMessage("§aYou have been scattered!");

                // Announce progress
                double progress = (double) (currentPlayerIndex + 1) / playersToScatter.size() * 100;
                if ((currentPlayerIndex + 1) % Math.max(1, playersToScatter.size() / 4) == 0) {
                    Bukkit.broadcastMessage("Teleporting players: " + (int)progress + "% complete");
                }
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
        Bukkit.broadcastMessage("§aPlayer scattering completed! Starting game...");
        Bukkit.getLogger().info("Scatter completed successfully");

        // Start the game
        game.startGame();

        // Clean up and cancel
        cancel();
    }

    private Location findValidScatterLocation(Random random) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS_PER_LOCATION; attempt++) {
            int x = random.nextInt(radius * 2) - radius;
            int z = random.nextInt(radius * 2) - radius;

            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);
            candidate.setY(world.getHighestBlockYAt(candidate) + 1);

            // Check if location is far enough from other players
            if (isLocationValid(candidate)) {
                return candidate;
            }
        }

        return null; // Couldn't find valid location
    }

    private boolean isLocationValid(Location candidate) {
        // Check distance from other scatter locations
        for (Location existing : scatterLocations.values()) {
            if (candidate.distance(existing) < MIN_DISTANCE_BETWEEN_PLAYERS) {
                return false;
            }
        }

        return GameUtil.isLocationSafe(candidate);
    }

    public void startScattering() {
        // Run every tick for smooth progress
        this.runTaskTimer(UHC.getInstance(), 0, 1);
    }

    public ScatterPhase getCurrentPhase() {
        return currentPhase;
    }

    public double getProgress() {
        switch (currentPhase) {
            case GENERATING_LOCATIONS:
                return (double) currentPlayerIndex / playersToScatter.size() * 25; // 0-25%
            case PRELOADING_CHUNKS:
                if (chunksToPreload.isEmpty()) return 50;
                int chunksProcessed = chunksToPreload.size() - (chunkIterator.hasNext() ? 0 : chunksToPreload.size());
                return 25 + ((double) chunksProcessed / chunksToPreload.size() * 25); // 25-50%
            case TELEPORTING_PLAYERS:
                return 50 + ((double) currentPlayerIndex / playersToScatter.size() * 50); // 50-100%
            case COMPLETED:
                return 100;
            default:
                return 0;
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        super.cancel();
    }
}
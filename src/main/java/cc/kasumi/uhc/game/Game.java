package cc.kasumi.uhc.game;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.state.ScatteringGameState;
import cc.kasumi.uhc.game.state.WaitingGameState;
import cc.kasumi.uhc.game.task.*;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.scenario.ScenarioManager;
import cc.kasumi.uhc.util.GameUtil;
import cc.kasumi.uhc.util.ProgressiveScatterManager;
import cc.kasumi.uhc.util.TickCounter;
import cc.kasumi.uhc.world.WorldManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

@Getter
@Setter
public class Game {

    private final Map<UUID, UHCPlayer> players = new HashMap<>();
    private final CombatLogVillagerManager combatLogVillagerManager = new CombatLogVillagerManager(this);
    private final ScenarioManager scenarioManager = new ScenarioManager(this);


    private GameState state = new WaitingGameState(this);


    private int maxPlayers = 100;
    private int pvpTime = 30;
    private int healTime = 10 * 60;
    private int starterFood = 10;

    //border info
    private int initialBorderSize = 1000;
    private int currentBorderSize = initialBorderSize;
    private int shrinkInterval = 5 * 60;
    private int shrinkBorderUntil = 25;
    private int shrinkInitialBorder = 30 * 60;
    private int finalBorderSize = 25;

    private long startTimeMillis; // Keep for compatibility/logging
    private long startTimeTicks;  // NEW: Tick-based game timer
    private long gameStartTick;   // NEW: When the game actually started

    private boolean pvpEnabled = false;

    private String worldName = "world";

    private boolean startCountdownStarted = false;

    private TickCounter tickCounter = TickCounter.getInstance();

    public Game() {
        this.state.onEnable();
        setWorldBorder(initialBorderSize);
        initWorldEnvironment(Bukkit.getWorld(worldName));
    }

    public void setGameState(GameState newState) {
        if (!isValidTransition(this.state, newState)) {
            throw new IllegalStateException("Invalid transition from " +
                    this.state.getClass().getSimpleName() + " to " +
                    newState.getClass().getSimpleName());
        }

        this.state.onDisable();
        this.state = newState;
        newState.onEnable();
    }

    private boolean isValidTransition(GameState from, GameState to) {
        // Waiting -> Scattering -> Active
        if (from instanceof WaitingGameState) {
            return to instanceof ScatteringGameState;
        }
        if (from instanceof ScatteringGameState) {
            return to instanceof ActiveGameState;
        }
        if (from instanceof ActiveGameState) {
            return false; // Game is over, no transitions allowed
        }
        return false;
    }

    public void gameStartRunnable(int time) {
        this.startCountdownStarted = true;
        new StartTask(this, time).schedule();
    }

    // Add these methods to your existing Game class

    /**
     * Initialize world for UHC game
     */
    public void initializeWorld() {
        WorldManager worldManager = UHC.getInstance().getWorldManager();
        World uhcWorld = worldManager.getUhcWorld();

        if (uhcWorld == null) {
            UHC.getInstance().getLogger().warning("UHC world not found! Creating new one...");
            worldManager.createNewUHCWorld().thenAccept(world -> {
                this.worldName = world.getName();
                initWorldEnvironment(world);
                buildSetInitialBorder();
            });
        } else {
            this.worldName = uhcWorld.getName();
            initWorldEnvironment(uhcWorld);
            buildSetInitialBorder();
        }
    }

    /**
     * Reset world for new game
     */
    public void resetWorldForNewGame() {
        WorldManager worldManager = UHC.getInstance().getWorldManager();

        worldManager.resetUHCWorld().thenRun(() -> {
            // World has been reset, reinitialize
            World newWorld = worldManager.getUhcWorld();
            this.worldName = newWorld.getName();
            initWorldEnvironment(newWorld);

            // Reset game state
            this.state = new WaitingGameState(this);
            this.startCountdownStarted = false;
            this.pvpEnabled = false;
            this.currentBorderSize = this.initialBorderSize;

            // Teleport any remaining players to lobby
            World lobbyWorld = worldManager.getLobbyWorld();
            if (lobbyWorld != null) {
                Bukkit.getOnlinePlayers().forEach(player ->
                        player.teleport(lobbyWorld.getSpawnLocation()));
            }

            UHC.getInstance().getLogger().info("World reset completed for new game!");
        });
    }

    /**
     * Get the current UHC world
     */
    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    /**
     * Ensure world is properly loaded before game operations
     */
    public boolean isWorldReady() {
        World world = getWorld();
        return world != null && !UHC.getInstance().getWorldManager().isWorldGenerationInProgress();
    }

    /**
     * Enhanced world environment initialization
     */
    private void initWorldEnvironment(World world) {
        // Basic world rules
        world.setTime(0);
        world.setWeatherDuration(0);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("naturalRegeneration", "false");
        world.setGameRuleValue("keepInventory", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("doMobSpawning", "true");
        world.setGameRuleValue("doDaylightCycle", "false");

        // Set weather
        world.setStorm(false);
        world.setThundering(false);

        // Ensure spawn is at 0,0
        Location spawn = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());

        UHC.getInstance().getLogger().info("World environment initialized for: " + world.getName());
    }

    /**
     * Enhanced border setting with world validation
     */
    private void setWorldBorder(int borderSize) {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().warning("Cannot set world border: world not ready!");
            return;
        }

        World world = getWorld();
        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setDamageAmount(0);
        worldBorder.setDamageBuffer(1000);
        worldBorder.setCenter(0.5, 0.5);
        worldBorder.setSize(borderSize * 2 - 1.5);

        // Handle combat log villagers
        combatLogVillagerManager.handleBorderShrink(worldBorder, world);

        UHC.getInstance().getLogger().info("World border set to size: " + borderSize);
    }

    /**
     * Enhanced scatter starting with world checks
     */
    public void startScattering() {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().severe("Cannot start scattering: world not ready!");
            return;
        }

        setGameState(new ScatteringGameState(this));
        List<UUID> playerList = new ArrayList<>(getScatterPlayerUUIDs());

        // Create the progressive scatter manager
        ProgressiveScatterManager scatterManager = new ProgressiveScatterManager(this, playerList, initialBorderSize);
        scatterManager.startScattering();
    }

    /**
     * Enhanced game start with world validation
     */
    public void startGame() {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().severe("Cannot start game: world not ready!");
            return;
        }

        setGameState(new ActiveGameState(this));

        // Set both timers for compatibility
        this.startTimeMillis = System.currentTimeMillis();
        this.gameStartTick = tickCounter.getCurrentTick();
        this.startTimeTicks = gameStartTick;

        buildSetInitialBorder();

        new FinalHealTask(this, getHealTime()).schedule();
        new PvPEnableTask(this, getPvpTime()).schedule();
        new BorderShrinkTask(this, getShrinkInitialBorder()).schedule();
        players.forEach((uuid, uhcPlayer) -> uhcPlayer.setPlayerStateAndManage(PlayerState.ALIVE));

        UHC.getInstance().getLogger().info("Game started successfully in world: " + worldName);
    }

    /**
     * Get world-specific spawn location
     */
    public Location getSpawnLocation() {
        World world = getWorld();
        if (world == null) {
            return null;
        }
        return world.getSpawnLocation();
    }

    /**
     * Teleport all players to lobby
     */
    public void teleportAllPlayersToLobby() {
        WorldManager worldManager = UHC.getInstance().getWorldManager();
        World lobbyWorld = worldManager.getLobbyWorld();

        if (lobbyWorld == null) {
            UHC.getInstance().getLogger().warning("Lobby world not found!");
            return;
        }

        Location lobbySpawn = lobbyWorld.getSpawnLocation();
        World uhcWorld = getWorld();

        if (uhcWorld != null) {
            uhcWorld.getPlayers().forEach(player -> {
                player.teleport(lobbySpawn);
                player.sendMessage(ChatColor.YELLOW + "You have been teleported to the lobby!");
            });
        }

        // Also teleport any players in other worlds
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (!player.getWorld().equals(lobbyWorld)) {
                player.teleport(lobbySpawn);
            }
        });
    }

    public void healAlivePlayers() {
        for (UHCPlayer uhcPlayer : getOnlineAlivePlayers()) {
            Player player = uhcPlayer.getPlayer();

            player.setHealth(20.0D);
        }
    }

    public Set<UUID> getScatterPlayerUUIDs() {
        Set<UUID> uuids = new HashSet<>();

        for (UHCPlayer scatterPlayers : getAlivePlayers()) {
            uuids.add(scatterPlayers.getUuid());
        }

        return uuids;
    }

    public Set<UHCPlayer> getOnlineAlivePlayers() {
        Set<UHCPlayer> alivePlayers = new HashSet<>();

        for (UHCPlayer uhcPlayer : players.values()) {
            if (uhcPlayer.getState() != PlayerState.ALIVE) continue;

            alivePlayers.add(uhcPlayer);
        }

        return alivePlayers;
    }

    public Set<UHCPlayer> getAlivePlayers() {
        Set<UHCPlayer> alivePlayers = new HashSet<>();

        for (UHCPlayer uhcPlayer : players.values()) {
            if (uhcPlayer.getState() != PlayerState.ALIVE && uhcPlayer.getState() != PlayerState.COMBAT_LOG) continue;

            alivePlayers.add(uhcPlayer);
        }

        return alivePlayers;
    }

    private void buildSetBorder(int borderSize) {
        setCurrentBorderSize(borderSize);
        setWorldBorder(borderSize);
        GameUtil.shrinkBorder(borderSize, Bukkit.getWorld(worldName));
    }

    public void buildSetInitialBorder() {
        buildSetBorder(initialBorderSize);
    }

    public void shrinkBorder() {
        this.currentBorderSize = getNextBorder();
        buildSetBorder(currentBorderSize);
    }

    public int getNextBorder() {
        if (currentBorderSize > 500) return currentBorderSize - 500;
        if (currentBorderSize > 250) return currentBorderSize / 2;
        if (currentBorderSize > 100) return currentBorderSize - 50;
        return Math.max(currentBorderSize / 2, finalBorderSize);
    }

    public boolean canBorderShrinkMore() {
        return currentBorderSize > finalBorderSize;
    }

    public boolean isGameStarted() {
        return !(state instanceof WaitingGameState);
    }

    /**
     * Gets current server tick count (20 ticks per second)
     */
    public long getCurrentServerTick() {
        // Bukkit doesn't have a direct API for this, so we'll track it ourselves
        return tickCounter.getCurrentTick();
    }

    /**
     * Gets game duration in ticks since game started
     */
    public long getGameDurationTicks() {
        if (gameStartTick == 0) return 0;
        return getCurrentServerTick() - gameStartTick;
    }

    /**
     * Gets game duration in seconds (tick-based)
     */
    public long getGameDurationSeconds() {
        return getGameDurationTicks() / 20;
    }

    /**
     * Gets game duration in minutes (tick-based)
     */
    public long getGameDurationMinutes() {
        return getGameDurationSeconds() / 60;
    }

    /**
     * Formats game duration as MM:SS
     */
    public String getFormattedGameDuration() {
        long totalSeconds = getGameDurationSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void putUHCPlayer(UUID uuid, UHCPlayer uhcPlayer) {
        players.put(uuid, uhcPlayer);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public UHCPlayer getUHCPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public boolean containsUHCPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }
}

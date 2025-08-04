package cc.kasumi.uhc.game;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.barapi.BarAPI;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.state.ScatteringGameState;
import cc.kasumi.uhc.game.state.WaitingGameState;
import cc.kasumi.uhc.game.task.*;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.scenario.ScenarioManager;
import cc.kasumi.uhc.team.TeamManager;
import cc.kasumi.uhc.team.TeamScatterManager;
import cc.kasumi.uhc.team.UHCTeam;
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
    private final TeamManager teamManager = new TeamManager(this);
    private BarAPI barAPI;

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
    private boolean startCountdownStarted = false;
    private boolean teamMode = false; // NEW: Whether teams are enabled

    private TickCounter tickCounter = TickCounter.getInstance();

    // World Management Integration
    private WorldManager worldManager;
    private String worldName = "uhc"; // Default, will be updated by WorldManager

    public Game() {
        this.worldManager = UHC.getInstance().getWorldManager();

        // Initialize world system
        initializeWorldSystem();

        this.state.onEnable();
    }

    /**
     * Initialize the world system for UHC
     */
    private void initializeWorldSystem() {
        // Get the configured UHC world name
        this.worldName = worldManager.getUHCWorldName();

        // Ensure UHC world exists and is ready
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().info("UHC world not ready, will initialize when available");
            // Schedule a check for when world becomes available
            scheduleWorldReadyCheck();
        } else {
            setupInitialGameWorld();
        }
    }

    /**
     * Schedule periodic checks for world readiness
     */
    private void scheduleWorldReadyCheck() {
        new BukkitRunnable() {
            private int attempts = 0;
            private final int maxAttempts = 60; // 30 seconds maximum wait

            @Override
            public void run() {
                attempts++;

                if (isWorldReady()) {
                    setupInitialGameWorld();
                    cancel();
                    UHC.getInstance().getLogger().info("UHC world became ready after " + attempts + " attempts");
                } else if (attempts >= maxAttempts) {
                    UHC.getInstance().getLogger().severe("UHC world failed to become ready after " + maxAttempts + " attempts!");
                    cancel();
                } else if (attempts % 10 == 0) {
                    UHC.getInstance().getLogger().info("Still waiting for UHC world... (attempt " + attempts + "/" + maxAttempts + ")");
                }
            }
        }.runTaskTimer(UHC.getInstance(), 10L, 10L); // Check every 0.5 seconds
    }

    /**
     * Setup initial game world configuration
     */
    private void setupInitialGameWorld() {
        World uhcWorld = getWorld();
        if (uhcWorld != null) {
            this.worldName = uhcWorld.getName();
            setWorldBorder(initialBorderSize);
            initWorldEnvironment(uhcWorld);
            UHC.getInstance().getLogger().info("Game initialized with world: " + worldName);
        }
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
        if (!isWorldReady()) {
            Bukkit.broadcastMessage(ChatColor.RED + "Cannot start game: World is not ready!");
            UHC.getInstance().getLogger().warning("Attempted to start game but world is not ready");
            return;
        }

        this.startCountdownStarted = true;
        new StartTask(this, time).schedule();
    }

    /**
     * Enhanced world initialization with WorldManager integration
     */
    public void initializeWorld() {
        if (worldManager.getUhcWorld() == null) {
            UHC.getInstance().getLogger().warning("UHC world not found! Creating new one...");
            worldManager.createNewUHCWorld().thenAccept(world -> {
                this.worldName = world.getName();
                initWorldEnvironment(world);
                buildSetInitialBorder();
                UHC.getInstance().getLogger().info("New UHC world created and initialized: " + worldName);
            }).exceptionally(throwable -> {
                UHC.getInstance().getLogger().severe("Failed to create UHC world: " + throwable.getMessage());
                return null;
            });
        } else {
            World uhcWorld = worldManager.getUhcWorld();
            this.worldName = uhcWorld.getName();
            initWorldEnvironment(uhcWorld);
            buildSetInitialBorder();
            UHC.getInstance().getLogger().info("Existing UHC world initialized: " + worldName);
        }
    }

    /**
     * Reset world for new game with WorldManager integration
     */
    public void resetWorldForNewGame() {
        UHC.getInstance().getLogger().info("Resetting world for new game...");

        // Teleport all players to lobby first
        teleportAllPlayersToLobby();

        worldManager.resetUHCWorld().thenRun(() -> {
            // World has been reset, reinitialize game
            World newWorld = worldManager.getUhcWorld();
            this.worldName = newWorld.getName();
            initWorldEnvironment(newWorld);

            // Reset game state
            resetGameState();

            UHC.getInstance().getLogger().info("World reset completed for new game!");
        }).exceptionally(throwable -> {
            UHC.getInstance().getLogger().severe("Failed to reset world: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Reset game state after world reset
     */
    private void resetGameState() {
        this.state.onDisable();
        this.state = new WaitingGameState(this);
        this.state.onEnable();

        this.startCountdownStarted = false;
        this.pvpEnabled = false;
        this.currentBorderSize = this.initialBorderSize;
        this.gameStartTick = 0;
        this.startTimeTicks = 0;
        this.startTimeMillis = 0;

        // Clear teams if in team mode
        if (teamMode) {
            teamManager.clearAllTeams();
        }

        // Clear any existing tasks
        Bukkit.getScheduler().cancelTasks(UHC.getInstance());

        // Reset border
        buildSetInitialBorder();
    }

    /**
     * Get the current UHC world with proper error handling
     */
    public World getWorld() {
        World world = worldManager.getUhcWorld();
        if (world == null) {
            UHC.getInstance().getLogger().warning("UHC world is null, attempting fallback to Bukkit world");
            world = Bukkit.getWorld(worldName);
        }
        return world;
    }

    /**
     * Enhanced world readiness check
     */
    public boolean isWorldReady() {
        World world = getWorld();
        return world != null &&
                !worldManager.isWorldGenerationInProgress() &&
                world.getSpawnLocation() != null;
    }

    /**
     * Enhanced world environment initialization
     */
    private void initWorldEnvironment(World world) {
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot initialize null world environment!");
            return;
        }

        // Basic world rules
        world.setTime(6000); // Noon
        world.setWeatherDuration(0);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("naturalRegeneration", "false");
        world.setGameRuleValue("keepInventory", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("doMobSpawning", "true");

        // Set weather
        world.setStorm(false);
        world.setThundering(false);

        // Ensure spawn is at 0,0
        Location spawn = new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
        world.setSpawnLocation(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ());

        UHC.getInstance().getLogger().info("World environment initialized for: " + world.getName());
    }

    /**
     * Enhanced border setting with world validation and combat log handling
     */
    private void setWorldBorder(int borderSize) {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().warning("Cannot set world border: world not ready!");
            return;
        }

        World world = getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot set world border: world is null!");
            return;
        }

        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setDamageAmount(0);
        worldBorder.setDamageBuffer(1000);
        worldBorder.setCenter(0.5, 0.5);
        worldBorder.setSize(borderSize * 2 - 1.5);

        // Handle combat log villagers during border changes
        combatLogVillagerManager.handleBorderShrink(worldBorder, world);

        UHC.getInstance().getLogger().info("World border set to size: " + borderSize + " in world: " + world.getName());
    }

    /**
     * Enhanced scattering with team support and proper error handling
     */
    public void startScattering() {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().severe("Cannot start scattering: world not ready!");
            Bukkit.broadcastMessage(ChatColor.RED + "Cannot start scattering: World not ready!");
            return;
        }

        World world = getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot start scattering: world is null!");
            Bukkit.broadcastMessage(ChatColor.RED + "Cannot start scattering: World error!");
            return;
        }

        setGameState(new ScatteringGameState(this));

        // Check if we should use team scattering
        if (teamMode && !teamManager.getAllTeams().isEmpty()) {
            startTeamScattering();
        } else {
            startIndividualScattering();
        }
    }

    /**
     * Start team-based scattering
     */
    private void startTeamScattering() {
        Collection<UHCTeam> teams = teamManager.getAllTeams();

        if (teams.isEmpty()) {
            UHC.getInstance().getLogger().warning("Team mode enabled but no teams found! Switching to individual scatter.");
            startIndividualScattering();
            return;
        }

        int teamsWithPlayers = 0;
        for (UHCTeam team : teams) {
            if (!team.getOnlineMembers().isEmpty()) {
                teamsWithPlayers++;
            }
        }

        if (teamsWithPlayers == 0) {
            UHC.getInstance().getLogger().warning("No teams with online players! Starting game immediately.");
            startGame();
            return;
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "Starting team scatter for " + teamsWithPlayers +
                " teams in world: " + worldName);

        TeamScatterManager teamScatterManager = new TeamScatterManager(this, initialBorderSize);
        teamScatterManager.startScattering();
    }

    /**
     * Start individual player scattering
     */
    private void startIndividualScattering() {
        List<UUID> playerList = new ArrayList<>(getScatterPlayerUUIDs());

        if (playerList.isEmpty()) {
            UHC.getInstance().getLogger().warning("No players to scatter!");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "No players to scatter! Starting game immediately.");
            startGame();
            return;
        }

        World world = getWorld();
        Bukkit.broadcastMessage(ChatColor.GREEN + "Starting individual scatter for " + playerList.size() +
                " players in world: " + world.getName());

        // Create the progressive scatter manager
        ProgressiveScatterManager scatterManager = new ProgressiveScatterManager(this, playerList, initialBorderSize);
        scatterManager.startScattering();
    }

    /**
     * Enhanced game start with comprehensive world validation
     */
    public void startGame() {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().severe("Cannot start game: world not ready!");
            Bukkit.broadcastMessage(ChatColor.RED + "Cannot start game: World not ready!");
            return;
        }

        World world = getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot start game: world is null!");
            Bukkit.broadcastMessage(ChatColor.RED + "Cannot start game: World error!");
            return;
        }

        barAPI = new BarAPI();
        barAPI.onEnable();
        setGameState(new ActiveGameState(this));

        // Set both timers for compatibility
        this.startTimeMillis = System.currentTimeMillis();
        this.gameStartTick = tickCounter.getCurrentTick();
        this.startTimeTicks = gameStartTick;

        buildSetInitialBorder();

        // Schedule game tasks
        new FinalHealTask(this, getHealTime()).schedule();
        new PvPEnableTask(this, getPvpTime()).schedule();
        new BorderShrinkTask(this, getShrinkInitialBorder()).schedule();

        // Set all players to alive state
        players.forEach((uuid, uhcPlayer) -> uhcPlayer.setPlayerStateAndManage(PlayerState.ALIVE));

        // Announce game start with team info if applicable
        if (teamMode) {
            int aliveTeams = teamManager.getAliveTeams().size();
            Bukkit.broadcastMessage(ChatColor.GREEN + "Game started successfully with " + aliveTeams +
                    " teams in world: " + world.getName());
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Game started successfully in world: " + world.getName());
        }

        UHC.getInstance().getLogger().info("Game started successfully in world: " + worldName);
    }

    /**
     * Get world-specific spawn location with validation
     */
    public Location getSpawnLocation() {
        World world = getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().warning("Cannot get spawn location: world is null!");
            return null;
        }
        return world.getSpawnLocation();
    }

    /**
     * Enhanced teleport all players to lobby with proper error handling
     */
    public void teleportAllPlayersToLobby() {
        World lobbyWorld = worldManager.getLobbyWorld();

        if (lobbyWorld == null) {
            UHC.getInstance().getLogger().warning("Lobby world not found! Cannot teleport players.");
            return;
        }

        Location lobbySpawn = lobbyWorld.getSpawnLocation();
        World uhcWorld = getWorld();

        int playersTeleported = 0;

        // Teleport players from UHC world
        if (uhcWorld != null) {
            for (Player player : uhcWorld.getPlayers()) {
                player.teleport(lobbySpawn);
                player.sendMessage(ChatColor.YELLOW + "You have been teleported to the lobby!");
                playersTeleported++;
            }
        }

        // Also teleport any players in other worlds (except lobby)
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(lobbyWorld)) {
                player.teleport(lobbySpawn);
                playersTeleported++;
            }
        }

        UHC.getInstance().getLogger().info("Teleported " + playersTeleported + " players to lobby");

        if (playersTeleported > 0) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "All players have been teleported to the lobby!");
        }
    }

    /**
     * Enhanced heal alive players with world validation
     */
    public void healAlivePlayers() {
        World world = getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().warning("Cannot heal players: world is null!");
            return;
        }

        int playersHealed = 0;
        for (UHCPlayer uhcPlayer : getOnlineAlivePlayers()) {
            Player player = uhcPlayer.getPlayer();
            if (player != null && player.isOnline() && player.getWorld().equals(world)) {
                player.setHealth(20.0D);
                player.sendMessage(ChatColor.GREEN + "You have been healed!");
                playersHealed++;
            }
        }

        if (playersHealed > 0) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Final heal given to " + playersHealed + " players!");
            UHC.getInstance().getLogger().info("Healed " + playersHealed + " alive players");
        }
    }

    public Set<UUID> getScatterPlayerUUIDs() {
        Set<UUID> uuids = new HashSet<>();

        for (UHCPlayer scatterPlayers : getAlivePlayers()) {
            // Ensure player is actually online and in the correct world
            Player player = scatterPlayers.getPlayer();
            if (player != null && player.isOnline()) {
                uuids.add(scatterPlayers.getUuid());
            }
        }

        return uuids;
    }

    public Set<UHCPlayer> getOnlineAlivePlayers() {
        Set<UHCPlayer> alivePlayers = new HashSet<>();

        for (UHCPlayer uhcPlayer : players.values()) {
            if (uhcPlayer.getState() != PlayerState.ALIVE) continue;

            Player player = uhcPlayer.getPlayer();
            if (player != null && player.isOnline()) {
                alivePlayers.add(uhcPlayer);
            }
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

    /**
     * Enhanced border building with world validation
     */
    private void buildSetBorder(int borderSize) {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().warning("Cannot build border: world not ready!");
            return;
        }

        setCurrentBorderSize(borderSize);
        setWorldBorder(borderSize);

        World world = getWorld();
        if (world != null) {
            GameUtil.shrinkBorder(borderSize, world);

            if (teamMode) {
                int aliveTeams = teamManager.getAliveTeams().size();
                Bukkit.broadcastMessage(ChatColor.GOLD + "Border built with size: " + borderSize +
                        " (" + aliveTeams + " teams remaining)");
            } else {
                Bukkit.broadcastMessage(ChatColor.GOLD + "Border built with size: " + borderSize);
            }
        }
    }

    public void buildSetInitialBorder() {
        buildSetBorder(initialBorderSize);
    }

    /**
     * Enhanced border shrinking with world validation and team notifications
     */
    public void shrinkBorder() {
        if (!isWorldReady()) {
            UHC.getInstance().getLogger().warning("Cannot shrink border: world not ready!");
            return;
        }

        int oldSize = this.currentBorderSize;
        this.currentBorderSize = getNextBorder();

        buildSetBorder(currentBorderSize);

        // Enhanced border shrink notification with team info
        World world = getWorld();
        if (world != null) {
            int playersInWorld = world.getPlayers().size();
            String teamInfo = "";

            if (teamMode) {
                int aliveTeams = teamManager.getAliveTeams().size();
                teamInfo = ", Teams: " + aliveTeams;
            }

            Bukkit.broadcastMessage(ChatColor.YELLOW + "Border shrunk from " + oldSize + " to " + currentBorderSize +
                    ChatColor.GRAY + " (World: " + world.getName() + ", Players: " + playersInWorld + teamInfo + ")");
        }
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

    /**
     * Get world name for this game
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Check if the game is running in the correct world
     */
    public boolean isInGameWorld(Player player) {
        if (player == null) return false;
        World gameWorld = getWorld();
        return gameWorld != null && player.getWorld().equals(gameWorld);
    }

    /**
     * Get players currently in the game world
     */
    public List<Player> getPlayersInGameWorld() {
        World gameWorld = getWorld();
        if (gameWorld == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(gameWorld.getPlayers());
    }

    /**
     * Force reload world reference (useful after world reset)
     */
    public void refreshWorldReference() {
        World newWorld = worldManager.getUhcWorld();
        if (newWorld != null) {
            this.worldName = newWorld.getName();
            UHC.getInstance().getLogger().info("Refreshed world reference to: " + worldName);
        } else {
            UHC.getInstance().getLogger().warning("Failed to refresh world reference - world is null!");
        }
    }

    public void putUHCPlayer(UUID uuid, UHCPlayer uhcPlayer) {
        players.put(uuid, uhcPlayer);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);

        // Remove from team if in team mode
        if (teamMode) {
            teamManager.removePlayerFromTeam(uuid);
        }
    }

    public UHCPlayer getUHCPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public boolean containsUHCPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    /**
     * Enable or disable team mode
     */
    public void setTeamMode(boolean teamMode) {
        this.teamMode = teamMode;

        if (teamMode) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Team mode enabled!");
            UHC.getInstance().getLogger().info("Team mode enabled");
        } else {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Team mode disabled!");
            teamManager.clearAllTeams();
            UHC.getInstance().getLogger().info("Team mode disabled, all teams cleared");
        }
    }

    /**
     * Check if teams are enabled
     */
    public boolean isTeamMode() {
        return teamMode;
    }
}
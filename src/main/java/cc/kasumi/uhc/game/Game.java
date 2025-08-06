package cc.kasumi.uhc.game;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.barapi.BarAPI;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.state.GameEndedState;
import cc.kasumi.uhc.game.state.ScatteringGameState;
import cc.kasumi.uhc.game.state.WaitingGameState;
import cc.kasumi.uhc.game.task.BorderShrinkTask;
import cc.kasumi.uhc.game.task.FinalHealTask;
import cc.kasumi.uhc.game.task.PvPEnableTask;
import cc.kasumi.uhc.game.task.StartTask;
import cc.kasumi.uhc.packets.NameTagManager;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.scenario.ScenarioManager;
import cc.kasumi.uhc.team.TeamManager;
import cc.kasumi.uhc.team.UHCTeam;
import cc.kasumi.uhc.util.GameUtil;
import cc.kasumi.uhc.util.TickCounter;
import cc.kasumi.uhc.util.ProgressiveScatterManager;
import cc.kasumi.uhc.world.WorldManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refactored Game class that uses teams as the core system
 * Team size of 1 = solo mode, team size > 1 = team mode
 */
@Getter
@Setter
public class Game {

    private final Map<UUID, UHCPlayer> players = new HashMap<>();
    private final CombatLogVillagerManager combatLogVillagerManager = new CombatLogVillagerManager(this);
    private final ScenarioManager scenarioManager = new ScenarioManager(this);
    private final TeamManager teamManager = new TeamManager(this);
    private NameTagManager nameTagManager = new NameTagManager(this);
    private BarAPI barAPI;

    private GameState state = new WaitingGameState(this);

    // Game settings
    private int maxPlayers = 100;
    private int pvpTime = 30;
    private int healTime = 10 * 60;
    private int starterFood = 10;
    private int maxTeamSize = 1; // 1 = solo, >1 = teams

    // Border info
    private int initialBorderSize = 1000;
    private int currentBorderSize = initialBorderSize;
    private int shrinkInterval = 5 * 60;
    private int shrinkBorderUntil = 25;
    private int shrinkInitialBorder = 30 * 60;
    private int finalBorderSize = 25;

    // Game timing
    private long startTimeMillis;
    private long startTimeTicks;
    private long gameStartTick;

    private boolean pvpEnabled = false;
    private boolean startCountdownStarted = false;

    private TickCounter tickCounter = TickCounter.getInstance();

    // World Management Integration
    private WorldManager worldManager;
    private String worldName = "uhc";

    public Game() {
        this.worldManager = UHC.getInstance().getWorldManager();
        initializeWorldSystem();
        this.state.onEnable();
    }

    public double getEffectiveBorderSize() {
        // Use initial border size for calculations to prevent issues when border shrinks
        return initialBorderSize * 2 - 1.5; // Match the actual border setup formula
    }

    /**
     * Get the effective border radius for calculations
     * @return half of the effective border size
     */
    public double getEffectiveBorderRadius() {
        return getEffectiveBorderSize() / 2;
    }

    /**
     * Initialize the world system for UHC
     */
    private void initializeWorldSystem() {
        this.worldName = worldManager.getUHCWorldName();

        if (!isWorldReady()) {
            UHC.getInstance().getLogger().info("UHC world not ready, will initialize when available");
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
            private final int maxAttempts = 60;

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
        }.runTaskTimer(UHC.getInstance(), 10L, 10L);
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

    public void enableTeamNameTags() {
        if (nameTagManager != null) {
            nameTagManager.enableTeamNameTags();
            UHC.getInstance().getLogger().info("Team nametags enabled for game");
        }
    }

    /**
     * Disable team-based nametags
     * Should be called when the game ends
     */
    public void disableTeamNameTags() {
        if (nameTagManager != null) {
            nameTagManager.disableTeamNameTags();
            UHC.getInstance().getLogger().info("Team nametags disabled for game");
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

    // Update the isValidTransition method in Game.java

    private boolean isValidTransition(GameState from, GameState to) {
        // Allow transition from WaitingGameState to ScatteringGameState
        if (from instanceof WaitingGameState) {
            return to instanceof ScatteringGameState;
        }

        // Allow transition from ScatteringGameState to ActiveGameState
        if (from instanceof ScatteringGameState) {
            return to instanceof ActiveGameState;
        }

        // Allow transition from ActiveGameState to GameEndedState (THIS WAS MISSING!)
        if (from instanceof ActiveGameState) {
            return to instanceof GameEndedState;
        }

        // GameEndedState is terminal - no transitions allowed from it
        if (from instanceof GameEndedState) {
            return false;
        }

        // Default: no transition allowed
        return false;
    }

    // Also need to update the endGame method to be more robust
    public void endGame(GameEndResult result) {
        UHC.getInstance().getLogger().info("Game ending: " + result.getReason());

        try {
            // Set game state to ended - but handle the case where GameEndedState doesn't exist yet
            Class<?> gameEndedStateClass;
            try {
                gameEndedStateClass = Class.forName("cc.kasumi.uhc.game.state.GameEndedState");
                Object gameEndedState = gameEndedStateClass.getConstructor(Game.class, GameEndResult.class).newInstance(this, result);
                setGameState((GameState) gameEndedState);
            } catch (ClassNotFoundException e) {
                // GameEndedState doesn't exist yet, just log that game ended
                UHC.getInstance().getLogger().info("GameEndedState class not found - game ended without state transition");
            }

            // Cancel all game tasks
            Bukkit.getScheduler().cancelTasks(UHC.getInstance());

            // Cancel wall builders
            GameUtil.cancelAllWallBuilders();

            disableTeamNameTags();

            // Stop bar API
            if (barAPI != null) {
                barAPI.onDisable();
            }

            // Announce results
            announceGameResults(result);

            // Handle post-game actions after delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    handlePostGameActions(result);
                }
            }.runTaskLater(UHC.getInstance(), 100L); // 5 second delay

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error during game end: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Game end error", e);

            // Fallback - at least do cleanup
            try {
                Bukkit.getScheduler().cancelTasks(UHC.getInstance());
                GameUtil.cancelAllWallBuilders();
                if (barAPI != null) {
                    barAPI.onDisable();
                }
                announceGameResults(result);
            } catch (Exception fallbackError) {
                UHC.getInstance().getLogger().severe("Critical error during game end fallback: " + fallbackError.getMessage());
            }
        }
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

        teleportAllPlayersToLobby();

        worldManager.resetUHCWorld().thenRun(() -> {
            World newWorld = worldManager.getUhcWorld();
            this.worldName = newWorld.getName();
            initWorldEnvironment(newWorld);
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

        // Clear all teams
        teamManager.clearAllTeams();

        // Cancel any existing tasks
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
        world.setTime(6000);
        world.setWeatherDuration(0);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle", "false");
        world.setGameRuleValue("naturalRegeneration", "false");
        world.setGameRuleValue("keepInventory", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("doMobSpawning", "true");

        world.setStorm(false);
        world.setThundering(false);

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
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot set world border: world is null!");
            return;
        }

        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setDamageAmount(0);
        worldBorder.setDamageBuffer(1000);
        worldBorder.setCenter(0.5, 0.5);
        worldBorder.setSize(borderSize * 2 - 1.5);

        combatLogVillagerManager.handleBorderShrink(worldBorder, world);

        UHC.getInstance().getLogger().info("World border set to size: " + borderSize + " in world: " + world.getName());
    }

    // Update the startScattering() method in Game.java

    /**
     * Start scattering using improved system with better error handling
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

        // Ensure all online players are on teams
        ensureAllPlayersOnTeams();

        Collection<UHCTeam> teams = teamManager.getAllTeams();
        if (teams.isEmpty()) {
            UHC.getInstance().getLogger().warning("No teams found! Starting game immediately.");
            startGame();
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

        // Validate scatter conditions before starting
        if (!validateScatterConditions(teamsWithPlayers)) {
            UHC.getInstance().getLogger().warning("Scatter conditions not optimal, but proceeding anyway...");
        }

        Bukkit.broadcastMessage(ChatColor.GREEN + "Starting scatter for " + teamsWithPlayers +
                " teams in world: " + worldName);

        // Use improved scatter manager
        ProgressiveScatterManager scatterManager = new ProgressiveScatterManager(this, initialBorderSize);
        scatterManager.startScattering();

        // Schedule a backup check in case scatter gets stuck
        scheduleScatterBackupCheck(scatterManager);
    }

    /**
     * Validate conditions for successful scattering
     */
    private boolean validateScatterConditions(int teamCount) {
        World world = getWorld();
        WorldBorder border = world.getWorldBorder();

        // Check if border is large enough
        double borderRadius = border.getSize() / 2;
        int effectiveRadius = (int)(borderRadius - 80); // Safety buffer

        if (effectiveRadius < 150) {
            UHC.getInstance().getLogger().warning("Border very small for scattering (radius: " + effectiveRadius + ")");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Warning: Small border may cause scatter issues");
            return false;
        }

        // Estimate if we can fit all teams
        double borderArea = Math.PI * Math.pow(effectiveRadius, 2);
        double teamArea = Math.PI * Math.pow(100, 2); // 100 block min distance
        int theoreticalMaxTeams = (int)(borderArea / teamArea);

        if (teamCount > theoreticalMaxTeams) {
            UHC.getInstance().getLogger().warning("Too many teams for border size! (" + teamCount +
                    " teams, max ~" + theoreticalMaxTeams + ")");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Warning: Many teams for current border - some may not scatter optimally");
            return false;
        }

        // Check world generation
        if (world.getLoadedChunks().length < 100) {
            UHC.getInstance().getLogger().warning("Very few chunks loaded - may affect scatter performance");
            return false;
        }

        return true;
    }

    /**
     * Schedule a backup check for scatter completion
     */
    private void scheduleScatterBackupCheck(ProgressiveScatterManager scatterManager) {
        new BukkitRunnable() {
            private int checks = 0;
            private final int maxChecks = 60; // 5 minutes max

            @Override
            public void run() {
                checks++;

                if (scatterManager.isCancelled()) {
                    // Scatter completed or cancelled
                    cancel();
                    return;
                }

                if (checks >= maxChecks) {
                    // Timeout - force start game
                    UHC.getInstance().getLogger().severe("Scatter timeout reached! Force starting game...");
                    Bukkit.broadcastMessage(ChatColor.RED + "Scatter timeout - starting game at spawn!");

                    scatterManager.cancel();

                    // Start game anyway
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            startGame();
                        }
                    }.runTaskLater(UHC.getInstance(), 20L);

                    cancel();
                    return;
                }

                // Log progress every 30 seconds
                if (checks % 30 == 0) {
                    double progress = scatterManager.getProgress();
                    UHC.getInstance().getLogger().info("Scatter progress: " + String.format("%.1f", progress) +
                            "% (phase: " + scatterManager.getCurrentPhase() + ")");

                    if (progress < 10) {
                        Bukkit.broadcastMessage(ChatColor.YELLOW + "Scatter in progress, please wait...");
                    }
                }
            }
        }.runTaskTimer(UHC.getInstance(), 100L, 20L); // Start after 5 seconds, check every second
    }

    /**
     * Enhanced method to ensure all players are on teams with better logging
     */
    private void ensureAllPlayersOnTeams() {
        int soloTeamsCreated = 0;
        int playersAlreadyOnTeams = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!teamManager.isPlayerOnTeam(player.getUniqueId())) {
                // Create a solo team for this player
                String teamName = isSoloMode() ? player.getName() : player.getName() + "'s Team";
                UHCTeam soloTeam = teamManager.createTeam(teamName);
                if (soloTeam != null) {
                    teamManager.addPlayerToTeam(player.getUniqueId(), soloTeam.getTeamId());
                    soloTeamsCreated++;
                    UHC.getInstance().getLogger().info("Created team for unassigned player: " + player.getName());
                } else {
                    UHC.getInstance().getLogger().warning("Failed to create team for player: " + player.getName());
                }
            } else {
                playersAlreadyOnTeams++;
            }
        }

        if (soloTeamsCreated > 0) {
            UHC.getInstance().getLogger().info("Created " + soloTeamsCreated + " teams for unassigned players");
        }

        if (playersAlreadyOnTeams > 0) {
            UHC.getInstance().getLogger().info(playersAlreadyOnTeams + " players were already on teams");
        }

        // Log final team distribution
        Collection<UHCTeam> allTeams = teamManager.getAllTeams();
        int teamsWithPlayers = 0;
        int totalPlayersOnTeams = 0;

        for (UHCTeam team : allTeams) {
            if (!team.getOnlineMembers().isEmpty()) {
                teamsWithPlayers++;
                totalPlayersOnTeams += team.getOnlineMembers().size();
            }
        }

        UHC.getInstance().getLogger().info("Team assignment complete: " + teamsWithPlayers +
                " teams with " + totalPlayersOnTeams + " total players");
    }

    // Add this method to provide scatter statistics after completion
    public void onScatterCompleted(ProgressiveScatterManager.ScatterStatistics stats) {
        UHC.getInstance().getLogger().info("=== Scatter Statistics ===");
        UHC.getInstance().getLogger().info("Successful teams: " + stats.successfulTeams);
        UHC.getInstance().getLogger().info("Failed teams: " + stats.failedTeams);
        UHC.getInstance().getLogger().info("Total attempts: " + stats.totalAttempts);
        UHC.getInstance().getLogger().info("Used radius: " + stats.usedRadius);
        UHC.getInstance().getLogger().info("Min distance between teams: " + stats.minDistanceBetweenTeams);

        if (stats.failedTeams > 0) {
            UHC.getInstance().getLogger().info("Most common failure: " + stats.mostCommonFailureReason);
        }

        UHC.getInstance().getLogger().info("========================");
    }

    /**
     * Emergency scatter fallback - teleport all teams to spawn area with offsets
     */
    public void emergencyScatterFallback() {
        UHC.getInstance().getLogger().warning("Using emergency scatter fallback!");

        World world = getWorld();
        if (world == null) {
            UHC.getInstance().getLogger().severe("Cannot perform emergency scatter - world is null!");
            return;
        }

        Location spawn = world.getSpawnLocation();
        Random random = new Random();
        int teamIndex = 0;

        for (UHCTeam team : teamManager.getAllTeams()) {
            if (team.getOnlineMembers().isEmpty()) {
                continue;
            }

            // Create offset location based on team index
            double angle = (teamIndex * 45) * Math.PI / 180; // 8 directions
            double distance = 50 + (teamIndex * 20); // Increasing distance

            int x = (int) (spawn.getX() + distance * Math.cos(angle));
            int z = (int) (spawn.getZ() + distance * Math.sin(angle));

            Location teamLocation = new Location(world, x + 0.5, 0, z + 0.5);
            int groundY = world.getHighestBlockYAt(teamLocation);
            teamLocation.setY(groundY + 1);

            // Ensure area is safe
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        world.getBlockAt(x + dx, groundY + dy, z + dz).setType(Material.AIR);
                    }
                }
            }
            world.getBlockAt(x, groundY - 1, z).setType(Material.STONE);

            // Teleport team members
            for (Player player : team.getOnlineMembers()) {
                player.teleport(teamLocation);
                player.sendMessage(ChatColor.YELLOW + "Emergency scatter - you have been placed near spawn!");
            }

            teamIndex++;
        }

        Bukkit.broadcastMessage(ChatColor.YELLOW + "Emergency scatter completed - all teams placed near spawn!");
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

        enableTeamNameTags();
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

        // Announce game start with team info
        int aliveTeams = teamManager.getAliveTeams().size();
        int totalPlayers = 0;
        for (UHCTeam team : teamManager.getAllTeams()) {
            totalPlayers += team.getSize();
        }

        if (maxTeamSize == 1) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Solo UHC started! " + totalPlayers + " players competing.");
        } else {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Team UHC started! " + aliveTeams + " teams (" + totalPlayers + " players) competing.");
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

            int aliveTeams = teamManager.getAliveTeams().size();
            Bukkit.broadcastMessage(ChatColor.GOLD + "Border built with size: " + borderSize +
                    " (" + aliveTeams + " teams remaining)");
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
            int aliveTeams = teamManager.getAliveTeams().size();

            Bukkit.broadcastMessage(ChatColor.YELLOW + "Border shrunk from " + oldSize + " to " + currentBorderSize +
                    ChatColor.GRAY + " (World: " + world.getName() + ", Players: " + playersInWorld +
                    ", Teams: " + aliveTeams + ")");
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
        teamManager.removePlayerFromTeam(uuid);
    }

    public UHCPlayer getUHCPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public boolean containsUHCPlayer(UUID uuid) {
        return players.containsKey(uuid);
    }

    /**
     * Set maximum team size (1 = solo mode, >1 = team mode)
     */
    public void setMaxTeamSize(int maxTeamSize) {
        this.maxTeamSize = Math.max(1, maxTeamSize);
        teamManager.getConfig().setMaxTeamSize(this.maxTeamSize);

        if (maxTeamSize == 1) {
            UHC.getInstance().getLogger().info("Game mode set to SOLO (team size: 1)");
        } else {
            UHC.getInstance().getLogger().info("Game mode set to TEAMS (max team size: " + maxTeamSize + ")");
        }
    }

    /**
     * Check if game is in solo mode
     */
    public boolean isSoloMode() {
        return maxTeamSize == 1;
    }

    /**
     * Check if game is in team mode
     */
    public boolean isTeamMode() {
        return maxTeamSize > 1;
    }

    /**
     * Auto-assign players to teams based on max team size
     */
    public void autoAssignPlayersToTeams() {
        List<UUID> playersWithoutTeams = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!teamManager.isPlayerOnTeam(player.getUniqueId())) {
                playersWithoutTeams.add(player.getUniqueId());
            }
        }

        if (playersWithoutTeams.isEmpty()) {
            return;
        }

        if (isSoloMode()) {
            // Create individual teams for each player
            ensureAllPlayersOnTeams();
        } else {
            // Use team manager's auto-assign feature
            teamManager.autoAssignTeams(playersWithoutTeams, maxTeamSize);
        }
    }

    // Add to Game.java class

    /**
     * Check if the game should end based on remaining players/teams
     * Called after player deaths or eliminations
     */
    /**
     * Check if the game should end based on remaining players/teams
     * Called after player deaths or eliminations
     * Enhanced with better error handling
     */
    public void checkGameEndCondition() {
        try {
            if (!isGameStarted()) {
                return; // Game hasn't started yet
            }

            if (!(state instanceof ActiveGameState)) {
                return; // Game is not in active state
            }

            GameEndResult result = determineGameEndResult();

            if (result.isShouldEndGame()) {
                UHC.getInstance().getLogger().info("Game end condition met: " + result.getReason());
                endGame(result);
            }

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error checking game end condition: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Game end condition check error", e);

            // Don't let this crash the game - just log the error
            // The game will continue running if there's an error in end detection
        }
    }

    /**
     * Determine if the game should end and what the result should be
     * Enhanced with better null checks and error handling
     */
    private GameEndResult determineGameEndResult() {
        try {
            if (isSoloMode()) {
                return checkSoloGameEnd();
            } else {
                return checkTeamGameEnd();
            }
        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error determining game end result: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Game end result determination error", e);

            // Return continue game as fallback
            return GameEndResult.continueGame();
        }
    }

    /**
     * Check end conditions for solo mode
     * Enhanced with better error handling
     */
    private GameEndResult checkSoloGameEnd() {
        try {
            List<UHCPlayer> alivePlayers = new ArrayList<>();

            // Safely get alive players
            for (UHCPlayer uhcPlayer : players.values()) {
                if (uhcPlayer != null &&
                        (uhcPlayer.getState() == PlayerState.ALIVE || uhcPlayer.getState() == PlayerState.COMBAT_LOG)) {
                    alivePlayers.add(uhcPlayer);
                }
            }

            if (alivePlayers.isEmpty()) {
                // No players left - draw
                return GameEndResult.draw("No players remaining");
            } else if (alivePlayers.size() == 1) {
                // One player left - winner
                UHCPlayer winner = alivePlayers.get(0);
                return GameEndResult.soloWin(winner, "Last player standing");
            }

            // Game continues
            return GameEndResult.continueGame();

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error in solo game end check: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Solo game end check error", e);
            return GameEndResult.continueGame();
        }
    }

    /**
     * Check end conditions for team mode
     * Enhanced with better error handling
     */
    private GameEndResult checkTeamGameEnd() {
        try {
            if (teamManager == null) {
                UHC.getInstance().getLogger().warning("TeamManager is null during game end check!");
                return GameEndResult.continueGame();
            }

            List<UHCTeam> aliveTeams = teamManager.getAliveTeams();

            if (aliveTeams == null) {
                UHC.getInstance().getLogger().warning("getAliveTeams() returned null!");
                return GameEndResult.continueGame();
            }

            if (aliveTeams.isEmpty()) {
                // No teams left - draw
                return GameEndResult.draw("No teams remaining");
            } else if (aliveTeams.size() == 1) {
                // One team left - winner
                UHCTeam winnerTeam = aliveTeams.get(0);
                return GameEndResult.teamWin(winnerTeam, "Last team standing");
            }

            // Game continues
            return GameEndResult.continueGame();

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error in team game end check: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Team game end check error", e);
            return GameEndResult.continueGame();
        }
    }

    /**
     * Announce game results to all players
     */
    private void announceGameResults(GameEndResult result) {
        String announcement = formatGameEndAnnouncement(result);

        // Broadcast to all players
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "                        GAME OVER");
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(announcement);
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Game Duration: " + getFormattedGameDuration());

        if (result.getWinnerType() == GameEndResult.WinnerType.SOLO) {
            UHCPlayer winner = result.getSoloWinner();
            if (winner != null) {
                Bukkit.broadcastMessage(ChatColor.GRAY + "Kills: " + winner.getKills());
            }
        } else if (result.getWinnerType() == GameEndResult.WinnerType.TEAM) {
            UHCTeam winnerTeam = result.getTeamWinner();
            if (winnerTeam != null) {
                int totalKills = winnerTeam.getMembers().stream()
                        .mapToInt(uuid -> {
                            UHCPlayer player = getUHCPlayer(uuid);
                            return player != null ? player.getKills() : 0;
                        })
                        .sum();
                Bukkit.broadcastMessage(ChatColor.GRAY + "Team Kills: " + totalKills);
                Bukkit.broadcastMessage(ChatColor.GRAY + "Team Members: " + winnerTeam.getFormattedMemberList());
            }
        }

        Bukkit.broadcastMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        Bukkit.broadcastMessage("");

        // Send title to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendTitleToPlayer(player, result);
        }
    }

    /**
     * Format the game end announcement based on result type
     */
    private String formatGameEndAnnouncement(GameEndResult result) {
        switch (result.getWinnerType()) {
            case SOLO:
                UHCPlayer winner = result.getSoloWinner();
                String winnerName = winner != null && winner.getPlayer() != null ?
                        winner.getPlayer().getName() : "Unknown";
                return ChatColor.GREEN + "Winner: " + ChatColor.YELLOW + winnerName;

            case TEAM:
                UHCTeam winnerTeam = result.getTeamWinner();
                if (winnerTeam != null) {
                    return ChatColor.GREEN + "Winning Team: " + winnerTeam.getFormattedName();
                }
                return ChatColor.GREEN + "Team Victory!";

            case DRAW:
                return ChatColor.YELLOW + "Game ended in a draw - " + result.getReason();

            default:
                return ChatColor.RED + "Game ended unexpectedly";
        }
    }

    /**
     * Send title to a specific player based on game result
     */
    private void sendTitleToPlayer(Player player, GameEndResult result) {
        String title = "";
        String subtitle = "";
        ChatColor titleColor = ChatColor.YELLOW;

        switch (result.getWinnerType()) {
            case SOLO:
                UHCPlayer winner = result.getSoloWinner();
                if (winner != null && winner.getUuid().equals(player.getUniqueId())) {
                    title = ChatColor.GOLD + "VICTORY!";
                    subtitle = ChatColor.YELLOW + "You won the UHC!";
                    titleColor = ChatColor.GOLD;
                } else {
                    title = ChatColor.RED + "GAME OVER";
                    String winnerName = winner != null && winner.getPlayer() != null ?
                            winner.getPlayer().getName() : "Unknown";
                    subtitle = ChatColor.GRAY + winnerName + " won the game";
                    titleColor = ChatColor.RED;
                }
                break;

            case TEAM:
                UHCTeam winnerTeam = result.getTeamWinner();
                UHCTeam playerTeam = teamManager.getPlayerTeam(player.getUniqueId());

                if (winnerTeam != null && winnerTeam.equals(playerTeam)) {
                    title = ChatColor.GOLD + "VICTORY!";
                    subtitle = ChatColor.YELLOW + "Your team won!";
                    titleColor = ChatColor.GOLD;
                } else {
                    title = ChatColor.RED + "GAME OVER";
                    subtitle = winnerTeam != null ?
                            ChatColor.GRAY + winnerTeam.getTeamName() + " won the game" :
                            ChatColor.GRAY + "Another team won";
                    titleColor = ChatColor.RED;
                }
                break;

            case DRAW:
                title = ChatColor.YELLOW + "DRAW";
                subtitle = ChatColor.GRAY + result.getReason();
                break;
        }

        // Send title using 1.8.8 compatible method
        try {
            // Simple message approach for 1.8.8
            player.sendMessage(title);
            player.sendMessage(subtitle);
        } catch (Exception e) {
            // Fallback to just chat messages
            player.sendMessage(title + " - " + subtitle);
        }
    }

    /**
     * Handle actions after the game has ended
     */
    private void handlePostGameActions(GameEndResult result) {
        // Teleport all players to lobby
        teleportAllPlayersToLobby();

        // Reset world if configured
        if (worldManager.shouldAutoResetWorld()) {
            UHC.getInstance().getLogger().info("Auto-resetting world for next game...");
            resetWorldForNewGame();
        }

        // Log game statistics
        logGameStatistics(result);

        // Cleanup
        cleanupGameResources();
    }

    /**
     * Log detailed game statistics
     */
    private void logGameStatistics(GameEndResult result) {
        UHC.getInstance().getLogger().info("=== GAME STATISTICS ===");
        UHC.getInstance().getLogger().info("Duration: " + getFormattedGameDuration());
        UHC.getInstance().getLogger().info("Total Players: " + players.size());

        if (isSoloMode()) {
            UHC.getInstance().getLogger().info("Game Mode: Solo");
            UHC.getInstance().getLogger().info("Teams: " + teamManager.getAllTeams().size());
        } else {
            UHC.getInstance().getLogger().info("Game Mode: Teams (max size " + maxTeamSize + ")");
            UHC.getInstance().getLogger().info("Teams: " + teamManager.getAllTeams().size());
            UHC.getInstance().getLogger().info("Alive Teams at End: " + teamManager.getAliveTeams().size());
        }

        UHC.getInstance().getLogger().info("Result: " + result.getWinnerType() + " - " + result.getReason());
        UHC.getInstance().getLogger().info("Border Final Size: " + currentBorderSize);
        UHC.getInstance().getLogger().info("PvP Time: " + pvpTime + "s");
        UHC.getInstance().getLogger().info("=========================");
    }

    /**
     * Cleanup game resources
     */
    private void cleanupGameResources() {
        // Clean up combat log villagers
        if (combatLogVillagerManager != null) {
            // Remove any remaining villagers
            for (Map.Entry<org.bukkit.entity.Villager, CombatLogPlayer> entry :
                    combatLogVillagerManager.getCombatLogVillagers().entrySet()) {
                entry.getKey().remove();
            }
        }

        // Cancel any remaining tasks
        GameUtil.cancelAllWallBuilders();

        UHC.getInstance().getLogger().info("Game resources cleaned up");
    }

    /**
     * Get game statistics for external use
     */
    public GameStatistics getGameStatistics() {
        return new GameStatistics(
                getFormattedGameDuration(),
                getGameDurationSeconds(),
                players.size(),
                teamManager.getAllTeams().size(),
                teamManager.getAliveTeams().size(),
                currentBorderSize,
                initialBorderSize,
                isPvpEnabled(),
                isSoloMode() ? "Solo" : "Teams (" + maxTeamSize + ")",
                worldName
        );
    }

    /**
     * Game statistics holder
     */
    public static class GameStatistics {
        public final String formattedDuration;
        public final long durationSeconds;
        public final int totalPlayers;
        public final int totalTeams;
        public final int aliveTeams;
        public final int currentBorderSize;
        public final int initialBorderSize;
        public final boolean pvpEnabled;
        public final String gameMode;
        public final String worldName;

        public GameStatistics(String formattedDuration, long durationSeconds, int totalPlayers,
                              int totalTeams, int aliveTeams, int currentBorderSize,
                              int initialBorderSize, boolean pvpEnabled, String gameMode, String worldName) {
            this.formattedDuration = formattedDuration;
            this.durationSeconds = durationSeconds;
            this.totalPlayers = totalPlayers;
            this.totalTeams = totalTeams;
            this.aliveTeams = aliveTeams;
            this.currentBorderSize = currentBorderSize;
            this.initialBorderSize = initialBorderSize;
            this.pvpEnabled = pvpEnabled;
            this.gameMode = gameMode;
            this.worldName = worldName;
        }
    }
}
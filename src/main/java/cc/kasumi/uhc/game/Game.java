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
import cc.kasumi.uhc.util.GameUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

import java.util.*;

@Getter
@Setter
public class Game {

    private final Map<UUID, UHCPlayer> players = new HashMap<>();
    private final CombatLogVillagerManager combatLogVillagerManager = new CombatLogVillagerManager(this);

    private GameState state = new WaitingGameState(this);


    private int maxPlayers = 100;
    private int pvpTime = 20 * 60;
    private int healTime = 10 * 60;
    private int starterFood = 10;

    //border info
    private int initialBorderSize = 1000;
    private int currentBorderSize = initialBorderSize;
    private int shrinkInterval = 5 * 60;
    private int shrinkBorderUntil = 25;
    private int shrinkInitialBorder = 30 * 60;
    private int finalBorderSize = 25;

    private long startTimeMillis;

    private boolean pvpEnabled = false;

    private String worldName = "world";

    private boolean startCountdownStarted = false;

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

    public void startScattering() {
        setGameState(new ScatteringGameState(this));
        new ScatterRunnable(this, getScatterPlayerUUIDs()).runTaskTimer(UHC.getInstance(), 0, 20);
    }

    public void startGame() {
        setGameState(new ActiveGameState(this));

        this.startTimeMillis = System.currentTimeMillis();

        buildSetInitialBorder();

        new FinalHealTask(this, getHealTime()).schedule();
        new PvPEnableTask(this, getPvpTime()).schedule();
        new BorderShrinkTask(this, getShrinkInitialBorder()).schedule();
        players.forEach((uuid, uhcPlayer) -> uhcPlayer.setPlayerStateAndManage(PlayerState.ALIVE));
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

    private void initWorldEnvironment(World world) {
        world.setTime(0);
        world.setWeatherDuration(0);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doWeatherCycle",  "false");
        world.setGameRuleValue("naturalRegeneration", "false");
    }

    private void setWorldBorder(int borderSize) {
        World world = Bukkit.getWorld(worldName);
        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setCenter(0.5, 0.5);  // Offset center by 0.5
        worldBorder.setSize(borderSize * 2 - 1.5);

        for (Player player : world.getPlayers()) {
            if (!GameUtil.isEntityInBorder(player, worldBorder)) {
                GameUtil.teleportToNearestBorderPoint(player, worldBorder);
            }
        }

        for (Map.Entry<Villager, CombatLogPlayer> villagerCombatLogPlayerEntry :combatLogVillagerManager.getCombatLogVillagers().entrySet()) {
            Villager villager = villagerCombatLogPlayerEntry.getKey();
            CombatLogPlayer combatLogPlayer = villagerCombatLogPlayerEntry.getValue();

            if (!GameUtil.isEntityInBorder(villager, worldBorder)) {
                combatLogPlayer.setMoved(true);
                combatLogPlayer.setLocation(GameUtil.teleportToNearestBorderPoint(villager, worldBorder));
                //*todo unload the old chunk and make new chunk not unload
            }
        }
    }

    private void buildSetBorder(int borderSize) {
        setWorldBorder(borderSize);
        setCurrentBorderSize(borderSize);
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

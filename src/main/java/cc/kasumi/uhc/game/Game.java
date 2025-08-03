package cc.kasumi.uhc.game;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.state.ScatteringGameState;
import cc.kasumi.uhc.game.state.WaitingGameState;
import cc.kasumi.uhc.game.task.*;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

@Getter
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

    @Setter
    private boolean pvpEnabled = false;

    @Setter
    private String worldName = "world";

    private boolean startCountdownStarted = false;

    public Game() {
        this.state.onEnable();
        executeWorldBorderCommand(initialBorderSize, worldName);
        initWorldEnvironment(Bukkit.getWorld(worldName));
    }

    public void setGameState(GameState state) {
        this.state.onDisable();
        this.state = state;
        state.onEnable();
    }

    public void gameStartRunnable(int time) {
        this.startCountdownStarted = true;
        Bukkit.getScheduler().runTask(UHC.getInstance(), new StartTask(this, time));
    }

    public void startScattering() {
        setGameState(new ScatteringGameState(this));
        new ScatterRunnable(this, getScatterPlayerUUIDs()).runTaskTimer(UHC.getInstance(), 0, 20);
    }

    public void startGame() {
        setGameState(new ActiveGameState(this));

        this.startTimeMillis = System.currentTimeMillis();
        executeWorldBorderCommand(initialBorderSize, worldName);
        Bukkit.getScheduler().runTask(UHC.getInstance(), new FinalHealTask(this, getHealTime()));
        Bukkit.getScheduler().runTask(UHC.getInstance(), new PvPEnableTask(this, getPvpTime()));
        Bukkit.getScheduler().runTask(UHC.getInstance(), new BorderShrinkTask(this, getShrinkInitialBorder()));
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

    public void executeWorldBorderCommand(int borderSize, String worldName) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "wb " + worldName + " setcorners " + borderSize + " " + borderSize + " -" + borderSize + " -" + borderSize);
    }

    public void shrinkBorder() {
        this.currentBorderSize = getNextBorder();
        executeWorldBorderCommand(currentBorderSize, worldName);
    }

    public int getNextBorder() {
        return currentBorderSize > 500 ? currentBorderSize - 500 : currentBorderSize >= 500 ? currentBorderSize / 2 : currentBorderSize >= 250 ? currentBorderSize - 150 : currentBorderSize / 2;
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

package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.util.ProgressiveScatterManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ScatterRunnable extends BukkitRunnable {

    private final Game game;
    private final ProgressiveScatterManager scatterManager;

    public ScatterRunnable(Game game, Set<UUID> uuids) {
        this.game = game;

        // Convert Set to List for the scatter manager
        List<UUID> playerList = new ArrayList<>(uuids);

        // Create the progressive scatter manager
        this.scatterManager = new ProgressiveScatterManager(game, playerList, game.getInitialBorderSize());
    }

    @Override
    public void run() {
        // Start the progressive scattering process
        scatterManager.startScattering();

        // Cancel this runnable since ProgressiveScatterManager handles everything
        cancel();
    }
}
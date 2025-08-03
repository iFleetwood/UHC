package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.util.GameUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class ScatterRunnable extends BukkitRunnable {

    private final Game game;
    private final Iterator<UUID> uuids;

    public ScatterRunnable(Game game, Set<UUID> uuids) {
        this.game = game;
        this.uuids = uuids.iterator();
    }

    @Override
    public void run() {
        if (!uuids.hasNext()) {
            game.startGame();

            cancel();
            return;
        }


        Player player = Bukkit.getPlayer(uuids.next());

        if (player == null) {
            return;
        }

        player.teleport(GameUtil.getScatterLocation(UHC.getInstance().getGame().getInitialBorderSize(), "world"));
    }
}

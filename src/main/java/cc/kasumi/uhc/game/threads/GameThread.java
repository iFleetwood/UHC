package cc.kasumi.uhc.game.threads;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.UHCGame;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

@Getter
public class GameThread implements Runnable {

    private final UHCGame game;

    public GameThread(UHCGame game) {
        this.game = game;
    }

    @Override
    public void run() {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskAsynchronously(UHC.getInstance(), new BorderThread(game));
    }
}

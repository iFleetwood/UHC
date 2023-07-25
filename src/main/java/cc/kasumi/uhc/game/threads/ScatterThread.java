package cc.kasumi.uhc.game.threads;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.UHCGame;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

public class ScatterThread implements Runnable {

    private final UHCGame game;
    private int time;

    public ScatterThread(UHCGame game) {
        this.game = game;
    }

    @Override
    public void run() {
        BukkitScheduler scheduler = Bukkit.getScheduler();

        if (time > 60) {
            if (time % 60 == 0) {
                Bukkit.broadcastMessage("scattering in: " + time / 60 + " minute(s)");
            }


        } else {
            if (time == 0) {
                Bukkit.getScheduler().runTask(UHC.getInstance(), new ScatterTeams(game));

                return;
            }

            if (time <= 10 || time == 30) {
                Bukkit.broadcastMessage("scattering in: " + time + " second(s");
            }
        }

        time--;
        scheduler.runTaskLaterAsynchronously(UHC.getInstance(), this, 20);
    }
}

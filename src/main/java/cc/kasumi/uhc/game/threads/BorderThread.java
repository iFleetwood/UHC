package cc.kasumi.uhc.game.threads;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.UHCGame;
import org.bukkit.Bukkit;

public class BorderThread implements Runnable {

    private UHCGame game;
    private int time;

    public BorderThread(UHCGame game) {
        this.game = game;
        time = game.getBeforeTimeInterval();
    }

    @Override
    public void run() {
        if (time > 60) {
            if (time % 60 == 0) {
                Bukkit.broadcastMessage("shrinking in: " + time / 60 + " minute(s)");
            }


        } else {
            if (time == 0) {
                Bukkit.getScheduler().runTask(UHC.getInstance(), new BorderShrink(game));

                return;
            }

            if (time <= 10 || time == 30) {
                Bukkit.broadcastMessage("shrinking in: " + time + " second(s)");
            }
        }

        time--;
        Bukkit.getScheduler().runTaskLaterAsynchronously(UHC.getInstance(), this, 20);
    }
}

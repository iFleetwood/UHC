package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

public abstract class CountDownTask implements Runnable {

    protected final Game game;
    protected int timeBefore;

    public CountDownTask(Game game, int timeBefore) {
        this.game = game;
        this.timeBefore = timeBefore;
    }

    public abstract boolean cancelBoolean();
    public abstract void getMinutesLeftAction();
    public abstract void getSecondsLeftAction();
    public abstract void getFinalAction();

    @Override
    public void run() {
        if (cancelBoolean()) {
            return;
        }

        if (timeBefore == 0) {
            getFinalAction();
            return; // Stop thread
        }

        if (timeBefore <= 10 || (timeBefore < 60 * 5 && timeBefore % 60 == 0) || timeBefore % (60 * 5) == 0) {
            if (timeBefore % 60 == 0) {
                getMinutesLeftAction();
            } else {
                getSecondsLeftAction();
            }
        }

        if (timeBefore >= 20) {
            timeBefore -= 10;
            Bukkit.getScheduler().runTaskLater(UHC.getInstance(), this, 200);
        } else {
            timeBefore--;
            Bukkit.getScheduler().runTaskLater(UHC.getInstance(), this, 20);
        }
    }

    /**
     * Schedules this task to run immediately
     */
    public void schedule() {
        Bukkit.getScheduler().runTask(UHC.getInstance(), this);
    }
}
package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

public class StartTask extends CountDownTask {

    public StartTask(Game game, int timeBefore) {
        super(game, timeBefore);
    }

    @Override
    public boolean cancelBoolean() {
        return false;
    }

    @Override
    public void getMinutesLeftAction() {
        Bukkit.broadcastMessage("Scattering in: " + timeBefore / 60 + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage("Scattering in: " + timeBefore + " second(s)");
    }

    @Override
    public void getFinalAction() {
        game.startScattering();
        Bukkit.broadcastMessage("Starting scattering!");
    }
}

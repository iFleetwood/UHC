package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

import static cc.kasumi.uhc.UHCConfiguration.*;

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
        Bukkit.broadcastMessage(MAIN_COLOR + "Scattering in: " + SEC_COLOR + timeBefore / 60 + MAIN_COLOR + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage(MAIN_COLOR + "Scattering in: " + SEC_COLOR + timeBefore + MAIN_COLOR + " second(s)");
    }

    @Override
    public void getFinalAction() {
        game.startScattering();
        Bukkit.broadcastMessage(MAIN_COLOR + "Starting scattering!");
    }
}

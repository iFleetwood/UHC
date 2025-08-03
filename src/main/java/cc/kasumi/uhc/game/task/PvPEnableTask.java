package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

public class PvPEnableTask extends CountDownTask {

    public PvPEnableTask(Game game, int timeBefore) {
        super(game, timeBefore);
    }

    @Override
    public boolean cancelBoolean() {
        return !(game.getState() instanceof ActiveGameState);
    }

    @Override
    public void getMinutesLeftAction() {
        Bukkit.broadcastMessage("PvP is getting enabled in " + timeBefore / 60 + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage("PvP is getting enabled in " + timeBefore + " second(s)");
    }

    @Override
    public void getFinalAction() {
        game.setPvpEnabled(true);
        Bukkit.broadcastMessage("PvP has been enabled!");
    }
}

package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

public class FinalHealTask extends CountDownTask {

    public FinalHealTask(Game game, int timeBefore) {
        super(game, timeBefore);
    }

    @Override
    public boolean cancelBoolean() {
        return !(game.getState() instanceof ActiveGameState);
    }

    @Override
    public void getMinutesLeftAction() {
        Bukkit.broadcastMessage("Final heal in: " + timeBefore / 60 + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage("Final heal in: " + timeBefore + " second(s)");
    }

    @Override
    public void getFinalAction() {
        game.healAlivePlayers();
        Bukkit.broadcastMessage("Final heal has been given!");
    }
}

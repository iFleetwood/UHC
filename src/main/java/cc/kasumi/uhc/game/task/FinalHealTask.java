package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

import static cc.kasumi.uhc.UHCConfiguration.*;

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
        Bukkit.broadcastMessage(MAIN_COLOR + "Final heal in: " + SEC_COLOR + timeBefore / 60 + MAIN_COLOR + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage(MAIN_COLOR + "Final heal in: " + SEC_COLOR + timeBefore + MAIN_COLOR + " second(s)");
    }

    @Override
    public void getFinalAction() {
        game.healAlivePlayers();
        Bukkit.broadcastMessage(MAIN_COLOR + "Final heal has been given!");
    }
}

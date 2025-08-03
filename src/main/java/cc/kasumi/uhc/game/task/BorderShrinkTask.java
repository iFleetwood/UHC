package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

public class BorderShrinkTask extends CountDownTask {

    public BorderShrinkTask(Game game, int timeBefore) {
        super(game, timeBefore);
    }

    @Override
    public boolean cancelBoolean() {
        return !(game.getState() instanceof ActiveGameState);
    }

    @Override
    public void getMinutesLeftAction() {
        Bukkit.broadcastMessage("Border shrinking from " + game.getCurrentBorderSize() + " to " + game.getNextBorder() + " in " + timeBefore / 60 + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage("Border shrinking from " + game.getCurrentBorderSize() + " to " + game.getNextBorder() + " in " + timeBefore + " second(s)");
    }

    @Override
    public void getFinalAction() {
        int oldSize = game.getCurrentBorderSize();
        game.shrinkBorder();
        Bukkit.broadcastMessage("Border shrank from " + oldSize + " to " + game.getCurrentBorderSize());

        if (game.canBorderShrinkMore()) {
            Bukkit.broadcastMessage("");
            timeBefore = game.getShrinkInterval();

            Bukkit.getScheduler().runTask(UHC.getInstance(), this);
        }
    }
}

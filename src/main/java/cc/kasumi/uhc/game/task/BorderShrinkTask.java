package cc.kasumi.uhc.game.task;

import cc.kasumi.uhc.game.state.ActiveGameState;
import cc.kasumi.uhc.game.Game;
import org.bukkit.Bukkit;

import static cc.kasumi.uhc.UHCConfiguration.*;

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
        Bukkit.broadcastMessage(MAIN_COLOR + "Border shrinking from " + SEC_COLOR + game.getCurrentBorderSize() + MAIN_COLOR +
                " to " + SEC_COLOR + game.getNextBorder() + MAIN_COLOR + " in " + SEC_COLOR + timeBefore / 60 + MAIN_COLOR + " minute(s)");
    }

    @Override
    public void getSecondsLeftAction() {
        Bukkit.broadcastMessage(MAIN_COLOR + "Border shrinking from " + SEC_COLOR + game.getCurrentBorderSize() + MAIN_COLOR +
                " to " + SEC_COLOR + game.getNextBorder() + MAIN_COLOR + " in "+ SEC_COLOR + timeBefore + MAIN_COLOR + " second(s)");
    }

    @Override
    public void getFinalAction() {
        int oldSize = game.getCurrentBorderSize();
        game.shrinkBorder();
        Bukkit.broadcastMessage(MAIN_COLOR + "Border shrank from " + SEC_COLOR + oldSize + MAIN_COLOR + " to " + SEC_COLOR + game.getCurrentBorderSize());

        if (game.canBorderShrinkMore()) {
            Bukkit.broadcastMessage("");
            // Schedule a NEW BorderShrinkTask instead of reusing this one
            new BorderShrinkTask(game, game.getShrinkInterval()).schedule();
        }
    }
}
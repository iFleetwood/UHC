package cc.kasumi.uhc.game.threads;

import cc.kasumi.uhc.game.UHCGame;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class BorderShrink implements Runnable {

    private final UHCGame game;

    public BorderShrink(UHCGame game) {
        this.game = game;
    }

    @Override
    public void run() {
        game.shrinkBorder(game.getNextBorder(), new Location(Bukkit.getWorld("world"), 0, 0, 0));
    }
}

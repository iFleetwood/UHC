package cc.kasumi.uhc.game.threads;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.UHCGame;
import cc.kasumi.uhc.game.UHCGameStatus;
import cc.kasumi.uhc.team.UHCTeam;
import cc.kasumi.uhc.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ScatterTeams implements Runnable {

    private final UHCGame game;
    private final Iterator<UHCTeam> teamIterator;

    public ScatterTeams(UHCGame game) {
        this.game = game;
        teamIterator = game.getTeams().iterator();
    }

    @Override
    public void run() {
        UHCTeam team = teamIterator.next();

        game.scatterTeam(team);
        teamIterator.remove();

        if (teamIterator.hasNext()) {
            Bukkit.getScheduler().runTaskLater(UHC.getInstance(), this, 10);
        }
    }
}

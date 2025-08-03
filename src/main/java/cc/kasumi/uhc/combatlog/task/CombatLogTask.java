package cc.kasumi.uhc.combatlog.task;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class CombatLogTask extends BukkitRunnable {

    private final UUID uuid;

    public CombatLogTask(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void run() {
        Game game = UHC.getInstance().getGame();

        game.getCombatLogVillagerManager().deSpawnAndKillCombatLogVillager(uuid);
        game.removePlayer(uuid);
    }
}

package cc.kasumi.uhc.combatlog.task;

import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import org.bukkit.Location;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

public class CombatVillagerCheckTask extends BukkitRunnable {

    private final CombatLogVillagerManager combatLogVillagerManager;

    public CombatVillagerCheckTask(CombatLogVillagerManager combatLogVillagerManager) {
        this.combatLogVillagerManager = combatLogVillagerManager;
    }

    @Override
    public void run() {
        for (Map.Entry<Villager, CombatLogPlayer> villagerCombatLogPlayerEntry : combatLogVillagerManager.getCombatLogVillagers().entrySet()) {
            Villager villager = villagerCombatLogPlayerEntry.getKey();
            CombatLogPlayer combatLogPlayer = villagerCombatLogPlayerEntry.getValue();
            Location villagerLocation = villager.getLocation();
            Location combatLogPlayerLocation = combatLogPlayer.getLocation();

            if (villagerLocation.equals(combatLogPlayerLocation)) {
                continue;
            }

            villager.teleport(combatLogPlayerLocation);
        }
    }
}

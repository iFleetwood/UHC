package cc.kasumi.uhc.world.listener;

import cc.kasumi.uhc.world.custom.CaveSettings;
import cc.kasumi.uhc.world.custom.GiantCave;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

public class WorldPopulatorListener implements Listener {

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        if (!CaveSettings.CAVE_ENABLED) {
            return;
        }

        if (event.getWorld().getName().equalsIgnoreCase("uhc")) {
            event.getWorld().getPopulators().add(new GiantCave());
        }
    }
}

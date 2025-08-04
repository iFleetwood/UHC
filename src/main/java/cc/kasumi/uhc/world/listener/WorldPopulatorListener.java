package cc.kasumi.uhc.world.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.world.WorldManager;
import cc.kasumi.uhc.world.WorldPopulatorManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.logging.Logger;

/**
 * Enhanced WorldPopulatorListener that integrates with WorldManager
 * Handles automatic populator addition to UHC worlds
 */
public class WorldPopulatorListener implements Listener {

    private final Logger logger;

    public WorldPopulatorListener() {
        this.logger = UHC.getInstance().getLogger();
    }

    /**
     * Handle world initialization - add populators before world generation
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        logger.info("World initializing: " + worldName);

        // Get WorldManager instance
        UHC uhcPlugin = UHC.getInstance();
        if (uhcPlugin == null) {
            logger.warning("UHC plugin instance not available during world init");
            return;
        }

        WorldManager worldManager = uhcPlugin.getWorldManager();
        if (worldManager == null) {
            logger.warning("WorldManager not available during world init");
            return;
        }

        WorldPopulatorManager populatorManager = worldManager.getPopulatorManager();
        if (populatorManager == null) {
            logger.warning("WorldPopulatorManager not available during world init");
            return;
        }

        // Check if this world should have populators
        if (populatorManager.shouldAddPopulators(world)) {
            logger.info("Adding populators to world during initialization: " + worldName);
            populatorManager.addPopulatorsToWorld(world);
        } else {
            logger.fine("Skipping populator addition for world: " + worldName);
        }
    }

    /**
     * Handle world loading - ensure populators are present
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        logger.info("World loaded: " + worldName);

        // Get WorldManager instance
        UHC uhcPlugin = UHC.getInstance();
        if (uhcPlugin == null || uhcPlugin.getWorldManager() == null) {
            return;
        }

        WorldManager worldManager = uhcPlugin.getWorldManager();
        WorldPopulatorManager populatorManager = worldManager.getPopulatorManager();

        if (populatorManager == null) {
            return;
        }

        // Check if this is a UHC world that might be missing populators
        if (populatorManager.shouldAddPopulators(world)) {
            int currentPopulators = world.getPopulators().size();
            int registeredPopulators = populatorManager.getRegisteredPopulators().size();

            // If the world has fewer populators than expected, add missing ones
            if (currentPopulators < registeredPopulators) {
                logger.info("World " + worldName + " is missing populators (" +
                        currentPopulators + "/" + registeredPopulators + "), adding them...");
                populatorManager.addPopulatorsToWorld(world);
            } else {
                logger.fine("World " + worldName + " has correct number of populators: " + currentPopulators);
            }
        }
    }

    /**
     * Handle world unloading - cleanup if needed
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        logger.info("World unloading: " + worldName);

        // Get WorldManager instance
        UHC uhcPlugin = UHC.getInstance();
        if (uhcPlugin == null || uhcPlugin.getWorldManager() == null) {
            return;
        }

        WorldManager worldManager = uhcPlugin.getWorldManager();
        WorldPopulatorManager populatorManager = worldManager.getPopulatorManager();

        if (populatorManager == null) {
            return;
        }

        // Perform any necessary cleanup for UHC worlds
        if (populatorManager.shouldAddPopulators(world)) {
            logger.info("Cleaning up populators for unloading UHC world: " + worldName);
            // Any cleanup code here if needed
        }
    }

    /**
     * Get statistics about world populator management
     */
    public String getPopulatorStats() {
        UHC uhcPlugin = UHC.getInstance();
        if (uhcPlugin == null || uhcPlugin.getWorldManager() == null) {
            return "WorldManager not available";
        }

        WorldPopulatorManager populatorManager = uhcPlugin.getWorldManager().getPopulatorManager();
        if (populatorManager == null) {
            return "WorldPopulatorManager not available";
        }

        WorldPopulatorManager.PopulatorStats stats = populatorManager.getPopulatorStats();

        return String.format("Populators: %d registered, %d giant caves active, caves enabled: %s",
                stats.totalRegistered,
                stats.activeGiantCaves,
                stats.giantCavesEnabled);
    }
}
package cc.kasumi.uhc.scenario.type.listener;

import cc.kasumi.uhc.scenario.BaseScenarioListener;
import cc.kasumi.uhc.scenario.Scenario;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

public class CutCleanListener extends BaseScenarioListener {

    public CutCleanListener(Scenario scenario) {
        super(scenario);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isScenarioActive()) return;

        Block block = event.getBlock();
        Material type = block.getType();

        // Replace raw ores with smelted versions
        switch (type) {
            case IRON_ORE:
                event.setCancelled(true);
                block.setType(Material.AIR);
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.IRON_INGOT));
                break;
            case GOLD_ORE:
                event.setCancelled(true);
                block.setType(Material.AIR);
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.GOLD_INGOT));
                break;
            // Add more ore types as needed
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isScenarioActive()) return;

        // Replace raw food with cooked versions
        if (event.getEntityType() == EntityType.COW || event.getEntityType() == EntityType.PIG) {
            event.getDrops().clear();

            if (event.getEntityType() == EntityType.COW) {
                event.getDrops().add(new ItemStack(Material.COOKED_BEEF, 1 + (int)(Math.random() * 3)));
                event.getDrops().add(new ItemStack(Material.LEATHER, 1 + (int)(Math.random() * 3)));
            } else if (event.getEntityType() == EntityType.PIG) {
                event.getDrops().add(new ItemStack(Material.GRILLED_PORK, 1 + (int)(Math.random() * 3)));
            }
        }
    }
}

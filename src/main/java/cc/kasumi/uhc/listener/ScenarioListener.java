package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.scenario.CutCleanBlock;
import cc.kasumi.uhc.scenario.Scenario;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ScenarioListener implements Listener {

    private static final Map<String, Scenario> scenarios = UHC.getScenarios();
    private final Map<Material, CutCleanBlock> cutCleanBlocks = new HashMap<>();

    public ScenarioListener() {
        cutCleanBlocks.put(Material.IRON_ORE, new CutCleanBlock(Material.IRON_ORE, Material.IRON_INGOT, 1));
        cutCleanBlocks.put(Material.GOLD_ORE, new CutCleanBlock(Material.GOLD_ORE, Material.GOLD_INGOT, 2));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();
        Material type = block.getType();

        if (scenarios.get("cutclean").isEnabled() && cutCleanBlocks.containsKey(type)) {
            CutCleanBlock cutCleanBlock = cutCleanBlocks.get(type);

            event.setCancelled(true);
            event.setExpToDrop(cutCleanBlock.getExp()); // Probably doesn't work since the event is cancelled
            block.setType(Material.AIR);
            blockLocation.getWorld().dropItemNaturally(blockLocation,
                    new ItemStack(cutCleanBlock.getReplacement(), 1));
        }
    }
}
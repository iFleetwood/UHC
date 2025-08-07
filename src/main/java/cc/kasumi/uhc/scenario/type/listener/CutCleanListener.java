package cc.kasumi.uhc.scenario.type.listener;

import cc.kasumi.commons.util.ItemBuilder;
import cc.kasumi.uhc.scenario.BaseScenarioListener;
import cc.kasumi.uhc.scenario.Scenario;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.*;

import java.util.Iterator;

public class CutCleanListener extends BaseScenarioListener {

    private final ItemStack lapis;

    private boolean unlimitedLapis = true;
    private boolean checkTool = false;

    public CutCleanListener(Scenario scenario) {
        super(scenario);
        lapis = new ItemBuilder(Material.LAPIS_ORE, 64).build();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        // Hmm, this means that Donkey/Mule chest drops will smelt too...
        // Then again, not the biggest issue, we can just say it's "intended".
        for(int i = 0 ; i < e.getDrops().size(); i++) {
            // Cloned because we may end up having to mutate it, see below
            final ItemStack drop = e.getDrops().get(i).clone();
            // Note: On modern Minecraft versions, we could probably use Server#craftItem,
            // but it doesn't exist on older game versions such as 1.8.8.
            for (Iterator<Recipe> recipes = Bukkit.recipeIterator(); recipes.hasNext();) {
                final Recipe recipe = recipes.next();
                if (recipe instanceof FurnaceRecipe) {
                    // Note: getInputChoice would be more future-proof, but it doesn't exist on
                    // older Minecraft versions such as 1.8.8. Should be fine to ignore it for now.
                    final ItemStack smeltInput = ((FurnaceRecipe) recipe).getInput();
                    // Note	: On older game versions such as 1.8.8, the recipe input ItemStack
                    // may have a damage value of 32767, i.e. Short.MAX_VALUE (for a special reason),
                    // so ItemStack#isSimilar will always return false.
                    // For reference, see: https://www.spigotmc.org/threads/malformed-itemstack.60990/#post-677215
                    if (smeltInput.getDurability() == Short.MAX_VALUE) {
                        drop.setDurability(Short.MAX_VALUE);
                    }

                    if (smeltInput.isSimilar(drop)) {
                        final ItemStack smeltResult = recipe.getResult();
                        smeltResult.setAmount(drop.getAmount());
                        e.getDrops().set(i, smeltResult);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        // Handle only iron and gold ores
        if (type == Material.IRON_ORE || type == Material.GOLD_ORE) {
            event.setCancelled(true);
            block.setType(Material.AIR);

            Material drop = type == Material.IRON_ORE ? Material.IRON_INGOT : Material.GOLD_INGOT;

            // Drop smelted ingot
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(drop, 1));

            // Give furnace-like XP
            ExperienceOrb xp = block.getWorld().spawn(block.getLocation(), ExperienceOrb.class);
            xp.setExperience(2);
        }
    }

    @EventHandler
    public void openInventoryEvent(InventoryOpenEvent event){
        if (event.isCancelled()) {
            return;
        }

        if (!unlimitedLapis) return;

        if (event.getInventory() instanceof EnchantingInventory){
            event.getInventory().setItem(1, lapis);
        }
    }

    @EventHandler
    public void closeInventoryEvent(InventoryCloseEvent event){
        if (!unlimitedLapis) return;

        if (event.getInventory() instanceof EnchantingInventory){
            event.getInventory().setItem(1, null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event){
        if (event.isCancelled()) {
            return;
        }

        Inventory inventory = event.getInventory();
        ItemStack item = event.getCurrentItem();
        if (!unlimitedLapis) return;
        if (inventory == null || item == null) return;

        if (inventory instanceof EnchantingInventory){
            if (event.getRawSlot() == 1) {
                event.setCancelled(true);
            }
        }
    }
}

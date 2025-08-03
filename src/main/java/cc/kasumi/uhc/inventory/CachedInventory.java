package cc.kasumi.uhc.inventory;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

@Getter
public class CachedInventory {

    private Collection<PotionEffect> effects;

    private ItemStack[] contents;
    private ItemStack[] armorContents;

    private double health;
    private int foodLevel;

    public CachedInventory(Player player) {
        PlayerInventory inventory = player.getInventory();

        this.contents = inventory.getContents().clone();
        this.armorContents = inventory.getArmorContents().clone();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.effects = player.getActivePotionEffects();
    }

    public CachedInventory(ItemStack[] contents, ItemStack[] armorContents) {
        this.contents = contents;
        this.armorContents = armorContents;
    }
}

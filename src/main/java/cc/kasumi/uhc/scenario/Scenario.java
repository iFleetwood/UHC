package cc.kasumi.uhc.scenario;

import cc.kasumi.uhc.util.ItemBuilder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

@Getter
public class Scenario {

    private final String name;
    private String displayName;
    private Material displayMaterial;
    private String[] lore;

    @Setter
    private boolean enabled = false;

    public Scenario(String name, String displayName, Material displayMaterial, String[] lore) {
        this.name = name;
        this.displayName = displayName;
        this.displayMaterial = displayMaterial;
        this.lore = lore;
    }

    public ItemStack getDisplayItem() {
        return new ItemBuilder().material(displayMaterial).name(displayName).lore(lore).build();
    }
}

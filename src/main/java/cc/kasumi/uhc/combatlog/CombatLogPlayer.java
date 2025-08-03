package cc.kasumi.uhc.combatlog;

import cc.kasumi.uhc.inventory.CachedInventory;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

@Getter
public class CombatLogPlayer {

    private final UUID uuid;
    private final CachedInventory cachedInventory;
    private final BukkitTask bukkitTask;

    @Setter
    private Location location;

    @Setter
    private boolean moved = false;

    public CombatLogPlayer(UUID uuid, Player player, Location location, BukkitTask bukkitTask) {
        this.uuid = uuid;
        this.cachedInventory = new CachedInventory(player);
        this.location = location;
        this.bukkitTask = bukkitTask;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(this.uuid);
    }
}

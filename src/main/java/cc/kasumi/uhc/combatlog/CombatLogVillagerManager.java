package cc.kasumi.uhc.combatlog;

import cc.kasumi.commons.util.ItemStackUtil;
import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.task.CombatLogTask;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.inventory.CachedInventory;
import cc.kasumi.uhc.player.UHCPlayer;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

@Getter
public class CombatLogVillagerManager {

    private final Map<Villager, CombatLogPlayer> combatLogVillagers = new HashMap<>();
    private final Set<Chunk> combatLogVillagerChunk = new HashSet<>();

    private final Game game;

    @Setter
    private BukkitTask bukkitTask;

    public CombatLogVillagerManager(Game game) {
        this.game = game;
    }

    public void spawnCombatLogVillager(UUID playerUUID, Player player) { // Used for EntityDeathEvent in ActiveGameState
        Chunk playerChunk = player.getLocation().getChunk();
        Location spawnLocation = player.getLocation().clone();

        Villager villager = (Villager) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);
        villager.setCustomName(player.getName());
        villager.setCustomNameVisible(true);
        villager.setHealth(8.0D);
        Villager.Profession prof = Villager.Profession.FARMER;
        villager.setProfession(prof);

        CombatLogPlayer combatLogPlayer = new CombatLogPlayer(playerUUID, player, spawnLocation,
                new CombatLogTask(playerUUID).runTaskLater(UHC.getInstance(), 3 * 60 * 20));


        combatLogVillagers.put(villager, combatLogPlayer);
        combatLogVillagerChunk.add(playerChunk);
    }

    public void deSpawnCombatLogVillager(UUID playerUUID) { // Used for PlayerJoinEvent in ActiveGameState
        Map.Entry<Villager, CombatLogPlayer> villagerCombatLogPlayerEntry = getVillagerMapEntryByPlayerUUID(playerUUID);

        if (villagerCombatLogPlayerEntry == null) {
            return;
        }

        Villager villager = villagerCombatLogPlayerEntry.getKey();
        CombatLogPlayer combatLogPlayer = villagerCombatLogPlayerEntry.getValue();

        combatLogPlayer.getBukkitTask().cancel(); // Cancel task
        combatLogVillagerChunk.remove(villager.getLocation().getChunk());
        combatLogVillagers.remove(villager);
        villager.remove();
    }

    public void deSpawnAndKillCombatLogVillager(UUID playerUUID) { // Used for CombatLogTask
        Map.Entry<Villager, CombatLogPlayer> villagerCombatLogPlayerEntry = getVillagerMapEntryByPlayerUUID(playerUUID);

        if (villagerCombatLogPlayerEntry == null) {
            return;
        }

        Villager villager = villagerCombatLogPlayerEntry.getKey();
        CombatLogPlayer combatLogPlayer = villagerCombatLogPlayerEntry.getValue();

        dropItems(villager, combatLogPlayer);
        combatLogVillagerChunk.remove(villager.getLocation().getChunk());
        combatLogVillagers.remove(villager);
        villager.remove();
    }

    public void killCombatLogVillager(Villager villager, CombatLogPlayer combatLogPlayer) {
        combatLogPlayer.getBukkitTask().cancel();
        dropItems(villager, combatLogPlayer);
        combatLogVillagers.remove(villager);
        combatLogVillagerChunk.remove(villager.getLocation().getChunk());
    }

    public void dropItems(Villager villager, CombatLogPlayer combatLogPlayer) {
        Location location = villager.getLocation().clone();
        World world = location.getWorld();
        CachedInventory cachedInventory = combatLogPlayer.getCachedInventory();

        for (ItemStack content : cachedInventory.getContents()) {
            if (ItemStackUtil.isNullOrAir(content)) continue;

            world.dropItemNaturally(location, content);
        }

        for (ItemStack armorContent : cachedInventory.getArmorContents()) {
            if (ItemStackUtil.isNullOrAir(armorContent)) continue;

            world.dropItemNaturally(location, armorContent);
        }
    }

    public boolean containsVillager(Villager villager) {
        return combatLogVillagers.containsKey(villager);
    }

    public UHCPlayer getUHCPlayerByVillager(Villager villager) {
        return game.getUHCPlayer(combatLogVillagers.get(villager).getUuid());
    }

    public CombatLogPlayer getCombatLogPlayer(Villager villager) {
        return combatLogVillagers.get(villager);
    }

    public Map.Entry<Villager, CombatLogPlayer> getVillagerMapEntryByPlayerUUID(UUID playerUUID) {
        for (Map.Entry<Villager, CombatLogPlayer> villagerCombatLogPlayerEntry : combatLogVillagers.entrySet()) {
            if (!villagerCombatLogPlayerEntry.getValue().getUuid().equals(playerUUID)) {
                continue;
            }

            return villagerCombatLogPlayerEntry;
        }

        return null;
    }
}

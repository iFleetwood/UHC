package cc.kasumi.uhc.game.state;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.combatlog.task.CombatVillagerCheckTask;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.game.GameState;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.UUID;

public class ActiveGameState extends GameState {

    private final CombatLogVillagerManager combatLogVillagerManager = game.getCombatLogVillagerManager();

    public ActiveGameState(Game game) {
        super(game);
    }

    @Override
    public void onEnable() {
        UHC uhc = UHC.getInstance();

        Bukkit.getPluginManager().registerEvents(this, uhc);
        combatLogVillagerManager.setPositionCheckTask(
                new CombatVillagerCheckTask(combatLogVillagerManager).runTaskTimer(uhc, 20, 10)
        );
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        if (combatLogVillagerManager.getPositionCheckTask() != null) {
            combatLogVillagerManager.getPositionCheckTask().cancel();
            combatLogVillagerManager.setPositionCheckTask(null);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);
        PlayerState state = uhcPlayer.getState();

        if (state == PlayerState.COMBAT_LOG) {
            uhcPlayer.setState(PlayerState.ALIVE);
            // TODO Fix so we don't loop twice though the entry set

            CombatLogVillagerManager.CombatLogEntry entry = combatLogVillagerManager.findCombatLogEntry(uuid);
            if (entry != null && entry.getCombatLogPlayer().isMoved()) {
                player.teleport(entry.getCombatLogPlayer().getLocation());
            }

            combatLogVillagerManager.deSpawnCombatLogVillager(uuid);

            return;
        }

        PlayerUtil.resetPlayer(player);
        uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);
        PlayerState playerState = uhcPlayer.getState();

        if (playerState != PlayerState.ALIVE) {
            return;
        }

        // Spawn villager
        uhcPlayer.setState(PlayerState.COMBAT_LOG);
        combatLogVillagerManager.spawnCombatLogVillager(uuid, player);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (!combatLogVillagerManager.getCombatLogVillagers().containsKey(villager)) {
            return;
        }

        CombatLogPlayer combatLogPlayer = combatLogVillagerManager.getCombatLogPlayer(villager);
        UUID playerUUID = combatLogPlayer.getUuid();

        combatLogVillagerManager.killCombatLogVillager(villager, combatLogPlayer);
        game.removePlayer(playerUUID);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);
        uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (combatLogVillagerManager.containsVillagerChunk(event.getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();

        if (!(target instanceof Villager villager) || !combatLogVillagerManager.getCombatLogVillagers().containsKey(villager)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        Entity damager = event.getDamager();

        if (!(entity instanceof Player)) {
            if (!(entity instanceof Villager)) {
                return;
            }

            Villager villager = (Villager) entity;

            if (combatLogVillagerManager.getCombatLogVillagers().containsKey(villager) && !game.isPvpEnabled()) {
                event.setCancelled(true);
            }

            return;
        }

        if (damager instanceof Projectile projectile) {
            if (!(projectile.getShooter() instanceof Player)) {
                return;
            }

        } else if (!(damager instanceof Player)) {
            return;
        }

        if (game.isPvpEnabled()) {
            return;
        }

        event.setCancelled(true);
    }
}

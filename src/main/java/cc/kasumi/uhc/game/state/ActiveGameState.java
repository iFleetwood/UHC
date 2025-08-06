package cc.kasumi.uhc.game.state;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.combatlog.CombatLogPlayer;
import cc.kasumi.uhc.combatlog.CombatLogVillagerManager;
import cc.kasumi.uhc.combatlog.task.CombatVillagerCheckTask;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.game.GameState;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.scenario.ScenarioManager;
import cc.kasumi.uhc.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.UUID;

public class ActiveGameState extends GameState {

    private final CombatLogVillagerManager combatLogVillagerManager = game.getCombatLogVillagerManager();
    private final ScenarioManager scenarioManager = game.getScenarioManager();

    public ActiveGameState(Game game) {
        super(game);
    }

    @Override
    public void onEnable() {
        UHC uhc = UHC.getInstance();

        super.onEnable();

        scenarioManager.registerAllListeners();
        combatLogVillagerManager.setPositionCheckTask(
                new CombatVillagerCheckTask(combatLogVillagerManager).runTaskTimer(uhc, 20, 25)
        );
    }

    @Override
    public void onDisable() {
        super.onDisable();
        scenarioManager.unregisterAllListeners();

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

    // Updated ActiveGameState.java with game end checks

    // Update the event handlers in ActiveGameState.java to be safer

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        try {
            Player player = event.getEntity();
            UUID uuid = player.getUniqueId();

            if (!game.containsUHCPlayer(uuid)) {
                return;
            }

            UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);
            if (uhcPlayer == null) {
                UHC.getInstance().getLogger().warning("UHCPlayer was null for " + player.getName() + " during death event");
                return;
            }

            uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);

            // Handle team elimination
            if (game.getTeamManager() != null) {
                game.getTeamManager().handlePlayerDeath(uuid);
            }

            // Announce death
            announcePlayerDeath(player, event);

            // CHECK FOR GAME END - wrapped in try-catch
            try {
                game.checkGameEndCondition();
            } catch (Exception endCheckError) {
                UHC.getInstance().getLogger().severe("Error checking game end after player death: " + endCheckError.getMessage());
                UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Game end check error after player death", endCheckError);
                // Don't re-throw - let the death event complete normally
            }

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error handling player death event: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Player death event handling error", e);
            // Don't re-throw to prevent the original error from cascading
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof Villager villager)) {
                return;
            }

            if (!combatLogVillagerManager.isControlledVillager(villager)) {
                return;
            }

            CombatLogPlayer combatLogPlayer = combatLogVillagerManager.getCombatLogPlayer(villager);
            if (combatLogPlayer == null) {
                UHC.getInstance().getLogger().warning("CombatLogPlayer was null for villager death");
                return;
            }

            UUID playerUUID = combatLogPlayer.getUuid();

            combatLogVillagerManager.killCombatLogVillager(villager, combatLogPlayer);

            // Handle team elimination
            if (game.getTeamManager() != null) {
                game.getTeamManager().handlePlayerDeath(playerUUID);
            }

            game.removePlayer(playerUUID);

            // CHECK FOR GAME END - wrapped in try-catch
            try {
                game.checkGameEndCondition();
            } catch (Exception endCheckError) {
                UHC.getInstance().getLogger().severe("Error checking game end after villager death: " + endCheckError.getMessage());
                UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Game end check error after villager death", endCheckError);
                // Don't re-throw - let the death event complete normally
            }

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error handling entity death event: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Entity death event handling error", e);
            // Don't re-throw to prevent the original error from cascading
        }
    }

    /**
     * Announce player death with context - enhanced with error handling
     */
    private void announcePlayerDeath(Player player, PlayerDeathEvent event) {
        try {
            String deathMessage = event.getDeathMessage();

            if (deathMessage == null || deathMessage.isEmpty()) {
                deathMessage = player.getName() + " died";
            }

            // Get remaining players/teams info safely
            int remainingPlayers = 0;
            int remainingTeams = 0;

            try {
                remainingPlayers = game.getAlivePlayers().size();
                if (game.getTeamManager() != null) {
                    remainingTeams = game.getTeamManager().getAliveTeams().size();
                }
            } catch (Exception e) {
                UHC.getInstance().getLogger().warning("Error getting remaining player/team counts: " + e.getMessage());
                // Continue with 0 values
            }

            // Broadcast death with remaining count
            Bukkit.broadcastMessage(ChatColor.RED + deathMessage);

            if (game.isSoloMode()) {
                if (remainingPlayers > 1) {
                    Bukkit.broadcastMessage(ChatColor.GRAY + "Remaining: " + remainingPlayers + " players");
                } else if (remainingPlayers == 1) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Only 1 player remains!");
                }
            } else {
                if (remainingTeams > 1) {
                    Bukkit.broadcastMessage(ChatColor.GRAY + "Remaining: " + remainingTeams + " teams (" + remainingPlayers + " players)");
                } else if (remainingTeams == 1) {
                    Bukkit.broadcastMessage(ChatColor.YELLOW + "Only 1 team remains!");
                }
            }

        } catch (Exception e) {
            UHC.getInstance().getLogger().severe("Error announcing player death: " + e.getMessage());
            UHC.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Player death announcement error", e);

            // Fallback announcement
            try {
                Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " died");
            } catch (Exception fallbackError) {
                UHC.getInstance().getLogger().severe("Even fallback death announcement failed: " + fallbackError.getMessage());
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (combatLogVillagerManager.containsVillagerChunk(event.getChunk())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageEvent(EntityDamageEvent event) {
        if (event.isCancelled()) return;

        Entity entity = event.getEntity();

        if (entity instanceof Villager villager && combatLogVillagerManager.isControlledVillager(villager)) {
            CombatLogPlayer combatLogPlayer = combatLogVillagerManager.getCombatLogPlayer(villager);

            double newHealth = villager.getHealth() - event.getFinalDamage();
            newHealth = Math.max(0, newHealth); // Clamp to prevent negatives

            combatLogVillagerManager.updateVillagerHealthBar(villager, combatLogPlayer, newHealth);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        Entity entity = event.getEntity();
        Entity damager = event.getDamager();


        if (!(entity instanceof Player)) {
            if (!(entity instanceof Villager)) {
                return;
            }

            Villager villager = (Villager) entity;

            if (combatLogVillagerManager.isControlledVillager(villager) && !game.isPvpEnabled()) {
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

package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final Game game;

    public PlayerListener(Game game) {
        this.game = game;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (player.isDead()) {
            player.spigot().respawn();
        }

        if (game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = new UHCPlayer(uuid);
        game.getPlayers().put(uuid, uhcPlayer);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);

        if (uhcPlayer.getState() == PlayerState.COMBAT_LOG) {
            return;
        }

        UHC.getInstance().getGame().removePlayer(uuid);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().respawn();
            }
        }.runTaskLater(UHC.getInstance(), 1);
    }
}

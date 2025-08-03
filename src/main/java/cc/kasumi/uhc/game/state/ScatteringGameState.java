package cc.kasumi.uhc.game.state;

import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.game.GameState;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ScatteringGameState extends GameState {

    public ScatteringGameState(Game game) {
        super(game);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);
        uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }
}

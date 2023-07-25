package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.player.UHCPlayerStatus;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

public class SpectatorListener implements Listener {

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        UHCPlayer uhcPlayer = UHC.getPlayers().get(playerUUID);

        if (uhcPlayer.getStatus() == UHCPlayerStatus.SPECTATING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        UHCPlayer uhcPlayer = UHC.getPlayers().get(playerUUID);

        if (uhcPlayer.getStatus() == UHCPlayerStatus.SPECTATING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        UHCPlayer uhcPlayer = UHC.getPlayers().get(playerUUID);

        if (uhcPlayer.getStatus() == UHCPlayerStatus.SPECTATING && isRightClick(event.getAction())) {
            event.setCancelled(true);
        }
    }
}

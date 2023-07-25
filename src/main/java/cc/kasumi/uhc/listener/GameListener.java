package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.player.UHCPlayerStatus;
import cc.kasumi.uhc.util.SpectatorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public class GameListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerUUID = player.getUniqueId();
        UHCPlayer uhcPlayer = UHC.getPlayers().get(playerUUID);

        player.spigot().respawn();
        SpectatorUtil.hidePlayer(player);
        uhcPlayer.setStatus(UHCPlayerStatus.SPECTATING);
    }
}

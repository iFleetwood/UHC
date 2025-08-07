package cc.kasumi.uhc.listener;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.util.PlayerUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class AsyncPlayerPreLoginListener implements Listener {

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (UHC.getInstance().isLoadedFully()) {
            return;
        }

        event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        event.setKickMessage(ChatColor.DARK_RED + "UHC isn't Initialized");
    }

    /*
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (PlayerUtil.isAbove1_8(player)) {
            player.kickPlayer(ChatColor.DARK_RED + "PLEASE USE 1.8.9 OR BELOW");
        }
    }

     */
}

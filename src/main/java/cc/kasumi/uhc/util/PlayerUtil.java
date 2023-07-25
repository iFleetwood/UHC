package cc.kasumi.uhc.util;

import org.bukkit.entity.Player;

public class PlayerUtil {

    public static boolean isPlayerOnline(Player player) {
        return player != null && player.isOnline();
    }
}

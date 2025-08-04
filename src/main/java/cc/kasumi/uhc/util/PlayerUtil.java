package cc.kasumi.uhc.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerUtil {

    public static void sendMessageToOnlinePlayers(String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    public static void resetPlayer(Player player) {
        PlayerInventory inventory = player.getInventory();

        inventory.clear();
        player.setExp(0);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(potionEffect -> player.removePotionEffect(potionEffect.getType()));
    }

    public static List<Player> getOnlinePlayers() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    public static int getClientProtocolVersion(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            Object networkManager = connection.getClass().getField("networkManager").get(connection);

            for (Field f : networkManager.getClass().getDeclaredFields()) {
                if (f.getType().getSimpleName().equals("EnumProtocolDirection")) continue; // skip direction fields
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    int protocol = f.getInt(networkManager);
                    if (protocol > 0 && protocol < 1000) return protocol; // likely a valid version
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static boolean isAbove1_8(Player player) {
        int protocolId = getClientProtocolVersion(player);
        return protocolId > 47; // 47 = 1.8.9
    }


}

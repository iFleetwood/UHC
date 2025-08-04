package cc.kasumi.uhc.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerUtil {

    // Cache the server version for performance
    private static final String SERVER_VERSION = getServerVersion();
    private static final boolean IS_LEGACY_SERVER = isLegacyServerVersion();

    public static void sendMessageToOnlinePlayers(String message) {
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    public static void resetPlayer(Player player) {
        player.getInventory().clear();
        player.setExp(0);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(potionEffect ->
                player.removePotionEffect(potionEffect.getType()));
    }

    public static List<Player> getOnlinePlayers() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }

    /**
     * Get the client protocol version for a player
     * Returns -1 if unable to determine
     */
    public static int getClientProtocolVersion(Player player) {
        try {
            // Get the EntityPlayer handle
            Object handle = player.getClass().getMethod("getHandle").invoke(player);

            // Get the PlayerConnection
            Object connection = handle.getClass().getField("playerConnection").get(handle);

            // Get the NetworkManager
            Object networkManager = connection.getClass().getField("networkManager").get(connection);

            // Try different approaches based on server version
            if (IS_LEGACY_SERVER) {
                return getLegacyProtocolVersion(networkManager);
            } else {
                return getModernProtocolVersion(networkManager);
            }

        } catch (Exception e) {
            // Fallback: try to determine from server version
            return getProtocolFromServerVersion();
        }
    }

    /**
     * Protocol version detection for 1.8.x servers
     */
    private static int getLegacyProtocolVersion(Object networkManager) {
        try {
            // In 1.8, the protocol version is often stored in a field called 'version' or similar
            Field[] fields = networkManager.getClass().getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);

                // Look for fields that might contain protocol version
                if (field.getType() == int.class) {
                    String fieldName = field.getName().toLowerCase();

                    // Common field names for protocol version
                    if (fieldName.contains("version") ||
                            fieldName.contains("protocol") ||
                            fieldName.equals("j") || // Common obfuscated name
                            fieldName.equals("k")) {

                        int value = field.getInt(networkManager);

                        // Validate that this looks like a protocol version
                        if (isValidProtocolVersion(value)) {
                            return value;
                        }
                    }
                }
            }

            // Fallback: try to find any reasonable protocol version
            for (Field field : fields) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    int value = field.getInt(networkManager);

                    if (isValidProtocolVersion(value)) {
                        return value;
                    }
                }
            }

        } catch (Exception e) {
            // Ignore and return -1
        }

        return -1;
    }

    /**
     * Protocol version detection for newer servers
     */
    private static int getModernProtocolVersion(Object networkManager) {
        try {
            // Try to get the protocol version through the channel
            Method getChannelMethod = networkManager.getClass().getMethod("getChannel");
            Object channel = getChannelMethod.invoke(networkManager);

            if (channel != null) {
                // Try to get protocol version from channel attributes
                return getProtocolFromChannel(channel);
            }

        } catch (Exception e) {
            // Fall back to legacy method
            return getLegacyProtocolVersion(networkManager);
        }

        return -1;
    }

    /**
     * Try to extract protocol version from Netty channel
     */
    private static int getProtocolFromChannel(Object channel) {
        try {
            // This is a more advanced approach for newer versions
            // For now, fall back to server version detection
            return getProtocolFromServerVersion();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Check if a value looks like a valid Minecraft protocol version
     */
    private static boolean isValidProtocolVersion(int version) {
        // Known protocol version ranges
        // 1.7: 4-5
        // 1.8: 47
        // 1.9: 107-110
        // 1.10: 210
        // 1.11: 315-316
        // 1.12: 335-340
        // 1.13: 385-404
        // etc.

        return version >= 4 && version <= 1000; // Reasonable range for protocol versions
    }

    /**
     * Get protocol version based on server version as fallback
     */
    private static int getProtocolFromServerVersion() {
        String version = SERVER_VERSION;

        if (version.contains("1.8")) {
            return 47; // 1.8.x protocol
        } else if (version.contains("1.7")) {
            return 5; // 1.7.x protocol
        } else if (version.contains("1.9")) {
            return 107; // 1.9.x protocol
        } else if (version.contains("1.10")) {
            return 210; // 1.10.x protocol
        } else if (version.contains("1.11")) {
            return 315; // 1.11.x protocol
        } else if (version.contains("1.12")) {
            return 335; // 1.12.x protocol
        } else if (version.contains("1.13")) {
            return 385; // 1.13.x protocol
        } else if (version.contains("1.14")) {
            return 477; // 1.14.x protocol
        } else if (version.contains("1.15")) {
            return 573; // 1.15.x protocol
        } else if (version.contains("1.16")) {
            return 735; // 1.16.x protocol
        }

        // Default to 1.8 protocol if unknown
        return 47;
    }

    /**
     * Check if player is using a version above 1.8
     */
    public static boolean isAbove1_8(Player player) {
        int protocolId = getClientProtocolVersion(player);

        // If we couldn't determine the protocol version, be conservative
        if (protocolId == -1) {
            return false; // Assume 1.8 or below to be safe
        }

        return protocolId > 47; // 47 = 1.8.9 protocol version
    }

    /**
     * Check if player is using 1.8 or below
     */
    public static boolean is1_8OrBelow(Player player) {
        return !isAbove1_8(player);
    }

    /**
     * Get a human-readable version string for a player
     */
    public static String getPlayerVersionString(Player player) {
        int protocol = getClientProtocolVersion(player);

        switch (protocol) {
            case 4:
            case 5:
                return "1.7.x";
            case 47:
                return "1.8.x";
            case 107:
            case 108:
            case 109:
            case 110:
                return "1.9.x";
            case 210:
                return "1.10.x";
            case 315:
            case 316:
                return "1.11.x";
            case 335:
            case 338:
            case 340:
                return "1.12.x";
            default:
                if (protocol > 47) {
                    return "1.9+";
                } else {
                    return "1.8 or below";
                }
        }
    }

    /**
     * Get the server version string
     */
    private static String getServerVersion() {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Check if this is a legacy server version (1.8 and below)
     */
    private static boolean isLegacyServerVersion() {
        return SERVER_VERSION.contains("1_7") ||
                SERVER_VERSION.contains("1_8");
    }

    /**
     * Get server protocol version
     */
    public static int getServerProtocolVersion() {
        return getProtocolFromServerVersion();
    }

    /**
     * Check if the server supports a specific protocol version
     */
    public static boolean supportsProtocolVersion(int protocolVersion) {
        int serverProtocol = getServerProtocolVersion();
        return serverProtocol >= protocolVersion;
    }
}
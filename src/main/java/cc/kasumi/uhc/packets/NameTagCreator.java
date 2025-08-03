package cc.kasumi.uhc.packets;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NameTagCreator {

    private final Object packet = PacketAccessor.createPacket();

    public NameTagCreator(String teamName, Player player, String prefix, String suffix) throws IllegalAccessException {
        String playerName = player.getName();
        List<String> players = new ArrayList<>();
        players.add(player.getName());

        PacketAccessor.TEAM_NAME.set(packet, teamName);
        PacketAccessor.DISPLAY_NAME.set(packet, playerName);
        PacketAccessor.PREFIX.set(packet, prefix);
        PacketAccessor.SUFFIX.set(packet, suffix);
        PacketAccessor.VISIBILITY.set(packet, "ALWAYS");
        PacketAccessor.MEMBERS.set(packet, players);
        PacketAccessor.PACK_OPTION.set(packet, 0);
    }

    public void sendPacket() {
        for (Player all : Bukkit.getOnlinePlayers()) {
            PacketAccessor.sendPacket(all, packet);
        }
    }
}

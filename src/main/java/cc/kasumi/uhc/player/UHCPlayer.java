package cc.kasumi.uhc.player;

import cc.kasumi.uhc.inventory.CachedInventory;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.UUID;

@Getter
public class UHCPlayer {

    private final UUID uuid;

    private int kills = 0;

    @Setter
    private PlayerState state = PlayerState.SPECTATING;
    @Setter
    private CachedInventory cachedInventory;

    public UHCPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(this.uuid);
    }

    public void setPlayerStateAndManage(PlayerState state) {
        this.state = state;

        if (state == PlayerState.SPECTATING) {
            manageSpectator(getPlayer());
        } else if (state == PlayerState.ALIVE) {
            manageAlivePlayer(getPlayer());
        }
    }

    public void manageSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void manageAlivePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
    }

    public void addKill() {
        kills += 1;
    }
}

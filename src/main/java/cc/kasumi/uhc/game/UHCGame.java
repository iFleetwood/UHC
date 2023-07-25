package cc.kasumi.uhc.game;

import cc.kasumi.uhc.Configuration;
import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.threads.GameThread;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.player.UHCPlayerStatus;
import cc.kasumi.uhc.team.UHCTeam;
import cc.kasumi.uhc.util.PlayerUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Setter
public class UHCGame {

    private final Set<UHCTeam> teams  = new HashSet<>();
    private final GameThread gameThread;
    private final int beforeTimeInterval = 5 * 60;
    private long start;

    private int borderSize, firstShrink, finalBorder, shrinkInterval, playersAlive, teamsAlive;
    private boolean deathmatch = false;

    @Setter
    private UHCGameStatus status;

    public UHCGame() {
        gameThread = new GameThread(this);

        borderSize = Configuration.BORDER_SIZE;
        firstShrink = Configuration.FIRST_BORDER_SHRINK;
        finalBorder = Configuration.FINAL_BORDER_SIZE;
    }

    public void startCountdown() {
        new BukkitRunnable() {
            @Override
            public void run() {

            }
        }.runTaskTimerAsynchronously(UHC.getInstance(), 20, 20);
    }

    public void start() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(UHC.getInstance(), gameThread, firstShrink - beforeTimeInterval);
        start = System.currentTimeMillis();
    }

    public void shrinkBorder(int size, Location start) {
        // Shrink

        for (int y = 0; y < 5; y++) {
            for (double x = start.getX() - size; x <= start.getX() + size; x++) {
                for (double z = start.getZ() - size; z <= start.getZ() + size; z++) {
                    int blockY = start.getWorld().getHighestBlockYAt((int) x, (int) z) + y;
                    Location blockLocation = new Location(start.getWorld(), x, blockY, z);

                    blockLocation.getBlock().setType(Material.BEDROCK);
                }
            }
        }

        borderSize = size;
    }

    // Could do this by another way
    public void setPlayersStatus(UHCPlayerStatus playerStatus) {
        for (UHCTeam team : teams) {
            for (UUID playerUUID : team.getPlayers()) {
                UHCPlayer uhcPlayer = UHC.getPlayers().get(playerUUID);

                uhcPlayer.setStatus(playerStatus);
            }
        }
    }

    public void scatterTeam(UHCTeam team) {
        Set<UUID> players = team.getPlayers();

        for (UUID playerUUID : players) {
            Player player = Bukkit.getPlayer(playerUUID);

            if (!PlayerUtil.isPlayerOnline(player)) {
                continue;
            }

            player.teleport(getScatterLocation());
        }
    }

    public Location getScatterLocation() {
        World world = Bukkit.getWorld("world");

        double x = ThreadLocalRandom.current().nextDouble(borderSize + borderSize) - borderSize;
        double z = ThreadLocalRandom.current().nextDouble(borderSize + borderSize) - borderSize;
        int y = world.getHighestBlockYAt((int) x, (int) z);

        return new Location(world, x + 0.5, y + 2, z + 0.5);
    }

    public int getNextBorder() {
        return borderSize > 500 ? borderSize - 500 : borderSize > 50 ? borderSize - 50 : borderSize - 25;
    }

    public long getGameTime() {
        return System.currentTimeMillis() - start;
    }
}

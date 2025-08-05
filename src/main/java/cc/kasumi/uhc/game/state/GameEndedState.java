package cc.kasumi.uhc.game.state;

import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.game.GameEndResult;
import cc.kasumi.uhc.game.GameState;
import cc.kasumi.uhc.player.PlayerState;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.team.UHCTeam;
import cc.kasumi.uhc.util.PlayerUtil;
import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Game state when the game has ended
 * Players can no longer interact with the world and are put in spectator mode
 */
public class GameEndedState extends GameState {

    @Getter
    private final GameEndResult gameEndResult;

    public GameEndedState(Game game, GameEndResult gameEndResult) {
        super(game);
        this.gameEndResult = gameEndResult;
    }

    @Override
    public void onEnable() {
        super.onEnable();

        // Set all players to spectating
        for (UHCPlayer uhcPlayer : game.getPlayers().values()) {
            uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);
        }

        // Send game end messages to all online players
        for (Player player : game.getPlayersInGameWorld()) {
            sendGameEndMessage(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!game.containsUHCPlayer(uuid)) {
            return;
        }

        // Reset player and put in spectator mode
        PlayerUtil.resetPlayer(player);

        UHCPlayer uhcPlayer = game.getUHCPlayer(uuid);
        uhcPlayer.setPlayerStateAndManage(PlayerState.SPECTATING);

        // Send game end message
        sendGameEndMessage(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nothing special needed when players quit after game end
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Prevent all block breaking after game end
        event.setCancelled(true);

        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage(ChatColor.RED + "The game has ended! You cannot break blocks.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Prevent all block placing after game end
        event.setCancelled(true);

        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage(ChatColor.RED + "The game has ended! You cannot place blocks.");
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Prevent all PvP after game end
        event.setCancelled(true);
    }

    /**
     * Send personalized game end message to a player
     */
    private void sendGameEndMessage(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "===============================");
        player.sendMessage(ChatColor.YELLOW + "         GAME OVER");
        player.sendMessage("");

        switch (gameEndResult.getWinnerType()) {
            case SOLO:
                UHCPlayer winner = gameEndResult.getSoloWinner();
                if (winner != null && winner.getUuid().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.GREEN + "Congratulations! You won the UHC!");
                    player.sendMessage(ChatColor.GRAY + "Your kills: " + winner.getKills());
                } else {
                    String winnerName = winner != null && winner.getPlayer() != null ?
                            winner.getPlayer().getName() : "Unknown";
                    player.sendMessage(ChatColor.RED + "Game Over! " + ChatColor.YELLOW + winnerName + ChatColor.RED + " won the UHC.");
                }
                break;

            case TEAM:
                UHCTeam winnerTeam = gameEndResult.getTeamWinner();
                UHCPlayer uhcPlayer = game.getUHCPlayer(player.getUniqueId());

                if (winnerTeam != null && uhcPlayer != null) {
                    UHCTeam playerTeam = game.getTeamManager().getPlayerTeam(player.getUniqueId());

                    if (winnerTeam.equals(playerTeam)) {
                        player.sendMessage(ChatColor.GREEN + "Congratulations! Your team " +
                                winnerTeam.getFormattedName() + ChatColor.GREEN + " won the UHC!");
                        player.sendMessage(ChatColor.GRAY + "Your kills: " + uhcPlayer.getKills());
                    } else {
                        player.sendMessage(ChatColor.RED + "Game Over! Team " +
                                winnerTeam.getFormattedName() + ChatColor.RED + " won the UHC.");
                    }
                }
                break;

            case DRAW:
                player.sendMessage(ChatColor.YELLOW + "Game ended in a draw!");
                player.sendMessage(ChatColor.GRAY + "Reason: " + gameEndResult.getReason());
                break;

            case FORCE_END:
                player.sendMessage(ChatColor.RED + "Game was ended by an administrator.");
                player.sendMessage(ChatColor.GRAY + "Reason: " + gameEndResult.getReason());
                break;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Game Duration: " + game.getFormattedGameDuration());
        player.sendMessage(ChatColor.GRAY + "You are now in spectator mode.");
        player.sendMessage(ChatColor.GOLD + "===============================");
        player.sendMessage("");
    }
}
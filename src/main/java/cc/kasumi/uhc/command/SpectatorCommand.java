package cc.kasumi.uhc.command;

import cc.kasumi.uhc.UHC;
import cc.kasumi.uhc.game.Game;
import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.spectator.SpectatorManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@CommandAlias("spectator|spec")
public class SpectatorCommand extends BaseCommand {

    @Subcommand("tp")
    @Description("Teleport to a player (spectators only)")
    @CommandCompletion("@players")
    public void onTeleportToPlayer(Player sender, String targetName) {
        Game game = UHC.getInstance().getGame();
        SpectatorManager spectatorManager = game.getSpectatorManager();
        
        if (spectatorManager == null) {
            sender.sendMessage(ChatColor.RED + "Spectator system is not available!");
            return;
        }

        if (!spectatorManager.isSpectator(sender)) {
            sender.sendMessage(ChatColor.RED + "Only spectators can use this command!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online!");
            return;
        }

        if (spectatorManager.teleportSpectatorToPlayer(sender, target)) {
            sender.sendMessage(ChatColor.GREEN + "Teleported to " + target.getName());
        }
    }

    @Subcommand("random")
    @Description("Teleport to a random alive player (spectators only)")
    public void onTeleportToRandom(Player sender) {
        Game game = UHC.getInstance().getGame();
        SpectatorManager spectatorManager = game.getSpectatorManager();
        
        if (spectatorManager == null) {
            sender.sendMessage(ChatColor.RED + "Spectator system is not available!");
            return;
        }

        if (!spectatorManager.isSpectator(sender)) {
            sender.sendMessage(ChatColor.RED + "Only spectators can use this command!");
            return;
        }

        spectatorManager.teleportSpectatorToRandomPlayer(sender);
    }

    @Subcommand("list")
    @Description("List all alive players (spectators only)")
    public void onListPlayers(Player sender) {
        Game game = UHC.getInstance().getGame();
        SpectatorManager spectatorManager = game.getSpectatorManager();
        
        if (spectatorManager == null) {
            sender.sendMessage(ChatColor.RED + "Spectator system is not available!");
            return;
        }

        if (!spectatorManager.isSpectator(sender)) {
            sender.sendMessage(ChatColor.RED + "Only spectators can use this command!");
            return;
        }

        List<Player> alivePlayers = spectatorManager.getAlivePlayers();
        if (alivePlayers.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No alive players found!");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Alive Players (" + alivePlayers.size() + "):");
        for (Player player : alivePlayers) {
            sender.sendMessage(ChatColor.GRAY + "- " + ChatColor.WHITE + player.getName());
        }
    }

    @Subcommand("make")
    @Description("Make a player a spectator (admin only)")
    @CommandPermission("uhc.admin")
    @CommandCompletion("@players")
    public void onMakeSpectator(CommandSender sender, String targetName) {
        Game game = UHC.getInstance().getGame();
        SpectatorManager spectatorManager = game.getSpectatorManager();
        
        if (spectatorManager == null) {
            sender.sendMessage(ChatColor.RED + "Spectator system is not available!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online!");
            return;
        }

        if (spectatorManager.isSpectator(target)) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is already a spectator!");
            return;
        }

        if (spectatorManager.makeSpectator(target)) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " is now a spectator!");
            target.sendMessage(ChatColor.YELLOW + "You have been made a spectator by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to make " + target.getName() + " a spectator!");
        }
    }

    @Subcommand("remove")
    @Description("Remove spectator status from a player (admin only)")
    @CommandPermission("uhc.admin")
    @CommandCompletion("@players")
    public void onRemoveSpectator(CommandSender sender, String targetName) {
        Game game = UHC.getInstance().getGame();
        SpectatorManager spectatorManager = game.getSpectatorManager();
        
        if (spectatorManager == null) {
            sender.sendMessage(ChatColor.RED + "Spectator system is not available!");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Player not found or not online!");
            return;
        }

        if (!spectatorManager.isSpectator(target)) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is not a spectator!");
            return;
        }

        if (spectatorManager.removeSpectator(target)) {
            sender.sendMessage(ChatColor.GREEN + target.getName() + " is no longer a spectator!");
            target.sendMessage(ChatColor.YELLOW + "Your spectator status has been removed by " + sender.getName());
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to remove spectator status from " + target.getName() + "!");
        }
    }

    @Subcommand("info")
    @Description("Show spectator information")
    public void onSpectatorInfo(CommandSender sender) {
        Game game = UHC.getInstance().getGame();
        SpectatorManager spectatorManager = game.getSpectatorManager();
        
        if (spectatorManager == null) {
            sender.sendMessage(ChatColor.RED + "Spectator system is not available!");
            return;
        }

        List<Player> spectators = spectatorManager.getOnlineSpectators();
        List<Player> alivePlayers = spectatorManager.getAlivePlayers();

        sender.sendMessage(ChatColor.GOLD + "=== Spectator Information ===");
        sender.sendMessage(ChatColor.GREEN + "Online Spectators: " + ChatColor.WHITE + spectators.size());
        sender.sendMessage(ChatColor.GREEN + "Alive Players: " + ChatColor.WHITE + alivePlayers.size());
        
        if (sender instanceof Player && spectatorManager.isSpectator((Player) sender)) {
            sender.sendMessage(ChatColor.YELLOW + "You are currently spectating!");
            sender.sendMessage(ChatColor.GRAY + "Use /spec tp <player> to teleport to a player");
            sender.sendMessage(ChatColor.GRAY + "Use /spec random to teleport to a random player");
            sender.sendMessage(ChatColor.GRAY + "Use /spec list to list all alive players");
        }
    }

    @Default
    @HelpCommand
    public void onHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Spectator Commands ===");
        
        if (sender instanceof Player) {
            Game game = UHC.getInstance().getGame();
            SpectatorManager spectatorManager = game.getSpectatorManager();
            
            if (spectatorManager != null && spectatorManager.isSpectator((Player) sender)) {
                sender.sendMessage(ChatColor.GREEN + "/spec tp <player>" + ChatColor.GRAY + " - Teleport to a player");
                sender.sendMessage(ChatColor.GREEN + "/spec random" + ChatColor.GRAY + " - Teleport to a random alive player");
                sender.sendMessage(ChatColor.GREEN + "/spec list" + ChatColor.GRAY + " - List all alive players");
            }
        }
        
        sender.sendMessage(ChatColor.GREEN + "/spec info" + ChatColor.GRAY + " - Show spectator information");
        
        if (sender.hasPermission("uhc.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "Admin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/spec make <player>" + ChatColor.GRAY + " - Make a player a spectator");
            sender.sendMessage(ChatColor.YELLOW + "/spec remove <player>" + ChatColor.GRAY + " - Remove spectator status");
        }
    }
}
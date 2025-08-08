package cc.kasumi.uhc.spectator;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration options for the spectator system
 */
@Getter
@Setter
public class SpectatorConfiguration {

    // Whether spectators can see each other
    private boolean spectatorsCanSeeEachOther = true;

    // Whether spectators can use chat
    private boolean spectatorChatEnabled = true;

    // Whether spectators can teleport to players
    private boolean spectatorTeleportationEnabled = true;

    // Whether players automatically become spectators on death
    private boolean deathAutoSpectatorEnabled = true;

    // Whether spectators have a separate chat channel
    private boolean separateSpectatorChat = false;

    // Spectator chat prefix
    private String spectatorChatPrefix = "§8[SPECTATOR] §7";

    // Whether spectators can see other spectators in tab list
    private boolean showSpectatorsInTabList = true;

    // Whether to send spectator notifications to other players
    private boolean spectatorNotifications = false;

    public SpectatorConfiguration() {
        // Default configuration values are set above
    }
}
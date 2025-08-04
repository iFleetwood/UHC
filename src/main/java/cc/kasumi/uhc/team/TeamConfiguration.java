package cc.kasumi.uhc.team;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration class for team management
 */
@Getter
@Setter
public class TeamConfiguration {

    // Team size limits
    private boolean maxTeamSizeEnabled = true;
    private int maxTeamSize = 4;
    private int minTeamSize = 1;

    // Team management
    private boolean autoDeleteEmptyTeams = true;
    private boolean allowTeamSwitching = true;
    private boolean requireTeamLeaderApprovalForJoining = false;

    // Gameplay settings
    private boolean friendlyFireEnabled = false;
    private boolean teamChatEnabled = true;
    private boolean showTeamHealthToMembers = true;
    private boolean sharedTeamInventoryOnDeath = false;

    // Scatter settings
    private boolean scatterTeamsTogether = true;
    private int maxScatterDistance = 50; // Max distance between team members when scattering
    private int minScatterDistance = 10; // Min distance between team members

    // Team colors and display
    private boolean showTeamColorsInTabList = true;
    private boolean showTeamColorsInChat = true;
    private boolean showTeamPrefixInNametags = true;

    // Balance settings
    private boolean autoBalanceTeams = false;
    private int maxTeamSizeDifference = 1; // Max difference in team sizes

    // Combat settings
    private boolean preventFriendlyFire = true;
    private boolean allowTeamRevival = false;

    public TeamConfiguration() {
        // Default configuration
    }

    /**
     * Validate configuration values
     */
    public boolean isValidConfiguration() {
        if (maxTeamSize < minTeamSize) {
            return false;
        }

        if (maxTeamSize < 1 || minTeamSize < 1) {
            return false;
        }

        if (maxScatterDistance < minScatterDistance) {
            return false;
        }

        return true;
    }

    /**
     * Apply safe defaults if configuration is invalid
     */
    public void applySafeDefaults() {
        if (maxTeamSize < minTeamSize) {
            maxTeamSize = Math.max(4, minTeamSize);
        }

        if (maxTeamSize < 1) {
            maxTeamSize = 4;
        }

        if (minTeamSize < 1) {
            minTeamSize = 1;
        }

        if (maxScatterDistance < minScatterDistance) {
            maxScatterDistance = Math.max(50, minScatterDistance);
        }

        if (maxScatterDistance < 10) {
            maxScatterDistance = 50;
        }

        if (minScatterDistance < 5) {
            minScatterDistance = 10;
        }
    }
}
package cc.kasumi.uhc.game;

import cc.kasumi.uhc.player.UHCPlayer;
import cc.kasumi.uhc.team.UHCTeam;
import lombok.Getter;

/**
 * Represents the result of a game end check
 */
@Getter
public class GameEndResult {

    private final boolean shouldEndGame;
    private final WinnerType winnerType;
    private final String reason;
    private final UHCPlayer soloWinner;
    private final UHCTeam teamWinner;

    private GameEndResult(boolean shouldEndGame, WinnerType winnerType, String reason,
                          UHCPlayer soloWinner, UHCTeam teamWinner) {
        this.shouldEndGame = shouldEndGame;
        this.winnerType = winnerType;
        this.reason = reason;
        this.soloWinner = soloWinner;
        this.teamWinner = teamWinner;
    }

    /**
     * Game should continue
     */
    public static GameEndResult continueGame() {
        return new GameEndResult(false, WinnerType.NONE, "Game continues", null, null);
    }

    /**
     * Solo player won
     */
    public static GameEndResult soloWin(UHCPlayer winner, String reason) {
        return new GameEndResult(true, WinnerType.SOLO, reason, winner, null);
    }

    /**
     * Team won
     */
    public static GameEndResult teamWin(UHCTeam winner, String reason) {
        return new GameEndResult(true, WinnerType.TEAM, reason, null, winner);
    }

    /**
     * Game ended in a draw
     */
    public static GameEndResult draw(String reason) {
        return new GameEndResult(true, WinnerType.DRAW, reason, null, null);
    }

    /**
     * Game ended due to admin intervention or error
     */
    public static GameEndResult forceEnd(String reason) {
        return new GameEndResult(true, WinnerType.FORCE_END, reason, null, null);
    }

    public enum WinnerType {
        NONE,        // Game continues
        SOLO,        // Solo player won
        TEAM,        // Team won
        DRAW,        // Draw/no winner
        FORCE_END    // Forced end
    }
}
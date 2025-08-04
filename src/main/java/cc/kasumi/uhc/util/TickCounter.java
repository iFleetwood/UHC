package cc.kasumi.uhc.util;

import cc.kasumi.uhc.UHC;
import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Utility class to track server ticks accurately
 */
public class TickCounter {

    @Getter
    private volatile long currentTick = 0;
    private BukkitRunnable tickTask;

    private static TickCounter instance;

    public static TickCounter getInstance() {
        if (instance == null) {
            instance = new TickCounter();
        }
        return instance;
    }

    private TickCounter() {
        startTracking();
    }

    /**
     * Starts the tick tracking task
     */
    public void startTracking() {
        if (tickTask != null) {
            tickTask.cancel();
        }

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                currentTick++;
            }
        };

        // Run every tick (every 1 tick = 50ms)
        tickTask.runTaskTimer(UHC.getInstance(), 0, 1);
    }

    /**
     * Gets the current server tick count
     */

    /**
     * Resets the tick counter (useful for testing)
     */
    public void reset() {
        currentTick = 0;
    }

    /**
     * Stops the tick tracking (call in onDisable)
     */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /**
     * Converts ticks to seconds
     */
    public static long ticksToSeconds(long ticks) {
        return ticks / 20;
    }

    /**
     * Converts seconds to ticks
     */
    public static long secondsToTicks(long seconds) {
        return seconds * 20;
    }

    /**
     * Converts ticks to milliseconds (approximate)
     */
    public static long ticksToMillis(long ticks) {
        return ticks * 50;
    }
}

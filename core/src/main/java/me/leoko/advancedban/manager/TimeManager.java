package me.leoko.advancedban.manager;

import me.leoko.advancedban.Universal;

import java.util.Date;
import java.util.Locale;

/**
 * The Time Manager is used to have a centralized time for advanced ban which can be different from the system's time.
 */
public class TimeManager {
    /**
     * Get the current timestamp in milliseconds.
     *
     * @return the timestamp
     */
    public static long getTime() {
        long configuredHours = Universal.get().getMethods().getInteger(
                Universal.get().getMethods().getConfig(), "TimeDiff", 0);
        long boundedHours = Math.max(-876_000L, Math.min(876_000L, configuredHours));
        return System.currentTimeMillis() + boundedHours * 3_600_000L;
    }

    /**
     * Convert a Time String to the amount of milliseconds.
     * These Strings are used for the temporary advancedban punish commands.
     *
     * @param s the time string
     * @return the amount of milliseconds equivalent to the given string
     */
    public static long toMilliSec(String s) {
        if (s == null || !s.matches("[1-9][0-9]*(s|m|h|d|w|mo)")) {
            return -1;
        }
        // This is not my regex :P | From: http://stackoverflow.com/a/8270824
        String[] sl = s.toLowerCase(Locale.ROOT).split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

        if (sl[0].length() > 6) {
            return -1;
        }
        long i;
        try {
            i = Long.parseLong(sl[0]);
        } catch (NumberFormatException ignored) {
            return -1;
        }
        if (i > 100_000L) {
            return -1;
        }
        switch (sl[1]) {
            case "s":
                return i * 1000;
            case "m":
                return i * 1000 * 60;
            case "h":
                return i * 1000 * 60 * 60;
            case "d":
                return i * 1000 * 60 * 60 * 24;
            case "w":
                return i * 1000 * 60 * 60 * 24 * 7;
            case "mo":
                return i * 1000 * 60 * 60 * 24 * 30;
            default:
                return -1;
        }
    }
}

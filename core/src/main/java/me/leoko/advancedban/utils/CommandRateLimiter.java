package me.leoko.advancedban.utils;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandRateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean allow(Object sender, String commandLine) {
        if (!Security.getBoolean("Security.CommandRateLimit.Enabled", true)) {
            return true;
        }

        long now = System.currentTimeMillis();
        cleanup(now);

        String key = senderKey(sender);
        long window = Math.max(250L, Security.getInt("Security.CommandRateLimit.WindowMillis", 1000));
        int max = Math.max(1, Security.getInt("Security.CommandRateLimit.MaxCommands", 6));
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now, 0));

        synchronized (bucket) {
            if (now - bucket.windowStart > window) {
                bucket.windowStart = now;
                bucket.count = 0;
            }
            bucket.count++;
            return bucket.count <= max;
        }
    }

    private void cleanup(long now) {
        if (buckets.size() < 512) {
            return;
        }
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (now - entry.getValue().windowStart > 60_000L) {
                iterator.remove();
            }
        }
    }

    private String senderKey(Object sender) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            return mi.getName(sender).toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return String.valueOf(System.identityHashCode(sender));
        }
    }

    private static final class Bucket {
        private long windowStart;
        private int count;

        private Bucket(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}

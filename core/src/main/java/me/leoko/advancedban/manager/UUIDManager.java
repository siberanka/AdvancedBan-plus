package me.leoko.advancedban.manager;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Security;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * The UUID Manager used to resolve and cache the UUIDs.
 */
public class UUIDManager {
    private static UUIDManager instance = null;
    private FetcherMode mode;
    private final Map<String, String> activeUUIDs = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> activeNamesByUuid = new java.util.concurrent.ConcurrentHashMap<>();
    
    private MethodInterface mi() {
    	return Universal.get().getMethods();
    }

    /**
     * Get the uuid manager.
     *
     * @return the uuid manager instance
     */
    public static synchronized UUIDManager get() {
        return instance == null ? instance = new UUIDManager() : instance;
    }

    /**
     * Initially setup the uuid manager by determening which {@link FetcherMode} should be used
     * based on the configured preference and the servers capabilities.
     */
    public void setup() {
    	MethodInterface mi = mi();
        if (mi.getBoolean(mi.getConfig(), "UUID-Fetcher.Dynamic", true)) {
            if (!mi.isOnlineMode()) {
                mode = FetcherMode.DISABLED;
            } else {
                if (Universal.get().isBungee()) {
                    mode = FetcherMode.MIXED;
                } else {
                    mode = FetcherMode.INTERN;
                }
            }
        } else {
            if (!mi.getBoolean(mi.getConfig(), "UUID-Fetcher.Enabled", true)) {
                mode = FetcherMode.DISABLED;
            } else if (mi.getBoolean(mi.getConfig(), "UUID-Fetcher.Intern", false)) {
                mode = FetcherMode.INTERN;
            } else {
                mode = FetcherMode.RESTFUL;
            }
        }
    }

    /**
     * Initially request the uuid bypassing the cache.<br>
     * If request succeeds the uuid will be automatically entered into the cache.
     *
     * @param name the name
     * @return the uuid
     */
    public String getInitialUUID(String name) {
    	MethodInterface mi = mi();
        name = name.toLowerCase(Locale.ROOT);
        if (!Security.isValidPlayerName(name)) {
            return null;
        }
        if (mode == FetcherMode.DISABLED)
            return name;

        if (mode == FetcherMode.INTERN || mode == FetcherMode.MIXED) {
            String internUUID = mi.getInternUUID(name);
            if (internUUID != null && Security.isValidUuid(internUUID)) {
                internUUID = Security.normalizeUuid(internUUID);
                cache(name, internUUID);
                return internUUID;
            }
            if (mode == FetcherMode.INTERN) {
                return null;
            }
        }

        String uuid = null;
        try {
            uuid = askAPI(mi.getString(mi.getConfig(), "UUID-Fetcher.REST-API.URL"), name, mi.getString(mi.getConfig(), "UUID-Fetcher.REST-API.Key"));
        } catch (IOException e) {
            Universal.get().logMessage("Console.UUIDFetchFailed", "&cFailed fetching UUID of %NAME%.",
                    "NAME", name, "URL", mi.getString(mi.getConfig(), "UUID-Fetcher.REST-API.URL"), "KEY", "");
            Universal.get().debugException(e);
        }

        if (uuid == null) {
            Universal.get().debug(MessageManager.getMessageOrDefault("Console.UUIDTryingBackup", "Trying to fetch UUID from backup API."));
            try {
                uuid = askAPI(mi.getString(mi.getConfig(), "UUID-Fetcher.BackUp-API.URL"), name, mi.getString(mi.getConfig(), "UUID-Fetcher.BackUp-API.Key"));
            } catch (IOException e) {
                Universal.get().logMessage("Console.UUIDFetchFailed", "&cFailed fetching UUID of %NAME%.",
                        "NAME", name, "URL", mi.getString(mi.getConfig(), "UUID-Fetcher.BackUp-API.URL"), "KEY", "");
                Universal.get().debugException(e);
            }
        }

        if (uuid == null) {
            Universal.get().logMessage("Console.UUIDFetchWarning",
                    "&eCould not fetch UUID for %NAME%. Check spelling and UUID-Fetcher settings.",
                    "NAME", name, "URL", "", "KEY", "");
        }

        if (!Security.isValidUuid(uuid)) {
            return null;
        }
        uuid = Security.normalizeUuid(uuid);
        cache(name, uuid);
        return uuid;
    }

    /**
     * Adds uuid to the cache
     *
     * @param name the name
     * @param uuid the uuid
     */
    public void supplyInternUUID(String name, UUID uuid) {
        if ((mode == FetcherMode.INTERN || mode == FetcherMode.MIXED) && Security.isValidPlayerName(name) && uuid != null) {
            cache(name.toLowerCase(Locale.ROOT), uuid.toString().replace("-", ""));
        }
    }

    public void discard(String name) {
        if (name != null) {
            String uuid = activeUUIDs.remove(name.toLowerCase(Locale.ROOT));
            if (uuid != null) {
                activeNamesByUuid.remove(uuid, name.toLowerCase(Locale.ROOT));
            }
        }
    }

    public void clear() {
        activeUUIDs.clear();
        activeNamesByUuid.clear();
    }

    /**
     * Convert String to UUID even if dashes are missing
     *
     * @param uuid
     * @return
     */
    public UUID fromString(String uuid) {
        if (uuid == null) {
            return null;
        }
        if (!uuid.contains("-") && uuid.length() == 32)
            uuid = uuid
                    .replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5");

        try {
            return uuid.length() == 36 && uuid.contains("-") ? UUID.fromString(uuid) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Get the uuid to a name.
     *
     * @param name the name
     * @return the uuid
     */
    public String getUUID(String name) {
        String inMemoryUuid = getInMemoryUUID(name);
        return (inMemoryUuid != null) ? inMemoryUuid : getInitialUUID(name);
    }

    /**
     * Gets a uuid from a name only if AdvancedBan
     * already has the uuid/name mapping in memory.
     *
     * @param name the player name
     * @return the nonhyphenated uuid or null if not found
     */
    public String getInMemoryUUID(String name) {
        if (name == null) {
            return null;
        }
        return activeUUIDs.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets a name from a uuid only if AdvancedBan
     * already has the uuid/name mapping in memory.
     *
     * @param uuid the uuid without hyphens
     * @return the player name or null if not found
     */
    public String getInMemoryName(String uuid) {
        if (uuid == null) {
            return null;
        }
        return activeNamesByUuid.get(Security.normalizeUuid(uuid));
    }

    /**
     * Get name from an uuid.
     *
     * @param uuid         the uuid
     * @param forceInitial whether to bypass the cache
     * @return the name from uuid
     */
    public String getNameFromUUID(String uuid, boolean forceInitial) {
    	MethodInterface mi = mi();
        if (mode == FetcherMode.DISABLED)
            return uuid;
        if (!Security.isValidUuid(uuid)) {
            return null;
        }
        uuid = Security.normalizeUuid(uuid);

        if (mode == FetcherMode.INTERN || mode == FetcherMode.MIXED) {
            String internName = mi.getName(uuid);
            if (mode == FetcherMode.INTERN || internName != null)
                return internName;
        }

        if (!forceInitial) {
            String inMemoryName = getInMemoryName(uuid);
            if (inMemoryName != null) {
                return inMemoryName;
            }
        }

        try {
            String name = Security.fetchJsonValue(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid, "name");
            if (Security.isValidPlayerName(name)) {
                cache(name.toLowerCase(Locale.ROOT), uuid);
                return name;
            }
        } catch (IOException | RuntimeException exc) {
            return null;
        }
        return null;
    }



    private String askAPI(String url, String name, String key) throws IOException {
        name = name.toLowerCase(Locale.ROOT);
        if (url == null || key == null) {
            return null;
        }
        String requestUrl = url.replace("%NAME%", name)
                .replace("%TIMESTAMP%", String.valueOf(System.currentTimeMillis()));
        String uuid = Security.fetchJsonValue(requestUrl, key);

        if (uuid == null) {
            Universal.get().logMessage("Console.UUIDMissingKey",
                    "&cCould not find key '%KEY%' while fetching UUID of %NAME%.",
                    "NAME", name, "URL", url, "KEY", key);
        }
        return uuid;
    }

    private void cache(String name, String uuid) {
        if (!Security.isValidPlayerName(name) || !Security.isValidUuid(uuid)) {
            return;
        }
        String normalizedName = name.toLowerCase(Locale.ROOT);
        String normalizedUuid = Security.normalizeUuid(uuid);
        int maxEntries = Math.max(128, Math.min(100_000,
                Security.getInt("Security.UUIDCacheMaxEntries", 10_000)));
        if (!activeUUIDs.containsKey(normalizedName) && activeUUIDs.size() >= maxEntries) {
            Iterator<Entry<String, String>> iterator = activeUUIDs.entrySet().iterator();
            if (iterator.hasNext()) {
                Entry<String, String> oldest = iterator.next();
                activeUUIDs.remove(oldest.getKey(), oldest.getValue());
                activeNamesByUuid.remove(oldest.getValue(), oldest.getKey());
            }
        }
        String previous = activeUUIDs.put(normalizedName, normalizedUuid);
        if (previous != null && !previous.equals(normalizedUuid)) {
            activeNamesByUuid.remove(previous, normalizedName);
        }
        activeNamesByUuid.put(normalizedUuid, normalizedName);
    }

    /**
     * Get the {@link FetcherMode} which is used.
     *
     * @return the mode
     */
    public FetcherMode getMode() {
        return mode;
    }

    /**
     * The fetcher-mode describes how the {@link UUIDManager} resolves UUIDs.
     */
    public enum FetcherMode {
        /**
         * No UUID Fetcher is used. The Username will be treated as an UUID.<br>
         * <b>Recommended for:</b> Servers running in offline mode (cracked).
         */
        DISABLED,

        /**
         * Uses the integrated uuid fetcher from spigot/bungeecord to resolved UUIDs.<br>
         * <b>Recommended for:</b> None (should not be used as a default setting /
         * maybe useful to avoid exceeding API rate limits.)
         */
        INTERN,

        /**
         * Tries to resolve the UUID using the {@link #INTERN} fetcher and uses the
         * {@link #RESTFUL} fetcher as a fallback.<br>
         * <b>Recommended for:</b> Spigot &amp; Bungeecord Servers running in online mode.
         */
        MIXED,

        /**
         * Resolves the UUID using the REST-Services configured in the config.yml.
         * <b>Recommended for:</b> Servers in offline mode which still try to keep track of name changes.
         */
        RESTFUL
    }
}

package me.leoko.advancedban.utils.litebans;

import litebans.api.Database;
import litebans.api.Entry;
import litebans.api.Events;
import litebans.api.PlayerProvider;
import litebans.api.RandomID;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import me.leoko.advancedban.utils.Security;
import me.leoko.advancedban.utils.SQLQuery;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LiteBansCompatibility {
    private static final AdvancedBanLiteBansEvents EVENTS = new AdvancedBanLiteBansEvents();
    private static volatile boolean enabled;

    private LiteBansCompatibility() {
    }

    public static void setup() {
        enabled = Universal.get().getMethods().getBoolean(Universal.get().getMethods().getConfig(), "litebans-api-support", false);
        if (!enabled) {
            shutdown();
            return;
        }
        Database.setInstance(new AdvancedBanLiteBansDatabase());
        Events.setInstance(EVENTS);
        RandomID.setInstance(new AdvancedBanRandomId());
        if (safeProvider() == null) {
            PlayerProvider.setInstance(new IdentityPlayerProvider());
        }
        Universal.get().log("LiteBans API compatibility enabled.");
    }

    public static void shutdown() {
        enabled = false;
        EVENTS.clear();
        Database.setInstance(null);
        Events.setInstance(null);
        PlayerProvider.setInstance(null);
        RandomID.setInstance(null);
    }

    public static String providePlayerTarget(String target) {
        if (!enabled || target == null) {
            return target;
        }
        PlayerProvider provider = safeProvider();
        if (provider == null) {
            return target;
        }
        try {
            String provided = provider.provide(target);
            return provided == null ? target : Security.limit(Security.sanitizeForStorage(provided), 64);
        } catch (RuntimeException ex) {
            Universal.get().debugException(ex);
            return target;
        }
    }

    public static void entryAdded(Punishment punishment) {
        if (enabled) {
            EVENTS.entryAdded(toEntry(punishment, true));
        }
    }

    public static void entryRemoved(Punishment punishment) {
        if (enabled) {
            EVENTS.entryRemoved(toEntry(punishment, false));
        }
    }

    public static void broadcastSent(String message, String type) {
        if (enabled) {
            EVENTS.broadcastSent(message, type);
        }
    }

    private static PlayerProvider safeProvider() {
        try {
            return PlayerProvider.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Entry toEntry(Punishment punishment, boolean active) {
        String type = toLiteBansType(punishment.getType());
        boolean ipBan = punishment.getType().isIpOrientated();
        return new AdvancedBanLiteBansEntry(
                punishment.getId(),
                type,
                ipBan ? null : punishment.getUuid(),
                ipBan ? punishment.getUuid() : null,
                punishment.getReason(),
                null,
                punishment.getOperator(),
                null,
                null,
                null,
                punishment.getStart(),
                punishment.getEnd(),
                Database.ANY_SERVER_SCOPE,
                "AdvancedBan",
                (byte) 255,
                false,
                ipBan,
                active);
    }

    private static String toLiteBansType(PunishmentType type) {
        switch (type.getBasic()) {
            case BAN:
                return "ban";
            case MUTE:
                return "mute";
            case WARNING:
                return "warn";
            case KICK:
                return "kick";
            default:
                return type.getName().toLowerCase(Locale.ROOT);
        }
    }

    private static final class AdvancedBanLiteBansDatabase extends Database {
        @Override
        public boolean isPlayerBanned(UUID uuid, String ip) {
            return isPlayerBanned(uuid, ip, null);
        }

        @Override
        public boolean isPlayerBanned(UUID uuid, String ip, String server) {
            return getBan(uuid, ip, server) != null;
        }

        @Override
        public boolean isPlayerMuted(UUID uuid, String ip) {
            return isPlayerMuted(uuid, ip, null);
        }

        @Override
        public boolean isPlayerMuted(UUID uuid, String ip, String server) {
            return getMute(uuid, ip, server) != null;
        }

        @Override
        public Entry getBan(long id, String server) {
            return byId(id, PunishmentType.BAN);
        }

        @Override
        public Entry getBan(UUID uuid, String ip, String server) {
            return byTarget(uuid, ip, PunishmentType.BAN);
        }

        @Override
        public Entry getMute(long id, String server) {
            return byId(id, PunishmentType.MUTE);
        }

        @Override
        public Entry getMute(UUID uuid, String ip, String server) {
            return byTarget(uuid, ip, PunishmentType.MUTE);
        }

        @Override
        public Entry getWarning(long id, String server) {
            return byId(id, PunishmentType.WARNING);
        }

        @Override
        public Entry getWarning(UUID uuid, String ip, String server) {
            return byTarget(uuid, ip, PunishmentType.WARNING);
        }

        @Override
        public Entry getKick(UUID uuid, String ip, String server) {
            return null;
        }

        @Override
        public Entry getKick(long id, String server) {
            return null;
        }

        @Override
        public Collection<UUID> getUsersByIP(String ip) {
            if (ip == null) {
                return Collections.emptyList();
            }
            List<UUID> result = new ArrayList<>();
            Universal.get().getIps().forEach((name, cachedIp) -> {
                if (ip.equals(cachedIp)) {
                    UUID uuid = UUIDManager.get().fromString(UUIDManager.get().getUUID(name));
                    if (uuid != null) {
                        result.add(uuid);
                    }
                }
            });
            return result;
        }

        @Override
        public String getPlayerName(UUID uuid) {
            return uuid == null ? null : UUIDManager.get().getNameFromUUID(Security.normalizeUuid(uuid.toString()), false);
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            return DatabaseManager.get().prepareExternalStatement(replaceTokens(sql));
        }

        private Entry byId(long id, PunishmentType basicType) {
            if (id > Integer.MAX_VALUE || id < 0) {
                return null;
            }
            Punishment punishment = PunishmentManager.get().getPunishment((int) id);
            return punishment != null && punishment.getType().getBasic() == basicType ? toEntry(punishment, true) : null;
        }

        private Entry byTarget(UUID uuid, String ip, PunishmentType basicType) {
            if (uuid != null) {
                List<Punishment> punishments = PunishmentManager.get().getPunishments(Security.normalizeUuid(uuid.toString()), basicType, true);
                if (!punishments.isEmpty()) {
                    return toEntry(punishments.get(0), true);
                }
            }
            if (ip != null) {
                List<Punishment> punishments = PunishmentManager.get().getPunishments(ip, basicType, true);
                if (!punishments.isEmpty()) {
                    return toEntry(punishments.get(0), true);
                }
            }
            return null;
        }

        private String replaceTokens(String sql) {
            String bans = SQLQuery.SELECT_ALL_PUNISHMENTS.toString().contains("`") ? "`Punishments`" : "Punishments";
            String history = SQLQuery.SELECT_ALL_PUNISHMENTS_HISTORY.toString().contains("`") ? "`PunishmentHistory`" : "PunishmentHistory";
            return sql
                    .replace("{bans}", bans)
                    .replace("{mutes}", bans)
                    .replace("{warnings}", bans)
                    .replace("{kicks}", history)
                    .replace("{history}", history)
                    .replace("{servers}", history);
        }
    }

    private static final class AdvancedBanLiteBansEvents extends Events {
        private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

        @Override
        public void register(Listener listener) {
            if (listener != null) {
                listeners.addIfAbsent(listener);
            }
        }

        @Override
        public void unregister(Listener listener) {
            listeners.remove(listener);
        }

        private void clear() {
            listeners.clear();
        }

        private void entryAdded(Entry entry) {
            listeners.forEach(listener -> listener.entryAdded(entry));
        }

        private void entryRemoved(Entry entry) {
            listeners.forEach(listener -> listener.entryRemoved(entry));
        }

        private void broadcastSent(String message, String type) {
            listeners.forEach(listener -> listener.broadcastSent(message, type));
        }
    }

    private static final class AdvancedBanLiteBansEntry extends Entry {
        private AdvancedBanLiteBansEntry(long id, String type, String uuid, String ip, String reason, String executorUUID,
                                         String executorName, String removedByUUID, String removedByName, String removalReason,
                                         long dateStart, long dateEnd, String serverScope, String serverOrigin, byte template,
                                         boolean silent, boolean ipban, boolean active) {
            super(id, type, uuid, ip, reason, executorUUID, executorName, removedByUUID, removedByName, removalReason,
                    dateStart, dateEnd, serverScope, serverOrigin, template, silent, ipban, active);
        }

        @Override
        public long getDuration() {
            return isPermanent() ? -1 : Math.max(0, getDateEnd() - getDateStart());
        }

        @Override
        public String getDurationString() {
            return String.valueOf(getDuration());
        }

        @Override
        public long getRemainingDuration(long currentTime) {
            return isPermanent() || isExpired(currentTime) ? -1 : getDateEnd() - currentTime;
        }

        @Override
        public String getRemainingDurationString(long currentTime) {
            return String.valueOf(getRemainingDuration(currentTime));
        }

        @Override
        public String getRandomID() {
            return RandomID.get().convert(getId());
        }

        @Override
        public boolean isExpired(long currentTime) {
            return !isPermanent() && getDateEnd() <= currentTime;
        }

        @Override
        public boolean isPermanent() {
            return getDateEnd() <= 0;
        }

        @Override
        public int getTemplateID() {
            return getTemplate() & 0xFF;
        }

        @Override
        public String getTemplateName() {
            return "";
        }

        @Override
        public boolean hasTemplate() {
            return getTemplateID() != 255;
        }
    }

    private static final class AdvancedBanRandomId extends RandomID {
        @Override
        public String convert(long id) {
            return Long.toString(id, 36).toUpperCase(Locale.ROOT);
        }

        @Override
        public long reveal(String randomID) {
            try {
                return Long.parseLong(randomID, 36);
            } catch (RuntimeException ex) {
                return RESULT_ERROR;
            }
        }
    }

    private static final class IdentityPlayerProvider extends PlayerProvider {
        @Override
        public String provide(String target) {
            return target;
        }
    }
}

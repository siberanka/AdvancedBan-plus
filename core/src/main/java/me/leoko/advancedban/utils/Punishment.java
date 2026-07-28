package me.leoko.advancedban.utils;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.DiscordWebhookManager;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.utils.litebans.LiteBansCompatibility;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by Leoko @ dev.skamps.eu on 30.05.2016.
 */
public class Punishment {

    private static final Object[] PERSISTENCE_LOCKS = new Object[64];

    static {
        for (int i = 0; i < PERSISTENCE_LOCKS.length; i++) {
            PERSISTENCE_LOCKS[i] = new Object();
        }
    }

    private final String name, uuid, operator, calculation;
    private final long start, end;
    private final PunishmentType type;

    private String reason;
    private int id;

    public Punishment(String name, String uuid, String reason, String operator, PunishmentType type, long start, long end, String calculation, int id) {
        this.name = name;
        this.uuid = uuid;
        this.reason = reason;
        this.operator = operator;
        this.type = type;
        this.start = start;
        this.end = end;
        this.calculation = calculation;
        this.id = id;
    }

    public static void create(String name, String target, String reason, String operator, PunishmentType type, Long end,
                              String calculation, boolean silent) {
        new Punishment(name, target, reason, operator, end == -1 ? type.getPermanent() : type,
                TimeManager.getTime(), end, calculation, -1)
                .create(silent);
    }

    public String getReason() {
        MethodInterface methods = methods();
        return (reason == null ? methods.getString(methods.getConfig(), "DefaultReason", "none") : reason).replace("'", "");
    }

    public String getHexId() {
        return Integer.toHexString(id).toUpperCase();
    }

    public String getDate(long date) {
        MethodInterface methods = methods();
        SimpleDateFormat format = new SimpleDateFormat(methods.getString(methods.getConfig(), "DateFormat", "dd.MM.yyyy-HH:mm"));
        return format.format(new Date(date));
    }

    public void create() {
        create(false);
    }

    public void create(boolean silent) {
        synchronized (persistenceLock()) {
            createLocked(silent);
        }
    }

    private void createLocked(boolean silent) {
        if (id != -1) {
            Universal.get().logMessage("Console.PunishmentOverwriteBlocked", "&cAdvancedBan blocked an attempt to overwrite an existing punishment.");
            Universal.get().debug("Failed at: " + toString());
            return;
        }

        if (uuid == null) {
            Universal.get().logMessage("Console.PunishmentMissingUuid", "&cAdvancedBan did not save %TYPE% because there is no fetched UUID.", "TYPE", getType().getName());
            Universal.get().debug("Failed at: " + toString());
            return;
        }

        PunishmentType basicType = getType().getBasic();
        if ((basicType == PunishmentType.BAN && PunishmentManager.get().getBan(uuid) != null)
                || (basicType == PunishmentType.MUTE && PunishmentManager.get().getMute(uuid) != null)) {
            Universal.get().logMessage("Console.PunishmentDuplicateBlocked",
                    "&cAdvancedBan blocked a concurrent duplicate %TYPE% for %NAME%.",
                    "TYPE", basicType.getName(), "NAME", getName());
            return;
        }

        final int cWarnings = getType().getBasic() == PunishmentType.WARNING ? (PunishmentManager.get().getCurrentWarns(getUuid()) + 1) : 0;

        id = DatabaseManager.get().storePunishment(this, silent);
        if (id < 0) {
            Universal.get().logMessage("Console.PunishmentTransactionFailed",
                    "&cPunishment was not created because its database transaction failed.");
            Universal.get().debug("Failed at: " + toString());
            return;
        }

        MethodInterface methods = methods();
        if (!silent) {
            announce(cWarnings);
        }

        methods.runSync(() -> {
            if (methods.isOnline(getName())) {
                final Object player = methods.getPlayer(getName());
                if (getType().getBasic() == PunishmentType.BAN || getType() == PunishmentType.KICK) {
                    methods.kickPlayer(getName(), getLayoutBSN());
                } else {
                    if (getType().getBasic() != PunishmentType.NOTE) {
                        for (String str : getLayout()) {
                            methods.sendMessage(player, str);
                        }
                    }
                    PunishmentManager.get().getLoadedPunishments(false).add(this);
                }
            }
        });

        PunishmentManager.get().getLoadedHistory().add(this);

        methods.callPunishmentEvent(this);
        LiteBansCompatibility.entryAdded(this);
        DiscordWebhookManager.get().punishmentCreated(this, silent);

        if (getType().getBasic() == PunishmentType.WARNING) {
            String cmd = null;
            for (int i = 1; i <= cWarnings; i++) {
                if (methods.contains(methods.getConfig(), "WarnActions." + i)) {
                    cmd = methods.getString(methods.getConfig(), "WarnActions." + i);
                }
            }
            if (cmd != null) {
                final String finalCmd = cmd
                        .replace("%PLAYER%", Security.sanitizeCommandPlaceholder(getName()))
                        .replace("%COUNT%", cWarnings + "")
                        .replace("%REASON%", Security.sanitizeCommandPlaceholder(getReason()));
                methods.runSync(() -> {
                    methods.executeCommand(finalCmd);
                    Universal.get().logMessage("Console.WarnActionExecuted", "Executing command: %COMMAND%", "COMMAND", finalCmd);
                });
            }
        }
    }

    public void updateReason(String reason) {
        this.reason = reason;

        if (id != -1) {
            DatabaseManager.get().updatePunishmentReason(this, reason);
        }
    }

    private void announce(int cWarnings) {
        MethodInterface methods = methods();
        List<String> notification = MessageManager.getLayout(methods.getMessages(),
                getType().getName() + ".Notification",
                "OPERATOR", getOperator(),
                "PREFIX", methods.getBoolean(methods.getConfig(), "Disable Prefix", false) ? "" : MessageManager.getMessage("General.Prefix"),
                "DURATION", getDuration(true),
                "REASON", getReason(),
                "NAME", getName(),
                "ID", String.valueOf(id),
                "HEXID", getHexId(),
                "DATE", getDate(start),
                "COUNT", cWarnings + "");

        methods.notify("ab.notify." + getType().getName(), notification);
        notification.forEach(message -> LiteBansCompatibility.broadcastSent(message, getType().getName()));
    }

    public void delete() {
        delete(null, false, true);
    }

    public void delete(String who, boolean massClear, boolean removeCache) {
        if (getType() == PunishmentType.KICK) {
            Universal.get().logMessage("Console.PunishmentDeleteKickBlocked", "&cFailed deleting: kicks cannot be deleted.");
            return;
        }

        if (id == -1) {
            Universal.get().logMessage("Console.PunishmentDeleteNotCreated", "&cFailed deleting: the punishment is not created yet.");
            Universal.get().debug("Failed at: " + toString());
            return;
        }

        DatabaseManager.get().revokePunishment(this, who);

        if (removeCache) {
            PunishmentManager.get().getLoadedPunishments(false).remove(this);
        }

        if (who != null) {
            String message = MessageManager.getMessage("Un" + getType().getBasic().getConfSection("Notification"),
                    true, "OPERATOR", who, "NAME", getName());
            methods().notify("ab.undoNotify." + getType().getBasic().getName(), Collections.singletonList(message));

            Universal.get().debug(who + " is deleting a punishment");
        }

        Universal.get().debug("Deleted punishment " + getId() + " from " + getName() + " punishment reason: " + getReason());
        methods().callRevokePunishmentEvent(this, massClear);
        LiteBansCompatibility.entryRemoved(this);
        DiscordWebhookManager.get().punishmentRevoked(this, who, massClear);
    }

    public List<String> getLayout() {
        boolean isLayout = getReason().startsWith("@") || getReason().startsWith("~");
        MethodInterface methods = methods();

        return MessageManager.getLayout(
                isLayout ? methods.getLayouts() : methods.getMessages(),
                isLayout ? "Message." + getReason().split(" ")[0].substring(1) : getType().getName() + ".Layout",
                "OPERATOR", getOperator(),
                "PREFIX", methods.getBoolean(methods.getConfig(), "Disable Prefix", false) ? "" : MessageManager.getMessage("General.Prefix"),
                "DURATION", getDuration(false),
                "REASON", isLayout ? (getReason().split(" ").length < 2 ? "" : getReason().substring(getReason().split(" ")[0].length() + 1)) : getReason(),
                "HEXID", getHexId(),
                "ID", String.valueOf(id),
                "DATE", getDate(start),
                "COUNT", getType().getBasic() == PunishmentType.WARNING ? (PunishmentManager.get().getCurrentWarns(getUuid()) + 1) + "" : "0");
    }

    private Object persistenceLock() {
        int hash = 31 * String.valueOf(uuid).hashCode() + getType().getBasic().hashCode();
        return PERSISTENCE_LOCKS[(hash & Integer.MAX_VALUE) % PERSISTENCE_LOCKS.length];
    }

    private static MethodInterface methods() {
        MethodInterface methods = Universal.get().getMethods();
        if (methods == null) {
            throw new IllegalStateException("AdvancedBan platform methods are unavailable.");
        }
        return methods;
    }

    public String getDuration(boolean fromStart) {
        String duration = MessageManager.getMessageOrDefault("General.Permanent", "permanent");
        if (getType().isTemp()) {
            long diff = ceilDiv(getEnd() - (fromStart ? start : TimeManager.getTime()), 1000L);
            if (diff > 60 * 60 * 24) {
                duration = MessageManager.getMessage("General.TimeLayoutD", getDurationParameter("D", diff / 60 / 60 / 24 + "", "H", diff / 60 / 60 % 24 + "", "M", diff / 60 % 60 + "", "S", diff % 60 + ""));
            } else if (diff > 60 * 60) {
                duration = MessageManager.getMessage("General.TimeLayoutH", getDurationParameter("H", diff / 60 / 60 + "", "M", diff / 60 % 60 + "", "S", diff % 60 + ""));
            } else if (diff > 60) {
                duration = MessageManager.getMessage("General.TimeLayoutM", getDurationParameter("M", diff / 60 + "", "S", diff % 60 + ""));
            } else {
                duration = MessageManager.getMessage("General.TimeLayoutS", getDurationParameter("S", diff + ""));
            }
        }
        return duration;
    }

    long ceilDiv(long x, long y) {
        return -Math.floorDiv(-x, y);
    }

    private String[] getDurationParameter(String... parameter) {
        int length = parameter.length;
        String[] newParameter = new String[length * 2];
        for (int i = 0; i < length; i += 2) {
            String name = parameter[i];
            String count = parameter[i + 1];

            newParameter[i] = name;
            newParameter[i + 1] = count;
            newParameter[length + i] = name + name;
            newParameter[length + i + 1] = (count.length() <= 1 ? "0" : "") + count;
        }

        return newParameter;
    }

    public String getLayoutBSN() {
        StringBuilder msg = new StringBuilder();
        for (String str : getLayout()) {
            msg.append("\n").append(str);
        }
        return msg.substring(1);
    }

    public boolean isExpired() {
        return getType().isTemp() && getEnd() <= TimeManager.getTime();
    }

    public String getName() {
        return this.name;
    }

    public String getUuid() {
        return this.uuid;
    }

    public String getOperator() {
        return this.operator;
    }

    public String getCalculation() {
        return this.calculation;
    }

    public long getStart() {
        return this.start;
    }

    public long getEnd() {
        return this.end;
    }

    public PunishmentType getType() {
        return this.type;
    }

    public int getId() {
        return this.id;
    }

    public String toString() {
        return "Punishment(name=" + this.getName() + ", uuid=" + this.getUuid() + ", operator=" + this.getOperator() + ", calculation=" + this.getCalculation() + ", start=" + this.getStart() + ", end=" + this.getEnd() + ", type=" + this.getType() + ", reason=" + this.getReason() + ", id=" + this.getId() + ")";
    }
}

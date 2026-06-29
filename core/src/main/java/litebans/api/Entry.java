package litebans.api;

public abstract class Entry {
    private final long id;
    private final String type;
    private final String uuid;
    private final String ip;
    private final String reason;
    private final String executorUUID;
    private final String executorName;
    private final String removedByUUID;
    private final String removedByName;
    private final String removalReason;
    private final long dateStart;
    private final long dateEnd;
    private final String serverScope;
    private final String serverOrigin;
    private final byte template;
    private final boolean silent;
    private final boolean ipban;
    private final boolean active;

    protected Entry(long id, String type, String uuid, String ip, String reason, String executorUUID,
                    String executorName, String removedByUUID, String removedByName, String removalReason,
                    long dateStart, long dateEnd, String serverScope, String serverOrigin, byte template,
                    boolean silent, boolean ipban, boolean active) {
        this.id = id;
        this.type = type;
        this.uuid = uuid;
        this.ip = ip;
        this.reason = reason;
        this.executorUUID = executorUUID;
        this.executorName = executorName;
        this.removedByUUID = removedByUUID;
        this.removedByName = removedByName;
        this.removalReason = removalReason;
        this.dateStart = dateStart;
        this.dateEnd = dateEnd;
        this.serverScope = serverScope;
        this.serverOrigin = serverOrigin;
        this.template = template;
        this.silent = silent;
        this.ipban = ipban;
        this.active = active;
    }

    public long getId() { return id; }
    public String getType() { return type; }
    public String getUuid() { return uuid; }
    public String getIp() { return ip; }
    public String getReason() { return reason; }
    public String getExecutorUUID() { return executorUUID; }
    public String getExecutorName() { return executorName; }
    public String getRemovedByUUID() { return removedByUUID; }
    public String getRemovedByName() { return removedByName; }
    public String getRemovalReason() { return removalReason; }
    public long getDateStart() { return dateStart; }
    public long getDateEnd() { return dateEnd; }
    public String getServerScope() { return serverScope; }
    public String getServerOrigin() { return serverOrigin; }
    protected byte getTemplate() { return template; }
    public boolean isSilent() { return silent; }
    public boolean isIpban() { return ipban; }
    public boolean isActive() { return active; }

    public abstract long getDuration();
    public abstract String getDurationString();
    public abstract long getRemainingDuration(long currentTime);
    public abstract String getRemainingDurationString(long currentTime);
    public abstract String getRandomID();
    public abstract boolean isExpired(long currentTime);
    public abstract boolean isPermanent();
    public abstract int getTemplateID();
    public abstract String getTemplateName();
    public abstract boolean hasTemplate();
}

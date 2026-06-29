package litebans.api;

import litebans.api.exception.MissingImplementationException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.UUID;

public abstract class Database {
    public static final String ANY_SERVER_SCOPE = "__ALL__";

    private static Database instance;

    public static void setInstance(Database instance) {
        Database.instance = instance;
    }

    public static Database get() {
        if (instance == null) throw new MissingImplementationException();
        return instance;
    }

    public abstract boolean isPlayerBanned(UUID uuid, String ip);
    public abstract boolean isPlayerBanned(UUID uuid, String ip, String server);
    public abstract boolean isPlayerMuted(UUID uuid, String ip);
    public abstract boolean isPlayerMuted(UUID uuid, String ip, String server);
    public abstract Entry getBan(long id, String server);
    public abstract Entry getBan(UUID uuid, String ip, String server);
    public abstract Entry getMute(long id, String server);
    public abstract Entry getMute(UUID uuid, String ip, String server);
    public abstract Entry getWarning(long id, String server);
    public abstract Entry getWarning(UUID uuid, String ip, String server);
    public abstract Entry getKick(UUID uuid, String ip, String server);
    public abstract Entry getKick(long id, String server);
    public abstract Collection<UUID> getUsersByIP(String ip);
    public abstract String getPlayerName(UUID uuid);
    public abstract PreparedStatement prepareStatement(String sql) throws SQLException;
}

package me.leoko.advancedban.manager;

import com.zaxxer.hikari.HikariDataSource;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.DynamicDataSource;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.SQLQuery;
import me.leoko.advancedban.utils.Security;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetFactory;
import javax.sql.rowset.RowSetProvider;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The Database Manager is used to interact directly with the database is use.<br>
 * Will automatically direct the requests to either MySQL or HSQLDB.
 * <br><br>
 * Looking to request {@link me.leoko.advancedban.utils.Punishment Punishments} from the Database?
 * Use {@link PunishmentManager#getPunishments(SQLQuery, Object...)} or
 * {@link PunishmentManager#getPunishmentFromResultSet(ResultSet)} for already parsed data.
 */
public class DatabaseManager {

    private HikariDataSource dataSource;
    private boolean useMySQL;
    private DatabaseFormat databaseFormat = DatabaseFormat.DEFAULT;
    private LiteBansStorage liteBansStorage;

    private RowSetFactory factory;
    
    private static DatabaseManager instance = null;

    /**
     * Get the instance of the command manager
     *
     * @return the database manager instance
     */
    public static synchronized DatabaseManager get() {
        return instance == null ? instance = new DatabaseManager() : instance;
    }

    /**
     * Initially connects to the database and sets up the required tables of they don't already exist.
     *
     * @param useMySQLServer whether to preferably use MySQL (uses HSQLDB as fallback)
     */
    public void setup(boolean useMySQLServer) {
        useMySQL = useMySQLServer;
        databaseFormat = readDatabaseFormat();
        liteBansStorage = databaseFormat == DatabaseFormat.LITEBANS ? new LiteBansStorage(this) : null;

        try {
            dataSource = new DynamicDataSource(useMySQL).generateDataSource();
        } catch (ClassNotFoundException ex) {
            Universal.get().logMessage("Console.DatabaseConfigureFailed", "&cERROR: Failed to configure data source!");
            Universal.get().debug(ex.getMessage());
            return;
        }

        if (isLiteBansFormat()) {
            liteBansStorage.setup();
            Universal.get().logMessage("Console.LiteBansDatabaseFormatEnabled",
                    "LiteBans database format enabled. Existing AdvancedBan tables are left untouched.");
        } else {
            executeStatement(SQLQuery.CREATE_TABLE_PUNISHMENT);
            executeStatement(SQLQuery.CREATE_TABLE_PUNISHMENT_HISTORY);
        }
    }

    /**
     * Shuts down the HSQLDB if used.
     */
    public void shutdown() {
        if (dataSource == null) {
            return;
        }
        if (!useMySQL) {
            try(Connection connection = dataSource.getConnection(); final PreparedStatement statement = connection.prepareStatement("SHUTDOWN")){
                statement.execute();
            }catch (SQLException | NullPointerException exc){
                Universal.get().logMessage("Console.DatabaseShutdownFailed", "An unexpected error has occurred turning off the database");
                Universal.get().debugException(exc);
            }
        }

        if (!dataSource.isClosed()) {
            dataSource.close();
        }
        if (!useMySQL) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private CachedRowSet createCachedRowSet() throws SQLException {
    	if (factory == null) {
    		factory = RowSetProvider.newFactory();
    	}
    	return factory.createCachedRowSet();
    }

    /**
     * Execute a sql statement without any results.
     *
     * @param sql        the sql statement
     * @param parameters the parameters
     */
    public void executeStatement(SQLQuery sql, Object... parameters) {
        if (isLiteBansFormat() && liteBansStorage.handles(sql)) {
            liteBansStorage.execute(sql, parameters);
            return;
        }
        executeStatement(sql, false, parameters);
    }

    /**
     * Execute a sql statement.
     *
     * @param sql        the sql statement
     * @param parameters the parameters
     * @return the result set
     */
    public ResultSet executeResultStatement(SQLQuery sql, Object... parameters) {
        if (isLiteBansFormat() && liteBansStorage.handles(sql)) {
            return liteBansStorage.query(sql, parameters);
        }
        return executeStatement(sql, true, parameters);
    }

    private ResultSet executeStatement(SQLQuery sql, boolean result, Object... parameters) {
        return executeStatement(sql.toString(), result, parameters);
    }

    private synchronized ResultSet executeStatement(String sql, boolean result, Object... parameters) {
        if (dataSource == null || dataSource.isClosed()) {
            Universal.get().logMessage("Console.DatabaseUnavailable", "Database is not available; statement skipped.");
            return null;
        }
    	try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {

    		for (int i = 0; i < parameters.length; i++) {
    			statement.setObject(i + 1, parameters[i]);
    		}

    		if (result) {
    			CachedRowSet results = createCachedRowSet();
    			results.populate(statement.executeQuery());
    			return results;
    		}
   			statement.execute();
    	} catch (SQLException ex) {
            Universal.get().logMessage("Console.DatabaseStatementFailed",
                    "An unexpected error has occurred executing a statement in the database. Please check latest.log/error.log and report this at https://github.com/siberanka/AdvancedBan-plus/issues");
    		Universal.get().debug("Query: \n" + sql);
    		Universal.get().debugSqlException(ex);
       	} catch (NullPointerException ex) {
            Universal.get().logMessage("Console.DatabaseConnectionFailed",
                    "An unexpected error has occurred connecting to the database. Check your MySQL data/server and report this at https://github.com/siberanka/AdvancedBan-plus/issues");
            Universal.get().debugException(ex);
        }
        return null;
    }

    public void executeRawStatement(String sql, Object... parameters) {
        executeStatement(sql, false, parameters);
    }

    public ResultSet executeRawResultStatement(String sql, Object... parameters) {
        return executeStatement(sql, true, parameters);
    }

    public int insertPunishment(Punishment punishment, boolean silent) {
        if (isLiteBansFormat()) {
            return liteBansStorage.insert(punishment, silent);
        }
        return -1;
    }

    public void revokePunishment(Punishment punishment, String who) {
        if (isLiteBansFormat()) {
            liteBansStorage.revoke(punishment, who);
        } else {
            executeStatement(SQLQuery.DELETE_PUNISHMENT, punishment.getId());
        }
    }

    public void updatePunishmentReason(Punishment punishment, String reason) {
        if (isLiteBansFormat()) {
            liteBansStorage.updateReason(punishment, reason);
        } else {
            executeStatement(SQLQuery.UPDATE_PUNISHMENT_REASON, reason, punishment.getId());
        }
    }

    public PreparedStatement prepareExternalStatement(String sql) throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database is not available");
        }
        Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("close".equals(method.getName())) {
                SQLException thrown = null;
                try {
                    statement.close();
                } catch (SQLException ex) {
                    thrown = ex;
                }
                try {
                    connection.close();
                } catch (SQLException ex) {
                    if (thrown == null) {
                        thrown = ex;
                    }
                }
                if (thrown != null) {
                    throw thrown;
                }
                return null;
            }
            try {
                return method.invoke(statement, args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        };
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                handler);
    }

    /**
     * Check whether there is a valid connection to the database.
     *
     * @return whether there is a valid connection
     */
    public boolean isConnectionValid() {
        return dataSource != null && dataSource.isRunning();
    }

    /**
     * Check whether MySQL is actually used.
     *
     * @return whether MySQL is used
     */
    public boolean isUseMySQL() {
        return useMySQL;
    }

    public boolean isLiteBansFormat() {
        return databaseFormat == DatabaseFormat.LITEBANS && liteBansStorage != null;
    }

    public String getStorageDescription() {
        String engine = useMySQL ? "MySQL/MariaDB" : "HSQLDB";
        return isLiteBansFormat() ? engine + " (LiteBans format)" : engine + " (AdvancedBan format)";
    }

    private DatabaseFormat readDatabaseFormat() {
        String configured;
        try {
            configured = Universal.get().getMethods().getString(Universal.get().getMethods().getConfig(),
                    "Database.database-format", "default");
        } catch (RuntimeException ex) {
            configured = "default";
        }
        configured = configured == null ? "default" : configured.trim().toLowerCase();
        if ("litebans".equals(configured)) {
            return DatabaseFormat.LITEBANS;
        }
        if (!"default".equals(configured)) {
            Universal.get().logMessage("Console.DatabaseFormatInvalid",
                    "&cUnknown Database.database-format '%FORMAT%'. Falling back to default.",
                    "FORMAT", Security.sanitizeForLog(configured));
        }
        return DatabaseFormat.DEFAULT;
    }

    private enum DatabaseFormat {
        DEFAULT,
        LITEBANS
    }
}

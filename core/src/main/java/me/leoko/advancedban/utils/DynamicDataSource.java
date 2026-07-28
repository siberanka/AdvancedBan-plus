package me.leoko.advancedban.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;

import java.io.File;

public class DynamicDataSource {
    private HikariConfig config = new HikariConfig();

    public DynamicDataSource(boolean preferMySQL) throws ClassNotFoundException {
        MethodInterface mi = Universal.get().getMethods();
        if (preferMySQL) {
            String ip = mi.getString(mi.getMySQLFile(), "MySQL.IP", "Unknown");
            String dbName = mi.getString(mi.getMySQLFile(), "MySQL.DB-Name", "Unknown");
            String usrName = mi.getString(mi.getMySQLFile(), "MySQL.Username", "Unknown");
            String password = mi.getString(mi.getMySQLFile(), "MySQL.Password", "Unknown");
            String properties = mi.getString(mi.getMySQLFile(), "MySQL.Properties", "verifyServerCertificate=false&useSSL=false&useUnicode=true&characterEncoding=utf8");
            int port = clamp(mi.getInteger(mi.getMySQLFile(), "MySQL.Port", 3306), 1, 65_535);

            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
                Class.forName("com.mysql.jdbc.Driver");
            }
            config.setJdbcUrl("jdbc:mysql://" + ip + ":" + port + "/" + dbName + "?"+properties);
            config.setUsername(usrName);
            config.setPassword(password);
        } else {
            // No need to worry about relocation because the maven-shade-plugin also changes strings
            String driverClassName = "org.hsqldb.jdbc.JDBCDriver";
            Class.forName(driverClassName);
            File dataFolder = mi.getDataFolder();
            if (!dataFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dataFolder.mkdirs();
            }
            config.setDriverClassName(driverClassName);
            config.setJdbcUrl("jdbc:hsqldb:file:" + mi.getDataFolder().getPath()
                    + "/data/storage;shutdown=true;hsqldb.write_delay=false");
            config.setUsername("SA");
            config.setPassword("");
        }
        int maximumPoolSize = clamp(Security.getInt("Database.MaximumPoolSize", 10), 1, 64);
        int minimumIdle = clamp(Security.getInt("Database.MinimumIdle", 1), 0, maximumPoolSize);
        long connectionTimeout = clamp(Security.getInt("Database.ConnectionTimeoutMillis", 5000), 250, 60_000);
        long validationTimeout = clamp(Security.getInt("Database.ValidationTimeoutMillis", 3000), 250,
                (int) Math.min(connectionTimeout, 60_000L));
        long leakDetection = Security.getInt("Database.LeakDetectionThresholdMillis", 0);
        if (leakDetection != 0L) {
            leakDetection = Math.max(2000L, Math.min(600_000L, leakDetection));
        }
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setValidationTimeout(validationTimeout);
        config.setLeakDetectionThreshold(leakDetection);
    }

    public HikariDataSource generateDataSource(){
        return new HikariDataSource(config);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

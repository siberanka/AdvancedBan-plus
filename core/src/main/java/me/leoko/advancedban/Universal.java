package me.leoko.advancedban;

import com.google.gson.Gson;
import me.leoko.advancedban.manager.*;
import me.leoko.advancedban.utils.Command;
import me.leoko.advancedban.utils.InterimData;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.Security;
import me.leoko.advancedban.utils.litebans.LiteBansCompatibility;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This is the server independent entry point of the plugin.
 */
public class Universal {

    private static Universal instance = null;

    public static void setRedis(boolean redis) {
        Universal.redis = redis;
    }

    private final Map<String, String> ips = new ConcurrentHashMap<>();
    private MethodInterface mi;
    private LogManager logManager;

    private static boolean redis = false;


    private final Gson gson = new Gson();





    /**
     * Get universal.
     *
     * @return the universal instance
     */
    public static Universal get() {
        return instance == null ? instance = new Universal() : instance;
    }

    /**
     * Initially sets up the plugin.
     *
     * @param mi the mi
     */
    public void setup(MethodInterface mi) {
        this.mi = mi;
        mi.loadFiles();
        logManager = new LogManager();
        UpdateManager.get().setup();
        UUIDManager.get().setup();

        try {
            DatabaseManager.get().setup(mi.getBoolean(mi.getConfig(), "UseMySQL", false));
        } catch (Exception ex) {
            log("Failed enabling database-manager...");
            debugException(ex);
        }

        mi.setupMetrics();
        PunishmentManager.get().setup();
        LiteBansCompatibility.setup();

        for (Command command : Command.values()) {
            for (String commandName : command.getNames()) {
                mi.setCommandExecutor(commandName, command.getPermission(), command.getTabCompleter());
            }
        }

        String upt = "You have the newest version";
        String response = getFromURL("https://api.spigotmc.org/legacy/update.php?resource=8695");
        if (response == null) {
            upt = "Failed to check for updates :(";
        } else if ((!mi.getVersion().startsWith(response))) {
            upt = "There is a new version available! [" + response + "]";
        }

        if (mi.getBoolean(mi.getConfig(), "DetailedEnableMessage", true)) {
            mi.log("\n \n&8[]=====[&7Enabling AdvancedBan&8]=====[]&r"
                    + "\n&8| &cInformation:&r"
                    + "\n&8|   &cName: &7AdvancedBan&r"
                    + "\n&8|   &cDeveloper: &7Leoko&r"
                    + "\n&8|   &cVersion: &7" + mi.getVersion() + "&r"
                    + "\n&8|   &cStorage: &7" + (DatabaseManager.get().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)") + "&r"
                    + "\n&8| &cSupport:&r"
                    + "\n&8|   &cGithub: &7https://github.com/DevLeoko/AdvancedBan/issues &r"
                    + "\n&8|   &cDiscord: &7https://discord.gg/ycDG6rS &r"
                    + "\n&8| &cTwitter: &7@LeokoGar&r"
                    + "\n&8| &cUpdate:&r"
                    + "\n&8|   &7" + upt  + "&r"
                    + "\n&8[]================================[]&r\n ");
        } else {
            mi.log("&cEnabling AdvancedBan on Version &7&r" + mi.getVersion());
            mi.log("&cCoded by &7Leoko &8| &7Twitter: @LeokoGar&r");
        }
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        LiteBansCompatibility.shutdown();
        DatabaseManager.get().shutdown();

        if (mi.getBoolean(mi.getConfig(), "DetailedDisableMessage", true)) {
            mi.log("\n \n&8[]=====[&7Disabling AdvancedBan&8]=====[]"
                    + "\n&8| &cInformation:"
                    + "\n&8|   &cName: &7AdvancedBan"
                    + "\n&8|   &cDeveloper: &7Leoko"
                    + "\n&8|   &cVersion: &7" + getMethods().getVersion()
                    + "\n&8|   &cStorage: &7" + (DatabaseManager.get().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)")
                    + "\n&8| &cSupport:"
                    + "\n&8|   &cGithub: &7https://github.com/DevLeoko/AdvancedBan/issues"
                    + "\n&8|   &cDiscord: &7https://discord.gg/ycDG6rS"
                    + "\n&8| &cTwitter: &7@LeokoGar"
                    + "\n&8[]================================[]&r\n ");
        } else {
            mi.log("&cDisabling AdvancedBan on Version &7" + getMethods().getVersion());
            mi.log("&cCoded by Leoko &8| &7Twitter: @LeokoGar");
        }
    }

    /**
     * Gets methods.
     *
     * @return the methods
     */
    public MethodInterface getMethods() {
        return mi;
    }

    /**
     * Is bungee boolean.
     *
     * @return the boolean
     */
    public boolean isBungee() {
        return mi.isBungee();
    }

    public Map<String, String> getIps() {
        return ips;
    }

    public static boolean isRedis() {
        return redis;
    }

    public Gson getGson() {
        return gson;
    }

    /**
     * Gets from url.
     *
     * @param surl the surl
     * @return the from url
     */
    public String getFromURL(String surl) {
        String response = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(surl).openConnection();
            connection.setConnectTimeout(Security.getInt("Security.HttpConnectTimeoutMillis", 3000));
            connection.setReadTimeout(Security.getInt("Security.HttpReadTimeoutMillis", 3000));
            connection.setUseCaches(false);
            try (Scanner s = new Scanner(connection.getInputStream(), "UTF-8")) {
                if (s.hasNext()) {
                    response = Security.limit(s.next(), 64);
                }
            }
        } catch (IOException exc) {
            debug("!! Failed to connect to URL: " + surl);
        }
        return response;
    }

    /**
     * Is mute command boolean.
     *
     * @param cmd the cmd
     * @return the boolean
     */
    public boolean isMuteCommand(String cmd) {
        return isMuteCommand(cmd, getMethods().getStringList(getMethods().getConfig(), "MuteCommands"));
    }

    /**
     * Visible for testing. Do not use this. Please use {@link #isMuteCommand(String)}.
     * 
     * @param cmd          the command
     * @param muteCommands the mute commands from the config
     * @return true if the command matched any of the mute commands.
     */
    boolean isMuteCommand(String cmd, List<String> muteCommands) {
        if (cmd == null || cmd.length() > Security.DEFAULT_MAX_TOTAL_COMMAND_LENGTH) {
            return false;
        }
        String[] words = cmd.split(" ");
        if (words.length == 0 || words[0].isEmpty()) {
            return false;
        }
        // Handle commands with colons
        if (words[0].indexOf(':') != -1) {
            words[0] = words[0].split(":", 2)[1];
        }
        for (String muteCommand : muteCommands) {
            if (muteCommandMatches(words, muteCommand)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Visible for testing. Do not use this.
     * 
     * @param commandWords the command run by a player, separated into its words
     * @param muteCommand a mute command from the config
     * @return true if they match, false otherwise
     */
    boolean muteCommandMatches(String[] commandWords, String muteCommand) {
        // Basic equality check
        if (commandWords[0].equalsIgnoreCase(muteCommand)) {
            return true;
        }
        // Advanced equality check
        // Essentially a case-insensitive "startsWith" for arrays
        if (muteCommand.indexOf(' ') != -1) {
            String[] muteCommandWords = muteCommand.split(" ");
            if (muteCommandWords.length > commandWords.length) {
                return false;
            }
            for (int n = 0; n < muteCommandWords.length; n++) {
                if (!muteCommandWords[n].equalsIgnoreCase(commandWords[n])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Is exempt player boolean.
     *
     * @param name the name
     * @return the boolean
     */
    public boolean isExemptPlayer(String name) {
        List<String> exempt = getMethods().getStringList(getMethods().getConfig(), "ExemptPlayers");
        if (exempt != null) {
            for (String str : exempt) {
                if (name.equalsIgnoreCase(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Broadcast leoko boolean.
     *
     * @return the boolean
     */
    public boolean broadcastLeoko() {
        File readme = new File(getMethods().getDataFolder(), "readme.txt");
        if (!readme.exists()) {
            return true;
        }
        try {
            if (Files.readAllLines(Paths.get(readme.getPath()), Charset.defaultCharset()).get(0).equalsIgnoreCase("I don't want that there will be any message when the dev of this plugin joins the server! I want this even though the plugin is 100% free and the join-message is the only reward for the Dev :(")) {
                return false;
            }
        } catch (IOException ignore) {
        }
        return true;
    }

    /**
     * Call connection string.
     *
     * @param name the name
     * @param ip   the ip
     * @return the string
     */
    public String callConnection(String name, String ip) {
        name = name.toLowerCase();
        String uuid = UUIDManager.get().getUUID(name);
        if (uuid == null) return "[AdvancedBan] Failed to fetch your UUID";

        if (ip != null) {
            getIps().remove(name);
            getIps().put(name, ip);
        }

        InterimData interimData = PunishmentManager.get().load(name, uuid, ip);

        if (interimData == null) {
            if (getMethods().getBoolean(mi.getConfig(), "LockdownOnError", true)) {
                return "[AdvancedBan] Failed to load player data!";
            } else {
                return null;
            }
        }

        Punishment pt = interimData.getBan();

        if (pt == null) {
            interimData.accept();
            return null;
        }

        return pt.getLayoutBSN();
    }

    /**
     * Has perms boolean.
     *
     * @param player the player
     * @param perms  the perms
     * @return the boolean
     */
    public boolean hasPerms(Object player, String perms) {
        if (mi.hasPerms(player, perms)) {
            return true;
        }

        if (mi.getBoolean(mi.getConfig(), "EnableAllPermissionNodes", false)) {
            while (perms.contains(".")) {
                perms = perms.substring(0, perms.lastIndexOf('.'));
                if (mi.hasPerms(player, perms + ".all")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Log.
     *
     * @param msg the msg
     */
    public void log(String msg) {
        String cleanMessage = Security.sanitizeForLog(msg);
        mi.log("§8[§cAdvancedBan§8] §7" + cleanMessage);
        debugToFile(cleanMessage);
    }

    /**
     * Debug.
     *
     * @param msg the msg
     */
    public void debug(Object msg) {
        String cleanMessage = Security.sanitizeForLog(String.valueOf(msg));
        if (mi.getBoolean(mi.getConfig(), "Debug", false)) {
            mi.log("§8[§cAdvancedBan§8] §cDebug: §7" + cleanMessage);
        }
        debugToFile(cleanMessage);
    }

    public void debugException(Exception exc) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exc.printStackTrace(pw);
        debug(sw.toString());
    }

    /**
     * Debug.
     *
     * @param ex the ex
     */
    public void debugSqlException(SQLException ex) {
        if (mi.getBoolean(mi.getConfig(), "Debug", false)) {
            debug("§7An error has occurred with the database, the error code is: '" + ex.getErrorCode() + "'");
            debug("§7The state of the sql is: " + ex.getSQLState());
            debug("§7Error message: " + ex.getMessage());
        }
        debugException(ex);
    }

    private void debugToFile(Object msg) {
        File debugFile = new File(mi.getDataFolder(), "logs/latest.log");
        File parent = debugFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        if (!debugFile.exists()) {
            try {
                debugFile.createNewFile();
            } catch (IOException ex) {
                System.out.print("An error has occurred creating the 'latest.log' file again, check your server.");
                System.out.print("Error message" + ex.getMessage());
            }
        } else {
            logManager.checkLastLog(false);
        }
        try {
            FileUtils.writeStringToFile(debugFile, "[" + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()) + "] " + mi.clearFormatting(Security.sanitizeForLog(String.valueOf(msg))) + "\n", "UTF8", true);
        } catch (IOException ex) {
            System.out.print("An error has occurred writing to 'latest.log' file.");
            System.out.print(ex.getMessage());
        }
    }
}

package me.leoko.advancedban;

import com.google.gson.Gson;
import me.leoko.advancedban.manager.*;
import me.leoko.advancedban.utils.Command;
import me.leoko.advancedban.utils.GitHubUpdateChecker;
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
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
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
    private volatile GitHubUpdateChecker.Result lastUpdateCheck;

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
            logMessage("Console.DatabaseManagerEnableFailed", "Failed enabling database-manager...");
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

        String upt = mi.getBoolean(mi.getConfig(), "UpdateChecker.Enabled", true)
                ? "GitHub release check scheduled"
                : "GitHub release check disabled";

        if (mi.getBoolean(mi.getConfig(), "DetailedEnableMessage", true)) {
            logLayout("Console.EnableDetailed", getConsoleParameters(upt));
        } else {
            logMessage("Console.EnableSimple", "&cEnabling AdvancedBan on Version &7%VERSION%",
                    "VERSION", mi.getVersion());
        }

        if (!mi.isUnitTesting()) {
            requestUpdateCheck(null);
        }
    }

    /**
     * Shutdown.
     */
    public void shutdown() {
        LiteBansCompatibility.shutdown();
        DiscordWebhookManager.get().clear();
        DatabaseManager.get().shutdown();

        if (mi.getBoolean(mi.getConfig(), "DetailedDisableMessage", true)) {
            logLayout("Console.DisableDetailed", getConsoleParameters(""));
        } else {
            logMessage("Console.DisableSimple", "&cDisabling AdvancedBan on Version &7%VERSION%",
                    "VERSION", getMethods().getVersion());
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

    public GitHubUpdateChecker.Result getLastUpdateCheck() {
        return lastUpdateCheck;
    }

    public void requestUpdateCheck(Object requester) {
        if (!mi.getBoolean(mi.getConfig(), "UpdateChecker.Enabled", true)) {
            if (requester != null) {
                sendUpdateMessage(requester, "Update.Disabled", "&cGitHub update checks are disabled in config.");
            }
            return;
        }
        if (requester != null) {
            sendUpdateMessage(requester, "Update.Checking", "&7Checking GitHub releases...");
        }
        mi.runAsync(() -> {
            try {
                GitHubUpdateChecker.Result result = new GitHubUpdateChecker().check(mi.getVersion());
                lastUpdateCheck = result;
                if (requester != null) {
                    sendUpdateResult(requester, result);
                } else if (result.isUpdateAvailable()) {
                    logMessage("Console.UpdateAvailable", "&eAdvancedBan Plus update available: &7%CURRENT% &8-> &a%LATEST%",
                            "CURRENT", result.getCurrentVersion(), "LATEST", result.getLatestVersion(), "URL", result.getReleaseUrl());
                    logMessage("Console.UpdateDownload", "&eDownload: &7%URL%",
                            "CURRENT", result.getCurrentVersion(), "LATEST", result.getLatestVersion(), "URL", result.getReleaseUrl());
                    mi.notify("ab.update.notify", getUpdateNotification(result));
                } else {
                    debug(MessageManager.getMessageOrDefault("Console.UpdateCurrent", "AdvancedBan Plus is up to date according to GitHub releases."));
                }
            } catch (Exception ex) {
                if (requester != null) {
                    sendUpdateMessage(requester, "Update.Failed", "&cCould not check GitHub releases. See error.log for details.");
                } else {
                    logMessage("Console.UpdateFailed", "&cFailed to check GitHub releases. See error.log for details.");
                }
                debugException(ex);
            }
        });
    }

    private void sendUpdateResult(Object receiver, GitHubUpdateChecker.Result result) {
        if (result.isUpdateAvailable()) {
            sendUpdateMessage(receiver, "Update.Available",
                    "&eAdvancedBan Plus %CURRENT% is outdated. Latest: %LATEST%",
                    "CURRENT", result.getCurrentVersion(),
                    "LATEST", result.getLatestVersion(),
                    "URL", result.getReleaseUrl());
            sendUpdateMessage(receiver, "Update.AvailableLink",
                    "&7Download: %URL%",
                    "CURRENT", result.getCurrentVersion(),
                    "LATEST", result.getLatestVersion(),
                    "URL", result.getReleaseUrl());
        } else {
            sendUpdateMessage(receiver, "Update.UpToDate",
                    "&aYou are running the latest AdvancedBan Plus version (%CURRENT%).",
                    "CURRENT", result.getCurrentVersion(),
                    "LATEST", result.getLatestVersion(),
                    "URL", result.getReleaseUrl());
        }
    }

    private List<String> getUpdateNotification(GitHubUpdateChecker.Result result) {
        if (mi.contains(mi.getMessages(), "Update.Notification")) {
            return MessageManager.getLayout(mi.getMessages(), "Update.Notification",
                    "CURRENT", result.getCurrentVersion(),
                    "LATEST", result.getLatestVersion(),
                    "URL", result.getReleaseUrl());
        }
        return Arrays.asList(
                "\u00A7eAdvancedBan Plus " + result.getCurrentVersion() + " is outdated. Latest: " + result.getLatestVersion(),
                "\u00A77Download: " + result.getReleaseUrl());
    }

    private void sendUpdateMessage(Object receiver, String path, String fallback, String... parameters) {
        String message = mi.contains(mi.getMessages(), path)
                ? MessageManager.getMessage(path, true, parameters)
                : replaceParameters(fallback, parameters).replace('&', '\u00A7');
        mi.sendMessage(receiver, message);
    }

    private String replaceParameters(String message, String... parameters) {
        for (int i = 0; i < parameters.length - 1; i += 2) {
            message = message.replace("%" + parameters[i] + "%", parameters[i + 1]);
        }
        return message;
    }

    public void logMessage(String path, String fallback, String... parameters) {
        try {
            log(MessageManager.getMessageOrDefault(path, fallback, parameters));
        } catch (RuntimeException ex) {
            log(replaceParameters(fallback, parameters));
        }
    }

    public void logLayout(String path, String... parameters) {
        try {
            List<String> lines = MessageManager.getLayoutOrDefault(mi.getMessages(), path,
                    Collections.singletonList("&cAdvancedBan %VERSION%"), parameters);
            for (String line : lines) {
                mi.log(line);
            }
        } catch (RuntimeException ex) {
            mi.log(replaceParameters("&cAdvancedBan %VERSION%", parameters).replace('&', '\u00A7'));
        }
    }

    private String[] getConsoleParameters(String updateStatus) {
        return new String[]{
                "VERSION", mi.getVersion(),
                "STORAGE", DatabaseManager.get().getStorageDescription(),
                "UPDATE", updateStatus,
                "GITHUB", "https://github.com/siberanka/AdvancedBan-plus/issues",
                "DISCORD", "https://discord.gg/ycDG6rS"
        };
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
        if (uuid == null) {
            return MessageManager.getMessageOrDefault("Connection.FailedUUID", "[AdvancedBan] Failed to fetch your UUID");
        }

        if (ip != null) {
            getIps().remove(name);
            getIps().put(name, ip);
        }

        InterimData interimData = PunishmentManager.get().load(name, uuid, ip);

        if (interimData == null) {
            if (getMethods().getBoolean(mi.getConfig(), "LockdownOnError", true)) {
                return MessageManager.getMessageOrDefault("Connection.FailedDataLoad", "[AdvancedBan] Failed to load player data!");
            } else {
                return null;
            }
        }

        Punishment pt = interimData.getBan();

        if (pt == null) {
            interimData.accept();
            return null;
        }

        DiscordWebhookManager.get().bannedJoinAttempt(name, ip, pt);
        return pt.getLayoutBSN();
    }

    public void notifyMutedAttempt(String name, String ip, Punishment punishment, String message, boolean command) {
        DiscordWebhookManager.get().mutedChatAttempt(name, ip, punishment, message, command);
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
        debugThrowable(exc);
    }

    public void debugThrowable(Throwable exc) {
        if (exc == null) {
            return;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exc.printStackTrace(pw);
        String stackTrace = sw.toString();
        String advice = getErrorAdvice(exc, stackTrace);
        debug(stackTrace);
        if (advice != null) {
            logMessage("Console.ErrorPossibleSolution", "&ePossible solution: %SOLUTION%", "SOLUTION", advice);
        }
        writeErrorLog(advice == null ? stackTrace : stackTrace + "\nPossible solution: " + mi.clearFormatting(advice));
    }

    private String getErrorAdvice(Throwable throwable, String stackTrace) {
        if (throwable == null || mi == null || mi.getMessages() == null) {
            return null;
        }
        String text = String.valueOf(throwable.getMessage()) + "\n" + stackTrace;
        if (text.contains("YamlMaintenanceManager") &&
                (throwable instanceof ClassCastException || throwable instanceof ArrayStoreException)) {
            return MessageManager.getMessageOrDefault("ErrorSolutions.YamlMaintenanceKeyType",
                    "A YAML file contains numeric keys. Update AdvancedBan and let YamlMaintenance repair the file from a backup.");
        }
        if (throwable instanceof SQLException || text.contains("HikariPool") || text.contains("SQLTransientConnectionException")) {
            return MessageManager.getMessageOrDefault("ErrorSolutions.DatabaseConnection",
                    "Check database host, port, credentials, pool limits and network reachability. Keep LockdownOnError enabled until storage is healthy.");
        }
        if (text.contains("FoliaSchedulerBridge") || text.contains("Folia") && text.contains("scheduler")) {
            return MessageManager.getMessageOrDefault("ErrorSolutions.FoliaScheduler",
                    "Use the AdvancedBan-Bundle jar on Folia and do not run unsupported scheduler forks or reloaders.");
        }
        if (text.contains("voicechat") || text.contains("BukkitVoicechatService")) {
            return MessageManager.getMessageOrDefault("ErrorSolutions.VoiceChat",
                    "Update Simple Voice Chat and keep VoiceChat.MuteIntegration.Enabled false until the voicechat service loads cleanly.");
        }
        return null;
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
            if (logManager != null) {
                logManager.checkLastLog(false);
            }
        }
        try {
            FileUtils.writeStringToFile(debugFile, "[" + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()) + "] " + mi.clearFormatting(Security.sanitizeForLog(String.valueOf(msg))) + "\n", "UTF8", true);
        } catch (IOException ex) {
            System.out.print("An error has occurred writing to 'latest.log' file.");
            System.out.print(ex.getMessage());
        }
    }

    private void writeErrorLog(String stackTrace) {
        if (!Security.getBoolean("ErrorLog.Enabled", true) || mi == null || mi.getDataFolder() == null) {
            return;
        }
        int maxEntryChars = Security.getInt("ErrorLog.MaxEntryChars", 32768);
        String safeStackTrace = Security.limit(stackTrace.replace("${", "$ {").replace('\r', ' '), maxEntryChars);
        String entry = "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(System.currentTimeMillis()) + "] "
                + safeStackTrace + "\n";
        File errorFile = new File(mi.getDataFolder(), "error.log");
        File parent = errorFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try {
            rotateErrorLogIfNeeded(errorFile, entry.length());
            FileUtils.writeStringToFile(errorFile, entry, "UTF8", true);
        } catch (IOException ex) {
            System.out.print("An error has occurred writing to 'error.log'.");
            System.out.print(ex.getMessage());
        }
    }

    private void rotateErrorLogIfNeeded(File errorFile, int pendingBytes) throws IOException {
        long maxBytes = Math.max(64 * 1024L, Security.getInt("ErrorLog.MaxBytes", 1024 * 1024));
        if (!errorFile.exists() || errorFile.length() + pendingBytes <= maxBytes) {
            return;
        }
        int backups = Math.max(1, Math.min(10, Security.getInt("ErrorLog.Backups", 3)));
        File oldest = new File(errorFile.getParentFile(), "error.log." + backups);
        Files.deleteIfExists(oldest.toPath());
        for (int i = backups - 1; i >= 1; i--) {
            File source = new File(errorFile.getParentFile(), "error.log." + i);
            if (source.exists()) {
                Files.move(source.toPath(), new File(errorFile.getParentFile(), "error.log." + (i + 1)).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(errorFile.toPath(), new File(errorFile.getParentFile(), "error.log.1").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}

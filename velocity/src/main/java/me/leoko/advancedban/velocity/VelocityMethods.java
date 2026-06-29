package me.leoko.advancedban.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.UUIDManager;
import me.leoko.advancedban.utils.Permissionable;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.Security;
import me.leoko.advancedban.utils.tabcompletion.TabCompleter;
import me.leoko.advancedban.velocity.event.PunishmentEvent;
import me.leoko.advancedban.velocity.event.RevokePunishmentEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VelocityMethods implements MethodInterface {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final VelocityMain plugin;
    private final File dataFolder;
    private SimpleYamlConfig config;
    private SimpleYamlConfig messages;
    private SimpleYamlConfig layouts;
    private SimpleYamlConfig mysql;

    VelocityMethods(VelocityMain plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataDirectory().toFile();
    }

    static Component component(String message) {
        return LEGACY.deserialize(message == null ? "" : message.replace('&', '\u00A7'));
    }

    @Override
    public void loadFiles() {
        try {
            if (!dataFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dataFolder.mkdirs();
            }
            copyDefault("config.yml");
            copyDefault("Messages.yml");
            copyDefault("Layouts.yml");

            config = loadFile("config.yml");
            messages = loadFile("Messages.yml");
            layouts = loadFile("Layouts.yml");
            File mysqlFile = new File(dataFolder, "MySQL.yml");
            mysql = mysqlFile.exists() ? loadFile("MySQL.yml") : config;
        } catch (IOException ex) {
            Universal.get().debugException(ex);
        }
    }

    private void copyDefault(String name) throws IOException {
        Path target = new File(dataFolder, name).toPath();
        if (!Files.exists(target)) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
                if (input != null) {
                    Files.copy(input, target);
                }
            }
        }
    }

    private SimpleYamlConfig loadFile(String name) throws IOException {
        try (Reader reader = Files.newBufferedReader(new File(dataFolder, name).toPath(), StandardCharsets.UTF_8)) {
            return SimpleYamlConfig.load(reader);
        }
    }

    @Override
    public String getFromUrlJson(String url, String key) {
        try {
            HttpURLConnection request = (HttpURLConnection) new URL(url).openConnection();
            request.setConnectTimeout(Security.getInt("Security.HttpConnectTimeoutMillis", 3000));
            request.setReadTimeout(Security.getInt("Security.HttpReadTimeoutMillis", 3000));
            request.setUseCaches(false);
            request.connect();
            return parseJSON(new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8), key);
        } catch (Exception exc) {
            return null;
        }
    }

    @Override
    public String getVersion() {
        return "2026.06.29.7";
    }

    @Override
    public String[] getKeys(Object file, String path) {
        return ((SimpleYamlConfig) file).getKeys(path);
    }

    @Override
    public Object getConfig() { return config; }

    @Override
    public Object getMessages() { return messages; }

    @Override
    public Object getLayouts() { return layouts; }

    @Override
    public void setupMetrics() {
        // Velocity bStats requires platform injection; keep startup deterministic if it is absent.
    }

    @Override
    public boolean isBungee() { return true; }

    @Override
    public String clearFormatting(String text) { return Security.stripFormatting(text); }

    @Override
    public Object getPlugin() { return plugin; }

    @Override
    public File getDataFolder() { return dataFolder; }

    @Override
    public void setCommandExecutor(String cmd, String permission, TabCompleter tabCompleter) {
        plugin.getProxy().getCommandManager().register(
                plugin.getProxy().getCommandManager().metaBuilder(cmd).plugin(plugin).build(),
                new VelocityCommand(permission, tabCompleter));
    }

    @Override
    public void sendMessage(Object player, String msg) {
        ((CommandSource) player).sendMessage(component(msg));
    }

    @Override
    public String getName(Object player) {
        return player instanceof Player ? ((Player) player).getUsername() : "CONSOLE";
    }

    @Override
    public String getName(String uuid) {
        UUID parsed = UUIDManager.get().fromString(uuid);
        return parsed == null ? null : plugin.getProxy().getPlayer(parsed).map(Player::getUsername).orElse(null);
    }

    @Override
    public String getIP(Object player) {
        return player instanceof Player ? ((Player) player).getRemoteAddress().getAddress().getHostAddress() : "127.0.0.1";
    }

    @Override
    public String getInternUUID(Object player) {
        return player instanceof Player ? ((Player) player).getUniqueId().toString().replace("-", "") : "none";
    }

    @Override
    public String getInternUUID(String player) {
        return plugin.getProxy().getPlayer(player).map(value -> value.getUniqueId().toString().replace("-", "")).orElse(null);
    }

    @Override
    public boolean hasPerms(Object player, String perms) {
        return player instanceof CommandSource && ((CommandSource) player).hasPermission(perms);
    }

    @Override
    public Permissionable getOfflinePermissionPlayer(String name) {
        return permission -> false;
    }

    @Override
    public boolean isOnline(String name) {
        return plugin.getProxy().getPlayer(name).isPresent();
    }

    @Override
    public Object getPlayer(String name) {
        Optional<Player> player = plugin.getProxy().getPlayer(name);
        return player.orElse(null);
    }

    @Override
    public void kickPlayer(String player, String reason) {
        plugin.getProxy().getPlayer(player).ifPresent(value -> value.disconnect(component(reason)));
    }

    @Override
    public Object[] getOnlinePlayers() {
        return plugin.getProxy().getAllPlayers().toArray(new Player[0]);
    }

    @Override
    public void scheduleAsyncRep(Runnable rn, long l1, long l2) {
        plugin.getProxy().getScheduler().buildTask(plugin, rn).delay(l1 * 50, TimeUnit.MILLISECONDS).repeat(l2 * 50, TimeUnit.MILLISECONDS).schedule();
    }

    @Override
    public void scheduleAsync(Runnable rn, long l1) {
        plugin.getProxy().getScheduler().buildTask(plugin, rn).delay(l1 * 50, TimeUnit.MILLISECONDS).schedule();
    }

    @Override
    public void runAsync(Runnable rn) {
        plugin.getProxy().getScheduler().buildTask(plugin, rn).schedule();
    }

    @Override
    public void runSync(Runnable rn) {
        rn.run();
    }

    @Override
    public void executeCommand(String cmd) {
        plugin.getProxy().getCommandManager().executeAsync(plugin.getProxy().getConsoleCommandSource(), cmd);
    }

    @Override
    public boolean callChat(Object player) {
        Punishment pnt = PunishmentManager.get().getMute(UUIDManager.get().getUUID(getName(player)));
        if (pnt != null) {
            for (String str : pnt.getLayout()) {
                sendMessage(player, str);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean callCMD(Object player, String cmd) {
        Punishment pnt;
        if (cmd != null && cmd.length() > 1 && Universal.get().isMuteCommand(cmd.substring(1))
                && (pnt = PunishmentManager.get().getMute(UUIDManager.get().getUUID(getName(player)))) != null) {
            for (String str : pnt.getLayout()) {
                sendMessage(player, str);
            }
            return true;
        }
        return false;
    }

    @Override
    public Object getMySQLFile() { return mysql; }

    @Override
    public String parseJSON(InputStreamReader json, String key) {
        try {
            char[] buffer = new char[8192];
            int len = json.read(buffer);
            return len <= 0 ? null : parseJSON(new String(buffer, 0, len), key);
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public String parseJSON(String json, String key) {
        String quotedKey = "\"" + key + "\"";
        int keyIndex = json.indexOf(quotedKey);
        if (keyIndex == -1) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + quotedKey.length());
        if (colon == -1) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        int end = start == -1 ? -1 : json.indexOf('"', start + 1);
        return start == -1 || end == -1 ? null : json.substring(start + 1, end);
    }

    @Override
    public Boolean getBoolean(Object file, String path) { return ((SimpleYamlConfig) file).getBoolean(path, false); }

    @Override
    public String getString(Object file, String path) { return ((SimpleYamlConfig) file).getString(path, null); }

    @Override
    public Long getLong(Object file, String path) { return ((SimpleYamlConfig) file).getLong(path, 0L); }

    @Override
    public Integer getInteger(Object file, String path) { return ((SimpleYamlConfig) file).getInt(path, 0); }

    @Override
    public List<String> getStringList(Object file, String path) { return ((SimpleYamlConfig) file).getStringList(path); }

    @Override
    public boolean getBoolean(Object file, String path, boolean def) { return ((SimpleYamlConfig) file).getBoolean(path, def); }

    @Override
    public String getString(Object file, String path, String def) { return ((SimpleYamlConfig) file).getString(path, def); }

    @Override
    public long getLong(Object file, String path, long def) { return ((SimpleYamlConfig) file).getLong(path, def); }

    @Override
    public int getInteger(Object file, String path, int def) { return ((SimpleYamlConfig) file).getInt(path, def); }

    @Override
    public boolean contains(Object file, String path) { return ((SimpleYamlConfig) file).contains(path); }

    @Override
    public String getFileName(Object file) { return "velocity-yaml"; }

    @Override
    public void callPunishmentEvent(Punishment punishment) {
        plugin.getProxy().getEventManager().fireAndForget(new PunishmentEvent(punishment));
    }

    @Override
    public void callRevokePunishmentEvent(Punishment punishment, boolean massClear) {
        plugin.getProxy().getEventManager().fireAndForget(new RevokePunishmentEvent(punishment, massClear));
    }

    @Override
    public boolean isOnlineMode() { return plugin.getProxy().getConfiguration().isOnlineMode(); }

    @Override
    public void notify(String perm, List<String> notification) {
        plugin.getProxy().getAllPlayers().stream()
                .filter(player -> hasPerms(player, perm))
                .forEach(player -> notification.forEach(message -> sendMessage(player, message)));
    }

    @Override
    public void log(String msg) {
        plugin.getLogger().info(Security.stripFormatting(msg.replace('&', '\u00A7')));
    }

    @Override
    public boolean isUnitTesting() { return false; }
}

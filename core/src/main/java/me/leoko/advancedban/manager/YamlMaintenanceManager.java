package me.leoko.advancedban.manager;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Security;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlMaintenanceManager {
    private static final YamlMaintenanceManager INSTANCE = new YamlMaintenanceManager();
    private static final List<String> MAINTAINED_FILES = Arrays.asList("config.yml", "Messages.yml", "Layouts.yml");

    public static YamlMaintenanceManager get() {
        return INSTANCE;
    }

    public boolean maintain() {
        MethodInterface mi = Universal.get().getMethods();
        if (mi == null || mi.isUnitTesting() || !getBoolean("YamlMaintenance.Enabled", true)) {
            return false;
        }

        boolean changed = false;
        for (String fileName : MAINTAINED_FILES) {
            try {
                changed |= maintainFile(mi, fileName);
            } catch (RuntimeException | IOException ex) {
                Universal.get().debugException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
            }
        }
        return changed;
    }

    private boolean maintainFile(MethodInterface mi, String fileName) throws IOException {
        File file = new File(mi.getDataFolder(), fileName);
        if (!file.exists()) {
            copyDefault(fileName, file);
            return true;
        }

        Map<String, Object> defaults = loadDefault(fileName);
        Map<String, Object> current;
        try {
            current = loadYaml(file);
        } catch (RuntimeException ex) {
            backup(file, "invalid");
            copyDefault(fileName, file);
            Universal.get().logMessage("Console.YamlReplacedInvalid",
                    "&cReplaced unreadable %FILE% with the bundled default. A backup was created.",
                    "FILE", fileName);
            return true;
        }

        Map<String, Object> merged = deepCopy(current);
        int missing = mergeMissing(merged, defaults);
        int removed = 0;
        if (getBoolean("YamlMaintenance.RemoveUnknownEntries", false)) {
            removed = removeUnknown(merged, defaults);
        }

        if (missing == 0 && removed == 0) {
            return false;
        }

        if (getBoolean("YamlMaintenance.BackupBeforeChanges", true)) {
            backup(file, "before-maintenance");
        }
        writeYaml(file, merged);
        Universal.get().logMessage("Console.YamlMaintained",
                "&7Maintained %FILE%: added %ADDED% missing entries, removed %REMOVED% unknown entries.",
                "FILE", fileName,
                "ADDED", String.valueOf(missing),
                "REMOVED", String.valueOf(removed));
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Object loaded = createYaml().load(reader);
            if (loaded == null) {
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map)) {
                throw new IllegalArgumentException("YAML root is not a map: " + file.getName());
            }
            return (Map<String, Object>) loaded;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadDefault(String fileName) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                throw new IOException("Missing bundled YAML resource: " + fileName);
            }
            Object loaded = createYaml().load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>();
        }
    }

    private void copyDefault(String fileName, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                throw new IOException("Missing bundled YAML resource: " + fileName);
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            copy.put(entry.getKey(), value instanceof Map ? deepCopy((Map<String, Object>) value) : value);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private int mergeMissing(Map<String, Object> current, Map<String, Object> defaults) {
        int changes = 0;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            Object currentValue = current.get(entry.getKey());
            Object defaultValue = entry.getValue();
            if (!current.containsKey(entry.getKey())) {
                current.put(entry.getKey(), defaultValue);
                changes++;
            } else if (!(currentValue instanceof Map) && defaultValue instanceof Map) {
                current.put(entry.getKey(), defaultValue);
                changes++;
            } else if (currentValue instanceof Map && defaultValue instanceof Map) {
                changes += mergeMissing((Map<String, Object>) currentValue, (Map<String, Object>) defaultValue);
            }
        }
        return changes;
    }

    @SuppressWarnings("unchecked")
    private int removeUnknown(Map<String, Object> current, Map<String, Object> defaults) {
        int changes = 0;
        for (String key : current.keySet().toArray(new String[0])) {
            Object currentValue = current.get(key);
            Object defaultValue = defaults.get(key);
            if (!defaults.containsKey(key)) {
                current.remove(key);
                changes++;
            } else if (currentValue instanceof Map && defaultValue instanceof Map) {
                changes += removeUnknown((Map<String, Object>) currentValue, (Map<String, Object>) defaultValue);
            }
        }
        return changes;
    }

    private void writeYaml(File file, Map<String, Object> values) throws IOException {
        Files.writeString(file.toPath(), createYaml().dump(values), StandardCharsets.UTF_8);
    }

    private void backup(File file, String reason) throws IOException {
        File backupFolder = new File(file.getParentFile(), "yml-backups");
        if (!backupFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backupFolder.mkdirs();
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        String safeReason = Security.sanitizeForStorage(reason).replaceAll("[^A-Za-z0-9_-]", "-");
        Files.copy(file.toPath(), new File(backupFolder, file.getName() + "." + stamp + "." + safeReason + ".bak").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean getBoolean(String path, boolean def) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            return mi.getConfig() == null ? def : mi.getBoolean(mi.getConfig(), path, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private Yaml createYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        return new Yaml(options);
    }
}

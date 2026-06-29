package me.leoko.advancedban.velocity;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleYamlConfig {
    private final Map<String, Object> root;

    private SimpleYamlConfig(Map<String, Object> root) {
        this.root = root;
    }

    @SuppressWarnings("unchecked")
    static SimpleYamlConfig load(Reader reader) {
        Object loaded = new Yaml().load(reader);
        return new SimpleYamlConfig(loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    static SimpleYamlConfig load(InputStream inputStream) {
        Object loaded = new Yaml().load(inputStream);
        return new SimpleYamlConfig(loaded instanceof Map ? (Map<String, Object>) loaded : new LinkedHashMap<>());
    }

    Object get(String path) {
        String[] parts = path.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
        }
        return current;
    }

    boolean contains(String path) {
        return get(path) != null;
    }

    String getString(String path, String def) {
        Object value = get(path);
        return value == null ? def : String.valueOf(value);
    }

    boolean getBoolean(String path, boolean def) {
        Object value = get(path);
        return value instanceof Boolean ? (Boolean) value : value == null ? def : Boolean.parseBoolean(String.valueOf(value));
    }

    long getLong(String path, long def) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? def : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    int getInt(String path, int def) {
        return (int) getLong(path, def);
    }

    List<String> getStringList(String path) {
        Object value = get(path);
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : (List<?>) value) {
            result.add(String.valueOf(entry));
        }
        return result;
    }

    String[] getKeys(String path) {
        Object value = path == null || path.isEmpty() ? root : get(path);
        if (!(value instanceof Map)) {
            return new String[0];
        }
        return ((Map<?, ?>) value).keySet().stream().map(String::valueOf).toArray(String[]::new);
    }
}

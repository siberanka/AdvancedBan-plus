package me.leoko.advancedban;

import me.leoko.advancedban.manager.YamlMaintenanceManager;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlMaintenanceManagerTest {
    @Test
    @SuppressWarnings("unchecked")
    void deepCopyKeepsNumericYamlKeysSafe() throws Exception {
        Map<Object, Object> warnActions = new LinkedHashMap<>();
        warnActions.put(3, "kick %PLAYER%");

        Map<Object, Object> source = new LinkedHashMap<>();
        source.put("WarnActions", warnActions);

        Method deepCopy = YamlMaintenanceManager.class.getDeclaredMethod("deepCopy", Map.class);
        deepCopy.setAccessible(true);
        Map<Object, Object> copy = (Map<Object, Object>) deepCopy.invoke(YamlMaintenanceManager.get(), source);

        assertEquals("kick %PLAYER%", ((Map<Object, Object>) copy.get("WarnActions")).get(3));
        assertNotSame(warnActions, copy.get("WarnActions"));
    }

    @Test
    void removeUnknownHandlesNumericYamlKeysSafe() throws Exception {
        Map<Object, Object> currentWarnActions = new LinkedHashMap<>();
        currentWarnActions.put(3, "kick %PLAYER%");
        currentWarnActions.put(99, "ban %PLAYER%");

        Map<Object, Object> defaultWarnActions = new LinkedHashMap<>();
        defaultWarnActions.put(3, "kick %PLAYER%");

        Map<Object, Object> current = new LinkedHashMap<>();
        current.put("WarnActions", currentWarnActions);
        Map<Object, Object> defaults = new LinkedHashMap<>();
        defaults.put("WarnActions", defaultWarnActions);

        Method removeUnknown = YamlMaintenanceManager.class.getDeclaredMethod("removeUnknown", Map.class, Map.class);
        removeUnknown.setAccessible(true);
        int removed = (Integer) removeUnknown.invoke(YamlMaintenanceManager.get(), current, defaults);

        assertEquals(1, removed);
        assertEquals(false, currentWarnActions.containsKey(99));
    }

    @Test
    void parserRejectsDuplicateKeysAndUnsafeJavaTags() throws Exception {
        Method createYaml = YamlMaintenanceManager.class.getDeclaredMethod("createYaml");
        createYaml.setAccessible(true);
        Yaml yaml = (Yaml) createYaml.invoke(YamlMaintenanceManager.get());

        assertThrows(YAMLException.class, () -> yaml.load("value: 1\nvalue: 2\n"));
        assertThrows(YAMLException.class,
                () -> yaml.load("payload: !!javax.script.ScriptEngineManager []\n"));
    }
}

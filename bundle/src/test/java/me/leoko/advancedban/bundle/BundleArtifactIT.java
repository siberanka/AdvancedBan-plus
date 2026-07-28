package me.leoko.advancedban.bundle;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleArtifactIT {

    @Test
    void shouldContainOneProductionBundleForEveryPlatform() throws Exception {
        Path bundle = Paths.get("target", System.getProperty("bundle.file"));
        assertTrue(Files.isRegularFile(bundle), "Bundle jar is missing");
        assertTrue(Files.size(bundle) > 1_000_000L, "Bundle jar is unexpectedly small");

        try (JarFile jar = new JarFile(bundle.toFile())) {
            assertEntry(jar, "plugin.yml");
            assertEntry(jar, "bungee.yml");
            assertEntry(jar, "velocity-plugin.json");
            assertEntry(jar, "me/leoko/advancedban/bukkit/BukkitMain.class");
            assertEntry(jar, "me/leoko/advancedban/bungee/BungeeMain.class");
            assertEntry(jar, "me/leoko/advancedban/velocity/VelocityMain.class");

            String pluginYml = readText(jar, "plugin.yml");
            assertTrue(pluginYml.contains("api-version: '1.16'"));
            assertTrue(pluginYml.contains("folia-supported: true"));
            assertTrue(pluginYml.contains("authors: [Leoko, siberanka]"));

            String velocityJson = readText(jar, "velocity-plugin.json");
            assertTrue(velocityJson.contains("\"siberanka\""));
            assertTrue(velocityJson.contains("\"version\": \"2026.07.28.2\""));

            assertOwnAndShadedClassesRunOnJava11(jar);
        }
    }

    private void assertOwnAndShadedClassesRunOnJava11(JarFile jar) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().startsWith("me/leoko/advancedban/")
                    || !entry.getName().endsWith(".class")
                    || entry.getName().startsWith("META-INF/versions/")) {
                continue;
            }
            try (DataInputStream input = new DataInputStream(jar.getInputStream(entry))) {
                assertTrue(input.readInt() == 0xCAFEBABE, "Invalid class file: " + entry.getName());
                input.readUnsignedShort();
                int major = input.readUnsignedShort();
                assertTrue(major <= 55, "Java 11 compatibility broken by " + entry.getName() + " (major " + major + ")");
            }
        }
    }

    private void assertEntry(JarFile jar, String name) {
        assertNotNull(jar.getJarEntry(name), "Missing bundle entry: " + name);
    }

    private String readText(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        assertNotNull(entry, "Missing bundle entry: " + name);
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

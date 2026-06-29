package me.leoko.advancedban;

import me.leoko.advancedban.manager.DiscordWebhookManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordWebhookManagerTest {
    @TempDir
    File dataFolder;

    @Test
    void shouldAllowOnlyDiscordWebhookUrlsByDefault() {
        Universal.get().setup(new TestMethods(dataFolder, Map.of()));

        assertTrue(DiscordWebhookManager.get().isAllowedWebhookUrl("https://discord.com/api/webhooks/123/token"));
        assertTrue(DiscordWebhookManager.get().isAllowedWebhookUrl("https://discordapp.com/api/webhooks/123/token"));
        assertFalse(DiscordWebhookManager.get().isAllowedWebhookUrl("http://discord.com/api/webhooks/123/token"));
        assertFalse(DiscordWebhookManager.get().isAllowedWebhookUrl("https://example.com/api/webhooks/123/token"));
        assertFalse(DiscordWebhookManager.get().isAllowedWebhookUrl("https://discord.com/not-webhooks/123/token"));

        Universal.get().shutdown();
    }

    @Test
    void shouldParseSafeEmbedFieldsFromLanguageEntries() {
        Universal.get().setup(new TestMethods(dataFolder, Map.of(
                "DiscordWebhook.MaxFields", 2,
                "DiscordWebhook.PunishmentCreated.Fields", Arrays.asList(
                        "Player|%NAME%|true",
                        "Reason|%REASON%|false",
                        "Ignored|value|true")
        )));

        var fields = DiscordWebhookManager.get().parseFields("PunishmentCreated",
                "NAME", "PlayerOne",
                "REASON", "Testing");

        assertEquals(2, fields.size());
        assertEquals("Player", fields.get(0).get("name"));
        assertEquals("PlayerOne", fields.get(0).get("value"));
        assertEquals(true, fields.get(0).get("inline"));
        assertEquals("Testing", fields.get(1).get("value"));

        Universal.get().shutdown();
    }
}

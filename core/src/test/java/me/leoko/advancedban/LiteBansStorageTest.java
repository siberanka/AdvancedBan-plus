package me.leoko.advancedban;

import litebans.api.Database;
import litebans.api.Entry;
import litebans.api.Events;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.PunishmentManager;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LiteBansStorageTest {
    @TempDir
    public File dataFolder;

    @Test
    public void shouldStoreAndDeactivatePunishmentsInLiteBansFormat() throws Exception {
        Universal.get().setup(new TestMethods(dataFolder, Map.of("Database.database-format", "litebans")));
        try {
            assertTrue(DatabaseManager.get().isLiteBansFormat(), "LiteBans storage format should be enabled");

            Punishment punishment = new Punishment("liteuser", "liteuser", "LiteBans format test",
                    "JUnit5", PunishmentType.MUTE, TimeManager.getTime(), -1, "layout", -1);
            punishment.create();

            assertTrue(punishment.getId() >= 0, "Inserted punishment should receive a LiteBans table id");
            assertNotNull(PunishmentManager.get().getMute("liteuser"), "Mute should be queryable through AdvancedBan");

            try (ResultSet rs = DatabaseManager.get().executeRawResultStatement("SELECT COUNT(*) AS count FROM litebans_mutes")) {
                assertNotNull(rs);
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("count"), "LiteBans mute row should exist");
            }

            punishment.delete("JUnit5", false, true);
            assertFalse(PunishmentManager.get().isMuted("liteuser"), "Deactivated mute should not count as active");

            try (ResultSet rs = DatabaseManager.get().executeRawResultStatement("SELECT COUNT(*) AS count FROM litebans_mutes")) {
                assertNotNull(rs);
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("count"), "LiteBans revoke must not delete rows");
            }
        } finally {
            Universal.get().shutdown();
        }
    }

    @Test
    public void shouldResolveTypedIdsWhenLiteBansTablesShareNumericIds() {
        Universal.get().setup(new TestMethods(dataFolder, Map.of("Database.database-format", "litebans")));
        try {
            Punishment ban = new Punishment("liteban", "liteban", "Ban row",
                    "JUnit5", PunishmentType.BAN, TimeManager.getTime(), -1, null, -1);
            Punishment warn = new Punishment("litewarn", "litewarn", "Warn row",
                    "JUnit5", PunishmentType.WARNING, TimeManager.getTime(), -1, null, -1);

            ban.create();
            warn.create();

            assertEquals(ban.getId(), warn.getId(), "Independent LiteBans tables can share numeric ids");
            assertEquals(PunishmentType.WARNING, PunishmentManager.get().getWarn(warn.getId()).getType());
        } finally {
            Universal.get().shutdown();
        }
    }

    @Test
    public void shouldRollbackLiteBansInsertWhenMetadataWriteFails() throws Exception {
        Universal.get().setup(new TestMethods(dataFolder, Map.of("Database.database-format", "litebans")));
        try {
            DatabaseManager.get().executeRawStatement("DROP TABLE litebans_advancedban_meta");
            Punishment punishment = new Punishment("rollback", "rollback", "Rollback test",
                    "JUnit5", PunishmentType.MUTE, TimeManager.getTime(), -1, "layout", -1);

            assertFalse(punishment.create(), "A partial LiteBans transaction must report failure");
            try (ResultSet rs = DatabaseManager.get().executeRawResultStatement(
                    "SELECT COUNT(*) AS count FROM litebans_mutes")) {
                assertNotNull(rs);
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("count"), "Main LiteBans row must be rolled back");
            }
        } finally {
            Universal.get().shutdown();
        }
    }

    @Test
    public void shouldExposeApiStateAndIsolateFailingEventListeners() {
        Universal.get().setup(new TestMethods(dataFolder, Map.of("litebans-api-support", true)));
        Events.Listener failing = new Events.Listener() {
            @Override
            public void entryAdded(Entry entry) {
                throw new IllegalStateException("intentional listener failure");
            }
        };
        AtomicInteger added = new AtomicInteger();
        AtomicInteger removed = new AtomicInteger();
        Events.Listener healthy = new Events.Listener() {
            @Override
            public void entryAdded(Entry entry) {
                added.incrementAndGet();
            }

            @Override
            public void entryRemoved(Entry entry) {
                removed.incrementAndGet();
            }
        };

        try {
            Events.get().register(failing);
            Events.get().register(healthy);
            UUID uuid = UUID.randomUUID();
            Punishment ban = new Punishment("liteapi", uuid.toString().replace("-", ""), "API event test",
                    "JUnit5", PunishmentType.BAN, TimeManager.getTime(), -1, null, -1);

            ban.create();

            assertTrue(Database.get().isPlayerBanned(uuid, null));
            assertEquals(1, added.get(), "A failing listener must not block later listeners");

            ban.delete("JUnit5", false, true);
            assertEquals(1, removed.get());
        } finally {
            try {
                Events.get().unregister(failing);
                Events.get().unregister(healthy);
            } catch (RuntimeException ignored) {
                // Universal shutdown below resets all compatibility API singletons.
            }
            Universal.get().shutdown();
        }
    }
}

package me.leoko.advancedban;

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
}

package me.leoko.advancedban.manager;

import litebans.api.Database;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.PunishmentType;
import me.leoko.advancedban.utils.SQLQuery;
import me.leoko.advancedban.utils.Security;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class LiteBansStorage {
    private static final String PREFIX = "litebans_";
    private static final String META_TABLE = PREFIX + "advancedban_meta";

    private final DatabaseManager databaseManager;

    LiteBansStorage(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    void setup() {
        createPunishmentTable("bans", true, false);
        createPunishmentTable("mutes", true, false);
        createPunishmentTable("warnings", true, true);
        createPunishmentTable("kicks", false, false);
        createNotesTable();
        createHistoryTable();
        createServersTable();
        createAllowTable();
        createMetaTable();
    }

    ResultSet query(SQLQuery sql, Object... parameters) {
        switch (sql) {
            case SELECT_EXACT_PUNISHMENT:
            case SELECT_EXACT_PUNISHMENT_HISTORY:
                return exact(parameters);
            case SELECT_USER_PUNISHMENTS:
                return byTarget(String.valueOf(parameters[0]), true);
            case SELECT_USER_PUNISHMENTS_HISTORY:
                return byTarget(String.valueOf(parameters[0]), false);
            case SELECT_USER_PUNISHMENTS_WITH_IP:
                return byTargetPair(String.valueOf(parameters[0]), String.valueOf(parameters[1]), true);
            case SELECT_USER_PUNISHMENTS_HISTORY_WITH_IP:
                return byTargetPair(String.valueOf(parameters[0]), String.valueOf(parameters[1]), false);
            case SELECT_USER_PUNISHMENTS_HISTORY_BY_CALCULATION:
                return byCalculation(String.valueOf(parameters[0]), String.valueOf(parameters[1]));
            case SELECT_PUNISHMENT_BY_ID:
                return byId(parameters[0], true, parameters.length > 1 ? parseType(String.valueOf(parameters[1])) : null);
            case SELECT_PUNISHMENT_HISTORY_BY_ID:
                return byId(parameters[0], false, parameters.length > 1 ? parseType(String.valueOf(parameters[1])) : null);
            case SELECT_ALL_PUNISHMENTS:
                return all(true, -1);
            case SELECT_ALL_PUNISHMENTS_HISTORY:
                return all(false, -1);
            case SELECT_ALL_PUNISHMENTS_LIMIT:
                return all(true, toLimit(parameters[0]));
            case SELECT_ALL_PUNISHMENTS_HISTORY_LIMIT:
                return all(false, toLimit(parameters[0]));
            default:
                return null;
        }
    }

    boolean handles(SQLQuery sql) {
        switch (sql) {
            case CREATE_TABLE_PUNISHMENT:
            case CREATE_TABLE_PUNISHMENT_HISTORY:
            case INSERT_PUNISHMENT:
            case INSERT_PUNISHMENT_HISTORY:
            case DELETE_PUNISHMENT:
            case UPDATE_PUNISHMENT_REASON:
            case DELETE_OLD_PUNISHMENTS:
                return true;
            default:
                return queryOnly(sql);
        }
    }

    void execute(SQLQuery sql, Object... parameters) {
        if (sql == SQLQuery.DELETE_OLD_PUNISHMENTS) {
            deactivateExpired(toLong(parameters[0], TimeManager.getTime()));
        }
    }

    int insert(Punishment punishment, boolean silent) {
        String table = tableFor(punishment.getType());
        if (table == null) {
            Universal.get().logMessage("Console.LiteBansStorageUnsupportedType",
                    "&cLiteBans database format cannot store punishment type %TYPE%.",
                    "TYPE", punishment.getType().name());
            return -1;
        }

        String target = safeStorage(punishment.getUuid(), 64);
        String uuid = punishment.getType().isIpOrientated() ? null : toLiteBansUuid(target);
        String ip = punishment.getType().isIpOrientated() ? safeStorage(target, 45) : null;
        String operator = safeStorage(punishment.getOperator(), 128);
        String reason = safeStorage(punishment.getReason(), 2048);
        boolean ipBan = punishment.getType().isIpOrientated();

        if (punishment.getType() == PunishmentType.NOTE) {
            databaseManager.executeRawStatement(insertSql(table,
                    "uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, template, server_scope, server_origin, silent, ipban, ipban_wildcard, active"),
                    uuid, ip, reason, operator, operator, punishment.getStart(), punishment.getEnd(), 255,
                    Database.ANY_SERVER_SCOPE, "AdvancedBan", silent, ipBan, false, true);
        } else if (punishment.getType() == PunishmentType.KICK) {
            databaseManager.executeRawStatement(insertSql(table,
                    "uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, template, server_scope, server_origin, silent, ipban, ipban_wildcard, active"),
                    uuid, ip, reason, operator, operator, punishment.getStart(), punishment.getEnd(), 255,
                    Database.ANY_SERVER_SCOPE, "AdvancedBan", silent, ipBan, false, true);
        } else {
            String columns = punishment.getType().getBasic() == PunishmentType.WARNING
                    ? "uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, template, server_scope, server_origin, silent, ipban, ipban_wildcard, active, warned"
                    : "uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, template, server_scope, server_origin, silent, ipban, ipban_wildcard, active";
            if (punishment.getType().getBasic() == PunishmentType.WARNING) {
                databaseManager.executeRawStatement(insertSql(table, columns),
                        uuid, ip, reason, operator, operator, punishment.getStart(), punishment.getEnd(), 255,
                        Database.ANY_SERVER_SCOPE, "AdvancedBan", silent, ipBan, false, true, false);
            } else {
                databaseManager.executeRawStatement(insertSql(table, columns),
                        uuid, ip, reason, operator, operator, punishment.getStart(), punishment.getEnd(), 255,
                        Database.ANY_SERVER_SCOPE, "AdvancedBan", silent, ipBan, false, true);
            }
        }

        int id = resolveInsertedId(table, punishment, uuid, ip);
        if (id != -1) {
            upsertMeta(table, id, punishment);
            rememberIdentity(punishment.getName(), uuid, ip);
        }
        return id;
    }

    void revoke(Punishment punishment, String who) {
        String table = tableFor(punishment.getType());
        if (table == null || punishment.getId() < 0) {
            return;
        }
        databaseManager.executeRawStatement("UPDATE " + q(table)
                        + " SET active = ?, removed_by_name = ?, removed_by_uuid = ?, removed_by_reason = ? WHERE id = ?",
                false, safeStorage(who == null ? "AdvancedBan" : who, 128), safeStorage(who == null ? "AdvancedBan" : who, 36),
                "Removed by AdvancedBan", punishment.getId());
    }

    void updateReason(Punishment punishment, String reason) {
        String table = tableFor(punishment.getType());
        if (table == null || punishment.getId() < 0) {
            return;
        }
        databaseManager.executeRawStatement("UPDATE " + q(table) + " SET reason = ? WHERE id = ?",
                safeStorage(reason, 2048), punishment.getId());
    }

    private ResultSet exact(Object... parameters) {
        if (parameters.length < 3) {
            return null;
        }
        String target = String.valueOf(parameters[0]);
        long start = toLong(parameters[1], -1);
        PunishmentType type = parseType(String.valueOf(parameters[2]));
        String table = tableFor(type);
        if (table == null) {
            return null;
        }
        String select = selectFor(table, type, false);
        return databaseManager.executeRawResultStatement(select + " WHERE (p.uuid = ? OR p.uuid = ? OR p.ip = ?) AND p.time = ? ORDER BY p.id DESC",
                target, toLiteBansUuid(target), target, start);
    }

    private ResultSet byTarget(String target, boolean current) {
        String normalized = toLiteBansUuid(target);
        String where = current ? activeWhere("p") + " AND (p.uuid = ? OR p.uuid = ? OR p.ip = ?)" : "(p.uuid = ? OR p.uuid = ? OR p.ip = ?)";
        return databaseManager.executeRawResultStatement(union(where, current, -1), repeatedParams(current, normalized, target, target));
    }

    private ResultSet byTargetPair(String uuid, String ip, boolean current) {
        String normalized = toLiteBansUuid(uuid);
        String where = current ? activeWhere("p") + " AND (p.uuid = ? OR p.uuid = ? OR p.ip = ?)" : "(p.uuid = ? OR p.uuid = ? OR p.ip = ?)";
        return databaseManager.executeRawResultStatement(union(where, current, -1), repeatedParams(current, normalized, uuid, ip));
    }

    private ResultSet byCalculation(String uuid, String calculation) {
        String where = "(p.uuid = ? OR p.uuid = ?) AND m.calculation = ?";
        String normalized = toLiteBansUuid(uuid);
        return databaseManager.executeRawResultStatement(union(where, false, -1), repeatedParams(false, normalized, uuid, calculation));
    }

    private ResultSet byId(Object id, boolean current) {
        return byId(id, current, null);
    }

    private ResultSet byId(Object id, boolean current, PunishmentType type) {
        String where = current ? activeWhere("p") + " AND p.id = ?" : "p.id = ?";
        if (type != null) {
            String table = tableFor(type);
            if (table == null) {
                return null;
            }
            return databaseManager.executeRawResultStatement(selectFor(table, type, current) + " WHERE " + where, id);
        }
        return databaseManager.executeRawResultStatement(union(where, current, -1), repeatedParams(current, id));
    }

    private ResultSet all(boolean current, int limit) {
        return databaseManager.executeRawResultStatement(union(current ? activeWhere("p") : "1 = 1", current, limit));
    }

    private String union(String where, boolean current, int limit) {
        StringBuilder sql = new StringBuilder();
        appendUnion(sql, selectFor(PREFIX + "bans", PunishmentType.BAN, current), where);
        appendUnion(sql, selectFor(PREFIX + "mutes", PunishmentType.MUTE, current), where);
        appendUnion(sql, selectFor(PREFIX + "warnings", PunishmentType.WARNING, current), where);
        if (!current) {
            appendUnion(sql, selectFor(PREFIX + "kicks", PunishmentType.KICK, false), where);
        }
        appendUnion(sql, selectFor(PREFIX + "notes", PunishmentType.NOTE, current), where);
        sql.append(" ORDER BY ").append(alias("start")).append(" DESC");
        if (limit > 0) {
            sql.append(" LIMIT ").append(limit);
        }
        return sql.toString();
    }

    private void appendUnion(StringBuilder sql, String select, String where) {
        if (sql.length() > 0) {
            sql.append(" UNION ALL ");
        }
        sql.append(select).append(" WHERE ").append(where);
    }

    private Object[] repeatedParams(boolean current, Object... group) {
        int tableCount = current ? 4 : 5;
        List<Object> parameters = new ArrayList<>(tableCount * group.length);
        for (int i = 0; i < tableCount; i++) {
            for (Object value : group) {
                parameters.add(value);
            }
        }
        return parameters.toArray();
    }

    private String selectFor(String table, PunishmentType type, boolean current) {
        String calculatedType = typeSql(type);
        String name = "COALESCE(m.name, h.name, p.uuid, p.ip, '#')";
        return "SELECT p.id AS " + alias("id") + ", " + name + " AS " + alias("name")
                + ", COALESCE(p.uuid, p.ip, '#') AS " + alias("uuid") + ", "
                + "p.reason AS " + alias("reason") + ", p.banned_by_name AS " + alias("operator")
                + ", " + calculatedType + " AS " + alias("punishmentType") + ", "
                + "p.time AS " + alias("start") + ", p.until AS " + alias("end")
                + ", m.calculation AS " + alias("calculation") + " FROM " + q(table) + " p "
                + "LEFT JOIN " + q(META_TABLE) + " m ON m.table_name = '" + table + "' AND m.punishment_id = p.id "
                + "LEFT JOIN " + q(PREFIX + "history") + " h ON h.uuid = p.uuid ";
    }

    private String typeSql(PunishmentType type) {
        switch (type.getBasic()) {
            case BAN:
                return "CASE WHEN p.ipban = ? AND p.until > 0 THEN 'TEMP_IP_BAN' WHEN p.ipban = ? THEN 'IP_BAN' WHEN p.until > 0 THEN 'TEMP_BAN' ELSE 'BAN' END"
                        .replace("?", databaseManager.isUseMySQL() ? "1" : "TRUE");
            case MUTE:
                return "CASE WHEN p.until > 0 THEN 'TEMP_MUTE' ELSE 'MUTE' END";
            case WARNING:
                return "CASE WHEN p.until > 0 THEN 'TEMP_WARNING' ELSE 'WARNING' END";
            case KICK:
                return "'KICK'";
            case NOTE:
                return "'NOTE'";
            default:
                return "'" + type.name() + "'";
        }
    }

    private String activeWhere(String alias) {
        return alias + ".active = " + (databaseManager.isUseMySQL() ? "1" : "TRUE")
                + " AND (" + alias + ".until < 1 OR " + alias + ".until > " + TimeManager.getTime() + ")";
    }

    private void deactivateExpired(long now) {
        for (String table : new String[]{PREFIX + "bans", PREFIX + "mutes", PREFIX + "warnings", PREFIX + "notes"}) {
            databaseManager.executeRawStatement("UPDATE " + q(table) + " SET active = ? WHERE active = ? AND until > 0 AND until <= ?",
                    false, true, now);
        }
    }

    private int resolveInsertedId(String table, Punishment punishment, String uuid, String ip) {
        try (ResultSet rs = databaseManager.executeRawResultStatement(
                "SELECT id FROM " + q(table) + " WHERE (uuid = ? OR ip = ?) AND time = ? ORDER BY id DESC LIMIT 1",
                uuid, ip, punishment.getStart())) {
            if (rs != null && rs.next()) {
                long id = rs.getLong("id");
                return id > Integer.MAX_VALUE ? -1 : (int) id;
            }
        } catch (Exception ex) {
            Universal.get().debugException(ex);
        }
        return -1;
    }

    private void upsertMeta(String table, int id, Punishment punishment) {
        databaseManager.executeRawStatement("DELETE FROM " + q(META_TABLE) + " WHERE table_name = ? AND punishment_id = ?",
                table, id);
        databaseManager.executeRawStatement(insertSql(META_TABLE, "table_name, punishment_id, name, calculation, type"),
                table, id, safeStorage(punishment.getName(), 16), safeStorage(punishment.getCalculation(), 50), punishment.getType().name());
    }

    private void rememberIdentity(String name, String uuid, String ip) {
        if (name == null || name.isEmpty()) {
            return;
        }
        String safeName = safeStorage(name, 16);
        String safeUuid = safeStorage(uuid == null ? "#" : uuid, 36);
        String safeIp = safeStorage(ip == null ? "#" : ip, 45);
        databaseManager.executeRawStatement(insertSql(PREFIX + "history", "name, uuid, ip"), safeName, safeUuid, safeIp);
    }

    private void createPunishmentTable(String suffix, boolean removable, boolean warned) {
        String table = PREFIX + suffix;
        if (databaseManager.isUseMySQL()) {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + q(table)
                    + " (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + " uuid VARCHAR(36) NULL, ip VARCHAR(45) NULL, reason VARCHAR(2048) NULL,"
                    + " banned_by_uuid VARCHAR(36) NULL, banned_by_name VARCHAR(128) NULL,"
                    + (removable ? " removed_by_uuid VARCHAR(36) NULL, removed_by_name VARCHAR(128) NULL, removed_by_reason VARCHAR(2048) NULL, removed_by_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," : "")
                    + " time BIGINT NOT NULL, until BIGINT NOT NULL, template TINYINT UNSIGNED NOT NULL DEFAULT 255,"
                    + " server_scope VARCHAR(32) NULL, server_origin VARCHAR(32) NULL,"
                    + " silent BIT NOT NULL, ipban BIT NOT NULL, ipban_wildcard BIT NOT NULL, active BIT NOT NULL,"
                    + (warned ? " warned BIT NOT NULL," : "")
                    + " PRIMARY KEY (id))");
        } else {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + table
                    + " (id INTEGER IDENTITY PRIMARY KEY,"
                    + " uuid VARCHAR(36), ip VARCHAR(45), reason VARCHAR(2048),"
                    + " banned_by_uuid VARCHAR(36), banned_by_name VARCHAR(128),"
                    + (removable ? " removed_by_uuid VARCHAR(36), removed_by_name VARCHAR(128), removed_by_reason VARCHAR(2048), removed_by_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP," : "")
                    + " time BIGINT, until BIGINT, template INTEGER DEFAULT 255,"
                    + " server_scope VARCHAR(32), server_origin VARCHAR(32),"
                    + " silent BOOLEAN, ipban BOOLEAN, ipban_wildcard BOOLEAN, active BOOLEAN,"
                    + (warned ? " warned BOOLEAN," : "")
                    + " dummy INTEGER)");
        }
    }

    private void createNotesTable() {
        createPunishmentTable("notes", true, false);
    }

    private void createHistoryTable() {
        if (databaseManager.isUseMySQL()) {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + q(PREFIX + "history")
                    + " (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " name VARCHAR(16) NOT NULL, uuid VARCHAR(36) NOT NULL, ip VARCHAR(45) NOT NULL, PRIMARY KEY (id))");
        } else {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + PREFIX + "history"
                    + " (id INTEGER IDENTITY PRIMARY KEY, date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + " name VARCHAR(16), uuid VARCHAR(36), ip VARCHAR(45))");
        }
    }

    private void createServersTable() {
        if (databaseManager.isUseMySQL()) {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + q(PREFIX + "servers")
                    + " (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, name VARCHAR(32) NOT NULL,"
                    + " uuid VARCHAR(32) NOT NULL UNIQUE, date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id))");
        } else {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + PREFIX + "servers"
                    + " (id INTEGER IDENTITY PRIMARY KEY, name VARCHAR(32), uuid VARCHAR(32), date TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    private void createAllowTable() {
        if (databaseManager.isUseMySQL()) {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + q(PREFIX + "allow")
                    + " (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, uuid BINARY(16) NOT NULL, type TINYINT UNSIGNED NOT NULL, PRIMARY KEY (id))");
        } else {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + PREFIX + "allow"
                    + " (id INTEGER IDENTITY PRIMARY KEY, uuid VARBINARY(16), type INTEGER)");
        }
    }

    private void createMetaTable() {
        if (databaseManager.isUseMySQL()) {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + q(META_TABLE)
                    + " (id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, table_name VARCHAR(64) NOT NULL,"
                    + " punishment_id BIGINT NOT NULL, name VARCHAR(16) NULL, calculation VARCHAR(50) NULL,"
                    + " type VARCHAR(16) NULL, PRIMARY KEY (id))");
        } else {
            databaseManager.executeRawStatement("CREATE TABLE IF NOT EXISTS " + META_TABLE
                    + " (id INTEGER IDENTITY PRIMARY KEY, table_name VARCHAR(64), punishment_id BIGINT,"
                    + " name VARCHAR(16), calculation VARCHAR(50), type VARCHAR(16))");
        }
    }

    private String insertSql(String table, String columns) {
        int count = columns.split(",").length;
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                values.append(", ");
            }
            values.append("?");
        }
        return "INSERT INTO " + q(table) + " (" + columns + ") VALUES (" + values + ")";
    }

    private String tableFor(PunishmentType type) {
        if (type == null) {
            return null;
        }
        switch (type.getBasic()) {
            case BAN:
                return PREFIX + "bans";
            case MUTE:
                return PREFIX + "mutes";
            case WARNING:
                return PREFIX + "warnings";
            case KICK:
                return PREFIX + "kicks";
            case NOTE:
                return PREFIX + "notes";
            default:
                return null;
        }
    }

    private PunishmentType parseType(String value) {
        try {
            return PunishmentType.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toLiteBansUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            String normalized = value.replace("-", "").toLowerCase(Locale.ROOT);
            if (normalized.matches("[0-9a-f]{32}")) {
                return UUID.fromString(normalized.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5")).toString();
            }
        } catch (RuntimeException ignored) {
        }
        return safeStorage(value, 36);
    }

    private String safeStorage(String value, int max) {
        return Security.limit(Security.sanitizeForStorage(value), max);
    }

    private int toLimit(Object value) {
        int limit = (int) Math.max(0, Math.min(1000, toLong(value, 150)));
        return limit == 0 ? 150 : limit;
    }

    private long toLong(Object value, long def) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    private boolean queryOnly(SQLQuery sql) {
        switch (sql) {
            case SELECT_EXACT_PUNISHMENT:
            case SELECT_EXACT_PUNISHMENT_HISTORY:
            case SELECT_USER_PUNISHMENTS:
            case SELECT_USER_PUNISHMENTS_HISTORY:
            case SELECT_USER_PUNISHMENTS_WITH_IP:
            case SELECT_USER_PUNISHMENTS_HISTORY_WITH_IP:
            case SELECT_USER_PUNISHMENTS_HISTORY_BY_CALCULATION:
            case SELECT_PUNISHMENT_BY_ID:
            case SELECT_PUNISHMENT_HISTORY_BY_ID:
            case SELECT_ALL_PUNISHMENTS:
            case SELECT_ALL_PUNISHMENTS_HISTORY:
            case SELECT_ALL_PUNISHMENTS_LIMIT:
            case SELECT_ALL_PUNISHMENTS_HISTORY_LIMIT:
                return true;
            default:
                return false;
        }
    }

    private String q(String identifier) {
        return databaseManager.isUseMySQL() ? "`" + identifier + "`" : identifier;
    }

    private String alias(String identifier) {
        return databaseManager.isUseMySQL() ? "`" + identifier + "`" : "\"" + identifier + "\"";
    }
}

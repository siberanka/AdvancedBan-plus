package me.leoko.advancedban.manager;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;
import me.leoko.advancedban.utils.Punishment;
import me.leoko.advancedban.utils.Security;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class DiscordWebhookManager {
    private static final DiscordWebhookManager INSTANCE = new DiscordWebhookManager();

    private final Map<String, Long> attemptThrottle = new ConcurrentHashMap<>();
    private final AtomicLong nextPostAt = new AtomicLong();

    public static DiscordWebhookManager get() {
        return INSTANCE;
    }

    public void punishmentCreated(Punishment punishment, boolean silent) {
        if (silent && getBoolean("DiscordWebhook.RespectSilent", true)) {
            return;
        }
        sendPunishmentEvent("PunishmentCreated", punishment, "SILENT", String.valueOf(silent));
    }

    public void punishmentRevoked(Punishment punishment, String revokedBy, boolean massClear) {
        sendPunishmentEvent("PunishmentRevoked", punishment,
                "REVOKED_BY", safeValue(revokedBy, "CONSOLE"),
                "MASS_CLEAR", String.valueOf(massClear));
    }

    public void bannedJoinAttempt(String name, String ip, Punishment punishment) {
        String key = "ban:" + safeValue(punishment == null ? null : punishment.getUuid(), name);
        if (!allowAttempt(key, getLong("StaffNotifications.BannedJoin.ThrottleMillis", 30000L))) {
            return;
        }
        String[] parameters = attemptParameters(name, ip, punishment, null, "join");
        notifyStaff("StaffNotifications.BannedJoin", "ab.notify.bannedjoin", parameters);
        send("BannedJoinAttempt", parameters);
    }

    public void mutedChatAttempt(String name, String ip, Punishment punishment, String message, boolean command) {
        String key = (command ? "cmd:" : "chat:") + safeValue(punishment == null ? null : punishment.getUuid(), name);
        if (!allowAttempt(key, getLong("StaffNotifications.MutedAttempt.ThrottleMillis", 10000L))) {
            return;
        }
        String event = command ? "MutedCommandAttempt" : "MutedChatAttempt";
        String section = command ? "StaffNotifications.MutedCommand" : "StaffNotifications.MutedChat";
        String permission = command ? "ab.notify.mutedcommand" : "ab.notify.mutedchat";
        String evidence = Security.limit(Security.sanitizeForStorage(message), getInt("StaffNotifications.MaxEvidenceLength", 160));
        String[] parameters = attemptParameters(name, ip, punishment, evidence, command ? "command" : "chat");
        notifyStaff(section, permission, parameters);
        send(event, parameters);
    }

    public void clear() {
        attemptThrottle.clear();
        nextPostAt.set(0L);
    }

    public boolean isAllowedWebhookUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(scheme) || host == null || path == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (getBoolean("DiscordWebhook.AllowDiscordDomainsOnly", true)
                    && !normalizedHost.equals("discord.com")
                    && !normalizedHost.equals("discordapp.com")
                    && !normalizedHost.equals("canary.discord.com")
                    && !normalizedHost.equals("ptb.discord.com")) {
                return false;
            }
            return path.startsWith("/api/webhooks/") && path.length() <= 256 && value.length() <= 512;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public List<Map<String, Object>> parseFields(String event, String... parameters) {
        MethodInterface mi = Universal.get().getMethods();
        List<Map<String, Object>> fields = new ArrayList<>();
        int maxFields = Math.max(0, Math.min(25, getInt("DiscordWebhook.MaxFields", 10)));
        int maxField = Math.max(32, Math.min(1024, getInt("DiscordWebhook.MaxFieldLength", 1024)));
        for (String line : mi.getStringList(mi.getMessages(), "DiscordWebhook." + event + ".Fields")) {
            if (fields.size() >= maxFields || line == null) {
                break;
            }
            String[] parts = line.split("\\|", 3);
            if (parts.length < 2) {
                continue;
            }
            String name = cleanDiscordText(replace(parts[0], parameters), 256);
            String value = cleanDiscordText(replace(parts[1], parameters), maxField);
            if (name.isEmpty() || value.isEmpty()) {
                continue;
            }
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", name);
            field.put("value", value);
            field.put("inline", parts.length >= 3 && Boolean.parseBoolean(parts[2].trim()));
            fields.add(field);
        }
        return fields;
    }

    private void sendPunishmentEvent(String event, Punishment punishment, String... extraParameters) {
        if (punishment == null) {
            return;
        }
        List<String> values = new ArrayList<>();
        Collections.addAll(values,
                "PREFIX", prefix(),
                "SERVER", serverName(),
                "NAME", punishment.getName(),
                "UUID", punishment.getUuid(),
                "OPERATOR", punishment.getOperator(),
                "TYPE", punishment.getType().getName(),
                "BASIC_TYPE", punishment.getType().getBasic().getName(),
                "REASON", punishment.getReason(),
                "DURATION", punishment.getDuration(false),
                "DATE", punishment.getDate(punishment.getStart()),
                "END_DATE", punishment.getEnd() <= 0 ? MessageManager.getMessageOrDefault("General.Permanent", "permanent") : punishment.getDate(punishment.getEnd()),
                "ID", String.valueOf(punishment.getId()),
                "HEXID", punishment.getHexId());
        Collections.addAll(values, extraParameters);
        send(event, values.toArray(new String[0]));
    }

    private String[] attemptParameters(String name, String ip, Punishment punishment, String evidence, String action) {
        return new String[]{
                "PREFIX", prefix(),
                "SERVER", serverName(),
                "NAME", safeValue(name, "unknown"),
                "UUID", safeValue(punishment == null ? null : punishment.getUuid(), "unknown"),
                "IP", Security.limit(Security.sanitizeForStorage(ip), 64),
                "TYPE", punishment == null ? "Unknown" : punishment.getType().getName(),
                "REASON", punishment == null ? "" : punishment.getReason(),
                "DURATION", punishment == null ? "" : punishment.getDuration(false),
                "DATE", punishment == null ? "" : punishment.getDate(punishment.getStart()),
                "ID", punishment == null ? "-1" : String.valueOf(punishment.getId()),
                "HEXID", punishment == null ? "" : punishment.getHexId(),
                "EVIDENCE", safeValue(evidence, ""),
                "ACTION", action
        };
    }

    private void notifyStaff(String section, String fallbackPermission, String... parameters) {
        if (!getBoolean("StaffNotifications.Enabled", true) || !getBoolean(section + ".Enabled", true)) {
            return;
        }
        MethodInterface mi = Universal.get().getMethods();
        String permission = mi.getString(mi.getConfig(), section + ".Permission", fallbackPermission);
        List<String> lines = MessageManager.getLayoutOrDefault(mi.getMessages(), section + ".Message",
                Collections.emptyList(), parameters);
        if (!lines.isEmpty()) {
            mi.notify(permission, lines);
        }
    }

    private void send(String event, String... parameters) {
        if (!getBoolean("DiscordWebhook.Enabled", false) || !getBoolean("DiscordWebhook.Events." + event, true)) {
            return;
        }
        MethodInterface mi = Universal.get().getMethods();
        String webhookUrl = mi.getString(mi.getConfig(), "DiscordWebhook.WebhookUrl", "");
        if (!isAllowedWebhookUrl(webhookUrl)) {
            Universal.get().logMessage("Console.DiscordWebhookInvalidUrl", "&cDiscord webhook URL is empty or not allowed; webhook skipped.");
            return;
        }
        long now = System.currentTimeMillis();
        long minimumDelay = Math.max(0L, getLong("DiscordWebhook.RateLimit.MinimumMillisBetweenPosts", 1000L));
        long next = nextPostAt.get();
        if (now < next || !nextPostAt.compareAndSet(next, now + minimumDelay)) {
            Universal.get().debug("Discord webhook skipped by local rate limit for event " + event);
            return;
        }

        String payload = buildPayload(event, parameters);
        if (payload == null) {
            return;
        }
        mi.runAsync(() -> post(webhookUrl, payload));
    }

    private String buildPayload(String event, String... parameters) {
        MethodInterface mi = Universal.get().getMethods();
        int maxPayload = Math.max(512, getInt("DiscordWebhook.MaxPayloadChars", 6000));
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "username", cleanDiscordText(mi.getString(mi.getConfig(), "DiscordWebhook.Username", "AdvancedBan Plus"), 80));
        putIfPresent(payload, "avatar_url", safeUrl(mi.getString(mi.getConfig(), "DiscordWebhook.AvatarUrl", "")));
        String content = message("DiscordWebhook." + event + ".Content", "", parameters);
        putIfPresent(payload, "content", cleanDiscordText(content, getInt("DiscordWebhook.MaxContentLength", 1900)));
        if (!getBoolean("DiscordWebhook.AllowMentions", false)) {
            Map<String, Object> allowedMentions = new LinkedHashMap<>();
            allowedMentions.put("parse", Collections.emptyList());
            payload.put("allowed_mentions", allowedMentions);
        }

        if (getBoolean("DiscordWebhook.Embed.Enabled", true)) {
            Map<String, Object> embed = new LinkedHashMap<>();
            putIfPresent(embed, "title", cleanDiscordText(message("DiscordWebhook." + event + ".Title", event, parameters), 256));
            putIfPresent(embed, "description", cleanDiscordText(message("DiscordWebhook." + event + ".Description", "", parameters),
                    getInt("DiscordWebhook.MaxDescriptionLength", 2048)));
            embed.put("color", colorFor(event));
            putObjectWithUrl(embed, "author",
                    cleanDiscordText(replace(mi.getString(mi.getConfig(), "DiscordWebhook.Embed.AuthorName", ""), parameters), 256),
                    safeUrl(replace(mi.getString(mi.getConfig(), "DiscordWebhook.Embed.AuthorIconUrl", ""), parameters)));
            putObjectWithUrl(embed, "thumbnail", null,
                    safeUrl(replace(mi.getString(mi.getConfig(), "DiscordWebhook.Embed.ThumbnailUrl", ""), parameters)));
            putObjectWithUrl(embed, "footer",
                    cleanDiscordText(replace(mi.getString(mi.getConfig(), "DiscordWebhook.Embed.FooterText", ""), parameters), 2048),
                    safeUrl(replace(mi.getString(mi.getConfig(), "DiscordWebhook.Embed.FooterIconUrl", ""), parameters)));
            List<Map<String, Object>> fields = parseFields(event, parameters);
            if (!fields.isEmpty()) {
                embed.put("fields", fields);
            }
            payload.put("embeds", Collections.singletonList(embed));
        }
        if (!payload.containsKey("content") && !payload.containsKey("embeds")) {
            return null;
        }
        String json = Universal.get().getGson().toJson(payload);
        if (json.length() > maxPayload) {
            Universal.get().logMessage("Console.DiscordWebhookPayloadTooLarge", "&cDiscord webhook payload for %EVENT% is too large; webhook skipped.", "EVENT", event);
            return null;
        }
        return json;
    }

    private void post(String webhookUrl, String payload) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(getInt("DiscordWebhook.ConnectTimeoutMillis", Security.getInt("Security.HttpConnectTimeoutMillis", 3000)));
            connection.setReadTimeout(getInt("DiscordWebhook.ReadTimeoutMillis", Security.getInt("Security.HttpReadTimeoutMillis", 3000)));
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("User-Agent", "AdvancedBanPlus/" + Security.limit(Universal.get().getMethods().getVersion(), 64));
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                Universal.get().logMessage("Console.DiscordWebhookFailed", "&cDiscord webhook failed with HTTP %CODE%.", "CODE", String.valueOf(code));
            }
        } catch (IOException | RuntimeException ex) {
            Universal.get().logMessage("Console.DiscordWebhookFailed", "&cDiscord webhook failed with HTTP %CODE%.", "CODE", "exception");
            Universal.get().debugException(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex));
        }
    }

    private boolean allowAttempt(String key, long throttleMillis) {
        if (throttleMillis <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = attemptThrottle.put(key, now);
        if (last == null || now - last >= throttleMillis) {
            if (attemptThrottle.size() > Math.max(128, getInt("StaffNotifications.MaxThrottleEntries", 1024))) {
                attemptThrottle.entrySet().removeIf(entry -> now - entry.getValue() > throttleMillis * 2L);
            }
            return true;
        }
        attemptThrottle.put(key, last);
        return false;
    }

    private String message(String path, String fallback, String... parameters) {
        return MessageManager.getMessageOrDefault(path, fallback, parameters);
    }

    private int colorFor(String event) {
        MethodInterface mi = Universal.get().getMethods();
        String path = "DiscordWebhook.Colors." + event;
        if (mi.contains(mi.getConfig(), path)) {
            return clampColor(mi.getInteger(mi.getConfig(), path, 15158332));
        }
        return clampColor(mi.getInteger(mi.getConfig(), "DiscordWebhook.Embed.Color", 15158332));
    }

    private int clampColor(int color) {
        return Math.max(0, Math.min(0xFFFFFF, color));
    }

    private String replace(String message, String... parameters) {
        if (message == null) {
            return "";
        }
        for (int i = 0; i < parameters.length - 1; i += 2) {
            message = message.replace("%" + parameters[i] + "%", safeValue(parameters[i + 1], ""));
        }
        return message;
    }

    private String cleanDiscordText(String value, int max) {
        if (value == null) {
            return "";
        }
        return Security.limit(Security.sanitizeForStorage(Security.stripFormatting(value)).replace("@everyone", "@\u200beveryone").replace("@here", "@\u200bhere"), max).trim();
    }

    private String safeUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || value.length() > 512) {
                return "";
            }
            return value.trim();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(key, value);
        }
    }

    private void putObjectWithUrl(Map<String, Object> embed, String key, String text, String iconUrl) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (key.equals("thumbnail")) {
            if (iconUrl != null && !iconUrl.isEmpty()) {
                values.put("url", iconUrl);
                embed.put(key, values);
            }
            return;
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        values.put(key.equals("footer") ? "text" : "name", text);
        if (iconUrl != null && !iconUrl.isEmpty()) {
            values.put("icon_url", iconUrl);
        }
        if (!values.isEmpty()) {
            embed.put(key, values);
        }
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private String prefix() {
        MethodInterface mi = Universal.get().getMethods();
        return mi.getBoolean(mi.getConfig(), "Disable Prefix", false) ? "" : MessageManager.getMessageOrDefault("General.Prefix", "[AdvancedBan]");
    }

    private String serverName() {
        try {
            MethodInterface mi = Universal.get().getMethods();
            return Security.limit(Security.sanitizeForStorage(mi.getString(mi.getConfig(), "DiscordWebhook.ServerName", "Global")), 64);
        } catch (RuntimeException ex) {
            return "Global";
        }
    }

    private boolean getBoolean(String path, boolean def) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            return mi.getBoolean(mi.getConfig(), path, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private int getInt(String path, int def) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            return mi.getInteger(mi.getConfig(), path, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }

    private long getLong(String path, long def) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            return mi.getLong(mi.getConfig(), path, def);
        } catch (RuntimeException ex) {
            return def;
        }
    }
}

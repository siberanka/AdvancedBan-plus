package me.leoko.advancedban.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Security {
    public static final int DEFAULT_MAX_REASON_LENGTH = 255;
    public static final int DEFAULT_MAX_TOTAL_COMMAND_LENGTH = 2048;
    public static final int DEFAULT_MAX_ARGUMENT_LENGTH = 256;
    public static final int DEFAULT_MAX_HTTP_RESPONSE_CHARS = 65_536;

    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern UUID = Pattern.compile("(?i)^[0-9a-f]{32}$|^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    private Security() {
    }

    public static boolean isValidPlayerName(String name) {
        return name != null && PLAYER_NAME.matcher(name).matches();
    }

    public static boolean isSafePlayerName(String name) {
        return isValidPlayerName(name);
    }

    public static boolean isValidUuid(String uuid) {
        return uuid != null && UUID.matcher(uuid).matches();
    }

    public static String normalizeUuid(String uuid) {
        return uuid == null ? null : uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    public static boolean isValidIpV4(String ip) {
        if (ip == null) {
            return false;
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255 || (part.length() > 1 && part.startsWith("0"))) {
                    return false;
                }
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidIpAddress(String ip) {
        if (isValidIpV4(ip)) {
            return true;
        }
        if (ip == null || ip.length() < 2 || ip.length() > 45 || ip.indexOf(':') < 0
                || !ip.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        try {
            return InetAddress.getByName(ip) instanceof Inet6Address;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static String sanitizeReason(String reason) {
        return limit(sanitizeForStorage(reason), getInt("Security.MaxReasonLength", DEFAULT_MAX_REASON_LENGTH));
    }

    public static boolean isReasonSafe(String reason) {
        return reason != null
                && reason.length() <= getInt("Security.MaxReasonLength", DEFAULT_MAX_REASON_LENGTH)
                && sanitizeForStorage(reason).length() == reason.trim().length();
    }

    public static String sanitizeForStorage(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 0x20 && c != 0x7F) || c == '\t') {
                builder.append(c);
            } else {
                builder.append(' ');
            }
        }
        return neutralizeJndi(builder.toString()).trim();
    }

    public static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return limit(neutralizeJndi(value.replace('\r', ' ').replace('\n', ' ')), 4096);
    }

    public static String sanitizeCommandPlaceholder(String value) {
        if (value == null) {
            return "";
        }
        return limit(sanitizeForStorage(value).replace('%', ' '), 160);
    }

    public static String stripFormatting(String value) {
        if (value == null) {
            return null;
        }
        return LEGACY_COLOR.matcher(value).replaceAll("");
    }

    public static String limit(String value, int maxLength) {
        if (value == null || maxLength < 0 || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public static long ticksToMillis(long ticks) {
        if (ticks <= 0L) {
            return 0L;
        }
        return ticks > Long.MAX_VALUE / 50L ? Long.MAX_VALUE : ticks * 50L;
    }

    public static Integer parseBoundedInt(String value, int min, int max) {
        if (value == null || value.isEmpty() || value.length() > 10) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= min && parsed <= max ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String fetchJsonValue(String url, String key) throws IOException {
        return parseJsonValue(fetchText(url, configuredHttpLimit()), key);
    }

    public static String fetchText(String value, int maxChars) throws IOException {
        if (value == null || value.length() > 2048) {
            throw new IOException("Invalid HTTP URL.");
        }
        URL url = new URL(value);
        if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Only HTTP(S) URLs are supported.");
        }
        if (url.getHost() == null || url.getHost().isEmpty() || url.getUserInfo() != null) {
            throw new IOException("Invalid HTTP URL authority.");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(clamp(getInt("Security.HttpConnectTimeoutMillis", 3000), 250, 60_000));
            connection.setReadTimeout(clamp(getInt("Security.HttpReadTimeoutMillis", 3000), 250, 60_000));
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json, text/plain;q=0.9");
            connection.setRequestProperty("User-Agent", "AdvancedBanPlus");
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Unexpected HTTP status " + status + ".");
            }
            try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                return readBounded(reader, clamp(maxChars, 1, 1_048_576));
            }
        } finally {
            connection.disconnect();
        }
    }

    public static String parseJsonValue(Reader reader, String key) throws IOException {
        if (reader == null) {
            return null;
        }
        return parseJsonValue(readBounded(reader, configuredHttpLimit()), key);
    }

    public static String parseJsonValue(String json, String key) {
        if (json == null || key == null || json.length() > configuredHttpLimit()
                || key.isEmpty() || key.length() > 256) {
            return null;
        }
        try {
            JsonElement current = JsonParser.parseString(json);
            String[] path = key.split("\\|", -1);
            if (path.length > 16) {
                return null;
            }
            for (String part : path) {
                if (part.isEmpty() || part.length() > 64 || !current.isJsonObject()) {
                    return null;
                }
                JsonObject object = current.getAsJsonObject();
                current = object.get(part);
                if (current == null || current.isJsonNull()) {
                    return null;
                }
            }
            String result = current.isJsonPrimitive() ? current.getAsString() : current.toString();
            return limit(sanitizeForStorage(result), 8192);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String readBounded(Reader reader, int maxChars) throws IOException {
        StringBuilder result = new StringBuilder(Math.min(maxChars, 8192));
        char[] buffer = new char[2048];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            if (result.length() + read > maxChars) {
                throw new IOException("HTTP response exceeded the configured size limit.");
            }
            result.append(buffer, 0, read);
        }
        return result.toString();
    }

    private static int configuredHttpLimit() {
        return clamp(getInt("Security.MaxHttpResponseChars", DEFAULT_MAX_HTTP_RESPONSE_CHARS),
                1024, 1_048_576);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int getInt(String path, int def) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            if (mi == null || mi.getConfig() == null) {
                return def;
            }
            return mi.getInteger(mi.getConfig(), path, def);
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    public static boolean getBoolean(String path, boolean def) {
        try {
            MethodInterface mi = Universal.get().getMethods();
            if (mi == null || mi.getConfig() == null) {
                return def;
            }
            return mi.getBoolean(mi.getConfig(), path, def);
        } catch (RuntimeException ignored) {
            return def;
        }
    }

    private static String neutralizeJndi(String value) {
        return value.replace("${", "$ {");
    }
}

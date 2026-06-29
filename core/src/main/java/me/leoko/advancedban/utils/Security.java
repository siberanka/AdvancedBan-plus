package me.leoko.advancedban.utils;

import me.leoko.advancedban.MethodInterface;
import me.leoko.advancedban.Universal;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Security {
    public static final int DEFAULT_MAX_REASON_LENGTH = 255;
    public static final int DEFAULT_MAX_TOTAL_COMMAND_LENGTH = 2048;
    public static final int DEFAULT_MAX_ARGUMENT_LENGTH = 256;

    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern UUID = Pattern.compile("(?i)^[0-9a-f]{32}$|^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    private Security() {
    }

    public static boolean isValidPlayerName(String name) {
        return name != null && PLAYER_NAME.matcher(name).matches();
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

    public static String sanitizeReason(String reason) {
        return limit(sanitizeForStorage(reason), getInt("Security.MaxReasonLength", DEFAULT_MAX_REASON_LENGTH));
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

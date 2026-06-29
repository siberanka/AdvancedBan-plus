package me.leoko.advancedban.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class GitHubUpdateChecker {
    public static final String API_URL = "https://api.github.com/repos/siberanka/AdvancedBan-plus/releases/latest";
    public static final String RELEASE_URL = "https://github.com/siberanka/AdvancedBan-plus/releases/latest";

    private static final int MAX_RESPONSE_BYTES = 16 * 1024;
    private static final Pattern DATE_BUILD_VERSION = Pattern.compile("^v?\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d+$");

    public Result check(String currentVersion) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
        connection.setRequestProperty("User-Agent", "AdvancedBanPlus/" + Security.limit(currentVersion, 64));
        connection.setConnectTimeout(Security.getInt("Security.HttpConnectTimeoutMillis", 3000));
        connection.setReadTimeout(Security.getInt("Security.HttpReadTimeoutMillis", 3000));
        connection.setUseCaches(false);

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("GitHub latest release check failed with HTTP " + responseCode);
        }

        String response = readLimited(connection.getInputStream());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        String latestVersion = normalizeVersion(readString(json, "tag_name"));
        if (!isSupportedVersion(latestVersion)) {
            throw new IOException("GitHub release tag has an unsupported version format");
        }

        String releaseUrl = readString(json, "html_url");
        if (releaseUrl == null || !releaseUrl.startsWith("https://github.com/siberanka/AdvancedBan-plus/releases/")) {
            releaseUrl = RELEASE_URL;
        }

        return new Result(currentVersion, latestVersion, releaseUrl, isNewer(latestVersion, currentVersion));
    }

    public static boolean isNewer(String latestVersion, String currentVersion) {
        int[] latest = parseVersion(latestVersion);
        int[] current = parseVersion(currentVersion);
        if (latest == null || current == null) {
            return false;
        }
        for (int i = 0; i < latest.length; i++) {
            if (latest[i] != current[i]) {
                return latest[i] > current[i];
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        String normalized = normalizeVersion(version);
        if (!isSupportedVersion(normalized)) {
            return null;
        }
        String[] parts = normalized.split("\\.");
        int[] values = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return values;
    }

    private static boolean isSupportedVersion(String version) {
        return version != null && DATE_BUILD_VERSION.matcher(version).matches();
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }
        String normalized = Security.limit(version.trim(), 64);
        return normalized.startsWith("v") || normalized.startsWith("V") ? normalized.substring(1) : normalized;
    }

    private static String readString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? null : Security.limit(element.getAsString(), 256);
    }

    private static String readLimited(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("GitHub latest release response is too large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    public static final class Result {
        private final String currentVersion;
        private final String latestVersion;
        private final String releaseUrl;
        private final boolean updateAvailable;

        private Result(String currentVersion, String latestVersion, String releaseUrl, boolean updateAvailable) {
            this.currentVersion = currentVersion;
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
            this.updateAvailable = updateAvailable;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getReleaseUrl() {
            return releaseUrl;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }
    }
}

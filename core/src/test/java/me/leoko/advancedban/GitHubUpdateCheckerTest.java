package me.leoko.advancedban;

import me.leoko.advancedban.utils.GitHubUpdateChecker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubUpdateCheckerTest {

    @Test
    void shouldCompareDateBuildVersions() {
        assertTrue(GitHubUpdateChecker.isNewer("2026.06.29.3", "2026.06.29.2"));
        assertTrue(GitHubUpdateChecker.isNewer("v2026.06.30.1", "2026.06.29.9"));
        assertFalse(GitHubUpdateChecker.isNewer("2026.06.29.2", "2026.06.29.2"));
        assertFalse(GitHubUpdateChecker.isNewer("2026.06.29.1", "2026.06.29.2"));
    }

    @Test
    void shouldIgnoreUnsupportedVersionFormats() {
        assertFalse(GitHubUpdateChecker.isNewer("latest", "2026.06.29.2"));
        assertFalse(GitHubUpdateChecker.isNewer("2026.06.29.3", "dev-build"));
    }
}

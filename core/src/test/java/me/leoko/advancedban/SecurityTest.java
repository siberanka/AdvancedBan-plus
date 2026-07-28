package me.leoko.advancedban;

import me.leoko.advancedban.utils.Security;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityTest {

    @Test
    void shouldConvertTicksWithoutOverflow() {
        assertEquals(0L, Security.ticksToMillis(-1L));
        assertEquals(0L, Security.ticksToMillis(0L));
        assertEquals(1_000L, Security.ticksToMillis(20L));
        assertEquals(Long.MAX_VALUE, Security.ticksToMillis(Long.MAX_VALUE));
    }

    @Test
    void shouldRejectOverflowingIntegersWithoutThrowing() {
        assertEquals(42, Security.parseBoundedInt("42", 1, 100));
        assertNull(Security.parseBoundedInt("999999999999999999999999", 1, Integer.MAX_VALUE));
        assertNull(Security.parseBoundedInt("-1", 0, Integer.MAX_VALUE));
    }

    @Test
    void shouldParseNestedJsonWithAConfiguredBound() {
        assertEquals("abc123", Security.parseJsonValue(
                "{\"profile\":{\"id\":\"abc123\"}}", "profile|id"));
        assertNull(Security.parseJsonValue("{\"profile\":[]}", "profile|id"));
        assertNull(Security.parseJsonValue("not-json", "id"));
    }

    @Test
    void shouldAcceptOnlyLiteralIpAddresses() {
        assertTrue(Security.isValidIpAddress("127.0.0.1"));
        assertTrue(Security.isValidIpAddress("2001:db8::1"));
        assertFalse(Security.isValidIpAddress("999.0.0.1"));
        assertFalse(Security.isValidIpAddress("localhost"));
        assertFalse(Security.isValidIpAddress("fe80::1%eth0"));
    }
}

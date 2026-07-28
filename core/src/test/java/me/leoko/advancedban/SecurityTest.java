package me.leoko.advancedban;

import me.leoko.advancedban.utils.Security;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityTest {

    @Test
    void shouldConvertTicksWithoutOverflow() {
        assertEquals(0L, Security.ticksToMillis(-1L));
        assertEquals(0L, Security.ticksToMillis(0L));
        assertEquals(1_000L, Security.ticksToMillis(20L));
        assertEquals(Long.MAX_VALUE, Security.ticksToMillis(Long.MAX_VALUE));
    }
}

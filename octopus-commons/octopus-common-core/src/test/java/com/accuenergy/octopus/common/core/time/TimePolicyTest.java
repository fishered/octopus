package com.accuenergy.octopus.common.core.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TimePolicyTest {
    @Test
    void acceptsIanaRegionAndUtc() {
        assertEquals(ZoneId.of("Asia/Shanghai"), TimePolicy.requireIanaZone("Asia/Shanghai"));
        assertEquals(ZoneId.of("UTC"), TimePolicy.requireIanaZone("UTC"));
    }

    @Test
    void rejectsFixedOffsetAsBusinessZone() {
        assertThrows(IllegalArgumentException.class, () -> TimePolicy.requireIanaZone("+08:00"));
    }
}


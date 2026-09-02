package com.accuenergy.octopus.mgmt.infrastructure.kafka;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.accuenergy.octopus.mgmt.application.operations.DltRecordReader.RecordHeader;
import com.accuenergy.octopus.mgmt.application.operations.DltReplayService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaDltReplayAdapterTest {
    @Test
    void replayHeadersRetainBusinessContextButReplaceFailureAndReplayMetadata() {
        UUID jobId = UUID.randomUUID();
        var headers = KafkaDltReplayAdapter.replayHeaders(List.of(
                new RecordHeader("trace-id", bytes("trace-7")),
                new RecordHeader("nullable-business-header", null),
                new RecordHeader("kafka_dlt-exception-message", bytes("old failure")),
                new RecordHeader(DltReplayService.REPLAY_ATTEMPT_HEADER, bytes("1"))),
                jobId, "raw.dlt:2:44", 2);

        assertArrayEquals(bytes("trace-7"), headers.lastHeader("trace-id").value());
        assertNull(headers.lastHeader("nullable-business-header").value());
        assertNull(headers.lastHeader("kafka_dlt-exception-message"));
        assertEquals("2", text(headers.lastHeader(DltReplayService.REPLAY_ATTEMPT_HEADER).value()));
        assertEquals("raw.dlt:2:44", text(headers.lastHeader(DltReplayService.REPLAY_ROOT_HEADER).value()));
        assertEquals(jobId.toString(), text(headers.lastHeader(DltReplayService.REPLAY_JOB_HEADER).value()));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String text(byte[] value) { return new String(value, StandardCharsets.UTF_8); }
}

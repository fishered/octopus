package com.accuenergy.octopus.mgmt.infrastructure.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring.DesiredStateModelMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JacksonDeviceDesiredStateValidatorTest {
    @Test
    void acceptsOnlyWritablePropertiesWithMatchingTypes() {
        var validator = new JacksonDeviceDesiredStateValidator(new ModelMapper(), new ObjectMapper());

        assertEquals("{\"relay\":true,\"limit\":10}",
                validator.validate(UUID.randomUUID(), "{\"relay\":true,\"limit\":10}").canonicalStateJson());
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(UUID.randomUUID(), "{\"voltage\":230}"));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(UUID.randomUUID(), "{\"relay\":1}"));
    }

    private static final class ModelMapper implements DesiredStateModelMapper {
        @Override public Long findPublishedModelVersion(UUID deviceId) { return 8L; }
        @Override public List<PropertyRow> findWritableProperties(UUID deviceId) {
            return List.of(new PropertyRow("relay", "BOOLEAN"), new PropertyRow("limit", "INTEGER"));
        }
    }
}

package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import com.accuenergy.octopus.api.control.DeviceConnectorBindingChanged;
import com.accuenergy.octopus.mgmt.application.asset.DeviceConnectorBindingRepository;
import com.accuenergy.octopus.mgmt.domain.asset.DeviceConnectorBinding;
import com.accuenergy.octopus.mgmt.infrastructure.outbox.OutboxMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public final class MybatisDeviceConnectorBindingRepository implements DeviceConnectorBindingRepository {
    private final DeviceConnectorBindingMapper mapper; private final OutboxMapper outbox; private final ObjectMapper json; private final String topic;
    public MybatisDeviceConnectorBindingRepository(DeviceConnectorBindingMapper mapper, OutboxMapper outbox, ObjectMapper json,
            @Value("$"+"{octopus.kafka.device-connector-binding-topic:octopus.local.device.connector-binding.v1}") String topic) {
        this.mapper=mapper; this.outbox=outbox; this.json=json; this.topic=topic;
    }
    @Override @Transactional(transactionManager="tenantTransactionManager", readOnly=true)
    public Optional<DeviceConnectorBinding> find(UUID deviceId) {
        return Optional.ofNullable(mapper.find(deviceId)).map(row -> DeviceConnectorBinding.restore(row.tenantId(), row.deviceId(),
                row.pluginId(), row.codecId(), row.configRef(), DeviceConnectorBinding.Status.valueOf(row.status()),
                row.version(), row.createdAt(), row.updatedAt()));
    }
    @Override @Transactional(transactionManager="tenantTransactionManager")
    public void insert(DeviceConnectorBinding binding) { if (mapper.insert(toEntity(binding)) != 1) throw new IllegalStateException("Unable to insert connector binding"); append(binding); }
    @Override @Transactional(transactionManager="tenantTransactionManager")
    public void update(DeviceConnectorBinding binding, long expectedVersion) { if (mapper.update(toEntity(binding), expectedVersion) != 1) throw new IllegalStateException("Connector binding update was concurrent or missing"); append(binding); }
    private void append(DeviceConnectorBinding binding) {
        DeviceConnectorBindingChanged event = new DeviceConnectorBindingChanged(1, UUID.randomUUID(), binding.tenantId(), binding.deviceId(),
                binding.pluginId(), binding.codecId(), binding.status().name(), binding.version(), binding.updatedAt());
        try {
            if (outbox.insertOutbox(event.eventId(), event.tenantId(), topic, event.orderingKey(),
                    DeviceConnectorBindingChanged.class.getSimpleName(), json.writeValueAsString(event), event.occurredAt()) != 1)
                throw new IllegalStateException("Unable to append connector binding event");
        } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize connector binding event", e); }
    }
    private static DeviceConnectorBindingEntity toEntity(DeviceConnectorBinding b) {
        DeviceConnectorBindingEntity e=new DeviceConnectorBindingEntity(); e.setTenantId(b.tenantId()); e.setDeviceId(b.deviceId()); e.setPluginId(b.pluginId());
        e.setCodecId(b.codecId()); e.setConfigRef(b.configRef()); e.setStatus(b.status().name()); e.setVersion(b.version());
        e.setCreatedAt(b.createdAt()); e.setUpdatedAt(b.updatedAt()); return e;
    }
}

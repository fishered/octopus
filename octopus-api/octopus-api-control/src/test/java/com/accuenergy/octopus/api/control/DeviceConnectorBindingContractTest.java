package com.accuenergy.octopus.api.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeviceConnectorBindingContractTest {
    @Test
    void validatesIdentityAndOrderingKey() {
        UUID tenant=UUID.randomUUID(), device=UUID.randomUUID();
        DeviceConnectorBindingChanged event=new DeviceConnectorBindingChanged(1,UUID.randomUUID(),tenant,device,"mqtt-json","json","ACTIVE",3,Instant.now());
        assertEquals(tenant+":"+device,event.orderingKey());
        assertThrows(IllegalArgumentException.class, () -> new DeviceConnectorBindingChanged(1,UUID.randomUUID(),tenant,device,"MQTT","json","ACTIVE",1,Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new DeviceConnectorBindingChanged(1,UUID.randomUUID(),tenant,device,"mqtt-json","json","ACTIVE",0,Instant.now()));
    }
}

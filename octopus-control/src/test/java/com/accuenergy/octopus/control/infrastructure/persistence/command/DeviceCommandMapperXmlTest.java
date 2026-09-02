package com.accuenergy.octopus.control.infrastructure.persistence.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DeviceCommandMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresCommandAndOutboxStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/command/DeviceCommandMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DeviceCommandMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "insertCommand"));
        assertTrue(configuration.hasStatement(namespace + "updateCommand"));
        assertTrue(configuration.hasStatement(namespace + "insertStatusOutbox"));
        assertTrue(configuration.hasStatement(namespace + "tryAcquireRelayLock"));
        assertTrue(configuration.hasStatement(namespace + "lockPending"));
    }
}

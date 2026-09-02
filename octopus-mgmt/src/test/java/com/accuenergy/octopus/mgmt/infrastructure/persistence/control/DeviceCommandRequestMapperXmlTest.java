package com.accuenergy.octopus.mgmt.infrastructure.persistence.control;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DeviceCommandRequestMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresProjectionAndInboxStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/control/DeviceCommandRequestMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DeviceCommandRequestMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findByIdempotency"));
        assertTrue(configuration.hasStatement(namespace + "insertCommand"));
        assertTrue(configuration.hasStatement(namespace + "insertOutbox"));
        assertTrue(configuration.hasStatement(namespace + "insertStatusInbox"));
        assertTrue(configuration.hasStatement(namespace + "updateCommand"));
    }
}

package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DeviceDesiredShadowMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresVersionedWorkflowStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/monitoring/DeviceDesiredShadowMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = DeviceDesiredShadowMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "lockCurrent"));
        assertTrue(configuration.hasStatement(namespace + "insertRequest"));
        assertTrue(configuration.hasStatement(namespace + "updateCurrent"));
        assertTrue(configuration.hasStatement(namespace + "findByCommandForUpdate"));
        assertTrue(configuration.hasStatement(namespace + "findCurrentRequestForUpdate"));
        assertTrue(configuration.hasStatement(namespace + "updateRequest"));
    }
}

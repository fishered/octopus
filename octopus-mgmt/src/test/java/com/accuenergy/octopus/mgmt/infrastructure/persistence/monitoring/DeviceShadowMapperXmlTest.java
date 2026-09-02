package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DeviceShadowMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresProjectionStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/monitoring/DeviceShadowMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DeviceShadowMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "find"));
        assertTrue(configuration.hasStatement(namespace + "findForUpdate"));
        assertTrue(configuration.hasStatement(namespace + "insertProjection"));
        assertTrue(configuration.hasStatement(namespace + "updateProjection"));
        assertTrue(configuration.hasStatement(namespace + "insertInbox"));
    }
}

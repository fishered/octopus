package com.accuenergy.octopus.mgmt.infrastructure.persistence.monitoring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DesiredStateModelMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresWritableModelQueries() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/monitoring/DesiredStateModelMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String namespace = DesiredStateModelMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findPublishedModelVersion"));
        assertTrue(configuration.hasStatement(namespace + "findWritableProperties"));
    }
}

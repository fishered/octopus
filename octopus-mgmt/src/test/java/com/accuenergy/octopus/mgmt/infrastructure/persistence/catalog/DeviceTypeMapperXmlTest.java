package com.accuenergy.octopus.mgmt.infrastructure.persistence.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DeviceTypeMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresCatalogStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/catalog/DeviceTypeMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DeviceTypeMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "countCode"));
        assertTrue(configuration.hasStatement(namespace + "insertType"));
        assertTrue(configuration.hasStatement(namespace + "findType"));
    }
}

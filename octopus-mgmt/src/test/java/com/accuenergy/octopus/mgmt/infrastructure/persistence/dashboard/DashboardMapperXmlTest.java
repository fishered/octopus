package com.accuenergy.octopus.mgmt.infrastructure.persistence.dashboard;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DashboardMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresAggregateStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/dashboard/DashboardMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DashboardMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findDashboard"));
        assertTrue(configuration.hasStatement(namespace + "findDashboardsByOrganizationPath"));
        assertTrue(configuration.hasStatement(namespace + "findWidgets"));
        assertTrue(configuration.hasStatement(namespace + "updateDashboard"));
        assertTrue(configuration.hasStatement(namespace + "updateDashboardVersion"));
        assertTrue(configuration.hasStatement(namespace + "insertWidget"));
        assertTrue(configuration.hasStatement(namespace + "updateWidget"));
        assertTrue(configuration.hasStatement(namespace + "deleteWidget"));
    }
}

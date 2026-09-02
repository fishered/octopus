package com.accuenergy.octopus.mgmt.infrastructure.persistence.alarm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AlarmMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresLifecycleStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/alarm/AlarmMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = AlarmMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "insertEvaluation"));
        assertTrue(configuration.hasStatement(namespace + "findOpenIncidentForUpdate"));
        assertTrue(configuration.hasStatement(namespace + "updateIncident"));
    }
}

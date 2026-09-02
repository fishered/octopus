package com.accuenergy.octopus.mgmt.infrastructure.persistence.operations;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DltReplayMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresReservationAndOutcomeStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/operations/DltReplayMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DltReplayMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "insert"));
        assertTrue(configuration.hasStatement(namespace + "insertRequestedAudit"));
        assertTrue(configuration.hasStatement(namespace + "markSucceeded"));
        assertTrue(configuration.hasStatement(namespace + "markFailed"));
        assertTrue(configuration.hasStatement(namespace + "insertOutcomeAudit"));
        assertTrue(configuration.hasStatement(namespace + "find"));
    }
}

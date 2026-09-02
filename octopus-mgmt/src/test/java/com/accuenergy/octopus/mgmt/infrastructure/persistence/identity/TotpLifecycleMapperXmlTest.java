package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class TotpLifecycleMapperXmlTest {
    @Test
    void parsesLifecycleStatementsAndNeverPersistsRawRecoveryCodes() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/identity/TotpLifecycleMapper.xml";
        String xml;
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = TotpLifecycleMapper.class.getName();
        assertTrue(configuration.hasStatement(namespace + ".insertPending"));
        assertTrue(configuration.hasStatement(namespace + ".activate"));
        assertTrue(configuration.hasStatement(namespace + ".insertRecoveryCode"));
        assertTrue(configuration.hasStatement(namespace + ".consumeRecoveryCode"));
        assertTrue(xml.contains("code_hash"));
        assertTrue(!xml.contains("raw_code"));
    }
}

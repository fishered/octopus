package com.accuenergy.octopus.mgmt.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class OutboxMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresOrderedRelayStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/outbox/OutboxMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = OutboxMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "tryAcquireRelayLock"));
        assertTrue(configuration.hasStatement(namespace + "lockPending"));
        assertTrue(configuration.hasStatement(namespace + "markPublished"));
        assertTrue(configuration.hasStatement(namespace + "markFailed"));
    }
}

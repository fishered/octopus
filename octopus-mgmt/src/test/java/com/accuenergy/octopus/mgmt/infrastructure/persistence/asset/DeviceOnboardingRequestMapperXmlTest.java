package com.accuenergy.octopus.mgmt.infrastructure.persistence.asset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class DeviceOnboardingRequestMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresClaimReplayAndCompletionStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/asset/DeviceOnboardingRequestMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = DeviceOnboardingRequestMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findForUpdate"));
        assertTrue(configuration.hasStatement(namespace + "insertClaim"));
        assertTrue(configuration.hasStatement(namespace + "complete"));
    }
}

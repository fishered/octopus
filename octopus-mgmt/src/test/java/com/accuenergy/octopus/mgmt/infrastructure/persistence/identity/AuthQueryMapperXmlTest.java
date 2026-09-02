package com.accuenergy.octopus.mgmt.infrastructure.persistence.identity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AuthQueryMapperXmlTest {
    @Test
    void tenantAuthoritiesAreFilteredByDelegationBoundary() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/identity/AuthQueryMapper.xml";
        String xml;
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        assertTrue(configuration.hasStatement(AuthQueryMapper.class.getName() + ".findTenantPermissions"));
        assertTrue(configuration.hasStatement(AuthQueryMapper.class.getName() + ".findOrganizationAdminPaths"));
        assertTrue(configuration.hasStatement(AuthQueryMapper.class.getName() + ".findOperatorPermissionScopes"));
        assertTrue(configuration.hasStatement(AuthQueryMapper.class.getName() + ".advanceSessionGeneration"));
        assertTrue(xml.contains("WHERE p.tenant_assignable"));
        assertTrue(xml.contains("permission.code = grant.resource_type || ':' || lower(grant.action)"));
    }
}

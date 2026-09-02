package com.accuenergy.octopus.mgmt.infrastructure.persistence.iam;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class IamAdministrationMapperXmlTest {
    @Test
    void mapperXmlParsesAndDeclaresDelegationBoundaryStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mapper/iam/IamAdministrationMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        String namespace = IamAdministrationMapper.class.getName() + ".";
        assertTrue(configuration.hasStatement(namespace + "findTenantAssignablePermissions"));
        assertTrue(configuration.hasStatement(namespace + "findAccountSecurity"));
        assertTrue(configuration.hasStatement(namespace + "updateAccountStatus"));
        assertTrue(configuration.hasStatement(namespace + "insertAccountStatusAudit"));
        assertTrue(configuration.hasStatement(namespace + "listTenantAssignablePermissions"));
        assertTrue(configuration.hasStatement(namespace + "listActiveMenus"));
        assertTrue(configuration.hasStatement(namespace + "findRolePermissions"));
        assertTrue(configuration.hasStatement(namespace + "findActiveRoleMemberships"));
        assertTrue(configuration.hasStatement(namespace + "findMembershipLifecycle"));
        assertTrue(configuration.hasStatement(namespace + "listRoles"));
        assertTrue(configuration.hasStatement(namespace + "listMemberships"));
        assertTrue(configuration.hasStatement(namespace + "insertRolePermission"));
        assertTrue(configuration.hasStatement(namespace + "deleteAssignment"));
        assertTrue(configuration.hasStatement(namespace + "suspendMembership"));
        assertTrue(configuration.hasStatement(namespace + "activateMembership"));
        assertTrue(configuration.hasStatement(namespace + "insertMembershipRoleAudit"));
        assertTrue(configuration.hasStatement(namespace + "insertMembershipAudit"));
        assertTrue(configuration.hasStatement(namespace + "deleteRolePermissions"));
        assertTrue(configuration.hasStatement(namespace + "touchRole"));
        assertTrue(configuration.hasStatement(namespace + "retireRole"));
        assertTrue(configuration.hasStatement(namespace + "insertRoleAudit"));
        assertTrue(configuration.hasStatement(namespace + "findDeviceGrantTarget"));
        assertTrue(configuration.hasStatement(namespace + "findActiveResourceGrantByKey"));
        assertTrue(configuration.hasStatement(namespace + "upsertResourceGrant"));
        assertTrue(configuration.hasStatement(namespace + "revokeResourceGrant"));
        assertTrue(configuration.hasStatement(namespace + "insertResourceGrantAudit"));
    }
}

WITH vocabulary(code, resource_type, action, description) AS (
    VALUES
      ('platform:all', 'platform', 'ALL', 'Platform super administrator capability'),
      ('organization:create', 'organization', 'CREATE', 'Create child organizations'),
      ('organization:view', 'organization', 'VIEW', 'View organization details'),
      ('organization:manage', 'organization', 'MANAGE', 'Manage organization settings'),
      ('account:create', 'account', 'CREATE', 'Create global accounts'),
      ('account:view', 'account', 'VIEW', 'View accounts in authorized scope'),
      ('account:manage', 'account', 'MANAGE', 'Manage memberships and account state'),
      ('role:view', 'role', 'VIEW', 'View roles and grants'),
      ('role:manage', 'role', 'MANAGE', 'Create roles and assign permissions'),
      ('permission:view', 'permission', 'VIEW', 'View the permission vocabulary'),
      ('menu:view', 'menu', 'VIEW', 'View authorized navigation'),
      ('menu:manage', 'menu', 'MANAGE', 'Manage navigation metadata'),
      ('device:create', 'device', 'CREATE', 'Register logical devices'),
      ('device:view', 'device', 'VIEW', 'View device and hardware details'),
      ('device:configure', 'device', 'CONFIGURE', 'Configure devices and commissioned hardware'),
      ('device:operate', 'device', 'OPERATE', 'Send commands to devices'),
      ('facility:create', 'facility', 'CREATE', 'Create facilities and GIS areas'),
      ('facility:view', 'facility', 'VIEW', 'View facilities and spatial data'),
      ('facility:configure', 'facility', 'CONFIGURE', 'Configure facilities and geometry'),
      ('catalog:view', 'catalog', 'VIEW', 'View units, parameters, and thing models'),
      ('catalog:configure', 'catalog', 'CONFIGURE', 'Configure parameters and thing models'),
      ('meter:create', 'meter', 'CREATE', 'Create device meters'),
      ('meter:view', 'meter', 'VIEW', 'View meter configuration and data'),
      ('meter:configure', 'meter', 'CONFIGURE', 'Configure meter calculation and rollover'),
      ('alarm:view', 'alarm', 'VIEW', 'View alarms'),
      ('alarm:configure', 'alarm', 'CONFIGURE', 'Configure alarm rules'),
      ('alarm:acknowledge', 'alarm', 'ACKNOWLEDGE', 'Acknowledge alarms'),
      ('analytics:view', 'analytics', 'VIEW', 'View analytics results'),
      ('dashboard:view', 'dashboard', 'VIEW', 'View dashboards'),
      ('dashboard:configure', 'dashboard', 'CONFIGURE', 'Configure dashboards'),
      ('ca:manufacture', 'ca', 'MANUFACTURE', 'Create manufactured device identities'),
      ('ca:claim', 'ca', 'CLAIM', 'Claim device identities into a tenant'),
      ('ca:issue', 'ca', 'ISSUE', 'Issue or renew device certificates'),
      ('ca:revoke', 'ca', 'REVOKE', 'Revoke device identities and certificates')
)
INSERT INTO permission(id, code, resource_type, action, description)
SELECT md5(code)::uuid, code, resource_type, action, description FROM vocabulary
ON CONFLICT (code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

WITH navigation(code, route, permission_code, sort_order) AS (
    VALUES
      ('dashboard', '/dashboard', 'dashboard:view', 10),
      ('organizations', '/organizations', 'organization:view', 20),
      ('accounts', '/accounts', 'account:view', 30),
      ('roles', '/roles', 'role:view', 40),
      ('facilities', '/facilities', 'facility:view', 50),
      ('devices', '/devices', 'device:view', 60),
      ('catalog', '/catalog', 'catalog:view', 70),
      ('meters', '/meters', 'meter:view', 80),
      ('alarms', '/alarms', 'alarm:view', 90),
      ('analytics', '/analytics', 'analytics:view', 100),
      ('device-ca', '/device-ca', 'ca:issue', 110)
)
INSERT INTO menu(id, parent_id, code, route, required_permission_code, sort_order, status)
SELECT md5('menu:' || code)::uuid, NULL, code, route, permission_code, sort_order, 'ACTIVE'
FROM navigation
ON CONFLICT (code) DO UPDATE SET
    route = EXCLUDED.route,
    required_permission_code = EXCLUDED.required_permission_code,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status;

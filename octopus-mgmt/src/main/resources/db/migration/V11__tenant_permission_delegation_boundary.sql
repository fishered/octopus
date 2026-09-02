ALTER TABLE permission
    ADD COLUMN tenant_assignable boolean NOT NULL DEFAULT true;

UPDATE permission
   SET tenant_assignable = false
 WHERE code IN ('platform:all', 'account:create', 'menu:manage', 'ca:manufacture');

COMMENT ON COLUMN permission.tenant_assignable IS
    'False for platform/global capabilities that must never be granted through a tenant role';

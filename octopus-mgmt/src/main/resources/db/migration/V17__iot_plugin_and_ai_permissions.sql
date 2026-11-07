WITH vocabulary(code, resource_type, action, description) AS (
    VALUES
      ('iot:plugin:read', 'iot_plugin', 'VIEW', 'Discover installed IoT plugins and preflight connector compatibility'),
      ('ai:integration:draft', 'ai_integration', 'DRAFT', 'Request a review-only AI device integration draft')
)
INSERT INTO permission(id, code, resource_type, action, description)
SELECT md5(code)::uuid, code, resource_type, action, description FROM vocabulary
ON CONFLICT (code) DO UPDATE SET
    resource_type = EXCLUDED.resource_type,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

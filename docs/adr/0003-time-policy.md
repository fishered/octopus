# ADR-0003: Time and time-zone policy

Status: Accepted

Events and persisted instants use UTC (`java.time.Instant`, ISO-8601 with `Z`, and
PostgreSQL `timestamptz`). Unix epoch nanoseconds are used where InfluxDB precision is
required. `LocalDateTime` is forbidden for event occurrence, auditing, sessions, alarms,
and commands because it cannot identify a global instant.

Tenant, organization, facility, and user preferences store IANA zone IDs such as
`America/Toronto`, never numeric UTC offsets. Calendar schedules retain a local date/time,
zone ID, and explicit daylight-saving overlap/gap policy. Unit tests must cover DST
transitions. API responses remain UTC and may include the effective zone as metadata.


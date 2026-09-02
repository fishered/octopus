# ADR-0002: Tenant and organization boundary

Status: Proposed, pending product confirmation

A tenant is the immutable SaaS billing, encryption, quota, and physical data-isolation
boundary. An organization is a tenant-owned hierarchy used for people, facilities,
devices, and authorization scope. A tenant has one root organization and may have child
organizations.

This avoids making an organization simultaneously represent a customer account and an
operational tree node. If the product requires exactly one organization per tenant, the
schema can enforce a one-to-one mapping without removing tenant identifiers from data.

Platform super administrators may enter an explicitly audited support scope. All other
human and machine principals must have a tenant. No public API accepts a caller-supplied
tenant ID as authority; it is derived from the verified session or device certificate.


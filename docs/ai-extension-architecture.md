# AI extension architecture

Octopus treats AI as an advisory bounded context, not as a privileged route around device,
tenant, or command authorization. Model vendors are plugins behind `octopus-ai-spi`; domain and
control services do not import a hosted-model SDK.

## Provider SPI

An `AiProviderPlugin` declares a stable provider ID, SPI version, supported models, capabilities,
and configuration schema. `AiProviderPluginRegistry` rejects duplicate or incompatible providers
at startup. `AiProviderRouter` validates the requested capability and model before invoking the
provider, and verifies response correlation and provider identity before returning output.

The first capabilities are:

- `DEVICE_INTEGRATION`: draft a connector, codec, thing model, mapping, and validation checklist.
- `THING_MODEL_DRAFT`: propose properties, events, commands, units, and constraints.
- `TELEMETRY_MAPPING`: propose a raw-to-canonical mapping and quarantine rules.
- `OPERATIONS_ASSISTANT`: summarize operational evidence without mutating platform state.

An external provider JAR contributes an `AiProviderPlugin` bean using Spring Boot
auto-configuration. Configure its ID and model with `OCTOPUS_AI_DEFAULT_PROVIDER` and
`OCTOPUS_AI_INTEGRATION_MODEL`. Credentials remain provider-specific Secrets and must never be
placed in a descriptor or model prompt.

`octopus-agent` also includes an opt-in `openai-compatible` provider for hosted services and local
gateways that expose the Chat Completions contract. It is disabled by default. A minimal setup is:

```text
OCTOPUS_AI_DEFAULT_PROVIDER=openai-compatible
OCTOPUS_AI_INTEGRATION_MODEL=gpt-5-mini
OCTOPUS_AI_OPENAI_COMPATIBLE_ENABLED=true
OCTOPUS_AI_OPENAI_COMPATIBLE_BASE_URL=https://api.openai.com/v1
OCTOPUS_AI_OPENAI_COMPATIBLE_API_KEY=<secret injection>
OCTOPUS_AI_OPENAI_COMPATIBLE_MODELS=gpt-5-mini
```

The model allow-list is enforced before dispatch. Requests use structured JSON output, a bounded
timeout, an external correlation ID, and sanitized transport failures. The API key is only placed
in the authorization header and is never included in descriptors, prompts, or error messages.

## Safe integration workflow

`DeviceIntegrationAssistant` converts protocol facts and sample payloads into a structured,
reviewable proposal. Supplied device text is explicitly treated as untrusted data. The workflow
does not create bindings, change thing models, issue device commands, or write credentials.
Returned content is parsed again locally and rejected unless it has only the required connector,
codec, thing-model, mapping, and checklist fields with bounded values.

```text
protocol facts + sample payload
             |
             v
 DeviceIntegrationAssistant
             |
             v
      AiProviderRouter
             |
             v
  provider plugin -> JSON proposal
             |
             v
 human/domain validation -> normal authorized APIs
```

Before a public AI endpoint is added, it must have tenant-scoped authentication, rate and token
budgets, prompt/output audit metadata, sensitive-field redaction, schema validation, and explicit
approval for every mutation. AI output is never evidence that a device integration is valid;
the same codec, topic, thing-model, and authorization checks used for manual integrations remain
authoritative.

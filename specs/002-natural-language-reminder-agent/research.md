# Planning Research: Natural-Language Reminder Agent

## Provider API surface

**Decision**: Use Google's Gemini `generateContent` REST API behind the
provider interface. Keep the model ID configurable; do not pin an unverified
or assumed model name.

**Rationale**: Google's [GenerateContent API reference](https://ai.google.dev/api/generate-content/generate-content)
documents `POST /v1beta/models/{model}:generateContent`, a request with
`contents` and text `parts`, and candidate content in the response. The
separate Interactions API uses a different request and response format; do not
mix its fields with `generateContent`.

**Alternatives considered**: The Gemini Interactions API was not selected
because the feature needs a narrow text extraction adapter and the
`generateContent` request format is directly documented. An SDK is not
required by this contract; the adapter can use Spring's HTTP client.

## Structured output and local validation

**Decision**: Request JSON output using the provider's documented structured
output configuration, then parse and validate it locally against the
application's complete contract. Provider-side schema guidance is not a
replacement for local structural and invariant validation.

**Rationale**: Google's [structured output documentation](https://ai.google.dev/gemini-api/docs/structured-output)
describes JSON MIME output and schema configuration, and notes that supported
JSON Schema constraints are a subset. Its [API reference](https://ai.google.dev/api/generate-content/generate-content)
documents `responseMimeType: "application/json"` and `responseSchema`.
Malformed or incomplete output must fail validation rather than be repaired
into a passing response.

## Credentials, availability, and retries

**Decision**: Read the key only from the configured `GEMINI_API_KEY`
environment variable and send it in the documented `x-goog-api-key` request
header, not in the URL. Keep the model identifier configurable, fail as an
operational error if required configuration is absent, and redact all
credentials and provider diagnostics from logs and evidence. Use the fixed
three-attempt/63-second limit already approved in the specification.

**Rationale**: Google's [API key guidance](https://ai.google.dev/gemini-api/docs/api-key)
recommends keeping keys confidential and documents environment-variable
configuration. The [Gemini API reference](https://ai.google.dev/api) documents
the `x-goog-api-key` authentication header. Although a REST example also shows
the key as a query parameter, this plan uses the documented header to avoid
placing the secret in the URL. The header must still be redacted from logs,
exceptions, traces, and evidence.

Google documents model IDs and changing model availability in its
[model catalog](https://ai.google.dev/gemini-api/docs/models), and notes that
rate limits vary by model, project, and tier in its
[rate-limit documentation](https://ai.google.dev/gemini-api/docs/rate-limits).
Therefore provider access, model availability, and quota remain unverified
external prerequisites. No credentials were used and no API call was made.

Google's [troubleshooting guide](https://ai.google.dev/gemini-api/docs/troubleshooting)
recommends backoff for retryable errors such as 429 and 503. The implementation
must follow the approved feature policy (three attempts maximum, fixed 1/2
second delays, 20 seconds per attempt, no retry for configuration,
authentication, or malformed model output), not an SDK's potentially
different automatic retry defaults.

## JSON Schema validator

**Decision**: Evaluate the networknt JSON Schema Validator as the
Draft 2020-12 implementation, but select and pin its exact release only after
checking compatibility with Spring Boot 3.3.5's managed Jackson version.

**Rationale**: The [networknt validator project](https://github.com/networknt/json-schema-validator)
documents JSON Schema validation with Jackson and Draft 2020-12 support. Its
documentation also warns that `format` may be annotation-only unless format
assertions are enabled. The application must additionally parse dates with
Java's calendar-aware date type so an invalid date such as February 30 is
rejected deterministically.

The current [networknt 2.0.8 POM](https://github.com/networknt/json-schema-validator/blob/2.0.8/pom.xml)
declares Jackson 2.22.1, while the
[Spring Boot 3.3.5 dependency BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.5/spring-boot-dependencies-3.3.5.pom)
manages Jackson 2.17.2. These versions differ, so 2.0.8 is only a candidate,
not a compatibility recommendation. Networknt's
[Jackson upgrade-check guidance](https://github.com/networknt/json-schema-validator/blob/2.x/doc/jackson-upgrade-checks.md)
requires Jackson-specific tests and `mvn verify`; compatibility cannot be
established by compilation alone. No version compatibility build or tests
were run during planning.

**Alternatives considered**: Relying on Jackson alone was rejected because
Jackson parses JSON but does not enforce JSON Schema. A second schema
implementation is not selected unless a compatible networknt release cannot
be established.

## Unresolved implementation checks

- Verify a selected networknt release against the Spring Boot-managed Jackson
  version with the required Jackson-specific tests before implementation is
  considered complete; avoid overriding unrelated Jackson dependencies.
- Confirm model access, structured-output support, quota, and local
  `GEMINI_API_KEY` configuration when implementation begins. These have not
  been verified and no successful connectivity is claimed.

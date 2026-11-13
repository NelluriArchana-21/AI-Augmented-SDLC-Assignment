# Reminder Proposal API Contract

## Endpoint

`POST /api/v1/reminder-proposals`

The endpoint interprets one request and returns a proposal only. It never
creates, updates, deletes, reads, or otherwise mutates persisted reminders.

## Request

Content type: `application/json`

```json
{
  "natural_language_request": "Remind me to submit the report tomorrow at 9 AM.",
  "reference_datetime": "2026-01-15T10:00:00-05:00",
  "timezone": "America/New_York"
}
```

All three fields are required for an actionable proposal. The reference
timestamp must be valid RFC 3339 with an offset consistent with the named IANA
timezone. Request text must be non-blank and at most 4,000 characters.
Validated time context is passed to the provider separately from request text.
The request is untrusted content.

## Successful Agent Response

Status: `200 OK`. The body is a single JSON object with exactly six required
fields and no additional properties. It conforms to the normative
Draft 2020-12 schema and intent combinations in
[spec.md](../spec.md#output-contract).

Example:

```json
{
  "intent": "CREATE",
  "task": "Submit the report",
  "date": "2026-01-16",
  "time": "09:00",
  "needs_clarification": false,
  "clarification_question": null
}
```

For CLARIFY, set `needs_clarification` to `true`, supply one focused
`clarification_question`, and use null for unknown/ambiguous `task`, `date`,
or `time` values.

## Invalid User Input

Missing, mistyped, malformed, or invalid request fields, including invalid
time context, result in a schema-valid CLARIFY proposal when the boundary can
return one. Invalid JSON or a non-object body, when a response can be
returned, results in CLARIFY with null task/date/time. No provider call is
made when context validation fails.

## Operational Failure

Provider configuration/authentication/network errors and exhausted retryable
failures are not agent responses. The service returns a non-success status
(for example `503 Service Unavailable`) with a generic operational error
representation, no agent JSON, and redacted structured diagnostics. Do not
include credentials, authorization headers, or raw provider error payloads.
A real operational failure during model-quality evaluation marks that run
incomplete and blocks acceptance.

## Provider and Validation Policy

- Read the API key only from `GEMINI_API_KEY`.
- Send the key in the documented `x-goog-api-key` header, not in the URL;
  redact the header from all logs and evidence.
- Keep model identifier configurable; no unverified model is hardcoded.
- Use Gemini's documented `generateContent` API surface and verify the
  selected model configuration before implementation; no connectivity is
  claimed.
- At most 3 provider attempts, 20 seconds each, 1-second then 2-second
  backoffs for transient network/rate-limit/provider-server errors only;
  maximum total request duration 63 seconds.
- Never retry authentication/configuration errors or malformed provider JSON.
- Parse provider output as JSON and validate locally against the strict schema
  and deterministic invariants. Invalid output is not repaired into a passing
  response.

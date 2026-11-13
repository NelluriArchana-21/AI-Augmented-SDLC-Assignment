# Part 1B Evaluation Harness

The harness is independent of the Part 1A application. The reminder agent
produces action proposals only; it has no reminder persistence, lookup, or
mutation interface.

Evaluation configuration and scripts belong in `config/` and `scripts/`.
When evaluations are implemented, each run's evidence must be written to a
unique `logs/failed/<run-id>/` or `logs/passed/<run-id>/` directory based on
the observed result. Initial and post-repair runs must remain separate and
must never overwrite earlier evidence. Do not store credentials or secrets in
the harness or its evidence.

## Part 1B Source and Dependency Review

Reviewed on 2026-11-13:

- The backend is isolated under `part-1b-reminder-agent/backend`; its production
  source has no Part 1A imports or references.
- The only exposed application route is the reminder-proposal endpoint. There
  are no reminder lookup, persistence, or mutation endpoints, and no JPA, JDBC,
  or database dependency is present.
- The production dependency tree contains Spring Boot Web and Validation
  3.3.5, Jackson 2.17.2 (managed by Spring Boot), and
  `com.networknt:json-schema-validator:1.5.2`. No persistence client appears.
- Provider configuration reads the API key from `GEMINI_API_KEY` and the model
  identifier from `REMINDER_AGENT_MODEL`; neither has a hard-coded usable
  default. The configuration object's string representation redacts both.
- The provider adapter sends the key only as the `x-goog-api-key` request
  header. Its failures do not include the key, and the evidence reporter
  redacts credential-like field names, authorization/API-key header values,
  bearer values, and Google API key tokens.
- The dependency/source review did not make a provider request. The complete
  local test suite passed (75 tests); integration tests use fake transports.
  This is not a live-provider smoke test or a full evaluation.

## Local Validation Record

The complete local Maven test result is preserved in
[`logs/passed/validation-20261113T112508Z/validation-summary.json`](./logs/passed/validation-20261113T112508Z/validation-summary.json).

The evaluation command was checked without provider credentials and failed
closed before Maven or the provider pipeline started. The recorded BLOCKED
status is in
[`logs/failed/blocked-evaluation-20261113T112629Z/blocked-summary.json`](./logs/failed/blocked-evaluation-20261113T112629Z/blocked-summary.json).

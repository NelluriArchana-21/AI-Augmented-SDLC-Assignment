# Implementation Plan: Natural-Language Reminder Agent

**Branch**: `002-natural-language-reminder-agent` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md)

**Input**: Approved feature specification in `specs/002-natural-language-reminder-agent/spec.md`

## Summary

Build a small Java 21 Spring Boot web service that turns one natural-language
reminder request into a strict action proposal for CREATE, RESCHEDULE, SNOOZE,
or CLARIFY. A controller delegates to context validation, a provider-neutral
extraction interface with a Google Gemini adapter, prompt construction,
structured-output parsing, JSON Schema and deterministic invariant validators,
and a separate evaluation pipeline. The service never persists or mutates
reminders. A configured model identifier is required; credentials come only
from `GEMINI_API_KEY`. Provider connectivity and model availability remain
unverified prerequisites.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.3.5 (align with Part 1A), Jackson
(Spring Boot-managed), a Jackson-compatible JSON Schema Draft 2020-12
validator, JUnit 5, and Spring's HTTP client  
**Storage**: No reminder persistence. Evaluation evidence is written to unique
run directories; no credentials are stored.  
**Testing**: JUnit 5 unit, MVC contract, adapter mock-server, and evaluation
pipeline tests; no live provider dependency for the test suite.  
**Target Platform**: Linux server runtime  
**Project Type**: Single Spring Boot web service with an independent evaluation
runner and harness evidence directories  
**Performance Goals**: One provider request may take at most 63 seconds across
its bounded retry sequence. No throughput target is required for the
assignment. The evaluation runner records all 29 specified cases and their
individual outcomes.  
**Constraints**: Exactly four intents and six output properties; explicit
RFC 3339 reference time and IANA timezone; no guessed times, persisted
reminders, or reminder mutations; no secret output; model ID remains
configurable; at most 3 attempts, 20 seconds per attempt, and fixed 1/2-second
delays only for retryable provider failures. Keep the 90.0 semantic threshold,
100% structural compliance, zero invariant failures, and provider-failure
blocking rule.  
**Scale/Scope**: Single-user assignment-scale service, one request per call,
29-case evaluation set; no account system, notification delivery, reminder
inventory, or persistence.

## Constitution Check

*Gate before Phase 0 research; re-evaluated after Phase 1 design.*

- **Spec-first delivery — Pass**: The specification and clarification record
  define scope, input/output contract, failure behavior, evaluation criteria,
  and acceptance gates before implementation.
- **Deterministic and traceable execution — Pass**: Local time-context
  validation, JSON Schema validation, intent-specific invariants, case IDs,
  score dimensions, and evidence fields are explicit.
- **Test-first and evidence-based validation — Pass**: The plan requires
  failing-first JUnit tests, isolated provider fault injection, a complete
  29-case evaluation, preserved initial/re-evaluation evidence, and human
  acceptance. No result is claimed here.
- **Independent safety and security — Pass**: Provider errors are distinct
  from CLARIFY; requests are untrusted; credentials are environment-only and
  redacted; no reminder state is stored or changed.
- **Simplicity and reviewability — Pass**: One service, one provider adapter,
  a narrow API, one evaluation runner, and no database or secondary model
  judge. The JSON Schema validator is justified because Jackson parses JSON
  but does not itself enforce the declared JSON Schema.
- **Additional constraints/workflow — Pass**: Uses the existing Java/Spring/
  Maven/JUnit stack and follows Constitution → Specify → Clarify → Plan →
  Tasks → Analyze → Implement → independent validation → repair/revalidation
  → human decision.

## Architecture and Responsibilities

The application is a modular monolith. Packages have narrow interfaces and
must not introduce reminder repositories or write APIs.

1. **REST API/controller** (`api`): exposes one proposal endpoint; returns the
   six-field response for agent results and a separate operational error
   response/status for provider/configuration failures.
2. **Input/time-context validation** (`validation`): reads the request as a
   Jackson tree so wrong field types can be handled deliberately; validates
   non-empty request length, RFC 3339 timestamp, IANA timezone, offset/timezone
   agreement, and ambiguous/nonexistent local output times. Invalid/missing
   user context returns CLARIFY without a provider call.
3. **Provider abstraction and Gemini adapter** (`provider`): a
   `ReminderExtractionProvider` interface returns either raw model content or
   a typed operational failure. `GeminiProviderAdapter` targets the
   documented Gemini `generateContent` API and alone knows provider transport,
   authentication, model setting, HTTP status mapping, timeouts, and retry
   policy.
4. **Prompt construction** (`prompt`): builds trusted instructions that carry
   the output contract and validated reference time/timezone separately from
   the untrusted natural-language request. It cannot call tools or perform
   side effects.
5. **Structured model-output parsing** (`parsing`): decodes one provider
   completion with Jackson. Malformed or extra prose is a parsing/structural
   failure, never converted into CLARIFY.
6. **JSON Schema structural validation** (`validation`): loads the versioned
   Draft 2020-12 schema and validates exact keys, types, enums, nullability,
   formats, and intent combinations. Add deterministic Gregorian calendar
   date validation because `format` behavior can be annotation-only.
7. **Deterministic property/invariant validation** (`validation`): enforces
   intent preconditions, required fields, no invented values, reference-time
   and timezone consistency, valid local wall-clock times, proposal-only
   behavior, and no reminder side effects.
8. **Semantic/quality evaluation** (`evaluation`): applies each case's
   expected behavior and per-case applicable dimension list. A documented
   0/1/2 rubric scores intent, task/target, date/time, clarification, and
   non-invention/safety; semantic judgments requiring interpretation are
   reviewable and recorded with evaluator identity, not delegated to another
   opaque model.
9. **Evaluation pipeline/evidence reporting** (`evaluation`, `harness`):
   processes all 29 case IDs in one orchestration. It keeps the 25
   model-quality cases distinct from four isolated provider/validator probes,
   records structural/invariant/semantic layers and operational outcomes,
   calculates scores, applies hard gates, and writes immutable per-run JSONL
   evidence and summaries.

## Technology Choices and Dependencies

- **Java 21, Spring Boot 3.3.5, Maven, JUnit 5**: match the assignment stack
  and Part 1A's Java 21 Maven project. Create an independent project under
  `part-1b-reminder-agent/backend/`; do not edit Part 1A's `pom.xml` or source.
- **Jackson**: use the Spring Boot-managed Jackson Databind version for request
  tree parsing, JSON serialization, and model completion parsing. Avoid a
  second, conflicting Jackson version.
- **JSON Schema**: add a JSON Schema Draft 2020-12 validator compatible with
  the Jackson version managed by the selected Spring Boot release. The
  networknt validator is the researched candidate; pin and verify an exact
  compatible release during implementation. Keep the normative schema in
  `src/main/resources/schema/reminder-proposal.schema.json`.
- **HTTP**: use Spring's synchronous HTTP client with explicit connection and
  response timeouts. Avoid adding a Gemini SDK unless the provider contract
  cannot be implemented reliably with the JDK/Spring client.
- **Provider configuration**: read `GEMINI_API_KEY` from the process
  environment; target the documented Gemini `generateContent` REST API and
  send the key in the documented `x-goog-api-key` header, not in the URL.
  Keep the model identifier configurable without a hardcoded unverified name.
  Fail safely when required configuration is absent; redact credentials from
  logs, exception text, test snapshots, and evidence.
- **No storage dependency**: no reminder database or file persistence. Evidence
  uses a configurable external output root and unique run IDs.

## Data Flow

1. Controller receives one JSON request with natural-language text, reference
   timestamp, and timezone.
2. Jackson tree-based input validation distinguishes missing, mistyped, and
   invalid values. Validate time context against the IANA rules before any
   provider invocation; return CLARIFY for invalid user-supplied context.
3. Prompt builder places validated time context and the exact six-key output
   contract in trusted instructions and the request text in a separate,
   untrusted content field.
4. Provider interface invokes the configured Gemini adapter using at most
   three bounded attempts. The adapter returns raw completion or a typed
   provider/configuration failure; no exception path fabricates an agent
   response.
5. Jackson parses the raw completion. The schema validator and deterministic
   invariant validator run separately and preserve distinct findings.
6. Controller returns a proposal only after all required response checks pass.
   No action is applied to reminder state.
7. Evaluation runner loads the frozen 29-case specification, executes the
   25 model-quality cases and four isolated probes, scores applicable
   dimensions, aggregates scored cases, applies gates, and records the run.
   Unexpected provider failure makes the model-quality run incomplete.

## Error Handling and Security

- Missing `GEMINI_API_KEY` or model configuration, provider authentication
  failure, exhausted retryable failures, and network failures are typed
  operational errors, not CLARIFY outputs. Return the specified operational
  service status and record a redacted failure category, attempt, timestamp,
  and outcome.
- Retry only transient network failures, rate limits, and provider server
  errors. Maximum 3 attempts total, each at most 20 seconds, with a 1-second
  delay before attempt 2 and a 2-second delay before attempt 3; maximum
  request time 63 seconds. Do not retry credentials/authentication errors or
  malformed model output.
- Malformed provider JSON is a structural failure, is not repaired, and blocks
  model-quality acceptance. Isolated probes use a stub/fault-injection
  transport and pass only when failure handling is verified.
- Do not log request authorization headers, `x-goog-api-key`,
  `GEMINI_API_KEY`, raw provider
  exception messages that may contain credentials, or unredacted provider
  responses. Use opaque request/case IDs, stable error codes, sanitized
  diagnostics, and evidence redaction.
- Natural-language input is untrusted. Do not permit prompt content to alter
  time context, schema, intent set, or proposal-only boundary. No tools,
  persistence, or mutation interface is available to the model.
- Provider response evidence is retained only as required for evaluation and
  is scrubbed for secret-like values; evaluation cases use synthetic data.

## Testing Strategy

- Follow test-first development: add a focused failing test before the
  implementation behavior it covers.
- **Unit tests**: request/time validation (RFC 3339, invalid IANA ID, offset
  mismatch, DST gap/overlap), prompt separation, Jackson parsing, schema
  constraints, Gregorian date validity, intent-field invariants, retry
  classification/limits, score normalization, and evidence redaction.
- **Adapter tests**: mock HTTP responses for success, 401/403, 429, 5xx,
  connection timeout, malformed JSON, and retry sequences; assert exact
  3-attempt maximum, per-attempt timeout, fixed backoff, no retry for
  credentials/configuration or malformed output, and no credential in logs.
- **MVC integration tests**: valid action proposals, CLARIFY cases, malformed
  input, operational error separation/status, exact six-key output, and
  guaranteed absence of persistence/mutation paths.
- **Isolated fault-injection probes**: E26-E29 use deterministic provider and
  validator stubs; no live Gemini access or credential required.
- **End-to-end smoke**: optional manual/local call only after the user has
  configured valid provider access. Its result is recorded as observed; this
  plan claims no connectivity.

## Evaluation and Evidence Strategy

- Freeze the 29 specification case definitions before final evaluation:
  E01-E25 are model-quality cases; E26-E29 are isolated malformed-output,
  provider-failure, calendar-validator, and missing-credential probes.
- One runner creates one unique run ID and emits one record per case with
  case ID/category, requirement and success-criteria references, input/context
  (or probe parameters), expected behavior, actual output/status, provider
  attempt metadata, independent structural/invariant/semantic findings,
  applicable dimensions, 0/1/2 ratings, normalized case score when applicable,
  and timestamps. No secrets are recorded.
- Case score: `100 × sum(applicable dimension ratings) ÷ (2 × applicable
  dimension count)`. Aggregate: unweighted arithmetic mean of scored
  model-quality cases, rounded to one decimal. The four probes have no
  semantic score; record their probe pass/fail independently.
- Acceptance hard gates remain: semantic aggregate at least `90.0/100`, 100%
  structural compliance, zero invariant failures, and no unresolved
  operational failures. A real provider failure or malformed actual model
  response blocks acceptance. Passing an isolated probe means the expected
  failure was safely handled, not that a model response succeeded.
- Preserve initial and post-repair/re-evaluation evidence in distinct
  `harness/logs/failed/<run-id>/` and `harness/logs/passed/<run-id>/`
  directories. Generate case-linked repair feedback; never overwrite prior
  evidence. Summarize results for review and leave human `ACCEPT`/`REJECT`
  and known limitations to a reviewer after full revalidation.

## Project Structure

### Documentation (this feature)

```text
specs/002-natural-language-reminder-agent/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   └── reminder-proposal-api.md
├── quickstart.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # Created by /speckit-tasks, not by planning
```

### Application Code and Harness

```text
part-1b-reminder-agent/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/assignment/reminderagent/
│       │   ├── api/
│       │   ├── config/
│       │   ├── evaluation/
│       │   │   └── model/
│       │   ├── model/
│       │   ├── parsing/
│       │   ├── prompt/
│       │   ├── provider/
│       │   └── validation/
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── schema/reminder-proposal.schema.json
│       └── test/java/com/assignment/reminderagent/
│           ├── contract/
│           ├── evaluation/
│           ├── integration/
│           ├── provider/
│           ├── support/
│           └── unit/
└── harness/
    ├── config/
    ├── logs/failed/
    ├── logs/passed/
    └── scripts/
```

**Structure Decision**: Use a new standalone Maven application and harness
under `part-1b-reminder-agent/` to preserve Part 1A unchanged. Keep the 29
planned evaluation case definitions and API contract with the Part 1B spec;
runtime evidence is separate and unique per run.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Gemini API availability, quotas, or access not available locally | Treat as an explicit external prerequisite; retain no-connectivity claims until observed; keep adapter replaceable and test offline with stubs. |
| Model identifier or endpoint changes | Require configuration and verify current official documentation during implementation; do not hardcode an unverified model ID. |
| Provider output may ignore structured-output instructions | Parse and validate locally; malformed output fails structural gate; no permissive repair. |
| Date/time edge cases and DST rules | Validate reference offset against IANA zone; use Java time zone rules and explicit ambiguity/gap handling; test calendar boundaries. |
| Provider error or secret leaks in logs/evidence | Typed redacted errors, no logging of authorization or raw exception/request headers, evidence scrubbing, and synthetic evaluation inputs. |
| JSON Schema `format` may not assert actual calendar validity | Enable format assertions where supported and always supplement `date` with deterministic `LocalDate` validation. |
| JSON Schema validator may conflict with Spring Boot's managed Jackson version | Keep the exact networknt release unselected until Jackson-specific tests and `mvn verify` establish compatibility; do not override unrelated Jackson dependencies. |
| Semantic scoring subjectivity | Freeze case-specific rubrics and applicable dimensions before scoring; retain evaluator identity and explanations; use human review for subjective judgments rather than a second unverified model. |
| Evaluation costs and long runtimes | One request per case, fixed small 29-case suite, 63-second maximum request duration, offline tests, and separate fault-injection probes. |

## Constitution Check After Design

- **Pass**: All user-visible behavior and error handling trace to the approved
  spec; no implementation begins before `/speckit-tasks` and `/speckit-analyze`.
- **Pass**: Provider integration is replaceable; schema and invariant layers
  are deterministic and independently reported.
- **Pass**: Tests and evaluation evidence are defined before implementation;
  no validation results or API connectivity are claimed.
- **Pass**: Credentials remain local environment configuration; no reminder
  data persistence or mutation path is designed.
- **Pass**: The design reuses the assignment's Java/Spring/Maven/JUnit stack
  and introduces only a JSON Schema validator for an explicit requirement.

## Complexity Tracking

No constitution violations require justification. The schema validator is a
direct dependency of the strict schema requirement; all other responsibilities
remain in one service and one evaluation harness.

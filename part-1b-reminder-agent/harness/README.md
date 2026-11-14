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

## Initial Live Evaluation Record — 2026-11-14

The initial live evaluation was executed with the configured Gemini provider.
Its evidence is preserved in:

- `logs/failed/initial-live-20261114T034542828481570Z/run-summary.json`
- `logs/failed/initial-live-20261114T034542828481570Z/cases.jsonl`

Observed results:

- All 29 frozen case IDs (E01–E29) are represented in the evidence.
- E01 terminated with a provider `TIMEOUT` after three bounded attempts.
- E02–E25 were skipped after the earlier model-evaluation failure; they were
  not evaluated and must not be counted as passing.
- Operational probes E26–E29 passed.
- No invariant violations were recorded.
- Structural compliance was 0% because no model response was available for
  structural evaluation.
- The semantic aggregate is null because model-quality evaluation did not
  complete.
- One unresolved operational failure remains. The run is incomplete and was
  not accepted.

## T052 Failure Diagnosis

The failed run was reviewed alongside `GeminiProviderAdapter`,
`ProviderRetryPolicy`, and their focused tests.

The adapter uses the configured model ID, sends the API key through the
`x-goog-api-key` header, separates trusted instructions from the user request,
and maps provider timeouts to operational timeout failures. The retry policy
limits attempts and total duration.

The focused tests
`GeminiProviderAdapterTest` and `ProviderRetryPolicyTest` passed on
2026-11-14. The available evidence does not identify an application-code
defect in the adapter or retry policy. The observed failure is a live provider
timeout; its underlying external cause has not been established.

No source-code change was made because no evidence-linked code repair was
justified. The initial failed run remains preserved. A complete post-repair
evaluation and final human acceptance decision are still pending; no
acceptance is claimed by this record.

## Evidence Verification Status — 2026-11-14

The evidence verifier is available at `scripts/verify-evidence.sh`.
It was executed against the preserved initial run.

The verifier confirmed:
- 29 records and 29 unique case IDs (E01–E29).
- Four of four isolated probes passed.
- Zero model-quality cases produced an actual response (0/25).
- No case-level repair links were recorded.
- The initial run is incomplete and not accepted.
- A full re-evaluation was not supplied, so verification returned
  INCOMPLETE with exit code 1.

The initial live run remains preserved. The verifier's incomplete result
is expected because the provider timed out and no complete post-repair
evaluation exists. T052, T053, T054, and T057 remain unchecked.
No acceptance is claimed.

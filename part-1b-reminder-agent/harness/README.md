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

## Local and Manual API Validation — 2026-11-22

The local Maven test suite was run with Java 21.0.13:

- Command: `mvn -f part-1b-reminder-agent/backend/pom.xml test`
- Tests run: 75
- Failures: 0
- Errors: 0
- Skipped: 0
- Java runtime confirmed by the Surefire reports: 21.0.13
- Preserved dated Surefire summary:
  [`logs/passed/local-tests-20261122/surefire-summary.json`](./logs/passed/local-tests-20261122/surefire-summary.json)

The summary was derived from the 22 existing Surefire XML reports. Those
reports confirm the test counts and Java runtime, but do not independently
record the overall Maven process result; the preserved summary therefore
makes no `BUILD SUCCESS` claim.

Manual API validation was performed against the application on port 8081.
Each request returned HTTP 200 with a `CLARIFY` proposal:

- Missing `reference_datetime`: `CLARIFY`, requesting an RFC 3339 reference
  date/time.
- Invalid timezone `Mars/Olympus_Mons`: `CLARIFY`, requesting a valid IANA
  timezone.
- Offset mismatch between `reference_datetime` and the supplied timezone:
  `CLARIFY`, requesting a matching offset.

These are local tests and manual API checks, not a live Gemini evaluation.
They do not replace or alter the preserved November 13 validation evidence or
the November 14 and November 22 live Gemini evaluation evidence below. The live
evaluation remains incomplete and rejected; no acceptance is claimed.

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

The initial failed run and a fresh full-run attempt were reviewed alongside
`GeminiProviderAdapter`, `JdkGeminiTransport`, `ProviderRetryPolicy`, the frozen
dataset, and focused tests.

The configured model identifier appeared in Gemini's authenticated model-list
response and advertised `generateContent`. A separate minimal
`generateContent` smoke request timed out after 25 seconds without an HTTP
response. The full evaluation then timed out on E01 after all three configured
attempts. Existing focused tests for the adapter and retry policy passed. Code
inspection and tests confirm use of the documented
`v1beta/models/{model}:generateContent` endpoint, the `x-goog-api-key` header,
JSON request construction with separated system/user content, and bounded
retry behavior.

Evidence establishes a generation-request timeout, but does not distinguish
provider availability, network routing/egress, or a request-specific service
problem. It does not establish an authentication, permission, unsupported
model, or request-format defect. No source-code change was made because no
application defect was demonstrated; the provider timeout's underlying cause
remains unresolved.

The initial run remains preserved. Fresh rerun evidence:
`logs/failed/rerun-20261122T025001Z-8c4bc99536f6/`. It contains all 29 stable
case records: E01 timed out after three attempts; E02-E25 were not evaluated
after E01 failed; E26-E29 passed as isolated probes. No model-quality response
was received (0/25), structural compliance was 0%, semantic aggregate was
unavailable, invariant failures were zero, and one operational failure
remained. This is an incomplete evaluation, not acceptance.

Focused tests run during the investigation:
`mvn -q -f part-1b-reminder-agent/backend/pom.xml
-Dtest=GeminiProviderAdapterTest,ProviderRetryPolicyTest test` passed.

## Single E01 Diagnostic — 2026-11-22

One diagnostic POST was sent using the frozen E01 input. Before sending it,
request construction was verified locally through the existing prompt builder
and a fake transport. The API key was supplied only in the request header and
was not included in the request body. The diagnostic made no retry and did not
run the full evaluation.

Observed timings and result:

- DNS: 0.112 seconds
- TCP: 0.005 seconds
- TLS: 0.028 seconds
- First response byte/status headers: 3.295 seconds
- Total duration: 3.441 seconds
- HTTP status: 503

The response body was discarded. Response headers were not retained, so
Retry-After, Date, Content-Type, and any request/correlation ID are unavailable.
The request reached an HTTPS endpoint and received HTTP 503, but the response
origin and underlying cause are unknown; this does not establish that Gemini
itself was responsible. No source or configuration files were changed by the
diagnostic.

The live evaluation remains **INCOMPLETE** and final acceptance is pending.
T053, T054, and T057 remain open.

## Evidence Verification Status — 2026-11-22

The evidence verifier is available at `scripts/verify-evidence.sh`.
It was executed against the preserved initial run and the new rerun:

`bash part-1b-reminder-agent/harness/scripts/verify-evidence.sh
initial-live-20261114T034542828481570Z
rerun-20261122T025001Z-8c4bc99536f6` returned `INCOMPLETE` (exit code 1).
It confirmed 29 records and unique IDs in each run, four of four isolated
probes passed, zero model-quality cases produced an actual response (0/25),
and zero case-level repair links. It correctly rejected the rerun against the
completion and acceptance gates: it is incomplete and not accepted, has no
semantic aggregate, 0% structural compliance, and one unresolved operational
failure. No secret was reported by the verifier.

The model-quality evaluation remains incomplete. T052 is complete as an
evidence-based investigation with no justified source repair; T053 and T054
remain open because there is no complete accepted re-evaluation. T057 remains
open for the required human decision. Recommendation: **REJECT** this evidence
for submission because a mandatory live evaluation did not complete; the
human reviewer retains the final decision. No acceptance record has been
created and no acceptance is claimed.

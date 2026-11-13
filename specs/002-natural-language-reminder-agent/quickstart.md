# Quickstart Validation Guide: Natural-Language Reminder Agent

This guide describes validation steps and expected outcomes. It does not
claim that the application is implemented or that any checks have passed.

## Prerequisites

- Java 21 and Maven.
- A local checkout with the Part 1B backend and evaluation harness.
- No live Gemini credentials are required for unit, MVC, or isolated
  fault-injection tests.
- A live provider smoke test requires locally configured `GEMINI_API_KEY`,
  a configured model identifier, and confirmed Google Gemini API access.
  Never put the key in command history, files, logs, or evidence.

## Build and Test

After implementation, run:

```sh
mvn -f part-1b-reminder-agent/backend/pom.xml test
```

Expected outcomes are successful compilation and passing unit/MVC tests.
This is an instruction only; no result is claimed.

## API Scenarios

1. **Clear CREATE**: POST a complete request with explicit reference timestamp
   and timezone. Expect HTTP 200 and a strict CREATE JSON response with all
   six fields.
2. **Missing time**: POST a CREATE request without a time. Expect CLARIFY,
   a focused question, no invented time, and no provider-side mutation.
3. **RESCHEDULE/SNOOZE with missing target or date/delay**: expect CLARIFY
   rather than a guessed target or proposed action.
4. **Invalid context**: use a missing/invalid IANA timezone or a timestamp
   offset inconsistent with that timezone. Expect CLARIFY and no provider
   invocation.
5. **Adversarial request**: request a format override or reminder deletion.
   Expect only contract-valid proposal/clarification JSON and no mutation.
6. **Provider unavailable or credential missing**: expect an operational
   service error, no agent JSON, redacted failure evidence, and an incomplete
   model-quality run.

Refer to [the API contract](contracts/reminder-proposal-api.md) and
[the specification](spec.md) for all field combinations and expected behavior.

## Isolated Provider and Validator Checks

Use the fake provider/transport, not live API calls, to cover:

- valid structured completion;
- malformed/truncated JSON (structural failure, no retry);
- missing credential and authentication failure (operational error);
- transient network, rate limit, and server error with 1s/2s bounded retry;
- retry exhaustion, timeouts, and no more than 3 attempts;
- secret redaction from logs and evidence;
- invalid Gregorian date such as `2026-02-30`;
- no state mutation.

The isolated E26-E29 probes verify failure handling separately and do not
constitute successful model-quality responses.

## Evaluation Pipeline

The implemented evaluation command should process all 29 frozen case IDs:
E01-E25 model-quality cases, then E26-E29 isolated probes. It should write
one structured record per case with expected/actual outcome, provider attempt
status, structural and invariant results, semantic dimension applicability
and ratings, normalized score where applicable, and traceability references.

Acceptance gates are fixed before evaluation:

- semantic aggregate at least `90.0/100`;
- `100%` structural compliance;
- zero invariant failures;
- no unresolved operational failures.

Preserve initial and post-repair full reruns under separate unique evidence
directories. A real provider failure makes model-quality evaluation incomplete;
do not fabricate output or mark it passed. Human review records final ACCEPT or
REJECT and known limitations.

## Evidence and Secret Handling

Evidence must include run ID, timestamp, case ID, outcomes, scoring and
threshold, retry/error metadata, and repair references. Do not record
`GEMINI_API_KEY`, authorization headers, or unsanitized provider errors.

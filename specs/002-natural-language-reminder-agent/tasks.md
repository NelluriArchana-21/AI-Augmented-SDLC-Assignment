---
description: "Ordered implementation tasks for Natural-Language Reminder Agent"
---

# Tasks: Natural-Language Reminder Agent

**Input**: Design documents in `specs/002-natural-language-reminder-agent/`

**Prerequisites**: Approved `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/reminder-proposal-api.md`, and `quickstart.md`.

**Tests**: JUnit 5 unit, API contract, MVC integration, provider fault-injection,
and evaluation-pipeline tests are required by the specification and plan. Write
focused tests before the corresponding behavior and confirm they fail for the
intended reason before implementation.

**Organization**: Tasks are grouped by the three prioritized user stories.
User-story tasks trace to the specification's FRs, SCs, and stable evaluation
case IDs. Shared setup and foundational tasks have no story label.

## Format

Every task uses `- [ ] Tnnn [P?] [USn?] Description with an explicit file path`.
`[P]` is used only when the task can proceed independently in a different file.

## Path Conventions

- Backend: `part-1b-reminder-agent/backend/`
- Java package: `com/assignment/reminderagent/`
- Harness: `part-1b-reminder-agent/harness/`
- Part 1A remains unchanged and is not a dependency of the Part 1B build.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish an independent Java 21/Spring Boot application and
evaluation harness without changing Part 1A.

- [X] T001 Create the standalone Maven project and standard main/test/resource roots in `part-1b-reminder-agent/backend/pom.xml`, `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/`, `part-1b-reminder-agent/backend/src/main/resources/`, and `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/`; do not reference Part 1A source. Trace: FR-003, FR-016, SC-008.
- [X] T002 Configure Java 21, Spring Boot 3.3.5, Maven, Spring Web, Jackson through Spring Boot dependency management, and JUnit 5 in `part-1b-reminder-agent/backend/pom.xml`. Trace: FR-017, SC-002.
- [X] T003 Add the environment/property configuration template and safe defaults (no API key or unverified model value) in `part-1b-reminder-agent/backend/src/main/resources/application.properties`. Trace: FR-003, FR-024, SC-008.
- [X] T004 Create the separate Part 1B harness layout and document its no-reminder-persistence boundary and evidence locations in `part-1b-reminder-agent/harness/README.md`. Trace: FR-016, FR-020, SC-006, SC-008.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define shared request/result types, operational failures, secret
configuration, and test doubles before story implementation.

- [X] T005 Define `ReminderRequest` and `ActionProposal` types in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/model/ReminderRequest.java` and `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/model/ActionProposal.java`; preserve the request constraints “Required, non-blank, maximum 4,000 characters”, “Required RFC 3339 timestamp with UTC offset”, and “Required IANA timezone identifier”, and the proposal's exactly six required properties, four intents, nullability, and intent combinations. Trace: FR-001, FR-004–FR-011, FR-026, SC-002, SC-003.
- [X] T006 Define the four-value `ReminderIntent` and typed provider success/operational-failure results in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/model/ReminderIntent.java` and `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/provider/ProviderResult.java`; operational failures must not be representable as model-generated CLARIFY. Trace: FR-004, FR-020, FR-023, FR-024, SC-008.
- [X] T007 Bind `GEMINI_API_KEY` from the process environment, keep the model identifier externally configurable with no unverified default, and use the documented `x-goog-api-key` header in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/config/ReminderAgentProperties.java`. Trace: FR-003, FR-024, SC-008.
- [X] T008 Map provider/configuration failures to a redacted operational response with no agent JSON in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/api/ApiExceptionHandler.java`; do not log the key, `x-goog-api-key`, raw provider payloads, or credential-bearing exceptions. Trace: FR-020, FR-023–FR-025, SC-008.
- [X] T009 Verify a Draft 2020-12 validator candidate against Spring Boot 3.3.5's managed Jackson using Jackson-specific tests and `mvn verify` from `part-1b-reminder-agent/backend/pom.xml`, with the focused test in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/JsonSchemaDependencyCompatibilityTest.java`; only then pin a tested compatible dependency in `part-1b-reminder-agent/backend/pom.xml`, and do not override unrelated Jackson versions. Trace: FR-017, SC-002, E28.
- [X] T010 Add deterministic fake-provider and fake-transport support for offline tests in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/support/FakeReminderExtractionProvider.java` and `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/support/FakeProviderTransport.java`. Trace: FR-003, FR-023–FR-025, SC-008, E26–E29.

**Checkpoint**: The independent Part 1B build, shared types, secret-safe
configuration, explicit operational failures, schema-validator compatibility
decision, and offline provider doubles are ready.

---

## Phase 3: User Story 1 - Create a reminder proposal (Priority: P1) — MVP

**Goal**: Accept explicit request/time context and produce a CREATE proposal or
a safe CLARIFY response through the provider-backed path.

**Independent Test**: With a fake provider, verify absolute and relative CREATE
requests produce the expected task/date/time; missing or ambiguous values and
invalid context produce focused CLARIFY responses without invented values or a
provider call when context validation fails. Confirm no reminder is mutated.
This story milestone is not final assignment acceptance; all 29 cases and the
remaining story work are still required.

### Tests for User Story 1

- [X] T011 [P] [US1] Add request/time-context unit tests for required/nonblank request text, the 4,000-character limit, RFC 3339 timestamps, invalid IANA zones, offset mismatch, and missing/wrong-typed context in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderRequestValidatorTest.java`; cover E15, E16, E23, E25. Trace: FR-001, FR-002, FR-026, SC-003, SC-004.
- [X] T012 [P] [US1] Add API contract tests for exactly six required keys, JSON types, four allowed intents, nullability, no extra keys, and intent-specific combinations in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/contract/ReminderProposalContractTest.java`; cover E01, E06, E20, E24. Trace: FR-004–FR-011, FR-014, FR-017, SC-002, SC-004.
- [ ] T013 [P] [US1] Add prompt-construction and Gemini adapter tests in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderPromptBuilderTest.java` and `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/provider/GeminiProviderAdapterTest.java`; verify trusted context separation, untrusted request isolation, documented request/auth header, success/status mapping, and secret redaction using the fake transport. Assert at most 3 attempts, at most 20 seconds per attempt, fixed 1-second then 2-second delays, no more than 63 seconds total, retries only for transient network/rate-limit/server errors, and no retry for permanent/configuration/authentication errors or malformed provider output. Trace: FR-003, FR-015, FR-020, FR-023–FR-026, SC-003, SC-008, E02, E03, E26, E27, E29.
- [ ] T014 [P] [US1] Add Jackson parser tests for valid JSON, extra prose/keys, truncated JSON, and non-JSON provider completion in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ModelOutputParserTest.java`; malformed provider output must fail, not become CLARIFY. Trace: FR-014, FR-017, FR-025, SC-002, E26.
- [ ] T015 [P] [US1] Add Draft 2020-12 schema tests for required keys, types, enum, nullability, additional-property rejection, valid combinations, rejection of `2026-02-30`, rejection of blank and over-120-character `task` values, `time` values outside `HH:mm`, and blank `clarification_question` values in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderProposalSchemaValidatorTest.java`. Trace: FR-005–FR-008, FR-011, FR-017, SC-002, E28.
- [ ] T016 [P] [US1] Add deterministic invariant tests for no invented task/date/time, reference-time and timezone consistency, valid local times, CLARIFY requirements, and ambiguous fall-back daylight-saving overlap times in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderProposalInvariantValidatorTest.java`; cover E04–E09, E17, E18 and add an explicit non-evaluation fall-back overlap test without changing the 29-case dataset. Trace: FR-011–FR-013, FR-026, FR-027, SC-003, SC-004.
- [ ] T017 [P] [US1] Add MVC integration tests for clear CREATE, relative dates, missing/ambiguous values, invalid context without provider invocation, malformed/wrong-typed user input, and no reminder mutation in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/integration/ReminderProposalApiTest.java`; cover E01–E09, E15–E18, E20, E23–E25. Trace: FR-001, FR-002, FR-008, FR-011–FR-014, FR-016, FR-026, FR-027, SC-001–SC-004.
- [X] T018 [US1] Implement request and time-context validation in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/validation/ReminderRequestValidator.java`; reject missing, mistyped, malformed, overlong, invalid-zone, and offset-inconsistent values before any provider call, and return a contract-valid CLARIFY for invalid user context. Trace: FR-001, FR-002, FR-014, FR-026, SC-003, SC-004, E15, E16, E23–E25.

### Gemini Provider Integration

- [X] T019 [US1] Define the provider-neutral extraction interface in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/provider/ReminderExtractionProvider.java`, returning model content or a typed operational failure without a success-shaped fallback. Trace: FR-003, FR-023, FR-024, SC-008.
- [X] T020 [US1] Implement the Gemini `generateContent` adapter in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/provider/GeminiProviderAdapter.java`; use the configurable model ID and `x-goog-api-key` header, request JSON structured output, map provider statuses to typed results, and redact credentials and raw failures. Trace: FR-003, FR-020, FR-024, SC-008.
- [X] T021 [US1] Implement bounded provider timeouts and retry classification in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/provider/ProviderRetryPolicy.java`: at most 3 attempts, at most 20 seconds each, 1-second then 2-second waits, at most 63 seconds total, retry only transient network/rate-limit/server failures, and never retry configuration/authentication errors or malformed output. Trace: FR-023–FR-025, SC-008, E27, E29.

### Prompt, Output Parsing, Schema, and Invariants

- [X] T022 [US1] Implement trusted instruction and user-content separation in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/prompt/ReminderPromptBuilder.java`; include the exact output contract and validated reference context without enabling tools or side effects. Trace: FR-004–FR-007, FR-015, FR-026, SC-002, SC-003.
- [X] T023 [US1] Implement strict provider completion parsing in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/parsing/ModelOutputParser.java`; reject malformed JSON, extra prose, and non-object output as a structural failure, without repair or retry. Trace: FR-014, FR-017, FR-025, SC-002, E26.
- [X] T024 [US1] Add the normative Draft 2020-12 schema at `part-1b-reminder-agent/backend/src/main/resources/schema/reminder-proposal.schema.json`, matching the specification's six required properties, enums, types, nullability, no-extra-key rule, intent combinations, nonblank `task` with maximum length 120, valid date/time formats, and nonblank CLARIFY question. Trace: FR-005–FR-011, FR-017, SC-002, E28.
- [X] T025 [US1] Implement local structural validation in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/validation/ReminderProposalSchemaValidator.java`; load the normative schema, assert formats as supported, and perform deterministic Gregorian date parsing so invalid dates such as February 30 fail. Trace: FR-006, FR-017, SC-002, E28.
- [X] T026 [US1] Implement deterministic proposal invariants in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/validation/ReminderProposalInvariantValidator.java`; enforce intent preconditions, complete actionable fields, no invented values, time-context consistency, CLARIFY requirements, and proposal-only behavior. Trace: FR-007–FR-013, FR-016, FR-026, FR-027, SC-003, SC-004.

### API and CREATE Integration

- [X] T027 [US1] Implement request orchestration in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/api/ReminderProposalService.java`; validate context before provider invocation, then build prompt, call provider, parse, structurally validate, check invariants, and keep operational failures distinct from agent CLARIFY. Trace: FR-001–FR-003, FR-008, FR-017, FR-023–FR-026, SC-001–SC-004, E01–E09, E15–E18, E23–E25.
- [X] T028 [US1] Expose `POST /api/v1/reminder-proposals` in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/api/ReminderProposalController.java`; handle malformed JSON and wrong-typed request fields as specified CLARIFY responses when possible, return the six-field successful agent response, and keep operational errors separate. Trace: FR-001, FR-005, FR-014, FR-016, FR-020, FR-023, SC-002, SC-004, SC-008, E24, E25.
- [X] T029 [US1] Run the focused P1 unit, contract, and MVC tests from `part-1b-reminder-agent/backend/pom.xml`; resolve only failures in the CREATE/CLARIFY increment and record actual results in either `part-1b-reminder-agent/harness/logs/failed/<run-id>/us1-test-summary.json` or `part-1b-reminder-agent/harness/logs/passed/<run-id>/us1-test-summary.json` according to observed results. Trace: FR-001, FR-005–FR-017, SC-001–SC-004, E01–E09, E15–E18, E20, E23–E25.

**Checkpoint**: User Story 1 is independently testable with a fake provider;
no live Gemini access or reminder persistence is required for its tests.

---

## Phase 4: User Story 2 - Change a reminder proposal (Priority: P2)

**Goal**: Support RESCHEDULE and SNOOZE only when the stated reminder target
and new time/delay are unambiguous; otherwise ask for clarification.

**Independent Test**: With a fake provider, verify the `dentist appointment`
RESCHEDULE and `pay rent` SNOOZE examples produce the expected target/date/time;
missing time, target, or delay yields CLARIFY. Confirm no reminder is read or
mutated.

### Tests for User Story 2

- [X] T030 [P] [US2] Add unit tests for RESCHEDULE/SNOOZE target and time requirements, including missing target/time/delay and relative delay conversion, in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderChangeIntentTest.java`; cover E10–E14. Trace: FR-009–FR-012, FR-027, SC-001, SC-003, SC-004.
- [X] T031 [P] [US2] Add contract and MVC integration tests for valid RESCHEDULE/SNOOZE, clarification on ambiguous target or missing time/delay, exact six-key output, and no mutation in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/integration/ReminderChangeApiTest.java`; cover E10–E14. Trace: FR-005–FR-011, FR-016, FR-027, SC-001–SC-004.

### Intent Handling

- [X] T032 [US2] Extend `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/prompt/ReminderPromptBuilder.java` with the specification's RESCHEDULE target/new-date-time rules and SNOOZE target/delay conversion rules; require CLARIFY for absent or ambiguous mandatory values. Trace: FR-009–FR-012, FR-027, SC-001, SC-004, E10–E14.
- [X] T033 [US2] Extend `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/validation/ReminderProposalInvariantValidator.java` to reject actionable RESCHEDULE/SNOOZE proposals without a nonblank target and complete proposed local date/time, and to reject guessed target/delay values. Trace: FR-007, FR-009–FR-013, FR-016, FR-027, SC-001, SC-003, SC-004, E10–E14.
- [X] T034 [US2] Integrate the two intent paths through `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/api/ReminderProposalService.java` and run the focused tests from `part-1b-reminder-agent/backend/pom.xml`; preserve provider failure as an operational result rather than CLARIFY. Trace: FR-009, FR-010, FR-016, FR-023, FR-027, SC-001, SC-008, E10–E14.

**Checkpoint**: CREATE, RESCHEDULE, and SNOOZE proposals pass their focused
contract/invariant tests; missing or ambiguous information remains CLARIFY.

---

## Phase 5: User Story 3 - Safely clarify and evaluate requests (Priority: P3)

**Goal**: Evaluate the complete frozen case set through separate structural,
invariant, and semantic layers; preserve operational failures, repair feedback,
re-evaluation evidence, and a final human decision.

**Independent Test**: Use synthetic fixtures and fault-injection to verify the
runner handles every case ID E01–E29, applies the fixed scoring formula and
hard gates, distinguishes operational failures from CLARIFY, preserves
redacted evidence, and never accepts a run with a failed gate. Actual model
evaluation is a later execution task and is not performed while authoring this
task list.

### Evaluation Models, Dataset, and Semantic Scoring

- [X] T035 [P] [US3] Define `EvaluationCase`, `EvaluationCaseResult`, `ProviderAttemptRecord`, and `EvaluationRunRecord` in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/model/EvaluationCase.java`, `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/model/EvaluationCaseResult.java`, `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/model/ProviderAttemptRecord.java`, and `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/model/EvaluationRunRecord.java`; include stable IDs, case categories, applicable dimensions, FR/SC references, separate validation outcomes, timestamps, and repair links. Trace: FR-018–FR-021, SC-005, SC-006, E01–E29.
- [X] T036 [US3] Freeze a structured dataset in `part-1b-reminder-agent/harness/config/evaluation-cases.json` containing every specification case E01, E02, E03, E04, E05, E06, E07, E08, E09, E10, E11, E12, E13, E14, E15, E16, E17, E18, E19, E20, E21, E22, E23, E24, E25, E26, E27, E28, and E29 with unchanged expected behavior, applicable dimensions (I/T/D/C/S or N/A), and FR/SC traceability; do not omit or soften difficult cases. Trace: FR-018–FR-020, SC-005, E01–E29.
- [X] T037 [P] [US3] Add dataset-loader tests for exactly 29 unique stable IDs, required FR/SC references, category coverage, and explicit score applicability in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/evaluation/EvaluationDatasetLoaderTest.java`. Trace: FR-018, FR-019, SC-005, E01–E29.
- [ ] T038 [P] [US3] Add score-calculation tests for the five 0/1/2 dimensions, N/A exclusion from numerator and denominator, one-decimal unweighted mean, and the 90.0 acceptance boundary in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/evaluation/ScoreCalculatorTest.java`. Trace: FR-019, SC-001, SC-005.
- [ ] T039 [US3] Implement the per-case score and aggregate calculation in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/ScoreCalculator.java` using `100 × sum(applicable ratings) ÷ (2 × applicable dimension count)` and the unweighted mean of scored model-quality cases rounded to one decimal. Trace: FR-019, SC-001, SC-005.
- [ ] T040 [P] [US3] Add semantic-quality rubric tests for expected intent, target/task, date/time, clarification, and non-invention/safety, retaining evaluator identity and rationale for subjective judgments, in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/evaluation/SemanticQualityEvaluatorTest.java`. Trace: FR-017, FR-019, SC-001, SC-004.
- [ ] T041 [US3] Implement the semantic evaluator and documented human-reviewable 0/1/2 rubric in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/SemanticQualityEvaluator.java`; never score N/A dimensions or replace human judgment with a second opaque model. Trace: FR-017–FR-019, SC-001, SC-005.

### Evaluation Pipeline and Fault-Injection Tests

- [ ] T042 [P] [US3] Add pipeline tests that verify one result for every E01–E29 ID, three independently reported validation layers, 90.0 minimum semantic score, 100% structural compliance, zero invariant failures, and blocking on unresolved operational failures in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/evaluation/EvaluationPipelineTest.java`. Trace: FR-017–FR-023, SC-001, SC-002, SC-006, SC-008, E01–E29.
- [ ] T043 [US3] Implement the single-run evaluation orchestrator in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/EvaluationPipeline.java`; process E01–E25 as model-quality cases and E26–E29 as isolated probes, keep structural/invariant/semantic findings separate, and mark actual provider failures incomplete and non-acceptable. Trace: FR-017–FR-023, SC-001, SC-002, SC-005, SC-008, E01–E29.
- [ ] T044 [P] [US3] Add isolated fault-injection tests using fake provider/validator components in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/evaluation/OperationalProbeTest.java`; cover malformed provider JSON E26, network/timeout failure E27, invalid date E28, and missing key E29, with no live API call and no fabricated agent response. Trace: FR-020, FR-023–FR-025, SC-002, SC-006, SC-008, E26–E29.
- [ ] T045 [US3] Implement isolated operational/validator probe execution in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/OperationalProbeRunner.java`; keep probe pass/fail separate from model-quality scores and require the expected safe failure behavior. Trace: FR-020, FR-023–FR-025, SC-006, SC-008, E26–E29.
- [ ] T046 [P] [US3] Add evidence-writer tests for one record per case, unique run IDs, initial/re-evaluation separation, full layer/scoring metadata, redaction of API-key/header values, and no overwrite in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/evaluation/EvidenceReporterTest.java`. Trace: FR-020, FR-021, FR-024, SC-006, SC-008, E26–E29.
- [ ] T047 [US3] Implement structured per-case JSONL and run-summary writing in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/EvidenceReporter.java`; store actual inputs/context, expected/actual result or operational failure, attempt metadata, three validation layers, applicability/ratings, aggregate/threshold, timestamps, repair links, and redacted diagnostics without secrets under either `part-1b-reminder-agent/harness/logs/failed/<run-id>/` or `part-1b-reminder-agent/harness/logs/passed/<run-id>/` according to observed results. Trace: FR-020, FR-021, FR-023, FR-024, SC-006, SC-008.
- [ ] T048 [US3] Implement case-linked actionable repair feedback from failed results in `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/RepairFeedbackReporter.java`; identify case IDs, unmet requirements, failure layer, expected/actual behavior, and rerun needs without inventing results. Trace: FR-020, FR-021, SC-006, E01–E29.
- [ ] T049 [US3] Add the evaluation command in `part-1b-reminder-agent/harness/scripts/run-evaluation.sh` to load the frozen dataset, invoke the single pipeline, and write unique run evidence; fail closed on missing configuration, dataset mismatch, structural violation, invariant failure, or unexpected operational failure. Trace: FR-018–FR-025, SC-001, SC-002, SC-005, SC-006, SC-008, E01–E29.
- [ ] T050 [P] [US3] Add MVC safety tests for the valid reminder embedded in adversarial text and unsupported malicious instructions in `part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/integration/ReminderProposalSafetyTest.java`; cover E19, E21, E22 and assert no hidden-instruction disclosure, extra intent, format bypass, or mutation. Trace: FR-004, FR-005, FR-014–FR-016, SC-002, SC-004.

### Evidence, Repair, and Re-Evaluation

- [ ] T051 [US3] After implementation and local provider configuration, execute the evaluation command for all 29 cases (E01–E25 model-quality plus E26–E29 isolated probes) using `part-1b-reminder-agent/harness/scripts/run-evaluation.sh`; do not start model-quality evaluation if required credentials/model settings are unavailable, and record actual outcomes under either `part-1b-reminder-agent/harness/logs/failed/<run-id>/` or `part-1b-reminder-agent/harness/logs/passed/<run-id>/` according to observed results. Trace: FR-018–FR-025, SC-001, SC-002, SC-005, SC-006, SC-008, E01–E29.
- [ ] T052 [US3] Review actual failed-run evidence and repair feedback, diagnose root causes, and make only evidence-linked repairs in the relevant files, including `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/provider/GeminiProviderAdapter.java`, `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/validation/ReminderProposalInvariantValidator.java`, `part-1b-reminder-agent/backend/src/main/java/com/assignment/reminderagent/evaluation/EvaluationPipeline.java`, or their focused tests as indicated by the evidence; retain the initial run and link each repair to its case/FR. Trace: FR-020, FR-021, SC-006, E01–E29.
- [ ] T053 [US3] Re-run the complete frozen E01–E29 suite after repair and preserve it under either the distinct `part-1b-reminder-agent/harness/logs/failed/<rerun-id>/` or `part-1b-reminder-agent/harness/logs/passed/<rerun-id>/` according to observed results; acceptance requires semantic aggregate at least 90.0/100, 100% structural compliance, zero invariant failures, and no unresolved operational failures. Trace: FR-019–FR-023, SC-001, SC-002, SC-006, SC-008, E01–E29.
- [ ] T054 [US3] Verify evidence completeness and redaction for the initial and full re-evaluation runs in `part-1b-reminder-agent/harness/scripts/verify-evidence.sh`; confirm all 29 case IDs, separate operational/probe outcomes, case-linked feedback, thresholds, repair links, no secrets, and no overwritten run. Trace: FR-020–FR-024, SC-006, SC-008, E01–E29.

**Checkpoint**: Evaluation work is complete only after full 29-case evidence,
all hard gates pass, failures are repaired and fully re-evaluated, and a human
reviews the resulting evidence. Isolated probes are never counted as model
successes.

---

## Phase 6: Polish and Final Human Review

**Purpose**: Verify implementation scope, requirements, repeatable validation,
and the human decision record after all evidence exists.

- [ ] T055 Run the documented build, unit, contract, and integration checks from `specs/002-natural-language-reminder-agent/quickstart.md` and `part-1b-reminder-agent/backend/pom.xml`; do not repeat or imply a live-provider smoke test or full evaluation in this task, and preserve actual outcomes in a unique `part-1b-reminder-agent/harness/logs/failed/<run-id>/validation-summary.json` or `part-1b-reminder-agent/harness/logs/passed/<run-id>/validation-summary.json` according to observed results. Trace: FR-017–FR-021, SC-001–SC-006.
- [ ] T056 Review the Part 1B source and dependency tree for separation from Part 1A, absence of reminder persistence/read/mutation endpoints, API-key/header/log/evidence redaction, and no unverified model hardcoding; document findings in `part-1b-reminder-agent/harness/README.md`. Trace: FR-003, FR-015, FR-016, FR-020, FR-024, SC-007, SC-008.
- [ ] T057 Only after complete initial and post-repair evidence is available, have a human record `ACCEPT` or `REJECT`, rationale, reviewer, evidence/run IDs, and known limitations in `part-1b-reminder-agent/harness/logs/failed/<final-run-id>/acceptance.json` for REJECT or `part-1b-reminder-agent/harness/logs/passed/<final-run-id>/acceptance.json` for ACCEPT; do not infer acceptance from aggregate score alone. Trace: FR-022, SC-007, SC-008, E01–E29.

---

## Dependencies and Execution Order

### Phase Dependencies

- **Pre-implementation gate**: After generating this task list and before T001,
  run `/speckit.analyze` against `spec.md`, `plan.md`, and `tasks.md`; resolve
  any critical consistency gaps before implementation.
- **Setup (Phase 1)**: No dependencies; creates the isolated backend and
  harness.
- **Foundational (Phase 2)**: Depends on Setup and blocks all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundation; establishes the shared
  request-to-proposal pipeline and P1 CREATE/CLARIFY behavior.
- **User Story 2 (Phase 4)**: Depends on the shared P1 provider/parser/
  validation pipeline; adds RESCHEDULE and SNOOZE without introducing
  persistence.
- **User Story 3 (Phase 5)**: Depends on the shared pipeline and all supported
  intents; then adds frozen dataset, scoring, evaluation, fault probes,
  evidence, repair, and full re-evaluation.
- **Polish and human review (Phase 6)**: Depends on all implementation and
  evidence tasks, especially T051–T054.

### User Story Dependencies

- **US1 (P1)**: Starts after Foundation; independently verifies CREATE and
  safe CLARIFY using a fake provider.
- **US2 (P2)**: Depends on the shared request/provider/validation pipeline
  from US1; independently verifies RESCHEDULE and SNOOZE.
- **US3 (P3)**: Depends on US1 and US2 for complete user-visible behavior;
  fault probes remain offline and separately scored.

### Key Task Dependencies

- T001 precedes T002–T004; T002–T003 precede application tests.
- T005–T010 complete before story implementation.
- T011–T017 are authored before their corresponding US1 implementation
  tasks; T018–T028 implement those behaviors in dependency order.
- T030–T031 precede T032–T034.
- T035–T050 build the evaluation model, dataset, scoring, pipeline, probes,
  and evidence before T051 can execute an evaluation.
- T052 requires actual failed-run evidence from T051; T053 requires T052;
  T054 requires both preserved runs. T057 is the final human-review task and
  is blocked until T051–T056 have produced complete evidence and review
  materials.

### Parallel Opportunities

- T001 and T004 can proceed in separate paths; T002 follows T001.
- After project setup, T005–T008 can proceed in separate source files;
  T009/T010 provide independent validator and provider test support.
- In US1, T011–T017 target separate test files and can be authored in parallel
  before implementation.
- In US2, T030 and T031 target separate test files and can be authored in
  parallel.
- In US3, T037, T038, T040, T042, T044, T046, and T050 target separate test
  files and can be authored in parallel after the shared evaluation types and
  test support are ready.
- T051, T052, T053, T054, and T057 are intentionally sequential; evidence
  review and human acceptance cannot be parallelized with evaluation or repair.

## Parallel Example: User Story 1

```text
Task: T011 Request/time-context unit tests in part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderRequestValidatorTest.java
Task: T012 API contract tests in part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/contract/ReminderProposalContractTest.java
Task: T013 Prompt separation tests in part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ReminderPromptBuilderTest.java
Task: T014 Provider output parsing tests in part-1b-reminder-agent/backend/src/test/java/com/assignment/reminderagent/unit/ModelOutputParserTest.java
```

## Implementation Strategy

### MVP First

1. Complete Setup and Foundation.
2. Write and verify the failing-first P1 contract/unit/integration tests.
3. Implement the CREATE/CLARIFY provider-backed path through the strict schema
   and invariant gates.
4. Validate US1 with the fake provider and stop at its checkpoint for review.

US1 is the first independently testable increment, not final assignment
acceptance. RESCHEDULE, SNOOZE, all 29 evaluation cases, the acceptance gates,
evidence/re-evaluation, and human review remain mandatory.

### Incremental Delivery

1. Complete Setup + Foundation.
2. Deliver US1 CREATE/CLARIFY and verify it independently.
3. Deliver US2 RESCHEDULE/SNOOZE and verify it independently.
4. Deliver US3 evaluation, fault-injection, and evidence pipeline.
5. Execute all 29 cases only after implementation and provider configuration;
   preserve actual failure evidence, repair, and re-run the full set.
6. Request human ACCEPT/REJECT only after complete evidence and known
   limitations are available.

## Acceptance Gates and Open Dependencies

- Preserve the fixed semantic threshold of **90.0/100**, **100% structural
  compliance**, **zero invariant failures**, and zero unresolved operational
  failures. No score can offset a failed hard gate.
- The exact JSON Schema validator release remains dependent on compatibility
  testing with Spring Boot 3.3.5's managed Jackson; do not select an unverified
  release or override Jackson versions without evidence.
- Gemini model access, quota, structured-output availability for the
  configured model, and local `GEMINI_API_KEY` configuration remain
  unverified. If unavailable, record a blocked/incomplete evaluation rather
  than fabricated output or a successful CLARIFY.
- No evaluation, build, provider call, or implementation result is asserted by
  this task list.

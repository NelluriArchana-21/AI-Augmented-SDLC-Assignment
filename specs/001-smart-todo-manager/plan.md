# Implementation Plan: Smart Todo Manager

**Branch**: `001-smart-todo-manager` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-smart-todo-manager/spec.md`

## Summary

This feature delivers a simple, deterministic task-management service whose business logic lives under `part-1a-smart-todo/backend/`. The specification, design artifacts, and Spec Kit records remain under `specs/001-smart-todo-manager/`. The implementation is intentionally limited to a single Spring Boot service, JSON-backed persistence, explicit validation, and an independent harness that checks quality, security, API behavior, and persistence integrity without introducing databases, authentication, or extra infrastructure.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot, Maven, JUnit 5, JSON serialization support, Spotless, SpotBugs with FindSecBugs, and OWASP Dependency-Check

**Storage**: File-backed JSON persistence with a configurable path; no database

**Testing**: JUnit 5 unit and integration tests, persistence/restart tests, and an independent validation harness using Spotless, SpotBugs with FindSecBugs, and OWASP Dependency-Check

**Target Platform**: Linux server runtime for the assignment environment

**Project Type**: web-service

**Performance Goals**: Keep the design lightweight and deterministic for an individual assignment; optimize for correctness, stable ordering, and reproducible validation rather than throughput.

**Constraints**: Must validate task state transitions, preserve insertion order for equal priorities, never silently discard existing data, and enforce clear REST semantics with HTTP 400/404/200/201/204 responses.

**Scale/Scope**: Single-user task manager with a small file-backed collection; no multi-user concurrency or distributed coordination in v1.

**Storage Configuration**: A configurable property such as `task.storage.path` will determine the JSON file location. The default runtime path must be outside application source and test resources. Tests must use temporary directories/files and must not share or mutate runtime task data. If the configured file is missing at startup, initialize an empty collection; if it exists but is unreadable or invalid, surface the failure without replacing or discarding it.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Pass: Spec-first delivery is satisfied because the feature is captured in the active specification before implementation.
- Pass: Deterministic execution is satisfied because task fields, status transitions, persistence rules, sorting, filtering, and HTTP contracts are explicit and testable.
- Pass: Evidence-based validation is satisfied because the harness separately requires formatting/static analysis, security scanning, unit tests, API contract tests, restart tests, and structured failure logging.
- Pass: Security constraints are satisfied because no authentication, secrets, or unnecessary infrastructure are introduced.
- Pass: Simplicity and maintainability are preserved by keeping the solution to one backend service with file-based persistence and a separate validation harness.

## Project Structure

### Documentation and Spec Artifacts

```text
specs/001-smart-todo-manager/
├── plan.md                  # This file
├── research.md              # Decision log and rationale
├── data-model.md            # Data contract and validation model
├── quickstart.md            # Validation scenarios and harness flow
├── contracts/
│   └── task-api.md          # REST contract and HTTP semantics
├── checklists/
│   └── requirements.md      # Requirement checklist
├── spec.md                 # Approved feature specification
└── tasks.md                # Phase 2 task breakdown; planning artifact, not application code
```

### Application Code and Validation Harness

```text
part-1a-smart-todo/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── ... application packages: config, controller, model, repository, service, validation
│       │   └── resources/
│       │       └── application.properties
│       └── test/
│           └── java/
│               └── ... unit and integration test packages
├── harness/
│   ├── scripts/
│   │   ├── run-validation.sh
│   │   ├── summarize-failures.sh
│   │   └── validate-api-blackbox.sh
│   ├── config/
│   └── logs/
│       ├── failed/
│       └── passed/
├── harness-log.md          # Populated from actual validation evidence after revalidation
└── README.md
```

**Structure Decision**: The service implementation is isolated under `part-1a-smart-todo/backend/` and uses standard Maven paths, including `backend/pom.xml` and test packages below `src/test/java/`. The specification and Spec Kit artifacts remain under `specs/001-smart-todo-manager/`. The independent validation harness is separated under `part-1a-smart-todo/harness/`. Runtime JSON is not source or fixture data: its configured default is outside the repository, and tests use temporary storage locations that are removed after each run.

## Independent Validation Harness

The validation harness will run separately from application business logic. It invokes each check as an independent step, records its result, and continues to the remaining checks after a failure so one defect does not hide other failures. The harness must cover the following categories:

- Java formatting: Spotless checks for consistent Java formatting.
- Static analysis and SAST: SpotBugs with FindSecBugs for code-quality and security findings.
- Dependency vulnerability scanning: OWASP Dependency-Check.
- Unit tests: JUnit 5 tests for title validation, tag normalization, duplicate-tag handling, filtering, priority sorting with stable insertion-order ties, status transitions, and persistence behavior.
- REST API integration and contract tests: JUnit 5 tests for create/list/get/update/delete operations, client-supplied ID rejection, immutable IDs, invalid transitions, missing resources, response codes, and unchanged stored state after rejected updates.
- External HTTP black-box API validation: `part-1a-smart-todo/harness/scripts/validate-api-blackbox.sh` sends requests to a running service and independently checks response codes and JSON, CRUD, invalid input, immutable IDs, rejected transition state preservation, combined filters, priority order, and stable insertion-order ties. These checks complement rather than rely solely on application integration tests.
- Persistence and restart tests: JUnit 5 tests for JSON read/write, restart recovery, ordering retention, atomic replacement where supported, and explicit read/write failures.
- Structured failure logging: each check must record timestamp, check name, failure details, and overall outcome so evidence is preserved for repair and review.

Each check result must be recorded in a structured format such as JSONL with timestamp, check name, outcome, and failure details when applicable. Store failed and passing run evidence in separate `harness/logs/failed/` and `harness/logs/passed/` locations, using unique run identifiers; later runs must not overwrite earlier evidence.

## Repair and Feedback Cycle

Run the independent harness once against the implemented feature before repair. Preserve that initial run's actual results and any failures; do not fabricate failure evidence when a check passes. The harness must provide any failure evidence directly to the AI coding agent in a structured, reviewable form. Each failed check should include at least:

- timestamp
- check name and tool
- failing component or endpoint
- failure detail and expected outcome
- actual outcome
- final status (passed, failed, or skipped)

The repair cycle will proceed as follows:

1. Run the initial independent harness, including external HTTP black-box API validation.
2. Preserve the actual initial outcomes and failure evidence, if any, in a unique failed-run or passing-run artifact.
3. Feed the observed failures to the AI coding agent, identify their root causes, and apply only the minimal justified repairs.
4. Rerun the complete validation suite after repair, including all independent checks and black-box API scenarios; do not rerun only the previously failing check.
5. Preserve initial and post-repair results separately under the failed/pass log locations. Never overwrite initial evidence.
6. Only after both runs and any repairs are complete, populate `part-1a-smart-todo/harness-log.md` with actual outcomes, evidence references, repair details, and the final human `ACCEPT` or `REJECT` decision.

Do not prefill the assignment log with assumed results. Human acceptance is a final recorded decision based on the complete revalidation evidence.

## Persistence Design

- The data file path will be configurable via a property such as `task.storage.path`.
- The default runtime JSON path will be outside the repository so application execution cannot create tracked source or fixture data.
- Every test that reads or writes task data will configure a fresh temporary directory/file and clean it up after the test; tests will not use the runtime path or committed JSON fixtures.
- The JSON file is the authoritative persisted state for the task collection and must survive restarts.
- The repository must attempt to read the existing JSON file at startup; if it exists and is valid JSON, the data must be loaded. If the file does not exist, it may initialize an empty collection.
- File write operations should use an atomic pattern where feasible: write to a temporary file, flush and close, then replace the target file with the new version. Rename-based replacement reduces the risk of partial writes.
- Read/write failures must be explicit and surfaced to the caller or logs; the implementation must not silently discard data or ignore invalid file content.
- If the file is unreadable or contains invalid JSON, the system should fail safely and record the failure rather than deleting the old file or continuing with silent data loss.

## API Contract Summary

### Create Task

- POST `/api/tasks`
- Success: 201 Created
- Failure: 400 Bad Request for invalid input
- Validation: reject client-supplied task IDs; the server generates a UUID and returns it in the created task representation.

### Get Task List

- GET `/api/tasks`
- Success: 200 OK
- Filters: `status`, `priority`, `tag`
- Behavior: combined with AND semantics; invalid status or priority filter values return HTTP 400 and do not modify persisted state; sorting is HIGH, MEDIUM, LOW with insertion-order stability for equal priorities.

### Get Single Task

- GET `/api/tasks/{id}`
- Success: 200 OK
- Failure: 404 Not Found

### Update Task

- PUT `/api/tasks/{id}`
- Success: 200 OK
- Failure: 400 Bad Request or 404 Not Found
- Rules: task IDs are immutable; client attempts to change the ID are rejected; a same-status update is accepted as a valid no-op and returns HTTP 200 with the unchanged current task representation; invalid status transitions return HTTP 400; the stored task state remains unchanged after a rejected update.

### Delete Task

- DELETE `/api/tasks/{id}`
- Success: 204 No Content
- Failure: 404 Not Found

## Deterministic Behavior

- Equal-priority tasks MUST preserve their original insertion order as required by FR-021, including after reload from JSON.
- Tags are trimmed, normalized to lowercase, and matched case-insensitively; duplicate tags are deduplicated while preserving first-seen order.
- Priority ordering remains `HIGH`, then `MEDIUM`, then `LOW`.

## Testing Strategy

The implementation must be covered by a small but complete JUnit 5 suite for:

- normal valid operations
- title validation, including blank titles and the 120-character boundary
- tag normalization, duplicate-tag handling, and tag filtering
- combined status/priority/tag filtering and priority sorting with stable insertion-order ties
- valid and invalid status transitions, including same-status no-op updates returning HTTP 200 with the unchanged task representation, with stored state unchanged after rejected updates
- invalid status or priority filter values on GET /api/tasks returning HTTP 400 without mutating persisted state
- REST contracts for client-supplied IDs on create, immutable IDs on update, HTTP response codes, and missing resources
- persistence read/write failures, invalid JSON, atomic replacement where feasible, and restart recovery
- empty optional descriptions and other specified normal, boundary, and invalid-input cases

The scope remains intentionally narrow and appropriate for an individual academic assignment: no databases, no authentication, no microservices, and no unnecessary infrastructure.

## Complexity Tracking

No constitution violations require justification; the design remains intentionally simple, evidence-driven, and bounded to the assignment requirements.

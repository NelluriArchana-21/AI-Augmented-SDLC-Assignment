# Research: Smart Todo Manager

## Decision Summary

The application will be implemented as a single Spring Boot Maven service under `part-1a-smart-todo/backend/`, with `backend/pom.xml` and standard Maven source roots. The approved specification and Spec Kit artifacts remain under `specs/001-smart-todo-manager/`. Runtime task data uses a configurable JSON file outside the repository by default, while tests use temporary storage. The validation harness runs separately under `part-1a-smart-todo/harness/`.

## Key Decisions

### Decision: Server-generated UUIDs for task identity
- The server creates the UUID during task creation.
- Clients are not allowed to submit an ID on create requests.
- The UUID remains immutable for updates.
- Rationale: this makes task identity stable, avoids client-side spoofing, and aligns with the specification’s contract for deterministic validation.
- Alternatives considered: client-supplied IDs or auto-incrementing integers.
  - Rejected because client-driven IDs create trust issues and non-portable IDs; integer IDs would not be as robust for future multi-instance or distributed scenarios.

### Decision: JSON file-backed persistence with configurable storage path
- The task collection is stored in a JSON file at a path configured by a property such as `task.storage.path`.
- The original insertion order is preserved in the persisted file so equal-priority tasks remain stable after restart.
- Safe or atomic writes are required where feasible: write to a temporary file and replace the target file once the write is complete.
- Rationale: this satisfies the assignment constraint for file-backed persistence and provides a clear, configurable, testable storage contract.
- Alternatives considered: database storage or memory-only state.
  - Rejected because the requirement explicitly forbids a database and persistence across restarts is mandatory.

### Decision: Explicit enum validation and transition enforcement
- Task priority values are limited to LOW, MEDIUM, and HIGH.
- Task status values are limited to TODO, IN_PROGRESS, and COMPLETED.
- Allowed transitions are: TODO → IN_PROGRESS, IN_PROGRESS → COMPLETED, COMPLETED → TODO, and same-status no-op updates.
- Invalid transitions return HTTP 400 and the existing stored task remains unchanged.
- Rationale: deterministic state transitions reduce ambiguity and improve testability and harness validation.
- Alternatives considered: permissive status strings or automatic conversions.
  - Rejected because invalid transitions would create inconsistent task state and unreliable validation outcomes.

### Decision: REST filtering via query parameters with AND semantics
- GET /api/tasks accepts `status`, `priority`, and `tag` filters.
- When multiple filters are supplied, they are combined with AND logic.
- Rationale: this is the clearest contract for list filtering and supports deterministic harness assertions.
- Alternatives considered: POST body filtering or OR semantics.
  - Rejected because query parameters are simpler for GET operations and align with REST conventions.

### Decision: Priority sort order with stable tie-breaker
- The ordering is HIGH, MEDIUM, LOW.
- Tasks with identical priority retain original insertion order.
- Rationale: this creates a consistent list view and preserves ordering across restarts.
- Alternatives considered: alphabetical sort or timestamp-based tie-breakers.
  - Rejected because the requirement explicitly requires priority precedence and insertion-order stability.

### Decision: Deterministic tag normalization and duplicate handling
- Tags are normalized by trimming whitespace and lowercasing before comparison.
- Matching is case-insensitive and whitespace-tolerant.
- Duplicate tags are deduplicated while preserving first-seen order.
- Rationale: deterministic tag semantics reduce ambiguity in filter behavior and allow stable, repeatable validation.
- Alternatives considered: raw-string comparison or preserving duplicates without normalization.
  - Rejected because raw comparison makes the behavior order-sensitive and harder to test under different input cases.

### Decision: Independent harness validation and structured failure logs
- The harness runs Spotless formatting, SpotBugs with FindSecBugs static/security analysis, OWASP Dependency-Check, and JUnit 5 unit/integration tests as separate checks.
- JUnit 5 coverage targets title validation, tag normalization and duplicate handling, filtering, priority sorting and stable ties, status transitions, REST contracts, and persistence/restart behavior.
- Each check records timestamp, check name, outcome, and failure details in structured results. Failed and passing run evidence is preserved separately and is never overwritten.
- Failures trigger a repair cycle in which the AI agent receives the evidence, fixes the root cause, and reruns the full validation suite.
- Rationale: this matches the project constitution’s requirements for evidence-based validation and independent verification.
- Alternatives considered: implementation-only testing without harness validation.
  - Rejected because the assignment and constitution require independent evidence and explicit repair behavior.

### Decision: Isolated runtime and test JSON data
- Runtime JSON data is not stored under application source or committed as a test fixture; the default storage path is outside the repository and remains configurable.
- Each test uses a fresh temporary storage location and removes it after the test.
- Rationale: tests remain independent and repeatable, and normal application runs cannot accidentally commit task state.
- Alternatives considered: a checked-in JSON file shared by runtime and tests.
  - Rejected because mutable data would make tests order-dependent and risk committing user/runtime state.

## Open Questions Resolved from the Specification

- Task title validation: required, non-blank, max 120 chars.
- Task description: optional.
- Tag support: zero or more values, multiple tags allowed.
- Response contract: valid create/list/get/update/delete operations use the HTTP codes in the specification.
- Persistence failure handling: silent data loss is not acceptable; errors must be logged and surfaced through repair and revalidation.
- Rejected updates: the stored task must remain unchanged when validation fails.

## Best Practices Applied

- Prefer explicit validation rules over silent coercion.
- Preserve deterministic ordering in persisted data.
- Keep API contracts narrow and testable.
- Separate application logic from validation, scripts, and logs.
- Treat validation and harness checks as gate conditions, not optional extras.

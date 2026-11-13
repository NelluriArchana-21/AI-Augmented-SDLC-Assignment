---

description: "Task list for Smart Todo Manager implementation"
---

# Tasks: Smart Todo Manager

**Input**: Design documents from `specs/001-smart-todo-manager/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/task-api.md](contracts/task-api.md), [quickstart.md](quickstart.md)

**Tests**: Required by FR-023, FR-024, and the project constitution. Add JUnit 5 tests before implementing each story's behavior and verify that the tests fail for the intended reason first.

**Organization**: Tasks are grouped by the three prioritized user stories in `spec.md`. Application paths use the planned Java package root `com/assignment/smarttodo` beneath `part-1a-smart-todo/backend/src/`.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Establish the Java 21 Spring Boot Maven project and the separate harness location.

- [X] T001 Create the Maven project descriptor and standard source roots in `part-1a-smart-todo/backend/pom.xml`, `part-1a-smart-todo/backend/src/main/java/`, `part-1a-smart-todo/backend/src/main/resources/`, and `part-1a-smart-todo/backend/src/test/java/`.
- [X] T002 Configure Java 21, Spring Boot, and JUnit 5 dependencies plus Spotless, SpotBugs with FindSecBugs, and OWASP Dependency-Check plugins in `part-1a-smart-todo/backend/pom.xml`.
- [X] T003 [P] Document the application/harness separation, validation categories, and evidence locations in `part-1a-smart-todo/harness/README.md`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Define shared task types, persistence configuration, and API error mapping required by all user stories.

- [X] T004 [P] Define `Task`, `Priority`, and `Status` under `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/model/` with UUID identity, required title (non-blank and at most 120 characters), optional description, allowed enum values, and zero or more tags.
- [X] T005 Configure `task.storage.path` with a default outside the repository in `part-1a-smart-todo/backend/src/main/resources/application.properties`.
- [X] T006 Define the task persistence interface in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/repository/TaskRepository.java` so service code can use JSON storage without database dependencies.
- [X] T007 [P] Map invalid input to HTTP 400, missing tasks to HTTP 404, and persistence failures to explicit logged errors in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/exception/ApiExceptionHandler.java`.

**Checkpoint**: Maven project, shared domain types, storage configuration, and error mapping are ready for story implementation.

---

## Phase 3: User Story 1 - Create and manage tasks with valid task data (Priority: P1) - MVP

**Goal**: Create, retrieve, update, and delete tasks through the REST API while enforcing identity, title, enum, and status-transition rules.

**Independent Test**: Using a temporary JSON storage path, create and retrieve a valid task; verify the server-generated UUID, reject a client-supplied ID and invalid title/enums/transitions with HTTP 400, verify a rejected update leaves stored state unchanged, then update through valid transitions and delete with HTTP 204.

### Tests for User Story 1

- [X] T008 [P] [US1] Add JUnit 5 unit tests for title validation, omitted description, description provided as an empty string, description provided with a valid value, valid enum values, generated immutable UUIDs, allowed status transitions, same-status no-ops returning HTTP 200 with the unchanged current task representation, and rejected transitions in `part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/unit/TaskServiceTest.java`.
- [X] T009 [P] [US1] Add JUnit 5 REST contract tests for POST/GET-by-ID/PUT/DELETE response codes, client-supplied ID rejection, immutable IDs, same-status no-op handling, missing resources, and unchanged stored state after rejected updates in `part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/integration/TaskApiIntegrationTest.java`.

### Implementation for User Story 1

- [X] T010 [US1] Implement title, enum, and request-ID validation in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/validation/TaskValidator.java`.
- [X] T011 [US1] Implement JSON task loading and saving through the configured path, preserving collection insertion order and using temporary-file replacement where feasible, in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/repository/JsonTaskRepository.java`.
- [X] T012 [US1] Implement task creation with server-generated UUIDs, retrieval, update with immutable IDs and allowed transitions, deletion, and no mutation after rejected updates in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/service/TaskService.java`.
- [X] T013 [US1] Implement POST `/api/tasks`, GET `/api/tasks/{id}`, PUT `/api/tasks/{id}`, and DELETE `/api/tasks/{id}` with the contract response codes in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/controller/TaskController.java`.

**Checkpoint**: User Story 1 is independently testable against a temporary file-backed task collection.

---

## Phase 4: User Story 2 - Prioritize and organize work with filtering and sorting (Priority: P2)

**Goal**: Normalize and deduplicate tags, combine supplied filters with AND semantics, and return tasks in deterministic priority and insertion order.

**Independent Test**: Create tasks across statuses, priorities, and tags; verify trimmed case-insensitive tag matching, first-seen duplicate-tag retention, AND behavior across filters, HIGH/MEDIUM/LOW sorting, and stable insertion order for equal priorities.

### Tests for User Story 2

- [X] T014 [P] [US2] Add JUnit 5 unit tests for tag trimming/lowercasing, duplicate-tag removal while preserving first-seen order, combined status/priority/tag filtering, priority order, and stable equal-priority ties in `part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/unit/TaskQueryServiceTest.java`.
- [X] T015 [P] [US2] Add JUnit 5 REST integration tests for individual and combined query filters, invalid status or priority filter values returning HTTP 400 without altering persisted state, normalized tag matching, priority sorting, and stable ties in `part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/integration/TaskFilteringIntegrationTest.java`.

### Implementation for User Story 2

- [X] T016 [US2] Implement trimming, lowercase normalization, and first-seen duplicate removal for task tags in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/validation/TagNormalizer.java`.
- [X] T017 [US2] Implement the canonical task filtering behavior defined by FR-020: status/priority/tag filters with AND semantics and HIGH/MEDIUM/LOW sorting that retains collection insertion order for ties in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/service/TaskQueryService.java`.
- [X] T018 [US2] Add GET `/api/tasks` query parameters `status`, `priority`, and `tag`, validate filter values so invalid status or priority parameters return HTTP 400 without modifying persisted state, and return the filtered stable-sorted results in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/controller/TaskController.java`.

**Checkpoint**: User Stories 1 and 2 work together; filtering and ordering are deterministic and independently verified.

---

## Phase 5: User Story 3 - Persist tasks reliably and validate behavior through independent checks (Priority: P3)

**Goal**: Verify restart recovery and persistence failure behavior, then run independent quality, security, API, and persistence checks with traceable evidence.

**Independent Test**: Configure a temporary JSON file, save tasks, restart/reload the service, and verify data and insertion order remain intact. Exercise unreadable/invalid JSON and write failures to ensure errors are surfaced without silently replacing existing data. Run every harness check, including external HTTP black-box API validation, and confirm structured results are preserved separately for failed and passing runs and accurately summarized in the assignment harness log.

### Tests for User Story 3

- [X] T019 [P] [US3] Add JUnit 5 persistence tests using a fresh temporary directory for each case, covering missing files, valid JSON reload, malformed/unreadable files, write failures, atomic replacement behavior, and preservation of existing data in `part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/unit/JsonTaskRepositoryTest.java`.
- [X] T020 [P] [US3] Add a JUnit 5 restart integration test using temporary storage to verify task recovery and stable insertion order after application reload in `part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/integration/TaskPersistenceIntegrationTest.java`.

### Implementation and Harness for User Story 3

- [X] T021 [US3] Ensure JSON read/write failures and invalid stored JSON are surfaced and logged without deleting, truncating, or replacing existing task data in `part-1a-smart-todo/backend/src/main/java/com/assignment/smarttodo/repository/JsonTaskRepository.java`.
- [X] T022 [US3] Define independent harness checks for `mvn -f part-1a-smart-todo/backend/pom.xml spotless:check`, `spotbugs:check`, OWASP Dependency-Check, and JUnit 5 tests in `part-1a-smart-todo/harness/config/validation.yml`.
- [X] T023 [US3] Implement the harness runner in `part-1a-smart-todo/harness/scripts/run-validation.sh` so every configured check runs independently, later checks still run after failures, and each result records timestamp, check name/tool, outcome, and failure details.
- [X] T024 [US3] Store unique failed-run and passing-run evidence separately under `part-1a-smart-todo/harness/logs/failed/` and `part-1a-smart-todo/harness/logs/passed/`, and produce an AI-readable failure summary with expected/actual outcomes in `part-1a-smart-todo/harness/scripts/summarize-failures.sh`.
- [X] T025 [US3] Document the preserved initial validation evidence, observed failure details and root cause, required repair or no-repair rationale, complete-suite rerun, passing evidence, and human acceptance steps in `part-1a-smart-todo/harness/README.md`.
- [X] T026 [US3] Create `part-1a-smart-todo/harness/scripts/validate-api-blackbox.sh` to send external HTTP requests (for example with `curl` and `jq`) to the running Spring Boot service and independently assert response codes and JSON for CRUD behavior, invalid input rejection, immutable IDs, same-status no-op handling, invalid status transitions with unchanged stored state, invalid status or priority filter values returning HTTP 400 without altering state, combined filters, priority sorting, and stable equal-priority insertion order; configure the harness runner to execute it independently of JUnit integration tests.
- [X] T027 [US3] Run the initial independent harness after implementation and capture its actual per-check outcomes and failure details in a unique run artifact under `part-1a-smart-todo/harness/logs/failed/` or `part-1a-smart-todo/harness/logs/passed/`; do not assume or fabricate failures or results.
- [X] T028 [US3] Review the initial run artifact, provide actual failure evidence to the AI coding agent, identify each failure's root cause, and apply the smallest justified repair to the affected implementation or harness files; record repair references alongside the initial evidence under `part-1a-smart-todo/harness/logs/failed/`, or record that no repair was needed if the initial run passed.
- [X] T029 [US3] After T028, rerun the complete independent validation suite, including the black-box API script and quickstart scenarios, and preserve the actual post-repair per-check results as a separate unique artifact under `part-1a-smart-todo/harness/logs/passed/` or `part-1a-smart-todo/harness/logs/failed/`.
- [X] T030 [US3] Only after T027-T029 results are available, populate `part-1a-smart-todo/harness-log.md` with the actual initial outcome, any failure evidence and defect/root cause/repair (or that none were needed), the preserved initial and post-repair evidence references, complete revalidation results, and the final human `ACCEPT` or `REJECT` decision; do not invent or infer unobserved outcomes.

**Checkpoint**: Persistence survives restart, failure paths preserve data, and the independent harness produces separate, reviewable failure/pass evidence.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verify complete requirement coverage and the documented validation workflow without adding infrastructure or expanding feature scope.

- [X] T031 [P] Confirm all Java source and tests use standard Maven roots and all persistence tests use temporary storage in `part-1a-smart-todo/backend/src/`.
- [X] T032 Review implementation and harness scope against `specs/001-smart-todo-manager/spec.md` and `specs/001-smart-todo-manager/plan.md`, confirming no database, authentication, microservices, or unnecessary infrastructure was introduced.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; establishes Maven and harness locations.
- **Foundational (Phase 2)**: Depends on Setup; blocks all user stories.
- **User Story 1 (Phase 3)**: Depends on Foundational; delivers the MVP lifecycle and JSON-backed task operations.
- **User Story 2 (Phase 4)**: Depends on Foundational and the task collection/service from US1.
- **User Story 3 (Phase 5)**: Depends on the persisted task implementation from US1 and harness configuration. T026 depends on the running API from US1/US2 and T023. T027 depends on T023, T024, and T026. T028 depends on T027. T029 depends on T028 and reruns the complete suite after any repair. T030 depends on T024, T026, T027, T028, and T029; it must not be completed until initial-run and revalidation evidence exist.
- **Polish (Phase 6)**: Depends on all three user stories.

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational; no dependency on another story.
- **US2 (P2)**: Uses the task model and persisted collection delivered in US1.
- **US3 (P3)**: Verifies and hardens the JSON persistence introduced in US1, then validates the complete feature independently.

### Parallel Opportunities

- T003 can run alongside T001; T002 follows T001.
- T004, T005, and T007 can proceed in parallel after the project skeleton exists; T006 follows T004.
- Within each story, the listed unit and integration test tasks target separate files and can be authored in parallel before implementation.
- For US2, T016 and T017 target separate files after the US2 tests; T018 follows the query service.
- For US3, T019 and T020 can proceed in parallel; harness configuration and application persistence hardening can proceed independently after their prerequisites.
- T026 can be prepared after the API implementation and is executed as an independent external HTTP check. T027, T028, T029, and T030 are sequential: initial run, evidence-driven repair, full revalidation, then evidence log.
- User stories are delivered sequentially because US2 and US3 build on the task lifecycle and persisted collection from US1.

### Parallel Example: User Story 1

```text
Task: T008 unit validation/transition tests in part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/unit/TaskServiceTest.java
Task: T009 REST contract tests in part-1a-smart-todo/backend/src/test/java/com/assignment/smarttodo/integration/TaskApiIntegrationTest.java
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Setup and Foundational phases.
2. Write and run T008-T009, confirming they fail for the expected missing behavior.
3. Implement T010-T013 and verify User Story 1 independently using temporary JSON storage.
4. Stop for review at the P1 checkpoint before continuing to filtering or harness completion.

### Incremental Delivery

1. Deliver US1 task lifecycle and JSON-backed operations as the MVP.
2. Add US2 deterministic tag handling, filtering, and sorting; verify it independently.
3. Add US3 persistence failure/restart coverage and the independent validation feedback cycle.
4. Run the initial harness, preserve actual results, provide failures to the AI coding agent for root-cause repair, then run the complete suite again.
5. Populate the harness log from preserved evidence and record the human acceptance decision only after revalidation.

---

## Notes

- Every task uses the required checkbox, sequential ID, optional `[P]`, story label for story tasks, and an explicit file path.
- `[P]` marks work that can proceed independently without editing the same file or depending on incomplete tasks.
- JUnit 5 tests are included because FR-023 and FR-024 explicitly require them.
- Runtime JSON defaults outside the repository, and tests use temporary storage rather than committed mutable fixtures.

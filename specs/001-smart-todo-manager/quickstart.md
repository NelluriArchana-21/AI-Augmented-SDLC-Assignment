# Quickstart Validation Guide: Smart Todo Manager

## Prerequisites

- Java 21
- Maven
- Access to the repository root
- A writable task data file location for JSON persistence
- Separate harness directory for scripts, configuration, and logs

## Directory Layout

```text
part-1a-smart-todo/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   └── resources/
│       └── test/
│           └── java/       # unit and integration test packages
├── harness/
│   ├── scripts/
│   ├── config/
│   └── logs/
└── README.md

specs/001-smart-todo-manager/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── checklists/
```

## Validation Scenarios

### 1. Create a valid task
- Start the service.
- Send POST /api/tasks with a valid task payload without an id field.
- Expected outcome: HTTP 201 and the response contains a generated UUID.

### 2. Reject invalid task input
- Submit a task with a blank title, invalid priority, invalid status, or an explicit id on POST.
- Expected outcome: HTTP 400 and no partial task persisted.

### 3. Update with valid transitions only
- Change a task from TODO to IN_PROGRESS, then to COMPLETED, then back to TODO.
- Attempt an invalid transition such as TODO to COMPLETED.
- Expected outcome: valid transitions succeed; invalid transitions return HTTP 400 with the original state preserved.

### 4. List and filter tasks
- Create several tasks with different statuses, priorities, and tags.
- Call GET /api/tasks?status=TODO&priority=HIGH&tag=work.
- Expected outcome: only tasks matching all supplied filters are returned and the results are sorted HIGH, MEDIUM, LOW with stable ordering for equal priorities.

### 5. Restart persistence check
- Create tasks, stop the service, start it again, and retrieve the list.
- Expected outcome: persisted tasks remain available and ordering is preserved.

### 6. Persistence failure handling
- Corrupt the JSON file or make it unreadable.
- Expected outcome: the system reports the failure, logs it, and does not silently discard prior data.

### 7. Harness validation coverage
- Run Spotless for Java formatting.
- Run SpotBugs with FindSecBugs for static analysis and security checks.
- Run OWASP Dependency-Check for dependency vulnerability scanning.
- Run JUnit 5 unit tests for title validation, tag normalization, duplicate-tag handling, filtering, priority sorting, and status transitions.
- Run JUnit 5 REST integration/contract tests for CRUD operations, IDs, response codes, invalid transitions, missing resources, and unchanged state after rejected updates.
- Run JUnit 5 persistence and restart tests using temporary JSON storage locations.
- Expected outcome: failures are logged with timestamps, check names, details, and outcomes; they are then repaired and rerun until the full suite passes.

Each harness check is invoked independently and reports its own structured result; a failure in one check does not prevent the remaining checks from running. Runtime task JSON defaults outside the repository, and tests must never use or modify runtime data or committed JSON fixtures.

## Independent Harness Log Format

Each harness record should include:

- timestamp
- check name
- component or endpoint under test
- failure details and expected vs actual outcome
- status (passed, failed, skipped)

Examples of log destinations include timestamped JSONL records or timestamped reports stored in `part-1a-smart-todo/harness/logs/`.

## Repair and Revalidation Cycle

1. Run the independent harness.
2. Preserve the original failing evidence in the harness logs directory.
3. Provide the resulting failure evidence to the AI coding agent.
4. Fix the root cause in the smallest possible scope.
5. Rerun the complete validation suite rather than only the single failing check.
6. Store the passing evidence separately so both the initial failure and the final pass remain visible for review.

This workflow is required to prove that the repair was based on objective evidence and that there are no regressions in other validation categories.

## Suggested Validation Commands

- `mvn -f part-1a-smart-todo/backend/pom.xml test`
- `mvn -f part-1a-smart-todo/backend/pom.xml spotless:check`
- `mvn -f part-1a-smart-todo/backend/pom.xml spotbugs:check`
- `mvn -f part-1a-smart-todo/backend/pom.xml org.owasp:dependency-check-maven:check`
- `mvn -f part-1a-smart-todo/backend/pom.xml spring-boot:run`
- API validation via HTTP calls or a dedicated harness

These commands are validation guidance only; they are not implementation code.

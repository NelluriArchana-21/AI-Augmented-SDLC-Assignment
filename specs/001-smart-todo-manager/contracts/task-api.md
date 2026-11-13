# Task API Contract

## Create Task

**Method**: POST
**Path**: /api/tasks
**Success**: 201 Created
**Failure**: 400 Bad Request

Request body:
- title: required, non-blank, <= 120 chars
- description: optional
- priority: LOW | MEDIUM | HIGH
- status: TODO | IN_PROGRESS | COMPLETED
- tags: optional array of strings

Server behavior:
- Generates UUID automatically.
- Rejects client-supplied `id` values during creation.
- Rejects invalid enums or blank title values.
- Returns the created task representation.

## List Tasks

**Method**: GET
**Path**: /api/tasks
**Success**: 200 OK
**Failure**: 400 Bad Request for invalid filter values

Query parameters:
- status: optional enum filter
- priority: optional enum filter
- tag: optional tag filter

Behavior:
- Multiple query parameters are combined with AND logic.
- Invalid status or priority filter values return HTTP 400.
- GET filtering must not modify the persisted task collection.
- Results are sorted by priority order HIGH, MEDIUM, LOW.
- Equal-priority tasks retain insertion order.
- Tag matching is case-insensitive and uses normalized values.

## Retrieve Task

**Method**: GET
**Path**: /api/tasks/{id}
**Success**: 200 OK
**Failure**: 404 Not Found

## Update Task

**Method**: PUT
**Path**: /api/tasks/{id}
**Success**: 200 OK
**Failure**: 400 Bad Request, 404 Not Found

Rules:
- ID is immutable and cannot be changed.
- Client attempts to send a different or new `id` in the request body are rejected.
- Status transitions must follow the allowed rules.
- A PUT request that keeps the existing status value unchanged is a valid no-op: it returns HTTP 200 and the current task representation unchanged.
- Invalid transitions return HTTP 400 and the stored task remains unchanged.
- Rejected updates must not mutate the persisted task collection.

## Delete Task

**Method**: DELETE
**Path**: /api/tasks/{id}
**Success**: 204 No Content
**Failure**: 404 Not Found

## Error Handling

- Invalid data, invalid transitions, or invalid filter values return HTTP 400.
- Missing resources return HTTP 404.
- Persistence failures must be logged and must not silently lose data.
- When an update or filter request is rejected, the response must indicate validation failure and the stored state must remain exactly as before the request.

## Contract Validation Expectations

The implementation must be tested for:

- client-supplied IDs in create requests being rejected
- immutable-ID enforcement on update requests
- invalid status transitions returning 400
- HTTP response codes staying aligned with the contract
- unchanged stored task data when a rejected update fails validation

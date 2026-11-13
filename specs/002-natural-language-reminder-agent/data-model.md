# Data Model: Natural-Language Reminder Agent

This model defines transient request/response data and evaluation evidence.
It does not define persisted reminders.

## Reminder Request

| Field | Type | Constraints |
|---|---|---|
| `natural_language_request` | string | Required, non-blank, maximum 4,000 characters; treated as untrusted text |
| `reference_datetime` | string | Required RFC 3339 timestamp with UTC offset; parsed and validated |
| `timezone` | string | Required IANA timezone identifier; valid at the reference instant and consistent with timestamp offset |

Missing, mistyped, malformed, invalid, or offset-inconsistent time context prevents provider invocation and yields a CLARIFY response asking for corrected context. A malformed JSON/non-object request, when a response can be returned, yields CLARIFY with null task/date/time.

## Action Proposal

The response contains exactly these six properties:

| Field | Type | Constraints |
|---|---|---|
| `intent` | string | `CREATE`, `RESCHEDULE`, `SNOOZE`, or `CLARIFY` |
| `task` | string or null | Non-blank, maximum 120 characters when present; new title for CREATE or stated reminder target for RESCHEDULE/SNOOZE |
| `date` | ISO date string or null | Gregorian-valid `YYYY-MM-DD`; local to supplied timezone |
| `time` | string or null | Local 24-hour `HH:mm` |
| `needs_clarification` | boolean | False for actionable intents; true for CLARIFY |
| `clarification_question` | non-blank string or null | Required for CLARIFY; null for actionable intents |

### Intent Combinations

| Intent | `task` | `date` | `time` | `needs_clarification` | `clarification_question` |
|---|---|---|---|---|---|
| CREATE | Required | Required | Required | false | null |
| RESCHEDULE | Required target text | Required new date | Required new time | false | null |
| SNOOZE | Required target text | Required proposed target date | Required proposed target time | false | null |
| CLARIFY | Known value or null | Known value or null | Known value or null | true | Required focused question |

For CLARIFY, only reliably extracted values may be retained. Do not populate
unknown or ambiguous values. All six keys are required, additional keys are
forbidden, and model output must be JSON without surrounding prose.

## Provider Invocation and Result

- **Provider configuration**: Google Gemini API, subject to availability;
  model identifier is configurable and has no hardcoded unverified value.
  `GEMINI_API_KEY` is environment-only, sent in the documented
  `x-goog-api-key` header, and must never be logged or persisted.
- **Prompt context**: validated `reference_datetime` and `timezone` are
  distinct trusted context fields. The natural-language text is passed
  separately as untrusted content.
- **Model result**: raw completion text, parsed JSON tree, and validated
  `ActionProposal`. Model-produced CLARIFY is an ordinary valid response;
  provider/network/configuration failures are a different operational result
  and never converted to CLARIFY.
- **Provider attempt**: at most 3 total attempts, each capped at 20 seconds;
  retry only transient network, rate-limit, and provider-server errors, with
  waits of 1 second then 2 seconds. Total per-request duration is at most
  63 seconds. Do not retry configuration/authentication failures or malformed
  model output.

## Evaluation Case

Each of the 29 stable case IDs has:

- category and case type (model-quality or isolated probe);
- synthetic input/context or fault-injection parameters;
- expected behavior and requirement/success-criterion references;
- explicit applicable score dimensions (I, T, D, C, S) or N/A for probes;
- structural, deterministic invariant, and semantic outcomes;
- per-dimension score (0/1/2) where applicable and normalized case score.

E01-E25 are model-quality cases. E26-E29 are isolated provider/validator
probes and have no semantic score.

## Evaluation Run Record

The run record contains unique run ID, timestamps, case ID, expected and actual
result (or operational failure), provider attempt categories and dispositions,
separate structural/invariant/semantic findings, applicable dimensions,
ratings, score, aggregate and thresholds, evidence references, and
repair/re-evaluation linkage. It MUST NOT contain API keys or secrets. An
unexpected provider failure or malformed actual model response blocks the
model-quality run; isolated failure probes are not model responses.

## Relationships

- One evaluation run processes the fixed set of 29 evaluation cases.
- Each case produces one primary result record and may include bounded
  provider-attempt subrecords.
- A model response may produce one action proposal; an operational failure
  produces no agent response.
- Action proposals are never associated with reminder persistence or mutation.

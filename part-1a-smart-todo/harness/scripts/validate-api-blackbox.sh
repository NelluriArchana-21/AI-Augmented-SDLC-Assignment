#!/usr/bin/env bash
set -uo pipefail

fail_prerequisite() {
  printf 'Black-box API validation prerequisite missing: %s\n' "$1" >&2
  exit 2
}

for executable in curl jq; do
  command -v "$executable" >/dev/null 2>&1 ||
    fail_prerequisite "${executable} is required"
done

if [[ -z "${SMART_TODO_BASE_URL:-}" ]]; then
  fail_prerequisite 'SMART_TODO_BASE_URL must explicitly name the running service'
fi
BASE_URL="${SMART_TODO_BASE_URL%/}"
if [[ ! "$BASE_URL" =~ ^https?://[^/@?#]+(/[^?#]*)?$ ]]; then
  fail_prerequisite 'SMART_TODO_BASE_URL must be an http(s) URL without user info, query, or fragment'
fi
if [[ -z "${SMART_TODO_STORAGE_PATH:-}" ]]; then
  fail_prerequisite 'SMART_TODO_STORAGE_PATH must name the isolated task data file'
fi
if [[ "${SMART_TODO_STORAGE_ISOLATED:-}" != "true" ]]; then
  fail_prerequisite 'SMART_TODO_STORAGE_ISOLATED=true must confirm that task storage is isolated'
fi

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/smart-todo-api.XXXXXXXX")" ||
  fail_prerequisite 'unable to create a temporary response directory'
RESPONSE_FILE="${TEMP_DIR}/response.body"
: >"$RESPONSE_FILE"

RUN_TOKEN="$(basename "$TEMP_DIR" | tr '[:upper:]' '[:lower:]' | tr '.' '-')"
TITLE_PREFIX="bbx-${RUN_TOKEN}-"
RUN_TAG="bbx-${RUN_TOKEN}"
BASE_URL="${BASE_URL%/}"

declare -a BASELINE_IDS=()
declare -a CREATED_IDS=()
declare -a CREATED_TITLES=()
BASELINE_IDS_JSON='[]'
BASELINE_READY=0
CLEANUP_DONE=0
CHECK_COUNT=0
FAILURE_COUNT=0
LAST_CREATED_ID=''
ACTUAL_STATUS='000'
ACTUAL_BODY=''
CURL_EXIT_CODE=0
REQUEST_TIMESTAMP=''
REQUEST_METHOD=''
REQUEST_PATH=''
REQUEST_BODY='null'

cleanup_temp_files() {
  rm -f -- "$RESPONSE_FILE"
  rmdir -- "$TEMP_DIR" 2>/dev/null || true
}

trap cleanup_temp_files EXIT

timestamp_utc() {
  date -u '+%Y-%m-%dT%H:%M:%SZ'
}

is_uuid() {
  [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$ ]]
}

id_in_baseline() {
  local candidate="$1"
  local known
  for known in "${BASELINE_IDS[@]}"; do
    [[ "$candidate" == "$known" ]] && return 0
  done
  return 1
}

id_is_tracked() {
  local candidate="$1"
  local known
  for known in "${CREATED_IDS[@]}"; do
    [[ "$candidate" == "$known" ]] && return 0
  done
  return 1
}

new_uuid() {
  local candidate
  local attempt
  for ((attempt = 0; attempt < 100; attempt++)); do
    printf -v candidate '00000000-0000-4000-8000-%04x%04x%04x' \
      "$RANDOM" "$RANDOM" "$RANDOM"
    if ! id_in_baseline "$candidate" && ! id_is_tracked "$candidate"; then
      printf '%s' "$candidate"
      return 0
    fi
  done
  return 1
}

http_request() {
  local method="$1"
  local path="$2"
  local request_body="$3"
  local url="${BASE_URL}${path}"
  local status_output=''
  local -a curl_arguments

  REQUEST_TIMESTAMP="$(timestamp_utc)"
  REQUEST_METHOD="$method"
  REQUEST_PATH="$path"
  REQUEST_BODY="$request_body"
  : >"$RESPONSE_FILE"

  curl_arguments=(
    --disable
    --silent
    --show-error
    --connect-timeout 3
    --max-time 10
    --output "$RESPONSE_FILE"
    --write-out '%{http_code}'
    --request "$method"
    --header 'Accept: application/json'
  )
  if [[ "$request_body" != "null" ]]; then
    curl_arguments+=(--header 'Content-Type: application/json' --data-binary "$request_body")
  fi

  if status_output="$(curl "${curl_arguments[@]}" "$url" 2>/dev/null)"; then
    CURL_EXIT_CODE=0
  else
    CURL_EXIT_CODE=$?
  fi
  ACTUAL_STATUS="${status_output:-000}"
  ACTUAL_BODY="$(cat "$RESPONSE_FILE" 2>/dev/null || true)"
}

response_projection() {
  if ! jq -e . >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    jq -cn --arg present "$([[ -n "$ACTUAL_BODY" ]] && printf true || printf false)" \
      '{json_valid:false, body_present:($present == "true")}'
    return
  fi

  jq -c \
    --arg prefix "$TITLE_PREFIX" \
    --arg marker "$RUN_TAG" \
    '
      if type == "array" then
        {
          json_type: "array",
          test_tasks: [
            .[]
            | select(
                type == "object"
                and (
                  ((.title // "") | startswith($prefix))
                  or (((.tags // []) | if type == "array" then index($marker) != null else false end))
                )
              )
            | {id, title, description, priority, status, tags}
          ]
        }
      elif type == "object" and (
        ((.title // "") | startswith($prefix))
        or (((.tags // []) | if type == "array" then index($marker) != null else false end))
      ) then
        {json_type: "task", task: {id, title, description, priority, status, tags}}
      elif type == "object" then
        {
          json_type: "object",
          error_field_present: has("error"),
          message_field_present: has("message")
        }
      else
        {json_type: type}
      end
    ' <<<"$ACTUAL_BODY"
}

perform_case() {
  local case_id="$1"
  local method="$2"
  local path="$3"
  local request_body="$4"
  local expected_status="$5"
  local expected_outcome="$6"
  local predicate="$7"
  shift 7
  local result='passed'
  local details=''
  local projection
  local request_json="$request_body"
  local -a predicate_arguments=("$@")

  [[ -n "$request_json" ]] || request_json='null'
  http_request "$method" "$path" "$request_json"

  if [[ "$CURL_EXIT_CODE" -ne 0 ]]; then
    result='failed'
    details="curl exited with code ${CURL_EXIT_CODE}"
  elif [[ "$ACTUAL_STATUS" != "$expected_status" ]]; then
    result='failed'
    details="Expected HTTP ${expected_status}; received HTTP ${ACTUAL_STATUS}"
  elif [[ -n "$predicate" ]] &&
    ! jq -e "${predicate_arguments[@]}" "$predicate" >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    result='failed'
    details='HTTP status matched, but the expected JSON assertions did not'
  fi

  projection="$(response_projection 2>/dev/null || printf '{"json_valid":false,"body_present":true}')"
  jq -cn \
    --arg timestamp "$REQUEST_TIMESTAMP" \
    --arg check "$case_id" \
    --arg method "$REQUEST_METHOD" \
    --arg path "$REQUEST_PATH" \
    --arg expected_outcome "$expected_outcome" \
    --arg actual_status "$ACTUAL_STATUS" \
    --arg result "$result" \
    --arg details "$details" \
    --argjson request "$request_json" \
    --argjson expected_status "$expected_status" \
    --argjson curl_exit "$CURL_EXIT_CODE" \
    --argjson response "$projection" \
    '{
      timestamp: $timestamp,
      check: $check,
      method: $method,
      path: $path,
      request: $request,
      expected: {status: $expected_status, outcome: $expected_outcome},
      actual: {
        status: $actual_status,
        curl_exit_code: $curl_exit,
        response: $response
      },
      result: $result,
      details: (if $details == "" then [] else [$details] end)
    }'

  CHECK_COUNT=$((CHECK_COUNT + 1))
  if [[ "$result" != "passed" ]]; then
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
  fi
}

record_local_assertion() {
  local check_id="$1"
  local expected="$2"
  local actual="$3"
  local result="$4"
  local details="$5"

  jq -cn \
    --arg timestamp "$(timestamp_utc)" \
    --arg check "$check_id" \
    --arg expected "$expected" \
    --arg actual "$actual" \
    --arg result "$result" \
    --arg details "$details" \
    '{
      timestamp: $timestamp,
      check: $check,
      method: "ASSERT",
      path: "/api/tasks",
      request: null,
      expected: {outcome: $expected},
      actual: {outcome: $actual},
      result: $result,
      details: (if $details == "" then [] else [$details] end)
    }'
  CHECK_COUNT=$((CHECK_COUNT + 1))
  if [[ "$result" != "passed" ]]; then
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
  fi
}

track_created_response() {
  local expected_title="$1"
  local task_id=''

  if ! jq -e \
    --arg title "$expected_title" \
    --arg prefix "$TITLE_PREFIX" \
    --arg marker "$RUN_TAG" \
    '
      type == "object"
      and (
        ((.title // "") == $title and ($title | startswith($prefix)))
        or (((.tags // []) | if type == "array" then index($marker) != null else false end))
      )
      and (.id | type == "string" and test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))
    ' >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    return 0
  fi

  task_id="$(jq -r '.id' <<<"$ACTUAL_BODY")"
  if id_in_baseline "$task_id"; then
    record_local_assertion \
      "created-id-collision-${task_id}" \
      'A created task ID must not belong to a task present before this run' \
      'The API returned an ID from the baseline task collection' \
      failed \
      'The colliding ID was not tracked and will not be deleted.'
    return 0
  fi
  if ! id_is_tracked "$task_id"; then
    CREATED_IDS+=("$task_id")
    CREATED_TITLES+=("$(jq -r '.title // ""' <<<"$ACTUAL_BODY")")
  fi
  LAST_CREATED_ID="$task_id"
}

create_task() {
  local key="$1"
  local priority="$2"
  local status="$3"
  local secondary_tag="$4"
  local title="${TITLE_PREFIX}${key}"
  local tags
  local body

  LAST_CREATED_ID=''
  tags="$(jq -cn --arg marker "$RUN_TAG" --arg secondary "$secondary_tag" '[$marker, $secondary]')"
  body="$(
    jq -cn \
      --arg title "$title" \
      --arg description "black-box ${key}" \
      --arg priority "$priority" \
      --arg status "$status" \
      --argjson tags "$tags" \
      '{title:$title, description:$description, priority:$priority, status:$status, tags:$tags}'
  )"

  perform_case \
    "create-${key}" POST /api/tasks "$body" 201 \
    'HTTP 201 with a generated UUID and the submitted task properties' \
    'type == "object" and .title == $title and .description == $description and .priority == $priority and .status == $status and .tags == $tags and (.id | type == "string" and test("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"))' \
    --arg title "$title" \
    --arg description "black-box ${key}" \
    --arg priority "$priority" \
    --arg status "$status" \
    --argjson tags "$tags"

  track_created_response "$title"
}

owned_ids_json() {
  if ((${#CREATED_IDS[@]} == 0)); then
    printf '[]'
  else
    printf '%s\n' "${CREATED_IDS[@]}" | jq -Rsc 'split("\n") | map(select(length > 0)) | sort'
  fi
}

assert_owned_snapshot() {
  local case_id="$1"
  local expected_ids="$2"
  perform_case \
    "$case_id" GET /api/tasks null 200 \
    'A list response contains exactly the expected IDs owned by this run' \
    'type == "array" and ([.[] | select(type == "object" and ((.title // "") | startswith($prefix))) | .id] | sort) == $expected_ids' \
    --arg prefix "$TITLE_PREFIX" \
    --argjson expected_ids "$expected_ids"
}

preflight_owned_task() {
  local task_id="$1"
  local expected_title="$2"
  perform_case \
    "cleanup-ownership-${task_id}" GET "/api/tasks/${task_id}" null 200 \
    'The response is the exact task created by this run before deletion' \
    'type == "object" and .id == $id and (.title // "") == $title and (((.title // "") | startswith($prefix)) or (((.tags // []) | if type == "array" then index($marker) != null else false end)))' \
    --arg id "$task_id" \
    --arg title "$expected_title" \
    --arg prefix "$TITLE_PREFIX" \
    --arg marker "$RUN_TAG"
}

remove_tracked_task() {
  local task_id="$1"
  local index
  local last_index=$((${#CREATED_IDS[@]} - 1))
  local -a remaining_ids=()
  local -a remaining_titles=()

  for ((index = 0; index <= last_index; index++)); do
    if [[ "${CREATED_IDS[index]}" != "$task_id" ]]; then
      remaining_ids+=("${CREATED_IDS[index]}")
      remaining_titles+=("${CREATED_TITLES[index]}")
    fi
  done
  CREATED_IDS=("${remaining_ids[@]}")
  CREATED_TITLES=("${remaining_titles[@]}")
}

delete_owned_task() {
  local task_id="$1"
  local task_title="$2"
  local expected_status="$3"

  if id_in_baseline "$task_id"; then
    record_local_assertion \
      "cleanup-refused-baseline-${task_id}" \
      'Never delete a task present before this run' \
      'A tracked ID unexpectedly matched the baseline' \
      failed \
      'Deletion refused because the task ID was present before the test started.'
    return 1
  fi
  preflight_owned_task "$task_id" "$task_title"
  if [[ "$ACTUAL_STATUS" != "200" ]] ||
    ! jq -e \
      --arg id "$task_id" \
      --arg title "$task_title" \
      --arg prefix "$TITLE_PREFIX" \
      --arg marker "$RUN_TAG" \
      'type == "object" and .id == $id and (.title // "") == $title and (((.title // "") | startswith($prefix)) or (((.tags // []) | if type == "array" then index($marker) != null else false end)))' \
      >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    return 1
  fi

  perform_case \
    "$expected_status" DELETE "/api/tasks/${task_id}" null 204 \
    'HTTP 204 for deleting the exact task created by this run' ''
  if [[ "$ACTUAL_STATUS" == "204" && "$CURL_EXIT_CODE" -eq 0 ]]; then
    remove_tracked_task "$task_id"
    return 0
  fi
  return 1
}

check_state_unchanged_after_invalid_create() {
  local case_id="$1"
  local title="$2"
  local body="$3"
  local expected_ids

  expected_ids="$(owned_ids_json)"
  perform_case \
    "$case_id" POST /api/tasks "$body" 400 \
    'HTTP 400 with a JSON error for invalid task creation' \
    'type == "object" and (.error | type == "string" and length > 0)'
  track_created_response "$title"
  assert_owned_snapshot "state-unchanged-${case_id}" "$expected_ids"
}

run_suite() {
  local lifecycle_id=''
  local tie_one_id=''
  local tie_two_id=''
  local medium_id=''
  local low_id=''
  local high_progress_id=''
  local no_task_id=''
  local client_id=''
  local before_invalid_filters=''
  local expected_sorted=''
  local expected_todo=''
  local expected_high=''
  local expected_work=''
  local expected_combined=''
  local original_task=''
  local update_body=''
  local invalid_body=''
  local missing_body=''

  perform_case \
    baseline-list GET /api/tasks null 200 \
    'The service returns a JSON array with string task IDs before test data is created' \
    'type == "array" and all(.[]; type == "object" and (.id | type == "string" and length > 0)) and all(.[]; ((.title // "") | startswith($prefix) | not)) and all(.[]; (((.tags // []) | if type == "array" then index($tag) == null else true end)))' \
    --arg prefix "$TITLE_PREFIX" \
    --arg tag "$RUN_TAG"
  if [[ "$ACTUAL_STATUS" != "200" ]] ||
    ! jq -e \
      --arg prefix "$TITLE_PREFIX" \
      --arg tag "$RUN_TAG" \
      '
        type == "array"
        and all(.[]; type == "object" and (.id | type == "string" and length > 0))
        and all(.[]; ((.title // "") | startswith($prefix) | not))
        and all(.[]; (((.tags // []) | if type == "array" then index($tag) == null else true end)))
      ' >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    return 0
  fi

  BASELINE_IDS_JSON="$(jq -c '[.[].id]' <<<"$ACTUAL_BODY")"
  mapfile -t BASELINE_IDS < <(jq -r '.[].id' <<<"$ACTUAL_BODY")
  BASELINE_READY=1

  perform_case \
    unique-run-tag GET "/api/tasks?tag=${RUN_TAG}" null 200 \
    'The run-specific tag has no existing results before test tasks are created' \
    'type == "array" and length == 0'
  if [[ "$ACTUAL_STATUS" != "200" ]] ||
    ! jq -e 'type == "array" and length == 0' >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    return 0
  fi

  client_id="$(new_uuid)" || return 0
  no_task_id="$(new_uuid)" || return 0

  create_task lifecycle HIGH TODO work
  lifecycle_id="$LAST_CREATED_ID"
  [[ -n "$lifecycle_id" ]] || return 0
  create_task high-tie-one HIGH TODO work
  tie_one_id="$LAST_CREATED_ID"
  [[ -n "$tie_one_id" ]] || return 0
  create_task high-tie-two HIGH TODO work
  tie_two_id="$LAST_CREATED_ID"
  [[ -n "$tie_two_id" ]] || return 0
  create_task medium-todo MEDIUM TODO work
  medium_id="$LAST_CREATED_ID"
  [[ -n "$medium_id" ]] || return 0
  create_task low-todo LOW TODO work
  low_id="$LAST_CREATED_ID"
  [[ -n "$low_id" ]] || return 0
  create_task high-progress HIGH IN_PROGRESS ops
  high_progress_id="$LAST_CREATED_ID"
  [[ -n "$high_progress_id" ]] || return 0

  perform_case \
    get-by-id GET "/api/tasks/${lifecycle_id}" null 200 \
    'HTTP 200 returns the created task with its generated ID and properties' \
    'type == "object" and .id == $id and .title == $title and .status == "TODO" and .priority == "HIGH"' \
    --arg id "$lifecycle_id" \
    --arg title "${TITLE_PREFIX}lifecycle"

  update_body="$(
    jq -cn \
      --arg id "$lifecycle_id" \
      --arg title "${TITLE_PREFIX}lifecycle" \
      --arg description 'updated through HTTP' \
      --arg marker "$RUN_TAG" \
      '{id:$id,title:$title,description:$description,priority:"HIGH",status:"IN_PROGRESS",tags:[$marker,"work"]}'
  )"
  perform_case \
    update-valid PUT "/api/tasks/${lifecycle_id}" "$update_body" 200 \
    'HTTP 200 updates a task through a valid TODO to IN_PROGRESS transition' \
    'type == "object" and .id == $id and .title == $title and .description == "updated through HTTP" and .status == "IN_PROGRESS"' \
    --arg id "$lifecycle_id" \
    --arg title "${TITLE_PREFIX}lifecycle"
  original_task="$(jq -cS . <<<"$ACTUAL_BODY" 2>/dev/null || printf '{}')"

  update_body="$(
    jq -cn \
      --arg id "$lifecycle_id" \
      --arg title "${TITLE_PREFIX}changed-by-noop" \
      --arg marker "$RUN_TAG" \
      '{id:$id,title:$title,description:"changed-by-noop",priority:"LOW",status:"IN_PROGRESS",tags:[$marker]}'
  )"
  perform_case \
    same-status-noop PUT "/api/tasks/${lifecycle_id}" "$update_body" 200 \
    'A same-status update returns the existing task representation unchanged' \
    '. == $original' \
    --argjson original "$original_task"

  update_body="$(
    jq -cn \
      --arg id "$client_id" \
      --arg title "${TITLE_PREFIX}lifecycle-mutated" \
      --arg marker "$RUN_TAG" \
      '{id:$id,title:$title,description:"must not change",priority:"HIGH",status:"COMPLETED",tags:[$marker,"work"]}'
  )"
  perform_case \
    immutable-id PUT "/api/tasks/${lifecycle_id}" "$update_body" 400 \
    'A PUT request with a different ID is rejected with a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'
  track_created_response "${TITLE_PREFIX}lifecycle-mutated"
  perform_case \
    immutable-id-state GET "/api/tasks/${lifecycle_id}" null 200 \
    'The task retains its original ID, title, description, and status after ID mutation is rejected' \
    'type == "object" and .id == $id and .title == $title and .description == $description and .status == "IN_PROGRESS"' \
    --arg id "$lifecycle_id" \
    --arg title "${TITLE_PREFIX}lifecycle" \
    --arg description 'updated through HTTP'

  update_body="$(
    jq -cn \
      --arg id "$lifecycle_id" \
      --arg title "${TITLE_PREFIX}invalid-transition" \
      --arg marker "$RUN_TAG" \
      '{id:$id,title:$title,description:"must not change",priority:"HIGH",status:"TODO",tags:[$marker,"work"]}'
  )"
  perform_case \
    invalid-status-transition PUT "/api/tasks/${lifecycle_id}" "$update_body" 400 \
    'The unsupported IN_PROGRESS to TODO transition returns a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'
  perform_case \
    invalid-transition-state GET "/api/tasks/${lifecycle_id}" null 200 \
    'The task is unchanged after the rejected status transition' \
    'type == "object" and .id == $id and .title == $title and .description == $description and .status == "IN_PROGRESS"' \
    --arg id "$lifecycle_id" \
    --arg title "${TITLE_PREFIX}lifecycle" \
    --arg description 'updated through HTTP'

  missing_body="$(
    jq -cn --arg priority HIGH --arg status TODO --arg marker "$RUN_TAG" \
      '{priority:$priority,status:$status,tags:[$marker]}'
  )"
  check_state_unchanged_after_invalid_create \
    create-missing-title "${TITLE_PREFIX}missing-title" "$missing_body"

  invalid_body="$(
    jq -cn --arg title "${TITLE_PREFIX}blank-title" --arg marker "$RUN_TAG" \
      '{title:"   ",priority:"HIGH",status:"TODO",tags:[$marker]}'
  )"
  check_state_unchanged_after_invalid_create \
    create-blank-title "${TITLE_PREFIX}blank-title" "$invalid_body"

  invalid_body="$(
    jq -cn \
      --arg title "$(printf '%121s' '' | tr ' ' x)" \
      --arg marker "$RUN_TAG" \
      '{title:$title,priority:"HIGH",status:"TODO",tags:[$marker]}'
  )"
  check_state_unchanged_after_invalid_create \
    create-too-long-title "${TITLE_PREFIX}too-long-title" "$invalid_body"

  invalid_body="$(
    jq -cn --arg title "${TITLE_PREFIX}invalid-priority" --arg marker "$RUN_TAG" \
      '{title:$title,priority:"INVALID",status:"TODO",tags:[$marker]}'
  )"
  check_state_unchanged_after_invalid_create \
    create-invalid-priority "${TITLE_PREFIX}invalid-priority" "$invalid_body"

  invalid_body="$(
    jq -cn --arg title "${TITLE_PREFIX}invalid-status" --arg marker "$RUN_TAG" \
      '{title:$title,priority:"HIGH",status:"INVALID",tags:[$marker]}'
  )"
  check_state_unchanged_after_invalid_create \
    create-invalid-status "${TITLE_PREFIX}invalid-status" "$invalid_body"

  invalid_body="$(
    jq -cn \
      --arg id "$client_id" \
      --arg title "${TITLE_PREFIX}client-id" \
      --arg marker "$RUN_TAG" \
      '{id:$id,title:$title,priority:"HIGH",status:"TODO",tags:[$marker]}'
  )"
  check_state_unchanged_after_invalid_create \
    create-client-supplied-id "${TITLE_PREFIX}client-id" "$invalid_body"

  expected_sorted="$(
    jq -cn \
      --arg lifecycle "$lifecycle_id" \
      --arg tie_one "$tie_one_id" \
      --arg tie_two "$tie_two_id" \
      --arg high_progress "$high_progress_id" \
      --arg medium "$medium_id" \
      --arg low "$low_id" \
      '[$lifecycle,$tie_one,$tie_two,$high_progress,$medium,$low]'
  )"
  perform_case \
    priority-sort-stable-ties GET "/api/tasks?tag=${RUN_TAG}" null 200 \
    'The run-tagged list is sorted HIGH, MEDIUM, LOW and preserves insertion order for equal priorities' \
    'type == "array" and ([.[] | select((.title // "") | startswith($prefix)) | .id]) == $expected_ids' \
    --arg prefix "$TITLE_PREFIX" \
    --argjson expected_ids "$expected_sorted"

  expected_todo="$(
    jq -cn --arg tie_one "$tie_one_id" --arg tie_two "$tie_two_id" \
      --arg medium "$medium_id" --arg low "$low_id" \
      '[$tie_one,$tie_two,$medium,$low]'
  )"
  perform_case \
    status-filter GET '/api/tasks?status=TODO' null 200 \
    'Status filter returns only the run-owned TODO tasks in priority order' \
    'type == "array" and ([.[] | select((.title // "") | startswith($prefix)) | .id]) == $expected_ids' \
    --arg prefix "$TITLE_PREFIX" \
    --argjson expected_ids "$expected_todo"

  expected_high="$(
    jq -cn \
      --arg lifecycle "$lifecycle_id" \
      --arg tie_one "$tie_one_id" \
      --arg tie_two "$tie_two_id" \
      --arg high_progress "$high_progress_id" \
      '[$lifecycle,$tie_one,$tie_two,$high_progress]'
  )"
  perform_case \
    priority-filter GET '/api/tasks?priority=HIGH' null 200 \
    'Priority filter returns only the run-owned HIGH-priority tasks' \
    'type == "array" and ([.[] | select((.title // "") | startswith($prefix)) | .id]) == $expected_ids' \
    --arg prefix "$TITLE_PREFIX" \
    --argjson expected_ids "$expected_high"

  expected_work="$(
    jq -cn \
      --arg lifecycle "$lifecycle_id" \
      --arg tie_one "$tie_one_id" \
      --arg tie_two "$tie_two_id" \
      --arg medium "$medium_id" \
      --arg low "$low_id" \
      '[$lifecycle,$tie_one,$tie_two,$medium,$low]'
  )"
  perform_case \
    normalized-tag-filter GET "/api/tasks?tag=$(printf '%s' work | tr '[:lower:]' '[:upper:]')" null 200 \
    'Tag filtering matches case-insensitively and includes only matching run-owned tasks' \
    'type == "array" and ([.[] | select((.title // "") | startswith($prefix)) | .id]) == $expected_ids' \
    --arg prefix "$TITLE_PREFIX" \
    --argjson expected_ids "$expected_work"

  expected_combined="$(
    jq -cn --arg tie_one "$tie_one_id" --arg tie_two "$tie_two_id" '[$tie_one,$tie_two]'
  )"
  perform_case \
    combined-and-filter GET '/api/tasks?status=TODO&priority=HIGH&tag=work' null 200 \
    'Status, priority, and normalized tag filters combine with AND semantics' \
    'type == "array" and ([.[] | select((.title // "") | startswith($prefix)) | .id]) == $expected_ids' \
    --arg prefix "$TITLE_PREFIX" \
    --argjson expected_ids "$expected_combined"

  before_invalid_filters="$(owned_ids_json)"
  perform_case \
    invalid-status-filter GET '/api/tasks?status=NOT_A_STATUS' null 400 \
    'Invalid status filter returns HTTP 400 with a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'
  assert_owned_snapshot invalid-status-filter-state "$before_invalid_filters"

  before_invalid_filters="$(owned_ids_json)"
  perform_case \
    invalid-priority-filter GET '/api/tasks?priority=NOT_A_PRIORITY' null 400 \
    'Invalid priority filter returns HTTP 400 with a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'
  assert_owned_snapshot invalid-priority-filter-state "$before_invalid_filters"

  perform_case \
    get-missing-task GET "/api/tasks/${no_task_id}" null 404 \
    'GET for an unknown UUID returns HTTP 404 with a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'
  update_body="$(
    jq -cn --arg id "$no_task_id" '{id:$id,title:"not-created",priority:"HIGH",status:"TODO"}'
  )"
  perform_case \
    update-missing-task PUT "/api/tasks/${no_task_id}" "$update_body" 404 \
    'PUT for an unknown UUID returns HTTP 404 with a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'
  perform_case \
    delete-missing-task DELETE "/api/tasks/${no_task_id}" null 404 \
    'DELETE for an unknown UUID returns HTTP 404 with a JSON error' \
    'type == "object" and (.error | type == "string" and length > 0)'

  if delete_owned_task "$low_id" "${TITLE_PREFIX}low-todo" delete-owned-task; then
    perform_case \
      get-deleted-task GET "/api/tasks/${low_id}" null 404 \
      'GET after a successful delete returns HTTP 404 with a JSON error' \
      'type == "object" and (.error | type == "string" and length > 0)'
  fi

  return 0
}

cleanup_owned_tasks() {
  local task_id
  local task_title
  local index=0
  local discovery_json

  if [[ "$BASELINE_READY" -ne 1 ]]; then
    return 0
  fi

  perform_case \
    cleanup-discover-owned GET /api/tasks null 200 \
    'A list response is available to discover any uniquely named tasks created by this run' \
    'type == "array" and all(.[]; type == "object" and (.id | type == "string"))'
  if [[ "$ACTUAL_STATUS" != "200" ]] ||
    ! jq -e 'type == "array" and all(.[]; type == "object" and (.id | type == "string"))' \
      >/dev/null 2>&1 <<<"$ACTUAL_BODY"; then
    return 1
  fi

  discovery_json="$(
    jq -c \
      --arg prefix "$TITLE_PREFIX" \
      --arg marker "$RUN_TAG" \
      --argjson baseline "$BASELINE_IDS_JSON" '
      [
        .[]
        | select(
            type == "object"
            and (
              ((.title // "") | startswith($prefix))
              or (((.tags // []) | if type == "array" then index($marker) != null else false end))
            )
            and (.id | type == "string")
            and (.id as $candidate | ($baseline | index($candidate)) == null)
          )
        | {id, title:(.title // "")}
      ]
    ' <<<"$ACTUAL_BODY"
  )"
  while IFS=$'\t' read -r task_id task_title; do
    [[ -n "$task_id" ]] || continue
    if ! is_uuid "$task_id"; then
      record_local_assertion \
        "cleanup-invalid-id-${CHECK_COUNT}" \
        'Only delete valid UUIDs returned for uniquely named run data' \
        'A task with this run marker had a non-UUID ID' \
        failed \
        'Deletion refused because the task ID did not satisfy the API UUID contract.'
      continue
    fi
    if ! id_is_tracked "$task_id"; then
      CREATED_IDS+=("$task_id")
      CREATED_TITLES+=("$task_title")
    fi
  done < <(jq -r '.[] | [.id, .title] | @tsv' <<<"$discovery_json")

  while ((index < ${#CREATED_IDS[@]})); do
    task_id="${CREATED_IDS[index]}"
    task_title="${CREATED_TITLES[index]}"
    if delete_owned_task "$task_id" "$task_title" "cleanup-delete-${index}"; then
      continue
    fi
    index=$((index + 1))
  done

  return 0
}

emit_summary() {
  local result='passed'
  local restart_reason='The script does not stop or restart the externally managed service.'
  [[ "$FAILURE_COUNT" -eq 0 ]] || result='failed'
  jq -cn \
    --arg timestamp "$(timestamp_utc)" \
    --arg result "$result" \
    --arg restart_reason "$restart_reason" \
    --argjson requests "$CHECK_COUNT" \
    --argjson failures "$FAILURE_COUNT" \
    '{
      timestamp: $timestamp,
      check: "blackbox-api-summary",
      result: $result,
      request_count: $requests,
      failure_count: $failures,
      restart_persistence: {result: "not_attempted", reason: $restart_reason}
    }'
}

run_suite
cleanup_owned_tasks || true
CLEANUP_DONE=1
emit_summary
cleanup_temp_files
trap - EXIT

if [[ "$FAILURE_COUNT" -gt 0 ]]; then
  exit 1
fi
exit 0

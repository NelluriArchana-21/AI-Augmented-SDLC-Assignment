#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${GEMINI_API_KEY:-}" || -z "${REMINDER_AGENT_MODEL:-}" ]]; then
  printf '%s\n' \
    "Evaluation blocked: GEMINI_API_KEY and REMINDER_AGENT_MODEL must be configured locally." \
    >&2
  exit 2
fi

script_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
backend_dir="$script_dir/../../backend"
if [[ ! -f "$backend_dir/pom.xml" || ! -f "$script_dir/../config/evaluation-cases.json" ]]; then
  printf '%s\n' "Evaluation blocked: backend or frozen dataset is unavailable." >&2
  exit 2
fi

run_id="${EVALUATION_RUN_ID:-evaluation-$(date -u +%Y%m%dT%H%M%S%N)}"
reviews_argument=""
if [[ -n "${EVALUATION_REVIEWS_FILE:-}" ]]; then
  if [[ ! -f "$EVALUATION_REVIEWS_FILE" ]]; then
    printf '%s\n' "Evaluation blocked: configured semantic review file is unavailable." >&2
    exit 2
  fi
  reviews_argument="--evaluation.reviews-file=$EVALUATION_REVIEWS_FILE"
fi

cd "$backend_dir"
if [[ -n "$reviews_argument" ]]; then
  exec mvn -q -f pom.xml spring-boot:run \
    "-Dspring-boot.run.arguments=--evaluation.run-id=$run_id --evaluation.harness-root=../harness $reviews_argument --spring.main.web-application-type=none"
fi
exec mvn -q -f pom.xml spring-boot:run \
  "-Dspring-boot.run.arguments=--evaluation.run-id=$run_id --evaluation.harness-root=../harness --spring.main.web-application-type=none"

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INITIAL_ID="${1:-initial-live-20261114T034542828481570Z}"
RERUN_ID="${2:-}"

find_run() {
  local id="$1"
  for outcome in passed failed; do
    local path="$ROOT/logs/$outcome/$id"
    if [[ -d "$path" ]]; then
      printf '%s\n' "$path"
      return 0
    fi
  done
  return 1
}

INITIAL_DIR="$(find_run "$INITIAL_ID")" || {
  echo "ERROR: Initial evidence run not found: $INITIAL_ID" >&2
  exit 2
}

RERUN_DIR=""
if [[ -n "$RERUN_ID" ]]; then
  RERUN_DIR="$(find_run "$RERUN_ID")" || {
    echo "ERROR: Re-evaluation evidence run not found: $RERUN_ID" >&2
    exit 2
  }
fi

python3 - "$ROOT" "$INITIAL_DIR" "$RERUN_DIR" <<'PY'
import json
import re
import sys
from pathlib import Path

root, initial_dir, rerun_dir = map(Path, sys.argv[1:])
expected = [f"E{i:02d}" for i in range(1, 30)]
secret_patterns = [
    re.compile(r"\bAIza[0-9A-Za-z_-]{20,}\b"),
    re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/-]+=*"),
    re.compile(r"(?i)(x-goog-api-key|authorization|api[_-]?key|access[_-]?token|client[_-]?secret)\s*[:=]\s*[^,\s]+"),
]

errors = []
warnings = []

def inspect_run(directory, label, require_acceptance):
    print(f"\n===== {label}: {directory.name} =====")
    cases_file = directory / "cases.jsonl"
    summary_file = directory / "run-summary.json"

    if not cases_file.is_file() or not summary_file.is_file():
        errors.append(f"{label}: cases.jsonl or run-summary.json is missing")
        return

    try:
        records = [
            json.loads(line)
            for line in cases_file.read_text().splitlines()
            if line.strip()
        ]
        summary = json.loads(summary_file.read_text())
    except Exception as exc:
        errors.append(f"{label}: invalid JSON evidence: {exc}")
        return

    ids = [record.get("case_id") for record in records]
    print("Record count:", len(records))
    print("Unique case IDs:", len(set(ids)))
    print("Summary complete:", summary.get("complete"))
    print("Summary accepted:", summary.get("accepted"))
    print("Semantic aggregate:", summary.get("semantic_aggregate"))
    print("Structural compliance:", summary.get("structural_compliance_percent"))
    print("Invariant failures:", summary.get("invariant_failure_count"))
    print("Unresolved operational failures:", summary.get("unresolved_operational_failure_count"))

    if len(records) != 29 or sorted(ids) != expected:
        errors.append(f"{label}: records must contain exactly E01-E29 once each")

    if sorted(summary.get("case_ids") or []) != expected:
        errors.append(f"{label}: summary case_ids do not match E01-E29")

    by_id = {record.get("case_id"): record for record in records}

    for case_id in expected:
        record = by_id.get(case_id)
        if not record:
            continue
        case = record.get("case") or {}
        expected_kind = "model_quality" if int(case_id[1:]) <= 25 else "isolated_probe"
        if case.get("kind") != expected_kind:
            errors.append(f"{label}: {case_id} has unexpected case kind")

    probe_results = []
    for case_id in ("E26", "E27", "E28", "E29"):
        record = by_id.get(case_id, {})
        passed = (record.get("probe_outcome") or {}).get("passed") is True
        probe_results.append(passed)
    print("Isolated probes passed:", sum(probe_results), "/ 4")
    if not all(probe_results):
        warnings.append(f"{label}: one or more isolated probes did not pass")

    model_evaluated = sum(
        1 for i in range(1, 26)
        if (by_id.get(f"E{i:02d}", {}).get("actual_output") is not None)
    )
    print("Model-quality cases with actual output:", model_evaluated, "/ 25")

    repair_link_count = sum(
        len(record.get("repair_links") or []) for record in records
    )
    print("Case-level repair links:", repair_link_count)

    text = cases_file.read_text() + "\n" + summary_file.read_text()
    for pattern in secret_patterns:
        if pattern.search(text):
            errors.append(f"{label}: possible secret detected in evidence")
            break

    if require_acceptance:
        score = summary.get("semantic_aggregate")
        structural = summary.get("structural_compliance_percent")
        if summary.get("complete") is not True:
            errors.append(f"{label}: re-evaluation is not complete")
        if summary.get("accepted") is not True:
            errors.append(f"{label}: re-evaluation is not accepted")
        if not isinstance(score, (int, float)) or score < 90:
            errors.append(f"{label}: semantic aggregate is below 90 or unavailable")
        if structural != 100:
            errors.append(f"{label}: structural compliance is not 100%")
        if summary.get("invariant_failure_count") != 0:
            errors.append(f"{label}: invariant failures are not zero")
        if summary.get("unresolved_operational_failure_count") != 0:
            errors.append(f"{label}: unresolved operational failures remain")
        if model_evaluated != 25:
            errors.append(f"{label}: all 25 model-quality cases were not evaluated")

    return summary

initial_summary = inspect_run(initial_dir, "INITIAL RUN", False)

if rerun_dir:
    if rerun_dir.resolve() == initial_dir.resolve():
        errors.append("Initial and re-evaluation run must be separate directories")
    else:
        inspect_run(rerun_dir, "FULL RE-EVALUATION", True)
else:
    errors.append("Full post-repair re-evaluation run has not been supplied")

print("\n===== VERIFICATION RESULT =====")
for warning in warnings:
    print("WARNING:", warning)
for error in errors:
    print("BLOCKED:", error)

if errors:
    print("\nRESULT: INCOMPLETE — evidence requirements are not all satisfied.")
    sys.exit(1)

print("\nRESULT: Evidence structure and acceptance gates verified.")
PY

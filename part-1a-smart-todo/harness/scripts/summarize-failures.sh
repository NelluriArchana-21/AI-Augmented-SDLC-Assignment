#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CONFIG="${SCRIPT_DIR}/../config/validation.yml"

if [[ "${1:-}" == "--config" ]]; then
  if [[ $# -ne 3 ]]; then
    printf 'Usage: %s [--config CONFIG_PATH] <run-directory|results.jsonl>\n' "$0" >&2
    exit 2
  fi
  CONFIG="$2"
  shift 2
elif [[ $# -ne 1 ]]; then
  printf 'Usage: %s [--config CONFIG_PATH] <run-directory|results.jsonl>\n' "$0" >&2
  exit 2
fi

if ! command -v python3 >/dev/null 2>&1; then
  printf 'Summarizer prerequisite missing: python3 is required\n' >&2
  exit 2
fi

if ! python3 -c 'import yaml' >/dev/null 2>&1; then
  printf 'Summarizer prerequisite missing: Python PyYAML is required to read validation.yml\n' >&2
  exit 2
fi

exec python3 - "$1" "$CONFIG" <<'PY'
import json
import math
import re
import sys
from collections import Counter
from pathlib import Path

import yaml


VALID_RESULTS = {"passed", "failed", "blocked", "timed_out", "error", "skipped"}
REQUIRED_FIELDS = {
    "id",
    "category",
    "mandatory",
    "command",
    "started_at",
    "ended_at",
    "duration_seconds",
    "exit_code",
    "result",
    "details",
    "expected",
    "stdout_file",
    "stderr_file",
    "stdout",
    "stderr",
}
MAX_OUTPUT_CHARS = 2000


def reject_nonstandard_constant(value):
    raise ValueError(f"Non-standard JSON value: {value}")


def load_expected_checks(config_path):
    try:
        with config_path.open(encoding="utf-8") as stream:
            config = yaml.safe_load(stream)
    except (OSError, UnicodeError, yaml.YAMLError) as error:
        raise ValueError(
            f"Unable to load validation configuration {config_path}: "
            f"{type(error).__name__}: {error}"
        ) from error

    if not isinstance(config, dict) or config.get("version") != 1:
        raise ValueError("Validation configuration must be a version 1 mapping")
    checks = config.get("checks")
    if not isinstance(checks, list) or not checks:
        raise ValueError("Validation configuration must contain a non-empty checks list")

    expected_checks = {}
    for check in checks:
        if not isinstance(check, dict):
            raise ValueError(f"Invalid check definition: {check!r}")
        check_id = check.get("id")
        expected = check.get("expected")
        command = check.get("command")
        if (
            not isinstance(check_id, str)
            or not re.fullmatch(r"[A-Za-z0-9_-]+", check_id)
            or check_id in expected_checks
            or not isinstance(check.get("category"), str)
            or not isinstance(check.get("mandatory", True), bool)
            or not isinstance(command, list)
            or not command
            or not all(isinstance(part, str) for part in command)
            or not isinstance(expected, dict)
            or not isinstance(expected.get("exit_code"), int)
            or isinstance(expected.get("exit_code"), bool)
        ):
            raise ValueError(f"Invalid or duplicate check definition: {check!r}")
        expected_checks[check_id] = check
    return expected_checks


def load_records(path, expected_checks):
    problems = []
    records = []
    if path.is_dir():
        path = path / "results.jsonl"
    if not path.exists():
        return path, records, [f"Evidence file does not exist: {path}"]
    if not path.is_file():
        return path, records, [f"Evidence path is not a file: {path}"]

    try:
        contents = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as error:
        return path, records, [
            f"Unable to read evidence: {type(error).__name__}: {error}"
        ]

    if not contents.strip():
        return path, records, ["Evidence file is empty"]

    seen_ids = set()
    for line_number, line in enumerate(contents.splitlines(), start=1):
        if not line.strip():
            problems.append(f"Line {line_number} is empty")
            continue
        try:
            record = json.loads(line, parse_constant=reject_nonstandard_constant)
        except (json.JSONDecodeError, ValueError) as error:
            problems.append(f"Line {line_number} is malformed JSON: {error}")
            continue
        if not isinstance(record, dict):
            problems.append(f"Line {line_number} must contain a JSON object")
            continue

        missing = sorted(REQUIRED_FIELDS - record.keys())
        if missing:
            problems.append(
                f"Line {line_number} is incomplete; missing fields: {', '.join(missing)}"
            )
            continue
        check_id = record["id"]
        if not isinstance(check_id, str) or not check_id:
            problems.append(f"Line {line_number} has an invalid check id")
            continue
        if check_id in seen_ids:
            problems.append(f"Line {line_number} duplicates check id {check_id!r}")
            continue
        if check_id not in expected_checks:
            problems.append(f"Line {line_number} has unknown check id {check_id!r}")
            continue
        if not isinstance(record["category"], str) or not record["category"]:
            problems.append(f"Line {line_number} has an invalid category")
            continue
        if not isinstance(record["mandatory"], bool):
            problems.append(f"Line {line_number} has an invalid mandatory flag")
            continue
        if not isinstance(record["result"], str) or record["result"] not in VALID_RESULTS:
            problems.append(f"Line {line_number} has an unknown result")
            continue
        if record["exit_code"] is not None and (
            not isinstance(record["exit_code"], int)
            or isinstance(record["exit_code"], bool)
        ):
            problems.append(f"Line {line_number} has an invalid exit code")
            continue
        if not isinstance(record["details"], list) or not all(
            isinstance(detail, str) for detail in record["details"]
        ):
            problems.append(f"Line {line_number} has invalid failure details")
            continue
        if not isinstance(record["command"], list) or not all(
            isinstance(argument, str) for argument in record["command"]
        ):
            problems.append(f"Line {line_number} has an invalid command")
            continue
        if not isinstance(record["expected"], dict):
            problems.append(f"Line {line_number} has an invalid expected outcome")
            continue
        if not isinstance(record["stdout"], str) or not isinstance(record["stderr"], str):
            problems.append(f"Line {line_number} has invalid standard output/error")
            continue
        if not all(
            isinstance(record[field], str) for field in ("stdout_file", "stderr_file")
        ):
            problems.append(f"Line {line_number} has invalid output file references")
            continue
        if not all(
            isinstance(record[field], str) and record[field]
            for field in ("started_at", "ended_at")
        ):
            problems.append(f"Line {line_number} has invalid timestamps")
            continue
        duration = record["duration_seconds"]
        if (
            not isinstance(duration, (int, float))
            or isinstance(duration, bool)
            or duration < 0
            or (isinstance(duration, float) and not math.isfinite(duration))
        ):
            problems.append(f"Line {line_number} has an invalid duration")
            continue

        configured = expected_checks[check_id]
        if record["category"] != configured["category"]:
            problems.append(f"Line {line_number} category does not match configuration")
            continue
        if record["mandatory"] != configured.get("mandatory", True):
            problems.append(f"Line {line_number} mandatory flag does not match configuration")
            continue
        if record["command"] != configured["command"]:
            problems.append(f"Line {line_number} command does not match configuration")
            continue
        if record["expected"] != configured["expected"]:
            problems.append(f"Line {line_number} expected outcome does not match configuration")
            continue

        result = record["result"]
        exit_code = record["exit_code"]
        expected_exit_code = configured["expected"]["exit_code"]
        if result == "passed" and exit_code != expected_exit_code:
            problems.append(f"Line {line_number} passed result conflicts with its exit code")
            continue
        if result == "failed" and (
            exit_code is None or exit_code == expected_exit_code
        ):
            problems.append(f"Line {line_number} failed result conflicts with its exit code")
            continue
        if result in {"blocked", "timed_out", "error", "skipped"} and exit_code is not None:
            problems.append(f"Line {line_number} {result} result has an unexpected exit code")
            continue

        seen_ids.add(check_id)
        records.append(record)

    for check_id in expected_checks:
        if check_id not in seen_ids:
            problems.append(f"Evidence is incomplete; missing check record {check_id!r}")
    if not records and not problems:
        problems.append("Evidence contains no check records")
    return path, records, problems


def output_excerpt(text):
    encoding = sys.stdout.encoding or "utf-8"
    text = text.encode(encoding, errors="backslashreplace").decode(encoding)
    if len(text) <= MAX_OUTPUT_CHARS:
        return text
    return text[:MAX_OUTPUT_CHARS] + "\n... output truncated ..."


def main():
    if len(sys.argv) != 3:
        print("Internal summarizer usage error", file=sys.stderr)
        return 2

    try:
        expected_checks = load_expected_checks(Path(sys.argv[2]).expanduser().resolve())
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2

    supplied_path = Path(sys.argv[1]).expanduser()
    if not supplied_path.is_absolute():
        supplied_path = Path.cwd() / supplied_path
    evidence_path, records, problems = load_records(supplied_path, expected_checks)
    counts = Counter(record["result"] for record in records)
    non_passing = [record for record in records if record["result"] != "passed"]
    mandatory_failures = [record for record in non_passing if record["mandatory"]]
    optional_failures = [record for record in non_passing if not record["mandatory"]]

    print(f"Evidence: {evidence_path}")
    print(f"Run: {evidence_path.parent.name}")
    print(f"Evidence status: {'INVALID/INCOMPLETE' if problems else 'VALID'}")
    print(f"Total checks: {len(records)}")
    print(f"Expected checks: {len(expected_checks)}")
    print(f"Passed checks: {counts['passed']}")
    print(f"Failed checks: {counts['failed']}")
    print(f"Blocked checks: {counts['blocked']}")
    print(f"Timed-out checks: {counts['timed_out']}")
    print(f"Execution errors: {counts['error']}")
    print(f"Skipped checks: {counts['skipped']}")
    print(f"Mandatory failures: {len(mandatory_failures)}")
    print(f"Optional failures: {len(optional_failures)}")

    if non_passing:
        print("\nNon-passing checks:")
        for record in non_passing:
            severity = "MANDATORY FAILURE" if record["mandatory"] else "OPTIONAL FAILURE"
            exit_code = (
                str(record["exit_code"])
                if record["exit_code"] is not None
                else "not available"
            )
            expected = record["expected"]
            expected_exit_code = expected.get("exit_code", "not specified")
            behavior = expected.get("behavior", "not specified")
            print(
                f"- [{severity}] {record['id']} "
                f"(category={record['category']}, result={record['result']}, "
                f"exit_code={exit_code})"
            )
            print(
                f"  Expected: {behavior} (exit code {expected_exit_code}); "
                f"actual: {record['result']} (exit code {exit_code})"
            )
            for detail in record["details"]:
                print(f"  Detail: {output_excerpt(detail)}")
            for label, output in (("stderr", record["stderr"]), ("stdout", record["stdout"])):
                if output.strip():
                    print(f"  {label}:\n{output_excerpt(output).rstrip()}")
            if not record["details"] and not record["stderr"].strip() and not record["stdout"].strip():
                print("  Detail: No additional failure output was recorded.")

    if problems:
        print("\nEvidence problems:")
        for problem in problems:
            print(f"- {problem}")
        return 2
    if mandatory_failures:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
PY

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../../.." && pwd)"
CONFIG="${REPO_ROOT}/part-1a-smart-todo/harness/config/validation.yml"

if [[ "${1:-}" == "--config" ]]; then
  if [[ $# -ne 2 ]]; then
    printf 'Usage: %s [--config CONFIG_PATH]\n' "$0" >&2
    exit 2
  fi
  CONFIG="$2"
elif [[ $# -ne 0 ]]; then
  printf 'Usage: %s [--config CONFIG_PATH]\n' "$0" >&2
  exit 2
fi

if ! command -v python3 >/dev/null 2>&1; then
  printf 'Harness prerequisite missing: python3 is required to read validation.yml\n' >&2
  exit 2
fi

if ! python3 -c 'import yaml' >/dev/null 2>&1; then
  printf 'Harness prerequisite missing: Python PyYAML is required to read validation.yml\n' >&2
  exit 2
fi

exec python3 - "$CONFIG" "$REPO_ROOT" <<'PY'
import datetime
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

import yaml


def timestamp():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="milliseconds")


def resolve_path(root, value):
    path = Path(value)
    return path if path.is_absolute() else root / path


def check_prerequisites(requirements, root):
    failures = []
    for requirement in requirements or []:
        if not isinstance(requirement, str):
            failures.append(f"Invalid prerequisite entry: {requirement!r}")
        elif requirement == "running_smart_todo_service":
            base_url = os.environ.get("SMART_TODO_BASE_URL")
            if not base_url:
                failures.append(
                    "SMART_TODO_BASE_URL is required to verify the running service"
                )
                continue
            try:
                request = urllib.request.Request(
                    base_url.rstrip("/") + "/api/tasks", method="GET"
                )
                with urllib.request.urlopen(request, timeout=5) as response:
                    if response.status != 200:
                        failures.append(
                            f"Service prerequisite returned HTTP {response.status}, expected 200"
                        )
            except (OSError, urllib.error.URLError, ValueError) as error:
                failures.append(
                    f"Service prerequisite unavailable: {type(error).__name__}: {error}"
                )
        elif requirement == "isolated_task_storage":
            storage_value = os.environ.get("SMART_TODO_STORAGE_PATH")
            confirmed = os.environ.get("SMART_TODO_STORAGE_ISOLATED", "").lower()
            if not storage_value:
                failures.append("SMART_TODO_STORAGE_PATH is required")
            elif confirmed != "true":
                failures.append(
                    "SMART_TODO_STORAGE_ISOLATED=true is required to confirm isolated storage"
                )
            else:
                storage_path = Path(storage_value).expanduser().resolve()
                repo_path = root.resolve()
                if storage_path == repo_path or repo_path in storage_path.parents:
                    failures.append(
                        "SMART_TODO_STORAGE_PATH must be outside the repository"
                    )
        elif re.fullmatch(r"[A-Z][A-Z0-9_]*", requirement):
            if not os.environ.get(requirement):
                failures.append(f"Required environment variable is unavailable: {requirement}")
        elif "/" in requirement or "\\" in requirement:
            path = resolve_path(root, requirement)
            if not path.exists():
                failures.append(f"Required file is unavailable: {requirement}")
        elif shutil.which(requirement) is None:
            failures.append(f"Required executable is unavailable: {requirement}")
    return failures


def run_check(check, root, default_cwd):
    started_at = timestamp()
    start_clock = time.monotonic()
    check_id = check["id"]
    stdout = ""
    stderr = ""
    exit_code = None
    details = []
    command = check["command"]
    result = "failed"

    prerequisite_failures = check_prerequisites(check.get("prerequisites"), root)
    if prerequisite_failures:
        result = "blocked"
        details.extend(prerequisite_failures)
    else:
        cwd_value = check.get("working_directory", default_cwd)
        cwd = resolve_path(root, cwd_value)
        if not cwd.is_dir():
            result = "error"
            details.append(f"Working directory does not exist: {cwd}")
        else:
            try:
                completed = subprocess.run(
                    command,
                    cwd=cwd,
                    capture_output=True,
                    text=True,
                    errors="replace",
                    timeout=check["timeout_seconds"],
                    check=False,
                    shell=False,
                )
                stdout = completed.stdout
                stderr = completed.stderr
                exit_code = completed.returncode
                expected_exit_code = check["expected"]["exit_code"]
                result = "passed" if exit_code == expected_exit_code else "failed"
                if result == "failed":
                    details.append(
                        f"Expected exit code {expected_exit_code}; actual exit code {exit_code}"
                    )
            except subprocess.TimeoutExpired as error:
                result = "timed_out"
                stdout = error.stdout or ""
                stderr = error.stderr or ""
                if isinstance(stdout, bytes):
                    stdout = stdout.decode(errors="replace")
                if isinstance(stderr, bytes):
                    stderr = stderr.decode(errors="replace")
                details.append(
                    f"Check exceeded timeout of {check['timeout_seconds']} seconds"
                )
            except OSError as error:
                result = "error"
                details.append(
                    f"Unable to execute command: {type(error).__name__}: {error}"
                )

    ended_at = timestamp()
    return {
        "id": check_id,
        "category": check.get("category"),
        "mandatory": check.get("mandatory", True),
        "command": command,
        "working_directory": str(
            resolve_path(root, check.get("working_directory", default_cwd))
        ),
        "started_at": started_at,
        "ended_at": ended_at,
        "duration_seconds": round(time.monotonic() - start_clock, 3),
        "exit_code": exit_code,
        "result": result,
        "details": details,
        "expected": check.get("expected"),
    }, stdout, stderr


def main():
    if len(sys.argv) != 3:
        print("Internal runner usage error", file=sys.stderr)
        return 2

    config_path = Path(sys.argv[1]).expanduser().resolve()
    repo_root = Path(sys.argv[2]).resolve()
    try:
        with config_path.open(encoding="utf-8") as stream:
            config = yaml.safe_load(stream)
        if not isinstance(config, dict) or config.get("version") != 1:
            raise ValueError("configuration must be a version 1 mapping")
        execution = config["execution"]
        evidence = config["evidence"]
        checks = config["checks"]
        if not isinstance(checks, list) or not checks:
            raise ValueError("checks must be a non-empty ordered list")
        for check in checks:
            if (
                not isinstance(check, dict)
                or not re.fullmatch(r"[A-Za-z0-9_-]+", check.get("id", ""))
                or not isinstance(check.get("command"), list)
                or not check["command"]
                or not all(isinstance(part, str) for part in check["command"])
                or not isinstance(check.get("timeout_seconds"), (int, float))
                or check["timeout_seconds"] <= 0
                or not isinstance(check.get("expected"), dict)
                or not isinstance(check["expected"].get("exit_code"), int)
            ):
                raise ValueError(f"invalid check definition: {check!r}")
        if not isinstance(execution.get("continue_after_check_failure"), bool):
            raise ValueError("continue_after_check_failure must be a boolean")
        if not isinstance(execution.get("expected_success_exit_code"), int):
            raise ValueError("expected_success_exit_code must be an integer")
        if not isinstance(execution.get("final_exit_code_on_mandatory_failure"), int):
            raise ValueError("final_exit_code_on_mandatory_failure must be an integer")
    except (OSError, KeyError, TypeError, ValueError, yaml.YAMLError) as error:
        print(
            f"Unable to load validation configuration {config_path}: {error}",
            file=sys.stderr,
        )
        return 2

    default_cwd = config["project"].get("working_directory", ".")
    passed_root = resolve_path(repo_root, evidence["passed_runs"])
    failed_root = resolve_path(repo_root, evidence["failed_runs"])
    run_id = (
        datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        + "-"
        + uuid.uuid4().hex[:12]
    )
    staging_root = Path(os.path.commonpath([passed_root, failed_root]))
    staging_root.mkdir(parents=True, exist_ok=True)
    staging_dir = staging_root / f".{run_id}.in-progress"
    staging_dir.mkdir()
    evidence_file = staging_dir / "results.jsonl"

    results = []
    mandatory_failure = False
    with evidence_file.open("w", encoding="utf-8") as records:
        for check in checks:
            record, stdout, stderr = run_check(check, repo_root, default_cwd)
            safe_id = record["id"]
            stdout_name = f"{safe_id}.stdout.txt"
            stderr_name = f"{safe_id}.stderr.txt"
            (staging_dir / stdout_name).write_text(stdout, encoding="utf-8")
            (staging_dir / stderr_name).write_text(stderr, encoding="utf-8")
            record["stdout_file"] = stdout_name
            record["stderr_file"] = stderr_name
            record["stdout"] = stdout
            record["stderr"] = stderr
            records.write(json.dumps(record, sort_keys=True) + "\n")
            records.flush()
            results.append(record)
            if record["mandatory"] and record["result"] != "passed":
                mandatory_failure = True
            print(f"{record['id']}: {record['result']}")
            if (
                record["result"] != "passed"
                and not execution["continue_after_check_failure"]
            ):
                for remaining in checks[len(results) :]:
                    skipped_at = timestamp()
                    skipped = {
                        "id": remaining["id"],
                        "category": remaining.get("category"),
                        "mandatory": remaining.get("mandatory", True),
                        "command": remaining["command"],
                        "started_at": skipped_at,
                        "ended_at": skipped_at,
                        "duration_seconds": 0,
                        "exit_code": None,
                        "result": "skipped",
                        "details": ["Not run because continue_after_check_failure is false"],
                        "expected": remaining.get("expected"),
                        "stdout_file": f"{remaining['id']}.stdout.txt",
                        "stderr_file": f"{remaining['id']}.stderr.txt",
                        "stdout": "",
                        "stderr": "",
                    }
                    (staging_dir / skipped["stdout_file"]).write_text("", encoding="utf-8")
                    (staging_dir / skipped["stderr_file"]).write_text("", encoding="utf-8")
                    records.write(json.dumps(skipped, sort_keys=True) + "\n")
                    records.flush()
                    results.append(skipped)
                    mandatory_failure = mandatory_failure or skipped["mandatory"]
                    print(f"{skipped['id']}: skipped")
                break

    destination_root = failed_root if mandatory_failure else passed_root
    destination_root.mkdir(parents=True, exist_ok=True)
    destination = destination_root / run_id
    if destination.exists():
        print(f"Refusing to overwrite existing evidence: {destination}", file=sys.stderr)
        return 2
    shutil.move(str(staging_dir), str(destination))
    print(f"Evidence: {destination}")
    if mandatory_failure:
        return execution["final_exit_code_on_mandatory_failure"]
    return execution["expected_success_exit_code"]


if __name__ == "__main__":
    sys.exit(main())
PY

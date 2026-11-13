import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "summarize-failures.sh"
)


def make_check(check_id, category, mandatory=True):
    return {
        "id": check_id,
        "category": category,
        "mandatory": mandatory,
        "command": ["tool", check_id],
        "timeout_seconds": 30,
        "expected": {
            "exit_code": 0,
            "behavior": f"{check_id} completes successfully.",
        },
    }


def make_record(check, result="passed", exit_code=0, details=None):
    return {
        "id": check["id"],
        "category": check["category"],
        "mandatory": check["mandatory"],
        "command": check["command"],
        "started_at": "2026-11-12T00:00:00.000+00:00",
        "ended_at": "2026-11-12T00:00:01.000+00:00",
        "duration_seconds": 1.0,
        "exit_code": exit_code,
        "result": result,
        "details": details or [],
        "expected": check["expected"],
        "stdout_file": f"{check['id']}.stdout.txt",
        "stderr_file": f"{check['id']}.stderr.txt",
        "stdout": "",
        "stderr": "",
    }


class SummarizeFailuresTest(unittest.TestCase):
    def run_summary(self, checks, contents=None, *, as_directory=False):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        config_path = root / "validation.yml"
        config_path.write_text(
            json.dumps({"version": 1, "checks": checks}), encoding="utf-8"
        )
        evidence_dir = root / "run-001"
        evidence_dir.mkdir()
        evidence_path = evidence_dir / "results.jsonl"
        if contents is not None:
            evidence_path.write_text(contents, encoding="utf-8")
        supplied_path = evidence_dir if as_directory else evidence_path
        result = subprocess.run(
            [
                "bash",
                str(SCRIPT),
                "--config",
                str(config_path),
                str(supplied_path),
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        return result, evidence_path

    def test_successful_run_is_summarized_from_run_directory(self):
        checks = [make_check("build", "build"), make_check("tests", "tests")]
        content = "".join(
            json.dumps(make_record(check)) + "\n" for check in checks
        )

        result, evidence_path = self.run_summary(checks, content, as_directory=True)

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Evidence status: VALID", result.stdout)
        self.assertIn("Total checks: 2", result.stdout)
        self.assertIn("Passed checks: 2", result.stdout)
        self.assertIn("Mandatory failures: 0", result.stdout)
        self.assertEqual(content, evidence_path.read_text(encoding="utf-8"))

    def test_mixed_results_distinguish_mandatory_and_optional_failures(self):
        checks = [
            make_check("passed", "build"),
            make_check("failed", "formatting"),
            make_check("optional-failed", "optional", mandatory=False),
            make_check("blocked", "environment"),
            make_check("timed-out", "tests"),
            make_check("execution-error", "tools", mandatory=False),
        ]
        records = [
            make_record(checks[0]),
            make_record(checks[1], "failed", 1, ["Expected exit code 0; actual exit code 1"]),
            make_record(checks[2], "failed", 1, ["Optional check failed"]),
            make_record(checks[3], "blocked", None, ["Missing prerequisite: service"]),
            make_record(checks[4], "timed_out", None, ["Check exceeded timeout"]),
            make_record(checks[5], "error", None, ["Unable to execute command"]),
        ]
        content = "".join(json.dumps(record) + "\n" for record in records)

        result, _ = self.run_summary(checks, content)

        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        for expected_line in (
            "Total checks: 6",
            "Passed checks: 1",
            "Failed checks: 2",
            "Blocked checks: 1",
            "Timed-out checks: 1",
            "Execution errors: 1",
            "Mandatory failures: 3",
            "Optional failures: 2",
            "[MANDATORY FAILURE] failed",
            "[OPTIONAL FAILURE] optional-failed",
            "category=formatting",
            "actual: failed (exit code 1)",
            "Expected exit code 0; actual exit code 1",
        ):
            self.assertIn(expected_line, result.stdout)

    def test_missing_empty_malformed_and_incomplete_evidence_never_pass(self):
        checks = [make_check("one", "build"), make_check("two", "tests")]
        partial_record = json.dumps(make_record(checks[0])) + "\n"
        incomplete_record = make_record(checks[0])
        del incomplete_record["details"]
        cases = (
            ("missing", None, "Evidence file does not exist"),
            ("empty", "", "Evidence file is empty"),
            ("malformed", '{"id":\n', "malformed JSON"),
            ("incomplete-record", json.dumps(incomplete_record) + "\n", "incomplete"),
            ("missing-check", partial_record, "missing check record 'two'"),
        )

        for name, content, expected_problem in cases:
            with self.subTest(name=name):
                result, evidence_path = self.run_summary(checks, content)
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertIn("Evidence status: INVALID/INCOMPLETE", result.stdout)
                self.assertIn(expected_problem, result.stdout)
                if content is not None:
                    self.assertEqual(content, evidence_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()

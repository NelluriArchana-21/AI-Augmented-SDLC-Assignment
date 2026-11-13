import json
import os
import shutil
import subprocess
import tempfile
import threading
import unittest
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit
from pathlib import Path


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "scripts"
    / "validate-api-blackbox.sh"
)
PRIORITY_ORDER = {"HIGH": 0, "MEDIUM": 1, "LOW": 2}
ALLOWED_TRANSITIONS = {
    ("TODO", "IN_PROGRESS"),
    ("IN_PROGRESS", "COMPLETED"),
    ("COMPLETED", "TODO"),
}


class MockTaskApi:
    def __init__(self):
        self.tasks = []
        self.accept_invalid_priority = False
        self.lock = threading.Lock()

    def add_existing_task(self):
        with self.lock:
            task = {
                "id": str(uuid.uuid4()),
                "title": "Existing user task",
                "description": None,
                "priority": "HIGH",
                "status": "TODO",
                "tags": ["work"],
            }
            self.tasks.append(task)
            return task.copy()

    def handler(self):
        api = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, _format, *_args):
                return

            def send_json(self, status, value):
                body = json.dumps(value, separators=(",", ":")).encode()
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def send_empty(self, status):
                self.send_response(status)
                self.send_header("Content-Length", "0")
                self.end_headers()

            def error(self, status, message):
                self.send_json(status, {"error": message})

            def read_body(self):
                length = int(self.headers.get("Content-Length", "0"))
                return json.loads(self.rfile.read(length) or b"{}")

            @staticmethod
            def normalize_tags(tags):
                result = []
                for tag in tags or []:
                    normalized = tag.strip().lower()
                    if normalized and normalized not in result:
                        result.append(normalized)
                return result

            def do_GET(self):
                parsed = urlsplit(self.path)
                if parsed.path == "/api/tasks":
                    query = parse_qs(parsed.query)
                    status = query.get("status", [None])[0]
                    priority = query.get("priority", [None])[0]
                    tag = query.get("tag", [None])[0]
                    if status is not None and status not in {
                        "TODO",
                        "IN_PROGRESS",
                        "COMPLETED",
                    }:
                        self.error(400, "Invalid status")
                        return
                    if priority is not None and priority not in PRIORITY_ORDER:
                        self.error(400, "Invalid priority")
                        return
                    normalized_tag = tag.strip().lower() if tag is not None else None
                    with api.lock:
                        selected = [
                            task.copy()
                            for task in api.tasks
                            if (status is None or task["status"] == status)
                            and (priority is None or task["priority"] == priority)
                            and (normalized_tag is None or normalized_tag in task["tags"])
                        ]
                    selected.sort(key=lambda task: PRIORITY_ORDER[task["priority"]])
                    self.send_json(200, selected)
                    return

                task_id = parsed.path.removeprefix("/api/tasks/")
                with api.lock:
                    task = next((item.copy() for item in api.tasks if item["id"] == task_id), None)
                if task is None:
                    self.error(404, "Task not found")
                else:
                    self.send_json(200, task)

            def do_POST(self):
                if self.path != "/api/tasks":
                    self.error(404, "Not found")
                    return
                task = self.read_body()
                if task.get("id") is not None:
                    self.error(400, "Client ID is not allowed")
                    return
                title = task.get("title")
                if not isinstance(title, str) or not title.strip() or len(title) > 120:
                    self.error(400, "Invalid title")
                    return
                priority = task.get("priority")
                if priority not in PRIORITY_ORDER:
                    if not api.accept_invalid_priority:
                        self.error(400, "Invalid priority")
                        return
                    priority = "LOW"
                status = task.get("status")
                if status not in {"TODO", "IN_PROGRESS", "COMPLETED"}:
                    self.error(400, "Invalid status")
                    return
                created = {
                    "id": str(uuid.uuid4()),
                    "title": title,
                    "description": task.get("description"),
                    "priority": priority,
                    "status": status,
                    "tags": self.normalize_tags(task.get("tags")),
                }
                with api.lock:
                    api.tasks.append(created)
                self.send_json(201, created)

            def do_PUT(self):
                task_id = urlsplit(self.path).path.removeprefix("/api/tasks/")
                update = self.read_body()
                with api.lock:
                    current = next((item for item in api.tasks if item["id"] == task_id), None)
                    if current is None:
                        self.error(404, "Task not found")
                        return
                    if update.get("id") is not None and update["id"] != task_id:
                        self.error(400, "Task ID is immutable")
                        return
                    next_status = update.get("status")
                    if next_status != current["status"] and (
                        current["status"],
                        next_status,
                    ) not in ALLOWED_TRANSITIONS:
                        self.error(400, "Invalid status transition")
                        return
                    if next_status == current["status"]:
                        self.send_json(200, current.copy())
                        return
                    if update.get("priority") not in PRIORITY_ORDER:
                        self.error(400, "Invalid priority")
                        return
                    current.update(
                        {
                            "title": update.get("title"),
                            "description": update.get("description"),
                            "priority": update["priority"],
                            "status": next_status,
                            "tags": self.normalize_tags(update.get("tags")),
                        }
                    )
                    self.send_json(200, current.copy())

            def do_DELETE(self):
                task_id = urlsplit(self.path).path.removeprefix("/api/tasks/")
                with api.lock:
                    task_index = next(
                        (index for index, task in enumerate(api.tasks) if task["id"] == task_id),
                        None,
                    )
                    if task_index is None:
                        self.error(404, "Task not found")
                        return
                    del api.tasks[task_index]
                self.send_empty(204)

        return Handler


@unittest.skipUnless(
    shutil.which("curl") and shutil.which("jq"),
    "curl and jq are required to exercise the black-box script",
)
class ValidateApiBlackboxTest(unittest.TestCase):
    def setUp(self):
        self.api = MockTaskApi()
        self.baseline_task = self.api.add_existing_task()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), self.api.handler())
        self.server_thread = threading.Thread(target=self.server.serve_forever)
        self.server_thread.start()
        self.addCleanup(self.server_thread.join)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def run_script(self):
        with tempfile.TemporaryDirectory() as isolated_storage:
            environment = os.environ.copy()
            environment.update(
                {
                    "SMART_TODO_BASE_URL": (
                        f"http://127.0.0.1:{self.server.server_port}"
                    ),
                    "SMART_TODO_STORAGE_PATH": (
                        Path(isolated_storage) / "tasks.json"
                    ).as_posix(),
                    "SMART_TODO_STORAGE_ISOLATED": "true",
                }
            )
            return subprocess.run(
                ["bash", str(SCRIPT)],
                capture_output=True,
                text=True,
                timeout=60,
                env=environment,
                check=False,
            )

    def assert_jsonl_evidence(self, output):
        records = [json.loads(line) for line in output.splitlines()]
        self.assertTrue(records)
        self.assertEqual("blackbox-api-summary", records[-1]["check"])
        for record in records[:-1]:
            self.assertIn("timestamp", record)
            self.assertIn("method", record)
            self.assertIn("path", record)
            self.assertIn("request", record)
            self.assertIn("expected", record)
            self.assertIn("actual", record)
            self.assertIn(record["result"], {"passed", "failed"})
        return records

    def test_contract_scenarios_pass_and_only_test_owned_tasks_are_deleted(self):
        result = self.run_script()

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stderr)
        self.assertNotIn("127.0.0.1", result.stdout)
        records = self.assert_jsonl_evidence(result.stdout)
        checks = {record["check"] for record in records}
        self.assertTrue(
            {
                "create-lifecycle",
                "create-missing-title",
                "create-blank-title",
                "create-too-long-title",
                "create-invalid-priority",
                "create-invalid-status",
                "create-client-supplied-id",
                "get-by-id",
                "update-valid",
                "same-status-noop",
                "immutable-id",
                "invalid-status-transition",
                "status-filter",
                "priority-filter",
                "normalized-tag-filter",
                "combined-and-filter",
                "priority-sort-stable-ties",
                "invalid-status-filter",
                "invalid-priority-filter",
                "delete-owned-task",
                "cleanup-discover-owned",
            }.issubset(checks)
        )
        self.assertEqual(
            [self.baseline_task],
            self.api.tasks,
            "The pre-existing user task must remain and all test-created tasks must be removed",
        )
        self.assertEqual("not_attempted", records[-1]["restart_persistence"]["result"])

    def test_unexpected_acceptance_of_invalid_input_fails_and_is_cleaned_up(self):
        self.api.accept_invalid_priority = True

        result = self.run_script()

        self.assertNotEqual(0, result.returncode)
        records = self.assert_jsonl_evidence(result.stdout)
        invalid_priority = next(
            record for record in records if record["check"] == "create-invalid-priority"
        )
        self.assertEqual("failed", invalid_priority["result"])
        self.assertEqual(
            [self.baseline_task],
            self.api.tasks,
            "Unexpectedly created test data must still be removed without deleting baseline data",
        )

    def test_base_url_is_required_and_no_http_request_is_sent_without_it(self):
        with tempfile.TemporaryDirectory() as isolated_storage:
            environment = os.environ.copy()
            environment.pop("SMART_TODO_BASE_URL", None)
            environment.update(
                {
                    "SMART_TODO_STORAGE_PATH": (
                        Path(isolated_storage) / "tasks.json"
                    ).as_posix(),
                    "SMART_TODO_STORAGE_ISOLATED": "true",
                }
            )
            result = subprocess.run(
                ["bash", str(SCRIPT)],
                capture_output=True,
                text=True,
                timeout=10,
                env=environment,
                check=False,
            )

        self.assertEqual(2, result.returncode)
        self.assertIn("SMART_TODO_BASE_URL", result.stderr)
        self.assertEqual(1, len(self.api.tasks))


if __name__ == "__main__":
    unittest.main()

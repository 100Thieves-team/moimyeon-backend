#!/usr/bin/env python3

import json
import os
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


SCRIPT = Path(__file__).with_name("notify-api-spec-change.sh")


class PayloadHandler(BaseHTTPRequestHandler):
    payload: dict | None = None

    def do_POST(self) -> None:
        content_length = int(self.headers["Content-Length"])
        type(self).payload = json.loads(self.rfile.read(content_length))
        self.send_response(200)
        self.end_headers()

    def log_message(self, format: str, *args: object) -> None:
        return


class NotifyApiSpecChangeTest(unittest.TestCase):
    def base_env(self, diff_path: Path) -> dict[str, str]:
        return {
            **os.environ,
            "API_SPEC_DIFF_FILE": str(diff_path),
            "API_SPEC_BRANCH": "dev",
            "API_SPEC_SHA": "a" * 40,
            "API_SPEC_ACTOR": "backend-developer",
            "API_SPEC_RUN_URL": "https://github.com/example/repository/actions/runs/1",
        }

    def test_sends_structured_payload_with_changed_operations(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            diff_path = Path(directory) / "diff.tsv"
            diff_path.write_text(
                "ADDED\tPOST\t/v1/members\n"
                "REMOVED\tDELETE\t/v1/members/{memberId}\n"
                "CHANGED\tGET\t/v1/members/{memberId}\n",
                encoding="utf-8",
            )
            PayloadHandler.payload = None
            server = ThreadingHTTPServer(("127.0.0.1", 0), PayloadHandler)
            thread = threading.Thread(target=server.handle_request, daemon=True)
            thread.start()

            env = self.base_env(diff_path)
            env["SLACK_WEBHOOK_URL"] = f"http://127.0.0.1:{server.server_port}/slack"
            subprocess.run(["bash", str(SCRIPT)], env=env, check=True, capture_output=True, text=True)

            thread.join(timeout=3)
            server.server_close()

        self.assertIsNotNone(PayloadHandler.payload)
        payload = PayloadHandler.payload or {}
        self.assertEqual("[dev] API 스펙 변경 3개 (aaaaaaaaaaaa)", payload["text"])
        change_list = payload["blocks"][2]["text"]["text"]
        self.assertIn("➕ `POST` /v1/members", change_list)
        self.assertIn("➖ `DELETE` /v1/members/{memberId}", change_list)
        self.assertIn("✏️ `GET` /v1/members/{memberId}", change_list)

    def test_missing_webhook_is_a_visible_non_blocking_warning(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            diff_path = Path(directory) / "diff.tsv"
            diff_path.write_text("CHANGED\tGET\t/v1/members\n", encoding="utf-8")
            env = self.base_env(diff_path)
            env.pop("SLACK_WEBHOOK_URL", None)

            result = subprocess.run(
                ["bash", str(SCRIPT)],
                env=env,
                check=True,
                capture_output=True,
                text=True,
            )

        self.assertIn("::warning::SLACK_API_SPEC_WEBHOOK_URL", result.stdout)

    def test_long_change_list_is_escaped_and_truncated_for_slack(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            diff_path = Path(directory) / "diff.tsv"
            diff_path.write_text(
                "".join(
                    f"CHANGED\tGET\t/v1/&<unsafe>/{index}/{'x' * 250}\n"
                    for index in range(25)
                ),
                encoding="utf-8",
            )
            PayloadHandler.payload = None
            server = ThreadingHTTPServer(("127.0.0.1", 0), PayloadHandler)
            thread = threading.Thread(target=server.handle_request, daemon=True)
            thread.start()

            env = self.base_env(diff_path)
            env["SLACK_WEBHOOK_URL"] = f"http://127.0.0.1:{server.server_port}/slack"
            subprocess.run(["bash", str(SCRIPT)], env=env, check=True, capture_output=True, text=True)

            thread.join(timeout=3)
            server.server_close()

        payload = PayloadHandler.payload or {}
        change_list = payload["blocks"][2]["text"]["text"]
        self.assertLessEqual(len(change_list), 2800)
        self.assertIn("&amp;&lt;unsafe&gt;", change_list)
        self.assertRegex(change_list, r"… 외 \d+개$")


if __name__ == "__main__":
    unittest.main()

import importlib.util
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("pr_brief.py")
spec = importlib.util.spec_from_file_location("pr_brief", SCRIPT)
pr_brief = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pr_brief)

BASE = "a" * 40
HEAD = "b" * 40
REPORT = {
    "summary": "[x] 검토 완료 **승인**",
    "before": "[외부 링크](https://example.org)",
    "after": "<script>alert(1)</script>",
    "impact": ["[x] 확인했습니다", "![이미지](https://example.org/image)"],
    "watch": [],
    "unknowns": [],
}
CHANGES = {
    "files": ["src/example.kt"],
    "diff_truncated": False,
    "commits_truncated": False,
}
ENV = {
    "GITHUB_REPOSITORY": "100Thieves-team/moimyeon-backend",
    "PR_NUMBER": "145",
    "PR_BASE_SHA": BASE,
    "PR_HEAD_SHA": HEAD,
    "GH_TOKEN": "test-token",
}


class PrBriefTest(unittest.TestCase):
    def test_model_text_cannot_create_comment_controls(self):
        report = pr_brief.checked_report(REPORT)
        markdown = pr_brief.render_markdown(
            report, ENV["GITHUB_REPOSITORY"], 145, BASE, HEAD, CHANGES
        )
        self.assertNotIn("- [x] 확인했습니다", markdown)
        self.assertIn("- \\[x\\] 확인했습니다", markdown)
        self.assertNotIn("![이미지](https://example.org/image)", markdown)
        self.assertIn("\\[외부 링크\\]\\(https://example\\.org\\)", markdown)
        self.assertNotIn("<script>", markdown)
        self.assertEqual(markdown.count("- [ ] 확인했습니다."), 1)

    def test_hermes_request_and_validated_response(self):
        calls = []

        def fake_urlopen(request, timeout):
            calls.append((request, timeout))
            if request.get_method() == "GET":
                return io.BytesIO(b"[]")
            payload = {"choices": [{"message": {"content": json.dumps(REPORT)}}]}
            return io.BytesIO(json.dumps(payload).encode())

        with patch.dict(os.environ, {"HERMES_API_KEY": "test-key", "PR_HEAD_SHA": HEAD}):
            with patch.object(pr_brief.urllib.request, "urlopen", fake_urlopen):
                result = pr_brief.ask_hermes({"diff": "test diff", "commits": []})
        self.assertEqual(result["impact"][0], "[x] 확인했습니다")
        preflight, _ = calls[0]
        self.assertEqual(preflight.full_url, "https://hermes.agent.plady.io/p/pr-brief/v1/toolsets")
        request, timeout = calls[1]
        self.assertEqual(request.full_url, "https://hermes.agent.plady.io/p/pr-brief/v1/chat/completions")
        self.assertEqual(timeout, 120)
        self.assertEqual(request.get_header("Authorization"), "Bearer test-key")
        body = json.loads(request.data)
        self.assertEqual(body["model"], "gpt-5.5")
        self.assertEqual(body["tool_choice"], "none")
        self.assertEqual(body["tools"], [])
        self.assertIn("test diff", body["messages"][1]["content"])

    def test_tool_enabled_profile_is_rejected_before_diff_upload(self):
        calls = []

        def fake_urlopen(request, timeout):
            calls.append(request)
            toolsets = [{"enabled": True, "tools": ["wiki_search"]}]
            return io.BytesIO(json.dumps(toolsets).encode())

        with patch.dict(os.environ, {"HERMES_API_KEY": "test-key", "PR_HEAD_SHA": HEAD}):
            with patch.object(pr_brief.urllib.request, "urlopen", fake_urlopen):
                with self.assertRaisesRegex(RuntimeError, "profile exposes tools"):
                    pr_brief.ask_hermes({"diff": "do not upload"})
        self.assertEqual(len(calls), 1)
        self.assertEqual(calls[0].get_method(), "GET")

    def test_invalid_model_json_is_rejected(self):
        payload = {"choices": [{"message": {"content": "not JSON"}}]}
        def fake_urlopen(request, timeout):
            if request.get_method() == "GET":
                return io.BytesIO(b"[]")
            return io.BytesIO(json.dumps(payload).encode())

        with patch.dict(os.environ, {"HERMES_API_KEY": "test-key", "PR_HEAD_SHA": HEAD}):
            with patch.object(pr_brief.urllib.request, "urlopen", fake_urlopen):
                with self.assertRaisesRegex(RuntimeError, "invalid JSON report"):
                    pr_brief.ask_hermes({"diff": "test"})

    def test_changed_base_skips_comment(self):
        current = {"state": "open", "head": {"sha": HEAD}, "base": {"sha": "c" * 40}}
        with patch.dict(os.environ, ENV):
            with patch.object(pr_brief, "api", return_value=current) as api:
                pr_brief.publish()
        api.assert_called_once()

    def test_existing_bot_comment_is_updated(self):
        current = {"state": "open", "head": {"sha": HEAD}, "base": {"sha": BASE}}
        comments = [{"user": {"login": "github-actions[bot]"}, "body": pr_brief.MARKER, "id": 42}]
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "pr-brief-data.json").write_text(
                json.dumps({"report": REPORT, "changes": CHANGES}), encoding="utf-8"
            )
            with patch.dict(os.environ, {**ENV, "RUNNER_TEMP": directory}):
                with patch.object(pr_brief, "api", side_effect=[current, comments, {}]) as api:
                    pr_brief.publish()
        self.assertEqual(api.call_args_list[-1].args[:2], (
            "PATCH", "/repos/100Thieves-team/moimyeon-backend/issues/comments/42"
        ))
        body = api.call_args_list[-1].args[3]["body"]
        self.assertIn("- \\[x\\] 확인했습니다", body)
        self.assertEqual(body.count("- [ ] 확인했습니다."), 1)


if __name__ == "__main__":
    unittest.main()

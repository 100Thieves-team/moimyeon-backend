#!/usr/bin/env python3
"""실제 모델·외부 서비스 없이 stream-json 판정 경계를 확인한다."""
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SCORER = Path(__file__).with_name("score.py").resolve()


class ScoreTest(unittest.TestCase):
    def test_result_events_and_real_calls_determine_the_score(self):
        assistant = lambda text: {"type": "assistant", "message": {"content": [{"type": "text", "text": text}]}}
        success = {"type": "result", "is_error": False, "result": "completed"}
        limit = {"type": "result", "is_error": True, "result": "You reached your usage limit"}
        called = {"type": "assistant", "message": {"content": [
            {"type": "tool_use", "name": "Skill", "input": {"skill": "wiki-sync"}}
        ]}}
        cases = {
            "success-discusses-limit": ([assistant("The API usage limit is 10 calls"), success], "no"),
            "quoted-call": ([assistant("Launching skill: wiki-sync"), success], "no"),
            "auth-failure": ([{"type": "result", "is_error": True, "result": "Not logged in"}], "ERROR"),
            "usage-error": ([limit], "LIMIT"),
            "observed-before-error": ([called, limit], "INVOKED"),
            "turn-limit": ([{"type": "result", "is_error": True, "subtype": "error_max_turns",
                             "result": "usage limit discussion interrupted"}], "INCOMPLETE"),
            "no-result": ([assistant("usage limit")], "ERROR"),
            "empty": ([], "ERROR"),
        }
        with tempfile.TemporaryDirectory(prefix="claude-score-test-") as directory:
            root = Path(directory)
            for name, (events, _) in cases.items():
                (root / f"{name}.jsonl").write_text("\n".join(json.dumps(event) for event in events))
            result = subprocess.run(["python3", str(SCORER), str(root), "wiki-sync"],
                                    capture_output=True, text=True, check=True)
            scores = dict(line.split("\t") for line in result.stdout.splitlines())
            self.assertEqual({name: expected for name, (_, expected) in cases.items()}, scores)


if __name__ == "__main__":
    unittest.main()

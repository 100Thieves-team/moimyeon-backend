#!/usr/bin/env python3
"""실제 원격 대신 임시 bare 저장소에서 문서의 리뷰 리라이트 절차를 검증한다."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

PRE_PUSH = Path(__file__).resolve().parents[3] / ".githooks/pre-push"


class ReviewRewriteTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / "remote.git"
        self.repo = self.root / "repo"
        subprocess.run(["git", "init", "--bare", "-q", "-b", "dev", str(self.remote)], check=True)
        subprocess.run(["git", "init", "-q", "-b", "dev", str(self.repo)], check=True)
        self.git("config", "user.name", "Rewrite test")
        self.git("config", "user.email", "rewrite@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.write("base", "base\n")
        self.save("base")
        self.base = self.git("rev-parse", "HEAD")
        self.git("remote", "add", "origin", str(self.remote))
        self.git("push", "-q", "origin", "dev", "dev:main")
        self.git("checkout", "-qb", "feature")
        self.write("first", "first\n")
        self.save("first")
        self.first = self.git("rev-parse", "HEAD")
        self.write("second", "second\n")
        self.save("second")
        self.git("push", "-q", "origin", "feature")
        self.old_head = self.git("rev-parse", "HEAD")
        hooks = self.root / "hooks"
        hooks.mkdir()
        shutil.copy2(PRE_PUSH, hooks / "pre-push")
        (hooks / "pre-push").chmod(0o755)
        self.git("config", "core.hooksPath", str(hooks))

    def run_git(self, *args, repo=None):
        return subprocess.run(["git", *args], cwd=repo or self.repo, capture_output=True, text=True)

    def git(self, *args, repo=None):
        result = self.run_git(*args, repo=repo)
        self.assertEqual(0, result.returncode, result.stderr)
        return result.stdout.strip()

    def write(self, name, text):
        (self.repo / name).write_text(text)

    def save(self, message):
        self.git("add", "--all")
        self.git("commit", "-qm", message)

    def remote_head(self, branch="feature"):
        return self.git("ls-remote", "origin", f"refs/heads/{branch}").split()[0]

    def push_rewrite(self, expected, branch="feature"):
        return self.run_git("push", f"--force-with-lease=refs/heads/{branch}:{expected}",
                            "origin", f"HEAD:refs/heads/{branch}")

    def amend(self):
        self.write("second", "review fix\n")
        self.git("add", "second")
        self.git("commit", "--amend", "--no-edit", "-q")

    def test_amend_is_intentional_divergence_and_preserves_commit_count(self):
        self.amend()
        self.assertNotEqual(0, self.run_git("merge-base", "--is-ancestor", self.old_head, "HEAD").returncode)
        self.assertEqual(self.old_head, self.remote_head())
        self.assertEqual("2", self.git("rev-list", "--count", "dev..HEAD"))
        comparison = self.git("range-diff", f"dev..{self.old_head}", "dev..HEAD")
        self.assertIn("first", comparison)
        self.assertIn("second", comparison)
        self.assertEqual("second", self.git("diff", "--name-only", self.old_head, "HEAD"))
        self.assertIn("review fix", self.git("diff", self.old_head, "HEAD"))
        result = self.push_rewrite(self.old_head)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.git("rev-parse", "HEAD"), self.remote_head())

    def test_earlier_fixup_is_squashed_before_push(self):
        self.write("first", "earlier review fix\n")
        self.git("add", "first")
        self.git("commit", "--fixup", self.first, "-q")
        self.git("-c", "sequence.editor=true", "rebase", "-i", "--autosquash", "dev")
        self.assertEqual("2", self.git("rev-list", "--count", "dev..HEAD"))
        self.assertNotIn("fixup!", self.git("log", "--format=%s", "dev..HEAD"))
        self.assertEqual("earlier review fix\n", (self.repo / "first").read_text())
        result = self.push_rewrite(self.old_head)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_explicit_lease_rejects_remote_change_even_after_fetch(self):
        other = self.root / "other"
        self.git("clone", "-q", str(self.remote), str(other))
        self.git("config", "user.name", "Other test", repo=other)
        self.git("config", "user.email", "other@example.invalid", repo=other)
        self.git("config", "commit.gpgsign", "false", repo=other)
        self.git("checkout", "-q", "feature", repo=other)
        (other / "other").write_text("other change\n")
        self.git("add", "other", repo=other)
        self.git("commit", "-qm", "other change", repo=other)
        self.git("push", "-q", "origin", "feature", repo=other)
        advanced = self.remote_head()
        self.amend()
        self.git("fetch", "-q", "origin")
        result = self.push_rewrite(self.old_head)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(advanced, self.remote_head())

    def test_protected_branches_reject_non_fast_forward(self):
        self.git("checkout", "-q", "dev")
        self.write("base", "rewritten base\n")
        self.git("add", "base")
        self.git("commit", "--amend", "--no-edit", "-q")
        for branch in ("dev", "main"):
            with self.subTest(branch=branch):
                result = self.push_rewrite(self.base, branch)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("보호 브랜치", result.stdout + result.stderr)
                self.assertEqual(self.base, self.remote_head(branch))

    def test_stack_child_rebases_only_its_own_change(self):
        self.git("checkout", "-qb", "child")
        self.write("child", "child change\n")
        self.save("child")
        old_child = self.git("rev-parse", "HEAD")
        self.git("push", "-q", "origin", "child")
        self.git("checkout", "-q", "feature")
        self.amend()
        self.assertEqual(0, self.push_rewrite(self.old_head).returncode)
        self.git("rebase", "--onto", "feature", self.old_head, "child")
        self.assertEqual(self.git("rev-parse", "feature"), self.git("merge-base", "feature", "child"))
        self.assertEqual("1", self.git("rev-list", "--count", "feature..child"))
        self.assertEqual("child change\n", (self.repo / "child").read_text())
        result = self.push_rewrite(old_child, "child")
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == "__main__":
    unittest.main()

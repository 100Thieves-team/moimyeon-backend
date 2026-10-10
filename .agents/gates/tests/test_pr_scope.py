#!/usr/bin/env python3
"""임시 Git 이력으로 PR 경계·스택 base·커밋 전 변경을 검증한다."""
from pathlib import Path
import subprocess
import tempfile
import unittest

import yaml

SCRIPT = Path(__file__).resolve().parents[1] / "check_pr_scope.py"


class PrScopeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "-q", "-b", "dev")
        self.git("config", "user.name", "Gate test")
        self.git("config", "user.email", "gate@example.invalid")
        self.write("seed", "base\n")
        self.save()
        self.git("checkout", "-qb", "feature")

    def git(self, *args):
        return subprocess.run(["git", "-c", "core.hooksPath=/dev/null", *args], cwd=self.repo,
                              check=True, capture_output=True, text=True).stdout.strip()

    def write(self, name, content="new\n"):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def save(self):
        self.git("add", "--all")
        self.git("commit", "-qm", "fixture")

    def check(self, count, code=0, base="dev", extra=(), cwd=None):
        result = subprocess.run(["python3", str(SCRIPT), "--base", base, *extra],
                                cwd=cwd or self.repo, capture_output=True, text=True)
        self.assertEqual(code, result.returncode, result.stderr + result.stdout)
        if count is not None:
            self.assertIn(f"파일 {count}개", result.stdout)
        return result

    def test_thresholds_include_worklog_and_generated_files(self):
        for count in (20, 21, 41, 50, 51):
            for index in range(count):
                self.write(f".worklog/generated/{index}.md", f"change {index}\n")
            self.save()
            result = self.check(count, int(count > 50))
            self.assertIn("[BLOCK]" if count > 50 else "[PASS]", result.stdout)
            self.assertNotIn("[WARN]", result.stdout)

    def test_rename_delete_add_and_modify_with_special_paths(self):
        self.git("checkout", "dev")
        self.write("rename me", "unique rename content\n")
        self.write("delete me", "obsolete\n")
        self.save()
        self.git("checkout", "feature")
        self.git("merge", "--ff-only", "dev")
        self.git("mv", "rename me", "renamed\n한글 file")
        self.git("rm", "delete me")
        self.write("seed", "changed\n")
        self.write("added\tfile", "added\n")
        self.save()
        self.check(4)

    def test_stack_uses_parent_instead_of_dev(self):
        self.write("parent", "parent\n")
        self.save()
        self.git("branch", "parent")
        self.write("child", "child\n")
        self.save()
        self.check(1, base="parent")
        self.check(2)

    def test_base_movement_does_not_count_other_peoples_changes(self):
        self.write("child", "child\n")
        self.save()
        self.git("checkout", "dev")
        self.write("unrelated", "other change\n")
        self.save()
        self.git("checkout", "feature")
        self.check(1)

    def test_worktree_counts_staged_unstaged_and_untracked_once(self):
        self.write("seed", "staged\n")
        self.git("add", "seed")
        self.write("seed", "unstaged\n")
        self.write(".worklog/new.md", "untracked\n")
        self.check(0)
        self.check(2, extra=("--worktree",))

    def test_worktree_rejects_different_head(self):
        self.write("committed")
        self.save()
        self.check(None, 2, extra=("--head", "dev", "--worktree"))

    def test_staged_new_file_removed_from_worktree_still_counts(self):
        self.write("staged-only", "staged content\n")
        self.git("add", "staged-only")
        (self.repo / "staged-only").unlink()
        self.check(1, extra=("--worktree",))

    def test_staged_change_reverted_on_disk_and_untracked_file_both_count(self):
        self.write("seed", "staged change\n")
        self.git("add", "seed")
        self.write("seed", "base\n")
        self.write("untracked", "another change\n")
        self.check(2, extra=("--worktree",))

    def test_staged_edit_and_unstaged_rename_share_one_change(self):
        content = "unchanged line\n" * 20
        self.git("checkout", "dev")
        self.write("seed", content)
        self.save()
        self.git("checkout", "feature")
        self.git("merge", "--ff-only", "dev")
        self.write("seed", content + "review edit\n")
        self.git("add", "seed")
        (self.repo / "seed").rename(self.repo / "renamed")
        self.check(1, extra=("--worktree",))

    def test_low_similarity_move_is_deletion_and_addition(self):
        self.git("mv", "seed", "moved")
        self.write("moved", "entirely different contents\n")
        self.save()
        self.check(2)

    def test_staged_rename_target_and_unstaged_source_deletion_at_limit(self):
        self.write("renamed", "base\n")
        self.git("add", "renamed")
        (self.repo / "seed").unlink()
        for index in range(49):
            self.write(f"new/{index}", f"new content {index}\n")
        self.check(50, extra=("--worktree",))

    def test_unstaged_rename_at_limit_preserves_index_and_objects(self):
        (self.repo / "seed").rename(self.repo / "renamed seed")
        for index in range(49):
            self.write(f"new/{index}", f"new content {index}\n")
        index_path = self.repo / ".git/index"
        before_index = index_path.read_bytes()
        objects = self.repo / ".git/objects"
        before_objects = set(objects.rglob("*"))
        self.check(50, extra=("--worktree",))
        self.assertEqual(before_index, index_path.read_bytes())
        self.assertEqual(before_objects, set(objects.rglob("*")))

    def test_wrong_ref_and_unrelated_history_fail_closed(self):
        self.check(None, 2, base="missing")
        self.check(None, 2, extra=("--head", "missing"))
        self.git("checkout", "--orphan", "unrelated")
        self.git("commit", "-qm", "other root")
        self.check(None, 2)

    def test_ci_head_is_explicit_not_the_merge_checkout(self):
        self.write("pr-change")
        self.save()
        pr_head = self.git("rev-parse", "HEAD")
        self.write("merge-only-change")
        self.save()
        self.check(1, extra=("--head", pr_head))
        self.check(2)

    def test_invocation_from_subdirectory_counts_whole_repo(self):
        self.write("one/a")
        self.write("two/b")
        self.save()
        self.check(2, cwd=self.repo / "one")


class WorkflowContractTest(unittest.TestCase):
    def test_stack_pr_events_and_explicit_pr_head(self):
        workflows = SCRIPT.parents[2] / ".github/workflows"
        ci = yaml.load((workflows / "ci.yml").read_text(), Loader=yaml.BaseLoader)
        review = yaml.load((workflows / "review-swarm.yml").read_text(), Loader=yaml.BaseLoader)
        for workflow in (ci, review):
            event = workflow["on"]["pull_request"]
            self.assertNotIn("branches", event)
            self.assertNotIn("branches-ignore", event)
            self.assertIn("edited", event["types"])
            self.assertNotIn("pull_request_target", workflow["on"])
        self.assertEqual(["main", "dev"], ci["on"]["push"]["branches"])
        scope = next(step for step in ci["jobs"]["harness-gates"]["steps"]
                     if step.get("name") == "PR file scope")
        # 릴리스 PR(같은 저장소의 dev → main)만 파일 수 검사를 건너뛴다. 다른 PR은 모두 검사한다.
        self.assertTrue(scope["if"].startswith("github.event_name == 'pull_request' && !("))
        for condition in ("github.event.pull_request.base.ref == 'main'",
                          "github.event.pull_request.head.ref == 'dev'",
                          "github.event.pull_request.head.repo.full_name == github.repository"):
            self.assertIn(condition, scope["if"])
        self.assertEqual("${{ github.event.pull_request.base.sha }}", scope["env"]["PR_BASE_SHA"])
        self.assertEqual("${{ github.event.pull_request.head.sha }}", scope["env"]["PR_HEAD_SHA"])
        self.assertIn('--base "${PR_BASE_SHA}" --head "${PR_HEAD_SHA}"', scope["run"])
        self.assertIn("github.event.pull_request.head.repo.full_name == github.repository",
                      review["jobs"]["review"]["if"])


if __name__ == "__main__":
    unittest.main()

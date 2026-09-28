#!/usr/bin/env python3
"""PR base와 head의 merge-base부터 변경 파일 수를 센다. 20 권장, 50 상한.

사용: check_pr_scope.py --base origin/dev [--head HEAD] [--worktree]
--worktree는 현재 HEAD의 커밋 전 변경·미추적 파일도 포함하는 사전 점검이다.
최종 PR 판정은 깨끗한 작업 트리에서 --worktree 없이 다시 수행한다.
종료: 0=통과/권고, 1=50개 초과, 2=비교 불가.
"""
import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

RECOMMENDED = 20
MAX_FILES = 50


def git(*args, env=None):
    result = subprocess.run(["git", *args], capture_output=True, env=env)
    if result.returncode:
        raise ValueError("Git 비교 실패: ref·이력·작업 트리를 확인한다")
    return result.stdout


def commit(ref):
    return git("rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}").decode().strip()


def changed_paths(raw):
    """--name-status -z: rename은 원본·대상 두 경로를 가진 한 변경이다."""
    fields = raw.split(b"\0")
    if fields.pop() != b"":
        raise ValueError("NUL로 끝나지 않는 Git diff")
    paths = set()
    index = 0
    while index < len(fields):
        status = fields[index]
        width = 3 if status.startswith((b"R", b"C")) else 2
        if not status or index + width > len(fields):
            raise ValueError("잘못된 Git diff 항목")
        paths.add(fields[index + width - 1])
        index += width
    return paths


def count_files(base, head="HEAD", worktree=False):
    base_sha, head_sha = commit(base), commit(head)
    bases = git("merge-base", "--all", base_sha, head_sha).splitlines()
    if len(bases) != 1:
        raise ValueError("단일 merge-base를 확정할 수 없다")
    start = bases[0].decode()
    if worktree and head_sha != commit("HEAD"):
        raise ValueError("--worktree는 현재 HEAD에만 사용할 수 있다")
    options = ["--no-ext-diff", "--no-textconv", "--name-status", "-z", "--find-renames=50%"]
    if worktree:
        # 미추적 대상까지 함께 봐야 미스테이지 rename도 한 건이다. 실제 인덱스와
        # object DB는 건드리지 않고 임시 인덱스·객체 저장소에 스냅샷을 만든다.
        objects = os.fsdecode(git("rev-parse", "--path-format=absolute", "--git-path", "objects").rstrip(b"\n"))
        with tempfile.TemporaryDirectory(prefix="pr-scope-") as directory:
            temp = Path(directory)
            (temp / "objects").mkdir()
            env = os.environ.copy()
            alternates = env.get("GIT_ALTERNATE_OBJECT_DIRECTORIES", "")
            env.update(GIT_INDEX_FILE=str(temp / "index"),
                       GIT_OBJECT_DIRECTORY=str(temp / "objects"),
                       GIT_ALTERNATE_OBJECT_DIRECTORIES=objects + (os.pathsep + alternates if alternates else ""))
            git("read-tree", head_sha, env=env)
            git("-c", "core.splitIndex=false", "-c", "core.fsmonitor=false", "add", "--all", "--", ".", env=env)
            raw = git("diff", "--cached", *options, start, "--", env=env)
    else:
        raw = git("diff", *options, start, head_sha, "--")
    return len(changed_paths(raw))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--worktree", action="store_true")
    args = parser.parse_args()
    try:
        # 하위 디렉토리에서 실행해도 PR 전체를 집계한다.
        os.chdir(os.fsdecode(git("rev-parse", "--show-toplevel").rstrip(b"\n")))
        count = count_files(args.base, args.head, args.worktree)
    except (ValueError, OSError) as error:
        print(f"[ERROR] PR 파일 수 확인 불가: {error}", file=sys.stderr)
        return 2
    level = "BLOCK" if count > MAX_FILES else "WARN" if count > RECOMMENDED else "PASS"
    print(f"[{level}] PR 변경 파일 {count}개 (권장 {RECOMMENDED}개 이하, 상한 {MAX_FILES}개)")
    if count > RECOMMENDED:
        print("관심사별 스택 PR로 분할을 검토하고, 21~50개를 유지하면 PR에 사유를 남긴다.")
    return int(count > MAX_FILES)


if __name__ == "__main__":
    sys.exit(main())

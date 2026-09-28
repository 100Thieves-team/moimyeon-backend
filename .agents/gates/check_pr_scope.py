#!/usr/bin/env python3
"""PR base와 head의 merge-base부터 변경 파일 수를 센다. 상한은 50개다.

사용: check_pr_scope.py --base origin/dev [--head HEAD] [--worktree]
--worktree는 현재 HEAD의 커밋 전 변경·미추적 파일도 포함하는 사전 점검이다.
최종 PR 판정은 깨끗한 작업 트리에서 --worktree 없이 다시 수행한다.
종료: 0=통과, 1=50개 초과, 2=비교 불가.
"""
import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

MAX_FILES = 50


def git(*args, env=None):
    result = subprocess.run(["git", *args], capture_output=True, env=env)
    if result.returncode:
        raise ValueError("Git 비교 실패: ref·이력·작업 트리를 확인한다")
    return result.stdout


def commit(ref):
    return git("rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}").decode().strip()


def changed_paths(raw):
    """동일 base의 여러 스냅샷을 합칠 때 rename은 원본 경로로 식별한다."""
    fields = raw.split(b"\0")
    if fields.pop() != b"":
        raise ValueError("NUL로 끝나지 않는 Git diff")
    paths, added, renames = set(), set(), {}
    index = 0
    while index < len(fields):
        status = fields[index]
        width = 3 if status.startswith((b"R", b"C")) else 2
        if not status or index + width > len(fields):
            raise ValueError("잘못된 Git diff 항목")
        paths.add(fields[index + 1] if status.startswith(b"R") else fields[index + width - 1])
        if status.startswith(b"R"):
            renames[fields[index + 2]] = fields[index + 1]
        elif status.startswith((b"A", b"C")):
            added.add(fields[index + width - 1])
        index += width
    return paths, added, renames


def count_paths(snapshots):
    aliases = {}
    for _, _, renames in snapshots:
        for target, source in renames.items():
            aliases.setdefault(target, set()).add(source)
    combined = set()
    for paths, added, _ in snapshots:
        normalized = set(paths)
        for target in added:
            sources = aliases.get(target, set())
            if len(sources) == 1:
                source = next(iter(sources))
                # 대상만 먼저 stage한 rename은 다른 snapshot의 원본과 같은 변경이다.
                # 같은 snapshot에 원본의 별도 변경도 있으면 두 변경을 보존한다.
                if source not in paths:
                    normalized.discard(target)
                    normalized.add(source)
        combined.update(normalized)
    return len(combined)


def count_files(base, head="HEAD", worktree=False):
    base_sha, head_sha = commit(base), commit(head)
    bases = git("merge-base", "--all", base_sha, head_sha).splitlines()
    if len(bases) != 1:
        raise ValueError("단일 merge-base를 확정할 수 없다")
    start = bases[0].decode()
    if worktree and head_sha != commit("HEAD"):
        raise ValueError("--worktree는 현재 HEAD에만 사용할 수 있다")
    options = ["--no-ext-diff", "--no-textconv", "--name-status", "-z", "--find-renames=50%"]
    snapshots = []
    if worktree:
        # 작업 트리에서 되돌리거나 지워도 실제 인덱스의 변경은 다음 커밋에 남는다.
        snapshots.append(changed_paths(git("diff", "--cached", *options, start, "--")))
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
    snapshots.append(changed_paths(raw))
    return count_paths(snapshots)


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
    level = "BLOCK" if count > MAX_FILES else "PASS"
    print(f"[{level}] PR 변경 파일 {count}개 (상한 {MAX_FILES}개)")
    if count > MAX_FILES:
        print("50개 상한을 넘었다. 관심사별로 분할한 뒤 다시 확인한다.")
    return int(count > MAX_FILES)


if __name__ == "__main__":
    sys.exit(main())

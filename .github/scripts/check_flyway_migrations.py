#!/usr/bin/env python3

"""Flyway migration 파일명과 순번 계약을 검사한다."""

import argparse
import re
from collections import defaultdict
from pathlib import Path


DEFAULT_MIGRATION_DIR = Path("storage/db-core/src/main/resources/db/migration")
MIGRATION_NAME = re.compile(
    r"^V(?P<version>[1-9][0-9]*)__"
    r"(?P<description>[a-z0-9]+(?:_[a-z0-9]+)*)\.sql$"
)


def validate_migrations(migration_dir: Path) -> list[str]:
    if not migration_dir.is_dir():
        return [f"Flyway migration 디렉터리가 없다: {migration_dir}"]

    sql_files = sorted(
        path.relative_to(migration_dir)
        for path in migration_dir.rglob("*.sql")
        if path.is_file()
    )
    if not sql_files:
        return ["Flyway migration SQL 파일이 없다."]

    versions: dict[int, list[str]] = defaultdict(list)
    invalid_names: list[str] = []
    for relative_path in sql_files:
        display_name = relative_path.as_posix()
        match = MIGRATION_NAME.fullmatch(relative_path.name)
        if len(relative_path.parts) != 1 or match is None:
            invalid_names.append(display_name)
            continue
        versions[int(match.group("version"))].append(display_name)

    errors: list[str] = []
    if invalid_names:
        errors.append(
            "Flyway migration 경로·파일명 규칙 위반: "
            f"{', '.join(invalid_names)} "
            "(migration 디렉터리 바로 아래 V<연속 순번>__<snake_case 설명>.sql)"
        )

    for version, file_names in sorted(versions.items()):
        if len(file_names) > 1:
            errors.append(
                f"Flyway migration version V{version}가 중복이다: "
                f"{', '.join(file_names)}"
            )

    if invalid_names or any(len(file_names) > 1 for file_names in versions.values()):
        return errors

    for expected, actual in enumerate(sorted(versions), start=1):
        if actual != expected:
            errors.append(
                "Flyway migration 순번이 연속적이지 않다: "
                f"V{expected}가 필요하지만 V{actual}을 찾았다."
            )
            break

    return errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Flyway migration 파일명·중복 version·연속 순번을 검사한다."
    )
    parser.add_argument("migration_dir", nargs="?", type=Path, default=DEFAULT_MIGRATION_DIR)
    return parser.parse_args()


def main() -> int:
    errors = validate_migrations(parse_args().migration_dir)
    for error in errors:
        print(f"::error::{error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

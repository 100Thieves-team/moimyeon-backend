#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from check_flyway_migrations import validate_migrations


class CheckFlywayMigrationsTest(unittest.TestCase):
    def test_accepts_contiguous_versioned_migrations(self) -> None:
        errors = self.validate(
            "V1__baseline.sql",
            "V2__add_member_profile.sql",
            "V3__add_room_v2.sql",
        )

        self.assertEqual([], errors)

    def test_reports_every_file_with_a_duplicate_version(self) -> None:
        errors = self.validate(
            "V1__baseline.sql",
            "V2__add_member.sql",
            "V2__add_room.sql",
        )

        self.assertEqual(
            [
                "Flyway migration version V2가 중복이다: "
                "V2__add_member.sql, V2__add_room.sql"
            ],
            errors,
        )

    def test_rejects_malformed_sql_file_names(self) -> None:
        errors = self.validate(
            "V1__baseline.sql",
            "V02__leading_zero.sql",
            "V2-add-member.sql",
            "R__repeatable.sql",
            "README.md",
        )

        self.assertEqual(
            [
                "Flyway migration 경로·파일명 규칙 위반: R__repeatable.sql, "
                "V02__leading_zero.sql, V2-add-member.sql "
                "(migration 디렉터리 바로 아래 "
                "V<연속 순번>__<snake_case 설명>.sql)"
            ],
            errors,
        )

    def test_rejects_a_gap_in_sequential_versions(self) -> None:
        errors = self.validate("V1__baseline.sql", "V3__add_room.sql")

        self.assertEqual(
            ["Flyway migration 순번이 연속적이지 않다: V2가 필요하지만 V3을 찾았다."],
            errors,
        )

    def test_rejects_a_migration_in_a_nested_directory(self) -> None:
        errors = self.validate(
            "V1__baseline.sql",
            "nested/V2__add_room.sql",
        )

        self.assertEqual(
            [
                "Flyway migration 경로·파일명 규칙 위반: "
                "nested/V2__add_room.sql (migration 디렉터리 바로 아래 "
                "V<연속 순번>__<snake_case 설명>.sql)"
            ],
            errors,
        )

    def test_rejects_a_timestamp_version_without_expanding_the_gap(self) -> None:
        errors = self.validate(
            "V1__baseline.sql",
            "V202609201530__add_room.sql",
        )

        self.assertEqual(
            [
                "Flyway migration 순번이 연속적이지 않다: "
                "V2가 필요하지만 V202609201530을 찾았다."
            ],
            errors,
        )

    def test_requires_at_least_one_migration(self) -> None:
        self.assertEqual(
            ["Flyway migration SQL 파일이 없다."],
            self.validate("README.md"),
        )

    def validate(self, *names: str) -> list[str]:
        with tempfile.TemporaryDirectory() as directory:
            migration_dir = Path(directory)
            for name in names:
                path = migration_dir / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            return validate_migrations(migration_dir)


if __name__ == "__main__":
    unittest.main()

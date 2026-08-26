#!/usr/bin/env python3

import copy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml

from openapi_operation_diff import diff_operations


def base_spec() -> dict:
    return {
        "openapi": "3.0.1",
        "info": {"title": "test", "version": "1"},
        "paths": {
            "/v1/members/{memberId}": {
                "parameters": [
                    {
                        "name": "memberId",
                        "in": "path",
                        "required": True,
                        "schema": {"type": "integer", "format": "int64"},
                    }
                ],
                "get": {
                    "summary": "회원 조회",
                    "responses": {
                        "200": {
                            "description": "OK",
                            "content": {
                                "application/json": {
                                    "schema": {"$ref": "#/components/schemas/MemberResponse"}
                                }
                            },
                        }
                    },
                },
            }
        },
        "components": {
            "schemas": {
                "MemberResponse": {
                    "type": "object",
                    "properties": {
                        "id": {"type": "integer", "format": "int64"},
                        "name": {"type": "string"},
                    },
                },
                "UnusedResponse": {
                    "type": "object",
                    "properties": {"value": {"type": "string"}},
                },
            }
        },
    }


class OpenApiOperationDiffTest(unittest.TestCase):
    def test_added_and_removed_operations_are_reported(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        del after["paths"]["/v1/members/{memberId}"]["get"]
        after["paths"]["/v1/members"] = {
            "post": {"responses": {"201": {"description": "Created"}}}
        }

        changes = diff_operations(before, after)

        self.assertEqual(
            [(change.kind, change.method, change.path) for change in changes],
            [
                ("REMOVED", "GET", "/v1/members/{memberId}"),
                ("ADDED", "POST", "/v1/members"),
            ],
        )

    def test_direct_operation_change_is_reported(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        after["paths"]["/v1/members/{memberId}"]["get"]["deprecated"] = True

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_referenced_schema_change_is_reported(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        after["components"]["schemas"]["MemberResponse"]["properties"]["email"] = {
            "type": "string",
            "format": "email",
        }

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_unused_component_change_is_ignored(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        after["components"]["schemas"]["UnusedResponse"]["properties"]["extra"] = {
            "type": "boolean"
        }

        self.assertEqual([], diff_operations(before, after))

    def test_example_only_change_is_ignored(self) -> None:
        before = base_spec()
        before["paths"]["/v1/members/{memberId}"]["get"]["responses"]["200"]["content"][
            "application/json"
        ]["example"] = {"id": 1, "generatedAt": "2026-08-27T10:00:00"}
        after = copy.deepcopy(before)
        after["paths"]["/v1/members/{memberId}"]["get"]["responses"]["200"]["content"][
            "application/json"
        ]["example"]["generatedAt"] = "2026-08-27T10:01:00"

        self.assertEqual([], diff_operations(before, after))

    def test_summary_and_description_only_changes_are_ignored(self) -> None:
        before = base_spec()
        before["components"]["schemas"]["MemberResponse"]["properties"]["name"][
            "description"
        ] = "회원 이름"
        after = copy.deepcopy(before)
        operation = after["paths"]["/v1/members/{memberId}"]["get"]
        operation["summary"] = "회원 상세 조회"
        operation["description"] = "회원의 공개 프로필을 조회한다."
        after["components"]["schemas"]["MemberResponse"]["properties"]["name"][
            "description"
        ] = "공개 회원 이름"

        self.assertEqual([], diff_operations(before, after))

    def test_schema_properties_named_summary_and_description_are_contractual(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        properties = after["components"]["schemas"]["MemberResponse"]["properties"]
        properties["summary"] = {"type": "string"}
        properties["description"] = {"type": "string"}

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_schema_properties_named_example_are_contractual(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        after["components"]["schemas"]["MemberResponse"]["properties"]["example"] = {
            "type": "string"
        }
        after["components"]["schemas"]["MemberResponse"]["properties"]["examples"] = {
            "type": "array",
            "items": {"type": "string"},
        }

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_header_named_example_is_contractual(self) -> None:
        before = base_spec()
        before["paths"]["/v1/members/{memberId}"]["get"]["responses"]["200"]["headers"] = {
            "example": {"schema": {"type": "string"}}
        }
        after = copy.deepcopy(before)
        after["paths"]["/v1/members/{memberId}"]["get"]["responses"]["200"]["headers"][
            "example"
        ]["schema"]["type"] = "integer"

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_example_keyword_under_property_named_content_is_ignored(self) -> None:
        before = base_spec()
        before["components"]["schemas"]["MemberResponse"]["properties"]["content"] = {
            "type": "string",
            "example": "generated-at-10:00",
        }
        after = copy.deepcopy(before)
        after["components"]["schemas"]["MemberResponse"]["properties"]["content"][
            "example"
        ] = "generated-at-10:01"

        self.assertEqual([], diff_operations(before, after))

    def test_security_scheme_named_example_is_contractual(self) -> None:
        before = base_spec()
        before["components"]["securitySchemes"] = {
            "example": {"type": "http", "scheme": "bearer"}
        }
        before["paths"]["/v1/members/{memberId}"]["get"]["security"] = [{}]
        after = copy.deepcopy(before)
        after["paths"]["/v1/members/{memberId}"]["get"]["security"] = [{"example": []}]

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_discriminator_mapping_named_example_is_contractual(self) -> None:
        before = base_spec()
        before["components"]["schemas"]["MemberResponse"]["discriminator"] = {
            "propertyName": "kind",
            "mapping": {"member": "#/components/schemas/MemberResponse"},
        }
        after = copy.deepcopy(before)
        after["components"]["schemas"]["MemberResponse"]["discriminator"]["mapping"][
            "example"
        ] = "#/components/schemas/MemberResponse"

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_oauth_scope_named_example_is_contractual(self) -> None:
        before = base_spec()
        before["components"]["securitySchemes"] = {
            "oauth": {
                "type": "oauth2",
                "flows": {
                    "authorizationCode": {
                        "authorizationUrl": "https://example.com/authorize",
                        "tokenUrl": "https://example.com/token",
                        "scopes": {"example": "회원 읽기"},
                    }
                },
            }
        }
        before["paths"]["/v1/members/{memberId}"]["get"]["security"] = [
            {"oauth": ["example"]}
        ]
        after = copy.deepcopy(before)
        after["components"]["securitySchemes"]["oauth"]["flows"]["authorizationCode"][
            "scopes"
        ]["example"] = "회원 상세 읽기"

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_server_variable_named_example_is_contractual(self) -> None:
        before = base_spec()
        before["paths"]["/v1/members/{memberId}"]["get"]["servers"] = [
            {
                "url": "https://{example}.example.com",
                "variables": {"example": {"default": "api"}},
            }
        ]
        after = copy.deepcopy(before)
        after["paths"]["/v1/members/{memberId}"]["get"]["servers"][0]["variables"][
            "example"
        ]["default"] = "api-v2"

        changes = diff_operations(before, after)

        self.assertEqual(
            [("CHANGED", "GET", "/v1/members/{memberId}")],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_path_level_parameter_change_marks_each_operation(self) -> None:
        before = base_spec()
        before["paths"]["/v1/members/{memberId}"]["delete"] = {
            "responses": {"204": {"description": "No Content"}}
        }
        after = copy.deepcopy(before)
        after["paths"]["/v1/members/{memberId}"]["parameters"][0]["schema"]["minimum"] = 1

        changes = diff_operations(before, after)

        self.assertEqual(
            [
                ("CHANGED", "DELETE", "/v1/members/{memberId}"),
                ("CHANGED", "GET", "/v1/members/{memberId}"),
            ],
            [(change.kind, change.method, change.path) for change in changes],
        )

    def test_recursive_schema_is_resolved_without_infinite_recursion(self) -> None:
        before = base_spec()
        before["components"]["schemas"]["MemberResponse"]["properties"]["manager"] = {
            "$ref": "#/components/schemas/MemberResponse"
        }
        after = copy.deepcopy(before)
        after["components"]["schemas"]["MemberResponse"]["properties"]["active"] = {
            "type": "boolean"
        }

        changes = diff_operations(before, after)

        self.assertEqual(1, len(changes))
        self.assertEqual("CHANGED", changes[0].kind)

    def test_cli_writes_stable_tsv(self) -> None:
        before = base_spec()
        after = copy.deepcopy(before)
        after["paths"]["/v1/members"] = {
            "post": {"responses": {"201": {"description": "Created"}}}
        }

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            before_path = root / "before.yaml"
            after_path = root / "after.yaml"
            before_path.write_text(yaml.safe_dump(before, allow_unicode=True), encoding="utf-8")
            after_path.write_text(yaml.safe_dump(after, allow_unicode=True), encoding="utf-8")
            script = Path(__file__).with_name("openapi_operation_diff.py")

            result = subprocess.run(
                [sys.executable, str(script), str(before_path), str(after_path)],
                check=True,
                capture_output=True,
                text=True,
            )

        self.assertEqual("ADDED\tPOST\t/v1/members\n", result.stdout)


if __name__ == "__main__":
    unittest.main()

#!/usr/bin/env python3

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml


HTTP_METHODS = frozenset({"get", "put", "post", "delete", "options", "head", "patch", "trace"})
CHANGE_ORDER = {"REMOVED": 0, "ADDED": 1, "CHANGED": 2}
NON_CONTRACT_KEYS = frozenset({"description", "example", "examples", "summary"})
NAMED_MAP_FIELDS = frozenset(
    {
        "$defs",
        "content",
        "dependentSchemas",
        "encoding",
        "headers",
        "links",
        "mapping",
        "parameters",
        "patternProperties",
        "properties",
        "requestBodies",
        "responses",
        "schemas",
        "scopes",
        "securitySchemes",
        "variables",
    }
)
ROLE_OBJECT = "object"
ROLE_NAMED_MAP = "named-map"
ROLE_SECURITY_LIST = "security-list"
ROLE_CALLBACK_MAP = "callback-map"


@dataclass(frozen=True)
class OperationChange:
    kind: str
    method: str
    path: str


class OpenApiDiffError(ValueError):
    pass


def load_spec(path: Path) -> dict[str, Any]:
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, yaml.YAMLError) as error:
        raise OpenApiDiffError(f"{path} OpenAPI 문서를 읽을 수 없다: {error}") from error

    if not isinstance(document, dict):
        raise OpenApiDiffError(f"{path} OpenAPI 루트는 object여야 한다.")
    if not isinstance(document.get("paths"), dict):
        raise OpenApiDiffError(f"{path} OpenAPI paths는 object여야 한다.")
    return document


def decode_pointer_token(token: str) -> str:
    return token.replace("~1", "/").replace("~0", "~")


def resolve_local_ref(spec: dict[str, Any], ref: str) -> Any:
    if not ref.startswith("#/"):
        return None

    current: Any = spec
    for raw_token in ref[2:].split("/"):
        token = decode_pointer_token(raw_token)
        if isinstance(current, dict) and token in current:
            current = current[token]
        else:
            raise OpenApiDiffError(f"존재하지 않는 local $ref: {ref}")
    return current


def child_role(parent_role: str, key: str, value: Any) -> str:
    if parent_role == ROLE_NAMED_MAP:
        return ROLE_OBJECT
    if parent_role == ROLE_CALLBACK_MAP:
        return ROLE_NAMED_MAP
    if key == "security" and isinstance(value, list):
        return ROLE_SECURITY_LIST
    if key == "callbacks" and isinstance(value, dict):
        return ROLE_CALLBACK_MAP
    if key in NAMED_MAP_FIELDS and isinstance(value, dict):
        return ROLE_NAMED_MAP
    return ROLE_OBJECT


def expand_refs(
    node: Any,
    spec: dict[str, Any],
    ref_stack: tuple[str, ...] = (),
    role: str = ROLE_OBJECT,
) -> Any:
    if isinstance(node, list):
        item_role = ROLE_NAMED_MAP if role == ROLE_SECURITY_LIST else ROLE_OBJECT
        return [expand_refs(item, spec, ref_stack, item_role) for item in node]

    if not isinstance(node, dict):
        return node

    ref = node.get("$ref")
    if not isinstance(ref, str) or not ref.startswith("#/"):
        return {
            key: expand_refs(value, spec, ref_stack, child_role(role, key, value))
            for key, value in node.items()
            if role != ROLE_OBJECT or key not in NON_CONTRACT_KEYS
        }

    siblings = {
        key: expand_refs(value, spec, ref_stack, child_role(role, key, value))
        for key, value in node.items()
        if key != "$ref" and (role != ROLE_OBJECT or key not in NON_CONTRACT_KEYS)
    }
    if ref in ref_stack:
        return {"$ref": ref, "$cycle": True, **siblings}

    resolved = resolve_local_ref(spec, ref)
    return {
        "$ref": ref,
        "$resolved": expand_refs(resolved, spec, (*ref_stack, ref)),
        **siblings,
    }


def security_context(operation: dict[str, Any], spec: dict[str, Any]) -> dict[str, Any]:
    requirements = operation.get("security", spec.get("security"))
    if not isinstance(requirements, list):
        return {}

    scheme_names = {
        name
        for requirement in requirements
        if isinstance(requirement, dict)
        for name in requirement
    }
    schemes = spec.get("components", {}).get("securitySchemes", {})
    if not isinstance(schemes, dict):
        schemes = {}

    return {
        "security": requirements,
        "securitySchemes": {name: schemes[name] for name in sorted(scheme_names) if name in schemes},
    }


def operation_fingerprints(spec: dict[str, Any]) -> dict[tuple[str, str], str]:
    fingerprints: dict[tuple[str, str], str] = {}
    paths = spec["paths"]

    for path, path_item in paths.items():
        if not isinstance(path, str) or not isinstance(path_item, dict):
            raise OpenApiDiffError("OpenAPI path key와 path item 형식이 올바르지 않다.")

        path_context = {
            key: value
            for key, value in path_item.items()
            if key.lower() not in HTTP_METHODS
        }
        for raw_method, operation in path_item.items():
            method = raw_method.lower()
            if method not in HTTP_METHODS:
                continue
            if not isinstance(operation, dict):
                raise OpenApiDiffError(f"{method.upper()} {path} operation은 object여야 한다.")

            contract = {
                "pathContext": path_context,
                "operation": operation,
                "securityContext": security_context(operation, spec),
            }
            expanded = expand_refs(contract, spec)
            fingerprints[(method.upper(), path)] = json.dumps(
                expanded,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )

    return fingerprints


def diff_operations(before: dict[str, Any], after: dict[str, Any]) -> list[OperationChange]:
    before_operations = operation_fingerprints(before)
    after_operations = operation_fingerprints(after)
    changes: list[OperationChange] = []

    for method, path in before_operations.keys() - after_operations.keys():
        changes.append(OperationChange(kind="REMOVED", method=method, path=path))
    for method, path in after_operations.keys() - before_operations.keys():
        changes.append(OperationChange(kind="ADDED", method=method, path=path))
    for method, path in before_operations.keys() & after_operations.keys():
        if before_operations[(method, path)] != after_operations[(method, path)]:
            changes.append(OperationChange(kind="CHANGED", method=method, path=path))

    return sorted(changes, key=lambda change: (CHANGE_ORDER[change.kind], change.path, change.method))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="두 OpenAPI 문서에서 추가·삭제·변경된 HTTP operation을 TSV로 출력한다."
    )
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        changes = diff_operations(load_spec(args.before), load_spec(args.after))
    except OpenApiDiffError as error:
        print(f"OpenAPI 비교 실패: {error}", file=sys.stderr)
        return 2

    for change in changes:
        print(f"{change.kind}\t{change.method}\t{change.path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

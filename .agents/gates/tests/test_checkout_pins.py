#!/usr/bin/env python3
"""Checkout 개수가 아니라 모든 실행 스텝의 승인된 SHA pin을 검증한다."""

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]
CHECKER_PATH = ROOT / ".agents/gates/check_checkout_pins.py"
APPROVED_CHECKOUT = "actions/checkout@11d5960a326750d5838078e36cf38b85af677262"

SPEC = importlib.util.spec_from_file_location("check_checkout_pins", CHECKER_PATH)
CHECKER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKER)


def workflow_with_checkouts(*references):
    return {
        "jobs": {
            f"job_{index}": {
                "runs-on": "ubuntu-latest",
                "steps": [{"uses": reference}],
            }
            for index, reference in enumerate(references)
        }
    }


class CheckoutPinTests(unittest.TestCase):
    def test_actual_ci_workflow_passes(self):
        workflow = yaml.safe_load((ROOT / ".github/workflows/ci.yml").read_text())
        self.assertEqual([], CHECKER.validate_workflow(workflow))

    def test_any_positive_number_of_approved_checkout_steps_passes(self):
        for count in (1, 2, 3, 4):
            with self.subTest(count=count):
                workflow = workflow_with_checkouts(*([APPROVED_CHECKOUT] * count))
                self.assertEqual([], CHECKER.validate_workflow(workflow))

    def test_multiple_approved_checkout_steps_in_one_job_pass(self):
        workflow = {
            "jobs": {
                "build": {
                    "steps": [
                        {"uses": APPROVED_CHECKOUT},
                        {"uses": APPROVED_CHECKOUT, "with": {"path": "second"}},
                        {"run": "true"},
                    ]
                }
            }
        }
        self.assertEqual([], CHECKER.validate_workflow(workflow))

    def test_unknown_full_sha_fails_even_with_an_approved_checkout(self):
        workflow = workflow_with_checkouts(
            APPROVED_CHECKOUT, "actions/checkout@" + "0" * 40
        )
        self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_mutable_or_unpinned_checkout_fails_even_with_an_approved_checkout(self):
        for reference in (
            "actions/checkout@v4",
            "actions/checkout@v6",
            "actions/checkout@main",
            "actions/checkout",
            " actions/checkout@v4 ",
            "Actions/Checkout@v4",
        ):
            with self.subTest(reference=reference):
                workflow = workflow_with_checkouts(APPROVED_CHECKOUT, reference)
                self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_invalid_checkout_in_same_job_as_approved_checkout_fails(self):
        workflow = {
            "jobs": {
                "build": {
                    "steps": [
                        {"uses": APPROVED_CHECKOUT},
                        {"uses": "actions/checkout@v4"},
                    ]
                }
            }
        }
        self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_conditional_step_cannot_bypass_pin_validation(self):
        workflow = workflow_with_checkouts(APPROVED_CHECKOUT, "actions/checkout@v4")
        workflow["jobs"]["job_1"]["steps"][0]["if"] = False
        self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_approved_reference_in_comments_and_shell_text_does_not_count(self):
        for reference in (None, "actions/checkout@v4"):
            with self.subTest(reference=reference):
                checkout = f"      - uses: {reference}\n" if reference else ""
                workflow = yaml.safe_load(
                    f"# uses: {APPROVED_CHECKOUT}\n"
                    "jobs:\n"
                    "  build:\n"
                    "    steps:\n"
                    "      - run: |\n"
                    f"          echo 'uses: {APPROVED_CHECKOUT}'\n"
                    f"{checkout}"
                )
                self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_checkout_absence_fails(self):
        workflow = {"jobs": {"build": {"steps": [{"run": "true"}]}}}
        self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_mutable_reference_in_comments_and_run_text_is_not_an_action(self):
        workflow = yaml.safe_load(
            "# uses: actions/checkout@v4\n"
            "jobs:\n"
            "  build:\n"
            "    steps:\n"
            f"      - uses: {APPROVED_CHECKOUT}\n"
            "      - run: |\n"
            "          echo 'uses: actions/checkout@v4'\n"
        )
        self.assertEqual([], CHECKER.validate_workflow(workflow))

    def test_malformed_workflow_structure_fails_closed(self):
        for workflow in (None, [], "workflow", {}, {"jobs": None}, {"jobs": []}, {"jobs": {}}):
            with self.subTest(workflow=workflow):
                self.assertTrue(CHECKER.validate_workflow(workflow))

    def test_cli_default_checks_actual_workflow(self):
        result = subprocess.run(
            [sys.executable, str(CHECKER_PATH)],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_cli_exit_code_distinguishes_approved_and_invalid_workflows(self):
        documents = (
            (yaml.safe_dump(workflow_with_checkouts(APPROVED_CHECKOUT)), 0),
            (yaml.safe_dump(workflow_with_checkouts("actions/checkout@v4")), 1),
            ("jobs: [unterminated", 1),
            ("jobs: []\n", 1),
            ("jobs:\n  build:\n    steps:\n      - run: true\n", 1),
        )
        with tempfile.TemporaryDirectory(prefix="checkout-pin-test-") as directory:
            path = Path(directory) / "workflow.yml"
            for document, expected_exit in documents:
                with self.subTest(document=document):
                    path.write_text(document, encoding="utf-8")
                    result = subprocess.run(
                        [sys.executable, str(CHECKER_PATH), str(path)],
                        cwd=ROOT,
                        capture_output=True,
                        text=True,
                    )
                    self.assertEqual(
                        expected_exit, result.returncode, result.stdout + result.stderr
                    )


if __name__ == "__main__":
    unittest.main()

import copy
import importlib.util
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / "infra/terraform/scripts"


def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


config_module = load("deploy_config")
task_module = load("prepare_ecs_task")
SHA = "b" * 40
API_ARN = "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-dev-core-api:95"
WORKER_ARN = "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-dev-core-worker:50"


def config_document():
    config = {key: "example" for key in config_module.OUTPUTS}
    config.update({
        "aws_region": "ap-northeast-2",
        "role_arn": "arn:aws:iam::123456789012:role/deploy",
        "ecs_task_definition": API_ARN,
        "worker_ecs_task_definition": WORKER_ARN,
    })
    return {
        "schema_version": 1, "environment": "dev", "source_sha": SHA,
        "run_id": "123", "run_attempt": "2", "config": config,
    }


def task_template(worker=False):
    container = "core-worker" if worker else "core-api"
    return {
        "taskDefinitionArn": WORKER_ARN if worker else API_ARN,
        "family": f"moimyeon-dev-{container}", "status": "ACTIVE", "revision": 50 if worker else 95,
        "registeredAt": "2026-09-11", "registeredBy": "terraform",
        "deregisteredAt": "response-only-regression-fixture", "futureResponseField": "ignored",
        "compatibilities": ["EC2"], "requiresAttributes": [],
        "taskRoleArn": "arn:aws:iam::123456789012:role/task",
        "executionRoleArn": "arn:aws:iam::123456789012:role/execution",
        "networkMode": "awsvpc", "cpu": "256", "memory": "512",
        "requiresCompatibilities": ["EC2"], "volumes": [],
        "runtimePlatform": {"cpuArchitecture": "X86_64", "operatingSystemFamily": "LINUX"},
        "enableFaultInjection": False,
        "containerDefinitions": [{
            "name": container, "image": "example/old:image",
            "environment": [
                {"name": "APP_RELEASE", "value": "old"},
                {"name": "MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED", "value": "true"},
                {"name": "SENTRY_ENABLED", "value": "true"},
            ],
            "secrets": [{"name": "SENTRY_DSN", "valueFrom": "arn:aws:ssm:ap-northeast-2:123456789012:parameter/example"}],
            "logConfiguration": {"logDriver": "awslogs", "options": {"awslogs-group": "dev"}},
            "healthCheck": {"command": ["CMD", "true"]},
        }, {"name": "sidecar", "image": "example/sidecar:unchanged"}],
    }


class DeploymentConfigTest(unittest.TestCase):
    def validate(self, document):
        return config_module.validate(document, SHA, "123", "2")

    def test_exact_source_run_attempt(self):
        self.assertEqual(self.validate(config_document())["ecs_task_definition"], API_ARN)

    def test_rejects_other_source_environment_run_or_attempt(self):
        for key, value in [("source_sha", "a" * 40), ("environment", "live"), ("run_id", "124"),
                           ("run_attempt", "1"), ("schema_version", 2)]:
            with self.subTest(key=key):
                document = config_document()
                document[key] = value
                with self.assertRaises(ValueError):
                    self.validate(document)

    def test_rejects_missing_extra_or_secret_fields(self):
        for mode in ("missing", "extra", "secret"):
            document = config_document()
            if mode == "missing":
                del document["config"]["ecs_task_definition"]
            elif mode == "extra":
                document["extra"] = "unexpected"
            else:
                document["config"]["SENTRY_DSN"] = "not-an-allowed-output"
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.validate(document)

    def test_rejects_empty_and_actions_or_shell_injection(self):
        for value in ("", "None\ninjected=true", "$(echo bad)", 'bad"', "bad`command`", None):
            document = config_document()
            document["config"]["ecs_cluster"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.validate(document)

    def test_rejects_wrong_template_account_region_family_or_unpinned_revision(self):
        for value in (API_ARN.replace(":95", ""), API_ARN.replace("dev", "live"),
                      API_ARN.replace("123456789012", "999999999999"),
                      API_ARN.replace("ap-northeast-2", "us-east-1"), WORKER_ARN):
            document = config_document()
            document["config"]["ecs_task_definition"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.validate(document)

    def test_export_reads_only_explicit_outputs_via_official_wrapper(self):
        values = config_document()["config"]
        reverse = {output: values[key] for key, output in config_module.OUTPUTS.items()}

        def fake_run(command, **kwargs):
            self.assertEqual(command[:4], ["bash", str(SCRIPTS / "terraform-command.sh"), "output-raw", "dev"])
            self.assertTrue(kwargs["check"])
            return subprocess.CompletedProcess(command, 0, reverse[command[4]] + "\n", "")

        with patch.object(config_module.subprocess, "run", side_effect=fake_run) as mocked:
            self.assertEqual(config_module.export_config(SHA, "123", "2"), config_document())
            self.assertEqual(mocked.call_count, len(config_module.OUTPUTS))

    def test_cli_ignores_old_repository_variable_and_uses_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            document = Path(directory) / "config.json"
            output = Path(directory) / "output"
            document.write_text(json.dumps(config_document()))
            env = dict(os.environ, GITHUB_OUTPUT=str(output), MOIMYEON_ECS_TASK_DEFINITION_DEV=API_ARN.replace(":95", ":43"))
            result = subprocess.run([
                sys.executable, str(SCRIPTS / "deploy_config.py"), "read", "--file", str(document),
                "--sha", SHA, "--run-id", "123", "--attempt", "2",
            ], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(f"ecs_task_definition={API_ARN}\n", output.read_text())
            self.assertNotIn(":43", output.read_text())

    def test_invalid_document_emits_no_partial_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            document = Path(directory) / "config.json"
            output = Path(directory) / "output"
            invalid = config_document()
            invalid["run_attempt"] = "1"
            document.write_text(json.dumps(invalid))
            result = subprocess.run([
                sys.executable, str(SCRIPTS / "deploy_config.py"), "read", "--file", str(document),
                "--sha", SHA, "--run-id", "123", "--attempt", "2",
            ], env=dict(os.environ, GITHUB_OUTPUT=str(output)), capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output.exists())


class EcsRegistrationTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("aws"), "AWS CLI is not installed")
    def test_request_passes_real_aws_cli_shape_validation_offline(self):
        for worker in (False, True):
            template = task_template(worker)
            request = task_module.prepare(template, template["taskDefinitionArn"],
                                          template["containerDefinitions"][0]["name"], "example/new:image", SHA)
            # Skeleton output validates input locally; an unreachable endpoint prevents mutation even on regression.
            result = subprocess.run([
                "aws", "ecs", "register-task-definition", "--cli-input-json", json.dumps(request),
                "--generate-cli-skeleton", "output", "--region", "ap-northeast-2", "--no-sign-request",
                "--endpoint-url", "http://127.0.0.1:1", "--cli-connect-timeout", "1", "--cli-read-timeout", "1",
            ], env=dict(os.environ, AWS_EC2_METADATA_DISABLED="true", AWS_PAGER=""),
                capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_api_and_worker_strip_response_only_fields_and_preserve_wiring(self):
        for worker in (False, True):
            with self.subTest(worker=worker):
                template = task_template(worker)
                original = copy.deepcopy(template)
                target = template["containerDefinitions"][0]
                result = task_module.prepare(template, template["taskDefinitionArn"], target["name"], "example/new:image", SHA)
                self.assertEqual(template, original)
                self.assertTrue(set(result) <= task_module.REGISTER_FIELDS)
                for key in ("deregisteredAt", "registeredAt", "taskDefinitionArn", "revision", "futureResponseField"):
                    self.assertNotIn(key, result)
                changed = result["containerDefinitions"][0]
                self.assertEqual(changed["image"], "example/new:image")
                self.assertEqual([item for item in changed["environment"] if item["name"] == "APP_RELEASE"],
                                 [{"name": "APP_RELEASE", "value": SHA}])
                for key in ("secrets", "logConfiguration", "healthCheck"):
                    self.assertEqual(changed[key], target[key])
                self.assertEqual(changed["environment"][:-1], target["environment"][1:])
                self.assertEqual(result["containerDefinitions"][1], template["containerDefinitions"][1])
                for key in task_module.REGISTER_FIELDS - {"containerDefinitions"}:
                    if key in template:
                        self.assertEqual(result[key], template[key])

    def test_firelens_router_and_buffers_survive_image_promotion(self):
        for worker in (False, True):
            with self.subTest(worker=worker):
                template = task_template(worker)
                app = template["containerDefinitions"][0]
                app["dependsOn"] = [{"containerName": "log-router", "condition": "HEALTHY"}]
                app["logConfiguration"] = {"logDriver": "awsfirelens", "options": {"Name": "null", "mode": "non-blocking"}}
                router = {
                    "name": "log-router", "image": "router@sha256:" + "a" * 64,
                    "essential": False, "memory": 128, "cpu": 64,
                    "restartPolicy": {"enabled": True, "restartAttemptPeriod": 60},
                    "firelensConfiguration": {"type": "fluentbit", "options": {
                        "config-file-type": "s3", "config-file-value": "arn:aws:s3:::config/revisions/v1/immutable.conf"}},
                    "mountPoints": [{"sourceVolume": "log-router-buffer", "containerPath": "/buffers", "readOnly": False}],
                }
                template["containerDefinitions"].append(router)
                template["volumes"] = [{"name": "log-router-buffer"}]
                template["memory"] = "928" if worker else "1760"
                result = task_module.prepare(template, template["taskDefinitionArn"], app["name"], "new@sha256:" + "b" * 64, SHA)
                self.assertEqual(result["containerDefinitions"][-1], router)
                self.assertEqual(result["volumes"], template["volumes"])
                self.assertEqual(result["memory"], template["memory"])
                self.assertEqual(result["containerDefinitions"][0]["dependsOn"], app["dependsOn"])
                self.assertEqual(result["containerDefinitions"][0]["logConfiguration"], app["logConfiguration"])

    def test_inactive_api_or_worker_is_rejected(self):
        for worker in (False, True):
            for status in ("INACTIVE", "DELETE_IN_PROGRESS", None):
                template = task_template(worker)
                template["status"] = status
                with self.subTest(worker=worker, status=status), self.assertRaises(ValueError):
                    task_module.validate_template(template, template["taskDefinitionArn"], template["containerDefinitions"][0]["name"])

    def test_wrong_arn_family_or_missing_duplicate_container(self):
        for mode in ("arn", "family", "missing", "duplicate"):
            template = task_template()
            if mode == "arn":
                template["taskDefinitionArn"] = API_ARN.replace(":95", ":43")
            elif mode == "family":
                template["family"] = "another-family"
            elif mode == "missing":
                template["containerDefinitions"] = []
            else:
                template["containerDefinitions"].append(copy.deepcopy(template["containerDefinitions"][0]))
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                task_module.validate_template(template, API_ARN, "core-api")

    def test_missing_environment_and_duplicate_release_are_normalized(self):
        for environment in (None, [], [{"name": "APP_RELEASE", "value": "old"}] * 2):
            template = task_template()
            template["containerDefinitions"][0]["environment"] = environment
            result = task_module.prepare(template, API_ARN, "core-api", "image:new", SHA)
            self.assertEqual(result["containerDefinitions"][0]["environment"], [{"name": "APP_RELEASE", "value": SHA}])

    def test_cli_preflight_failure_has_no_request_output(self):
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / "task.json"
            template = task_template(True)
            template["status"] = "INACTIVE"
            file.write_text(json.dumps(template))
            result = subprocess.run([
                sys.executable, str(SCRIPTS / "prepare_ecs_task.py"), "--file", str(file),
                "--expected-arn", WORKER_ARN, "--container", "core-worker", "--validate-only",
            ], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(result.stdout, "")
            self.assertNotIn("SENTRY_DSN", result.stderr)


class WorkflowWiringTest(unittest.TestCase):
    def test_deploy_uses_exact_artifact_not_mutable_template_variables(self):
        workflow = (ROOT / ".github/workflows/deploy-aws.yml").read_text()
        self.assertNotIn("vars.MOIMYEON_ECS_TASK_DEFINITION_DEV", workflow)
        self.assertNotIn("vars.MOIMYEON_WORKER_ECS_TASK_DEFINITION_DEV", workflow)
        self.assertIn('gh run download "${TERRAFORM_RUN_ID}"', workflow)
        self.assertIn('dev-deploy-config-${DEPLOY_SHA}-${TERRAFORM_RUN_ATTEMPT}', workflow)
        self.assertEqual(workflow.count("prepare_ecs_task.py"), 4)
        self.assertLess(workflow.index("Validate both Terraform task templates"), workflow.index("Build and push image"))
        self.assertLess(workflow.index("Load source-bound Terraform deployment config"), workflow.index("Configure AWS credentials"))
        for line in workflow.splitlines():
            if "task-definition" in line and ".json" in line:
                self.assertIn("${RUNNER_TEMP}/", line, "Task metadata must never enter the Docker build context")

    def test_sync_only_uploads_named_non_secret_config(self):
        workflow = (ROOT / ".github/workflows/terraform-sync-variables.yml").read_text()
        self.assertIn("path: dev-deploy-config.json", workflow)
        self.assertIn("dev-deploy-config-${{ inputs.source_sha }}-${{ github.run_attempt }}", workflow)
        self.assertIn("if-no-files-found: error", workflow)
        self.assertIn("inputs.environment == 'dev'", workflow)
        self.assertLess(workflow.index("Sync deployment variables from current state"), workflow.index("Publish source-bound"))


if __name__ == "__main__":
    unittest.main()

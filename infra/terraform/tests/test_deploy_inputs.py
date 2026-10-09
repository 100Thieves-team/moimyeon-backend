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


ROLE_ARN = "arn:aws:iam::123456789012:role/deploy"
REGISTRY = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com"


def config_document():
    config = {key: "example" for key in config_module.OUTPUTS}
    config.update({
        "aws_region": "ap-northeast-2",
        "role_arn": ROLE_ARN,
        "ecr_repository_url": f"{REGISTRY}/moimyeon/backend",
        "ecs_task_definition": API_ARN,
        "worker_ecs_task_definition": WORKER_ARN,
    })
    return {
        "schema_version": 2, "environment": "dev", "config": config,
        "candidate_repository_urls": {
            "core-api": f"{REGISTRY}/moimyeon/backend-candidate",
            "core-worker": f"{REGISTRY}/moimyeon/worker-candidate",
        },
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


def live_config_document():
    document = config_document()
    document["environment"] = "live"
    document["config"]["ecs_task_definition"] = API_ARN.replace("moimyeon-dev-", "moimyeon-live-")
    document["config"]["worker_ecs_task_definition"] = WORKER_ARN.replace("moimyeon-dev-", "moimyeon-live-")
    # Live promotes from the dev deployment bundle; it has no PR-built candidates.
    document["candidate_repository_urls"] = {}
    return document


class DeploymentConfigTest(unittest.TestCase):
    def validate(self, document):
        return config_module.validate(document, ROLE_ARN)

    def run_cli(self, document, directory, environment=None):
        path = Path(directory) / "config.json"
        output = Path(directory) / "output"
        path.write_text(json.dumps(document))
        extra = ["--environment", environment] if environment else []
        result = subprocess.run([
            sys.executable, str(SCRIPTS / "deploy_config.py"), "read", "--file", str(path), "--role-arn", ROLE_ARN, *extra,
        ], env=dict(os.environ, GITHUB_OUTPUT=str(output)), capture_output=True, text=True)
        return result, output

    def test_valid_live_document(self):
        config, candidates = config_module.validate(live_config_document(), ROLE_ARN, "live")
        self.assertTrue(config["ecs_task_definition"].endswith("moimyeon-live-core-api:95"))
        self.assertEqual(candidates, {})

    def test_live_rejects_dev_document_candidates_or_dev_templates(self):
        with_candidates = live_config_document()
        with_candidates["candidate_repository_urls"] = config_document()["candidate_repository_urls"]
        dev_template = live_config_document()
        dev_template["config"]["ecs_task_definition"] = API_ARN
        for name, document in [("dev document", config_document()), ("candidates", with_candidates),
                               ("dev template", dev_template)]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                config_module.validate(document, ROLE_ARN, "live")
        with self.assertRaises(ValueError):
            self.validate(live_config_document())

    def test_cli_emits_live_templates_without_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            result, output = self.run_cli(live_config_document(), directory, "live")
            self.assertEqual(result.returncode, 0, result.stderr)
            text = output.read_text()
            self.assertIn("name=live\n", text)
            self.assertIn("ecs_task_definition=arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-live-core-api:95\n", text)
            self.assertIn("worker_ecs_task_definition=arn:aws:ecs:ap-northeast-2:123456789012:task-definition/moimyeon-live-core-worker:50\n", text)
            self.assertNotIn("candidate_repository_url", text)

    def test_valid_dev_document(self):
        config, candidates = self.validate(config_document())
        self.assertEqual(config["ecs_task_definition"], API_ARN)
        self.assertEqual(candidates["core-worker"], f"{REGISTRY}/moimyeon/worker-candidate")

    def test_rejects_other_schema_environment_or_role(self):
        for key, value in [("environment", "live"), ("schema_version", 1)]:
            document = config_document()
            document[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.validate(document)
        with self.assertRaises(ValueError):
            config_module.validate(config_document(), "arn:aws:iam::123456789012:role/other")

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

    def test_rejects_candidate_outside_registry_or_missing(self):
        for mode in ("foreign", "deploy-repo", "missing"):
            document = config_document()
            if mode == "foreign":
                document["candidate_repository_urls"]["core-api"] = "999999999999.dkr.ecr.us-east-1.amazonaws.com/x-candidate"
            elif mode == "deploy-repo":
                document["candidate_repository_urls"]["core-api"] = f"{REGISTRY}/moimyeon/backend"
            else:
                del document["candidate_repository_urls"]["core-worker"]
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.validate(document)

    def test_cli_emits_config_and_candidates(self):
        with tempfile.TemporaryDirectory() as directory:
            result, output = self.run_cli(config_document(), directory)
            self.assertEqual(result.returncode, 0, result.stderr)
            text = output.read_text()
            self.assertIn(f"ecs_task_definition={API_ARN}\n", text)
            self.assertIn(f"candidate_repository_url={REGISTRY}/moimyeon/backend-candidate\n", text)
            self.assertIn(f"worker_candidate_repository_url={REGISTRY}/moimyeon/worker-candidate\n", text)

    def test_invalid_document_emits_no_partial_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            invalid = config_document()
            invalid["config"]["SENTRY_DSN"] = "secret-looking"
            result, output = self.run_cli(invalid, directory)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output.exists())
            self.assertNotIn("secret-looking", result.stderr)


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
                app["logConfiguration"] = {"logDriver": "awsfirelens", "options": {"Name": "null", "log-driver-buffer-limit": "256"}}
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


class RunningRevisionTest(unittest.TestCase):
    """MOI-590: the Worker is left alone only when its running revision is the current template."""

    def running(self, template, image="repo:dev-old", release="a" * 40):
        return task_module.prepare(template, template["taskDefinitionArn"], "core-worker", image, release)

    def test_same_when_only_image_and_release_differ(self):
        template = task_template(worker=True)
        running = self.running(template)
        running["containerDefinitions"][0]["environment"].reverse()
        self.assertTrue(task_module.same_runtime(template, WORKER_ARN, "core-worker", running))

    def test_changed_environment_secret_or_size_is_not_same(self):
        for change in (
            lambda c: c["containerDefinitions"][0]["environment"].append({"name": "NEW", "value": "1"}),
            lambda c: c["containerDefinitions"][0]["secrets"].clear(),
            lambda c: c.update({"memory": "1024"}),
            lambda c: c["containerDefinitions"][1].update({"image": "example/sidecar:new"}),
        ):
            with self.subTest(change=change):
                template = task_template(worker=True)
                running = self.running(template)
                change(template)
                self.assertFalse(task_module.same_runtime(template, WORKER_ARN, "core-worker", running))

    def test_running_without_release_is_not_same(self):
        template = task_template(worker=True)
        running = self.running(template)
        running["containerDefinitions"][0]["environment"] = [
            item for item in running["containerDefinitions"][0]["environment"] if item["name"] != "APP_RELEASE"
        ]
        self.assertFalse(task_module.same_runtime(template, WORKER_ARN, "core-worker", running))

    def test_cli_prints_comparison(self):
        template = task_template(worker=True)
        with tempfile.TemporaryDirectory() as directory:
            template_file = Path(directory) / "template.json"
            running_file = Path(directory) / "running.json"
            template_file.write_text(json.dumps(template))
            running_file.write_text(json.dumps(self.running(template)))
            result = subprocess.run(
                [sys.executable, str(SCRIPTS / "prepare_ecs_task.py"), "--file", str(template_file),
                 "--expected-arn", WORKER_ARN, "--container", "core-worker", "--compare-running", str(running_file)],
                capture_output=True, text=True, check=True,
            )
        self.assertEqual(result.stdout.strip(), "same=true")


class WorkflowWiringTest(unittest.TestCase):
    def test_deploy_reads_terraform_published_config_not_mutable_template_variables(self):
        workflow = (ROOT / ".github/workflows/deploy-aws.yml").read_text()
        self.assertNotIn("vars.MOIMYEON_ECS_TASK_DEFINITION_DEV", workflow)
        self.assertNotIn("vars.MOIMYEON_WORKER_ECS_TASK_DEFINITION_DEV", workflow)
        self.assertIn("vars.MOIMYEON_DEPLOY_CONFIG_PARAMETER_DEV", workflow)
        self.assertIn("deploy_config.py read", workflow)
        # Two template validations and two registrations; the Worker comparison
        # lives in decide-worker-change.sh (MOI-590).
        self.assertEqual(workflow.count("prepare_ecs_task.py"), 4)
        self.assertIn("decide-worker-change.sh", workflow)
        self.assertLess(workflow.index("Wait for the Terraform boundary"), workflow.index("Load Terraform deployment config"))
        self.assertLess(workflow.index("Validate both Terraform task templates"), workflow.index("Build and push image"))
        for line in workflow.splitlines():
            if "task-definition" in line and ".json" in line:
                self.assertIn("${RUNNER_TEMP}/", line, "Task metadata must never enter the Docker build context")

    def test_sync_records_applied_dev_boundary(self):
        workflow = (ROOT / ".github/workflows/terraform-sync-variables.yml").read_text()
        self.assertNotIn("dev-deploy-config", workflow)
        self.assertIn("--applied-sha", workflow)
        self.assertIn("terraform_applied_sha_parameter_name", workflow)
        # SSM (deploy boundary) first; the GitHub variable (Terraform skip) is written last.
        self.assertLess(workflow.index("aws ssm put-parameter"), workflow.index("sync-github-variables.sh --env"))
        script = (SCRIPTS / "sync-github-variables.sh").read_text()
        self.assertEqual(script.rindex('set_variable "'),
                         script.index('set_variable "MOIMYEON_TERRAFORM_APPLIED_SHA_DEV"'))


if __name__ == "__main__":
    unittest.main()

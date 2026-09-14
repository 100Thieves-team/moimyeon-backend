"""Offline runtime/dashboard checks, separate from Terraform resource contracts."""

import json
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[3]
OBS = ROOT / "infra/observability"


class MonitoringContractTest(unittest.TestCase):
    def test_pinned_images_and_private_admin_ports(self):
        compose = yaml.safe_load((OBS / "compose.yaml").read_text())
        services = compose["services"]
        self.assertEqual(set(services), {"grafana", "prometheus", "otel-collector"})
        for service in services.values():
            self.assertRegex(service["image"], r":[^@]+@sha256:[0-9a-f]{64}$")
            self.assertIn("no-new-privileges:true", service["security_opt"])
            self.assertEqual(service["cap_drop"], ["ALL"])
            self.assertIn("mem_limit", service)
        for name in ("grafana", "prometheus"):
            self.assertTrue(all(port.startswith("127.0.0.1:") for port in services[name]["ports"]))
        grafana_env = services["grafana"]["environment"]
        self.assertNotIn("GF_SECURITY_ADMIN_PASSWORD", grafana_env)
        self.assertEqual(grafana_env["GF_AUTH_ANONYMOUS_ENABLED"], "false")
        self.assertIn("GF_SECURITY_ADMIN_PASSWORD__FILE", grafana_env)

    def test_metrics_only_and_bounded_resource_labels(self):
        collector = yaml.safe_load((OBS / "otel-collector.yaml").read_text())
        self.assertEqual(set(collector["service"]["pipelines"]), {"metrics"})
        self.assertEqual(collector["service"]["pipelines"]["metrics"]["processors"][0], "memory_limiter")
        exporter = collector["exporters"]["prometheus"]
        self.assertFalse(exporter["send_timestamps"])
        self.assertEqual(exporter["metric_expiration"], "90s")
        self.assertEqual(set(exporter["resource_constant_labels"]["included"]), {
            "service.name", "service.instance.id", "service.version", "deployment.environment.name",
        })
        self.assertNotIn("resource_to_telemetry_conversion", exporter)

    def test_dashboard_semantics_and_sources(self):
        dashboard = json.loads((OBS / "grafana/dashboards/dev-overview.json").read_text())
        ids = [panel["id"] for panel in dashboard["panels"]]
        self.assertEqual(len(ids), len(set(ids)))
        expressions = [target["expr"] for panel in dashboard["panels"] for target in panel.get("targets", [])]
        self.assertTrue(any("time() - observability_heartbeat_seconds" in expr for expr in expressions))
        self.assertTrue(any('absent(observability_heartbeat_seconds' in expr for expr in expressions))
        pending = next(expr for expr in expressions if "notification_worker_pending_messages" in expr)
        self.assertTrue(pending.startswith("max by (consumer_group)"))
        self.assertFalse(any("vector(0)" in expr for expr in expressions))
        for panel in dashboard["panels"]:
            if panel.get("targets"):
                self.assertEqual(panel["datasource"]["uid"], "prometheus")

    def test_bootstrap_preserves_volume_and_never_embeds_password(self):
        template = (OBS / "bootstrap.sh.tftpl").read_text()
        start = (OBS / "start-monitoring.sh").read_text()
        self.assertIn("Requires=moimyeon-monitoring-storage.service", template)
        self.assertIn("Before=docker.service", template)
        self.assertNotIn("enable --now docker", template)
        self.assertIn("sha256sum --check --status", template)
        self.assertIn("wipefs --no-act", start)
        self.assertIn("readlink -f", start)
        self.assertIn("set +x", start)
        self.assertIn("/run/moimyeon-monitoring/grafana_admin_password", start)
        self.assertNotIn("get-parameter", template)
        self.assertNotIn("unit retries until ready", template)
        self.assertIn("Grafana credential preparation failed", start)
        runbook = (OBS / "README.md").read_text()
        self.assertIn("systemctl reset-failed moimyeon-monitoring-storage.service docker.service moimyeon-monitoring.service", runbook)
        self.assertIn("systemctl start moimyeon-monitoring.service", runbook)
        subprocess.run(["bash", "-n", str(OBS / "start-monitoring.sh")], check=True)
        subprocess.run(["bash", "-n", str(OBS / "bootstrap.sh.tftpl")], check=True)

    def test_terraform_plan_covers_runtime_config_changes(self):
        workflow = (ROOT / ".github/workflows/terraform-plan.yml").read_text()
        self.assertIn('"infra/observability/**"', workflow)

    def test_deployment_release_preserves_unrelated_task_wiring(self):
        workflow = (ROOT / ".github/workflows/deploy-aws.yml").read_text()
        # Execute the request builder used by both actual registration steps.
        steps = yaml.safe_load(workflow)["jobs"]["deploy"]["steps"]
        scripts = [re.search(r"python3 (\S+)", step["run"])[1]
                   for step in steps if step.get("id") in {"task_def", "worker_task_def"}]
        self.assertEqual(len(scripts), 2)
        fixture = {
            "taskDefinitionArn": "arn:aws:ecs:ap-northeast-2:123456789012:task-definition/template:1",
            "family": "template", "status": "ACTIVE", "deregisteredAt": "response-only",
            "containerDefinitions": [
                {"name": "target", "image": "old", "environment": [{"name": "APP_RELEASE", "value": "old"}, {"name": "KEEP", "value": "yes"}], "secrets": [{"name": "SENTRY_DSN", "valueFrom": "ssm-ref"}]},
                {"name": "other", "image": "unchanged", "environment": [{"name": "KEEP", "value": "other"}]},
            ],
        }
        for script in scripts:
            with tempfile.TemporaryDirectory() as directory:
                source = Path(directory) / "task.json"
                source.write_text(json.dumps(fixture))
                result = subprocess.run([
                    sys.executable, str(ROOT / script), "--file", str(source),
                    "--expected-arn", fixture["taskDefinitionArn"], "--container", "target",
                    "--image", "repo@sha256:digest", "--release", "a" * 40,
                ], text=True, capture_output=True, check=True)
            output = json.loads(result.stdout)
            target, other = output["containerDefinitions"]
            self.assertEqual(other, fixture["containerDefinitions"][1])
            self.assertEqual(target["secrets"], fixture["containerDefinitions"][0]["secrets"])
            self.assertEqual(target["environment"], [{"name": "KEEP", "value": "yes"}, {"name": "APP_RELEASE", "value": "a" * 40}])
            self.assertNotIn("taskDefinitionArn", output)
            self.assertNotIn("deregisteredAt", output)


if __name__ == "__main__":
    unittest.main()

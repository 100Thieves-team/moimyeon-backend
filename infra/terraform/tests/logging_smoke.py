"""Exercise the pinned production router against isolated S3/CW protocol doubles."""
import gzip
import json
from pathlib import Path
import re
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parents[3]
MODULE = ROOT / "infra/terraform/modules/application-logging"
IMAGE = "public.ecr.aws/aws-observability/aws-for-fluent-bit:3.4.17@sha256:940eee58ec25fc5b92328da54e9c834c0bb4656c76320ceb24be7a8dd4063029"
PYTHON = "python:3.13.7-alpine3.22@sha256:9ba6d8cbebf0fb6546ae71f2a1c14f6ffd2fdab83af7fa5669734ef30ad48844"


def docker(*args, check=True):
    return subprocess.run(["docker", *args], capture_output=True, text=True, check=check, timeout=180)


def cloudwatch_records(data):
    batches = [json.loads(line) for line in (data / "cloudwatch.jsonl").read_text().splitlines()] if (data / "cloudwatch.jsonl").exists() else []
    groups = {}
    for batch in batches:
        groups.setdefault(batch["logGroupName"], set()).update(json.loads(event["message"])["eventCode"] for event in batch["logEvents"])
    return batches, groups


def cloudwatch_complete(data):
    _, groups = cloudwatch_records(data)
    return (groups.get("test-ops") == {"smoke.info", "smoke.warn", "smoke.error", "logging.serialization_failed"}
            and groups.get("test-debug") == {"smoke.trace", "smoke.debug"})


def verify(data):
    assert not (data / "unexpected-action").exists(), "Router attempted an ungranted CloudWatch API"
    objects = []
    for file in data.glob("*.object"):
        assert file.read_bytes()[:2] == b"\x1f\x8b", "S3 objects must be gzip"
        unpacked = gzip.decompress(file.read_bytes())
        assert unpacked.startswith(b"{"), f"NDJSON expected after one decompression: {unpacked[:80]!r}"
        for row in unpacked.decode().splitlines():
            assert row.startswith("{"), f"Invalid NDJSON row: {row[:80]!r}"
            record = json.loads(row)
            kind = "growth" if "/retention=growth/" in file.with_suffix(".key").read_text() else "ops"
            assert record["category"] == kind, "S3 prefix must match the record category"
            objects.append(record)
    archive_codes = {row["eventCode"] for row in objects}
    batches, groups = cloudwatch_records(data)
    expected = {"smoke.info", "smoke.warn", "smoke.error", "smoke.growth", "logging.serialization_failed"}
    if archive_codes != expected or groups.get("test-ops") != expected - {"smoke.growth"} or groups.get("test-debug") != {"smoke.trace", "smoke.debug"}:
        return False
    assert int((data / "put-attempts").read_text()) > 2, "S3 failures must be retried"
    assert "SHOULD_NOT_LEAK" not in json.dumps(objects) + json.dumps(batches)
    assert all(row["service"] == "core-api" and row["environment"] == "test" for row in objects)
    for file in data.glob("*.key"):
        assert re.search(r"/env=test/service=core-api/retention=(ops|growth)/dt=\d{4}-\d{2}-\d{2}/hour=\d{2}/.+\.json\.gz", file.read_text())
    return True


def main():
    assert IMAGE in (MODULE / "main.tf").read_text(), "Smoke image must match the deployed image"
    name = "moi411-" + uuid.uuid4().hex[:10]
    sink, router = name + "-sink", name + "-router"
    # Pull before creating the internal-only network. Test containers cannot contact AWS.
    docker("pull", IMAGE)
    docker("pull", PYTHON)
    with tempfile.TemporaryDirectory(prefix="moi411-routing-") as directory:
        root = Path(directory)
        subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=mock-aws", "-keyout", str(root / "key.pem"), "-out", str(root / "cert.pem")],
                       capture_output=True, check=True, timeout=30)
        data = root / "data"
        data.mkdir()
        (root / "buffers").mkdir()
        values = {
            "container_name": "core-api", "region": "ap-northeast-2", "bucket": "test-logs",
            "environment": "test", "service_name": "core-api", "ops_group": "test-ops", "debug_group": "test-debug",
            "lua_code": (MODULE / "router/v1/sanitize.lua").read_text().strip().replace("\n", " "),
            "upload_timeout": "1s",
            "s3_endpoint_configuration": "endpoint http://mock-aws:4566\n    tls Off",
            "cloudwatch_endpoint_configuration": "endpoint mock-aws\n    port 4567\n    tls.verify Off",
        }
        template = (MODULE / "router/v1/fluent-bit.conf.tftpl").read_text()
        config = re.sub(r"\$\{([a-z0-9_]+)}", lambda match: values[match[1]], template)
        (root / "custom.conf").write_text(config)
        (root / "main.conf").write_text("""[INPUT]
    Name forward
    Listen 0.0.0.0
    Port 24224
@INCLUDE /config/custom.conf
[OUTPUT]
    Name null
    Match core-api-firelens-*
""")
        try:
            docker("network", "create", "--internal", name)
            docker("run", "-d", "--name", sink, "--network", name, "--network-alias", "mock-aws", "-v", f"{data}:/data", "-v", f"{root}:/tls:ro", "-v", f"{Path(__file__).parent}:/src:ro", PYTHON, "python", "/src/logging_mock_aws.py")
            docker("run", "-d", "--name", router, "--network", name, "--network-alias", "router", "--memory", "128m", "--cpus", "0.0625",
                   "-e", "AWS_ACCESS_KEY_ID=test", "-e", "AWS_SECRET_ACCESS_KEY=test", "-e", "AWS_EC2_METADATA_DISABLED=true",
                   "-e", "LOG_SERVICE_NAME=core-api", "-e", "LOG_ENVIRONMENT=test", "-e", "TZ=UTC",
                   "-v", f"{root}:/config:ro", "-v", f"{root / 'buffers'}:/buffers", "--entrypoint", "/fluent-bit/bin/fluent-bit", IMAGE, "-c", "/config/main.conf")
            docker("exec", sink, "python", "/src/logging_send_records.py")
            deadline = time.monotonic() + 50
            restarted = False
            while time.monotonic() < deadline:
                attempts = data / "put-attempts"
                if not restarted and attempts.exists() and int(attempts.read_text()) >= 2 and cloudwatch_complete(data):
                    assert not list(data.glob("*.object")), "S3 outage must leave only buffered data"
                    docker("kill", router)
                    (data / "allow-uploads").touch()
                    docker("start", router)
                    restarted = True
                if verify(data):
                    assert restarted, "Recovery must read the surviving disk buffer"
                    docker("exec", router, "curl", "-fsS", "http://127.0.0.1:2020/")
                    log = docker("logs", router).stderr
                    assert "[error] [config]" not in log and "Failed to send events" not in log, log
                    print("PASS: real Fluent Bit → gzip S3 objects; CW split; debug expiry; field filtering; 503 + killed router → disk recovery")
                    print(docker("stats", "--no-stream", "--format", "{{.MemUsage}}", router).stdout.strip())
                    return
                time.sleep(0.25)
            raise AssertionError("Expected records did not arrive at both destinations")
        finally:
            result = docker("logs", router, check=False)
            if result.stderr:
                print(result.stderr[-6000:])
            docker("rm", "-f", router, sink, check=False)
            docker("network", "rm", name, check=False)


if __name__ == "__main__":
    main()

"""Send real Forward protocol frames using only Python's standard library."""
from datetime import datetime, timedelta, timezone
import json
import socket
import struct
import time


def pack(value):
    if isinstance(value, str):
        data = value.encode()
        return (bytes([0xa0 + len(data)]) if len(data) < 32 else b"\xda" + struct.pack(">H", len(data))) + data
    if isinstance(value, int):
        return b"\xce" + struct.pack(">I", value)
    if isinstance(value, list):
        return bytes([0x90 + len(value)]) + b"".join(pack(item) for item in value)
    if isinstance(value, dict):
        return bytes([0x80 + len(value)]) + b"".join(pack(k) + pack(v) for k, v in value.items())
    raise TypeError(type(value))


now = datetime.now(timezone.utc)
rows = []
for level in ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]:
    rows.append({
        "schemaVersion": 1, "level": level, "eventCode": "smoke." + level.lower(),
        "timestamp": now.isoformat(timespec="milliseconds").replace("+00:00", "Z"),
        "message": "SHOULD_NOT_LEAK", "body": "SHOULD_NOT_LEAK",
        "exceptions": [{"type": "TestException", "message": "SHOULD_NOT_LEAK", "frames": [{"class": "Test", "line": 1, "locals": "SHOULD_NOT_LEAK"}]}],
    })
rows.append({"schemaVersion": 1, "level": "INFO", "eventCode": "smoke.growth", "category": "growth", "timestamp": now.strftime("%Y-%m-%dT%H:%M:%SZ")})
rows.append({"schemaVersion": 1, "level": "DEBUG", "eventCode": "smoke.expired", "timestamp": (now - timedelta(days=4)).strftime("%Y-%m-%dT%H:%M:%SZ")})
rows.append({"level": "ERROR", "eventCode": "logging.serialization_failed"})

for attempt in range(30):
    try:
        connection = socket.create_connection(("router", 24224), timeout=2)
        break
    except OSError:
        time.sleep(0.2)
else:
    raise RuntimeError("Forward input did not start")
with connection:
    for row in rows:
        connection.sendall(pack(["core-api-firelens-fixture", int(now.timestamp()), {"log": json.dumps(row)}]))
    connection.sendall(pack(["core-api-firelens-fixture", int(now.timestamp()), {"log": "SHOULD_NOT_LEAK plain text"}]))

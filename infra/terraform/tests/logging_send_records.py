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
        "message": "smoke message " + level, "body": "SHOULD_NOT_LEAK",
        # Router-owned fields: the app record must not be able to overwrite them.
        "service": "SHOULD_NOT_LEAK", "environment": "SHOULD_NOT_LEAK",
        "category": "growth" if level == "WARN" else "ops",
        "exceptions": [{"type": "TestException", "message": "smoke exception message", "frames": [{"class": "Test", "line": 1, "locals": "SHOULD_NOT_LEAK"}]}],
    })
stamp = now.isoformat(timespec="milliseconds").replace("+00:00", "Z")
# Message limit is 16384 bytes: exactly at the limit passes, a 4-byte character across it
# is dropped whole, and non-string messages are not copied.
rows.append({"schemaVersion": 1, "level": "INFO", "eventCode": "smoke.exact", "message": "a" * 16384, "timestamp": stamp})
rows.append({"schemaVersion": 1, "level": "INFO", "eventCode": "smoke.emoji", "message": "ab" + "\U0001F600" * 4096, "timestamp": stamp})
rows.append({"schemaVersion": 1, "level": "INFO", "eventCode": "smoke.nonstring", "message": 123, "timestamp": stamp})
rows.append({"schemaVersion": 1, "level": "INFO", "eventCode": "smoke.growth", "category": "growth", "timestamp": now.strftime("%Y-%m-%dT%H:%M:%SZ")})
rows.append({"schemaVersion": 1, "level": "DEBUG", "eventCode": "smoke.expired", "timestamp": (now - timedelta(days=4)).strftime("%Y-%m-%dT%H:%M:%SZ")})
rows.append({"level": "ERROR", "eventCode": "logging.serialization_failed", "message": "SHOULD_NOT_LEAK"})
# Docker splits stdout lines over 16 KiB into partial messages. This 3-byte-per-character
# record (about 72 KiB as escaped JSON) must be reassembled before parsing, then clipped.
oversize = json.dumps({"schemaVersion": 1, "level": "ERROR", "eventCode": "smoke.oversize", "timestamp": stamp,
                       "message": "\uac00" * 6000,
                       "exceptions": [{"type": "TestException", "message": "\uac00" * 6000, "frames": []}]})
chunks = [oversize[index:index + 16384] for index in range(0, len(oversize), 16384)]

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
    for ordinal, chunk in enumerate(chunks, start=1):
        connection.sendall(pack(["core-api-firelens-fixture", int(now.timestamp()), {
            "log": chunk, "partial_message": "true", "partial_id": "smoke-oversize",
            "partial_ordinal": str(ordinal), "partial_last": "true" if ordinal == len(chunks) else "false",
        }]))

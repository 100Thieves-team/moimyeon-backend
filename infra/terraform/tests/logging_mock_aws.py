"""Isolated test sink. No AWS credentials are read and no AWS API is called."""
import gzip
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import threading
import ssl

DATA = Path("/data")
LOCK = threading.Lock()
PUTS = 0


def atomic_write(path, body):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_bytes(body)
    temporary.replace(path)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *_args):
        pass

    def reply(self, status, body=b"{}", content_type="application/json"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("x-amzn-RequestId", "smoke-request")
        self.send_header("ETag", '"test-etag"')
        self.end_headers()
        self.wfile.write(body)

    def read_body(self, decompress=True):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        return gzip.decompress(body) if decompress and self.headers.get("Content-Encoding") == "gzip" else body

    def do_PUT(self):
        global PUTS
        body = self.read_body(decompress=False)
        with LOCK:
            PUTS += 1
            atomic_write(DATA / "put-attempts", str(PUTS).encode())
            if not (DATA / "allow-uploads").exists():
                self.reply(503, b"<Error><Code>ServiceUnavailable</Code></Error>", "application/xml")
                return
            key = hashlib.sha256(self.path.encode()).hexdigest()
            atomic_write(DATA / (key + ".key"), self.path.encode())
            atomic_write(DATA / (key + ".object"), body)
        self.reply(200, b"", "application/xml")

    def do_POST(self):
        payload = json.loads(self.read_body())
        action = self.headers.get("X-Amz-Target", "").rsplit(".", 1)[-1]
        if action not in {"CreateLogStream", "PutLogEvents"}:
            (DATA / "unexpected-action").write_text(action)
            self.reply(403, b'{"__type":"AccessDeniedException"}')
            return
        if action == "PutLogEvents":
            with LOCK:
                path = DATA / "cloudwatch.jsonl"
                previous = path.read_bytes() if path.exists() else b""
                atomic_write(path, previous + (json.dumps(payload) + "\n").encode())
        self.reply(200, b'{"nextSequenceToken":"1","logStreams":[]}')


tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
tls.load_cert_chain("/tls/cert.pem", "/tls/key.pem")
secure_server = ThreadingHTTPServer(("0.0.0.0", 4567), Handler)
secure_server.socket = tls.wrap_socket(secure_server.socket, server_side=True)
threading.Thread(target=secure_server.serve_forever, daemon=True).start()
ThreadingHTTPServer(("0.0.0.0", 4566), Handler).serve_forever()

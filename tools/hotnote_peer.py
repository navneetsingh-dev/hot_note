#!/usr/bin/env python3
"""
Hot Note P2P Peer for Linux (Arch/Debian/Fedora), macOS, and Windows laptops.
Zero external dependencies — uses Python 3 standard library only.

Usage:
  1) Start laptop receiver server (so Android tablet can send notes & files to laptop):
     python3 tools/hotnote_peer.py serve --port 8765 --pin 123456 --out ./received

  2) Send a file from laptop to Android tablet:
     python3 tools/hotnote_peer.py send-file 192.168.1.42 8765 123456 ./lecture.pdf

  3) Send a Markdown/text note from laptop to Android tablet:
     python3 tools/hotnote_peer.py send-note 192.168.1.42 8765 123456 "Meeting Notes" ./notes.md
"""

import argparse
import hashlib
import http.server
import json
import os
import socket
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


def get_lan_ips():
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ips.append(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    return ips or ["127.0.0.1"]


def make_handler(out_dir: Path, required_pin: str, peer_name: str):
    out_dir.mkdir(parents=True, exist_ok=True)
    notes_dir = out_dir / "notes"
    notes_dir.mkdir(parents=True, exist_ok=True)

    class HotNoteHandler(http.server.BaseHTTPRequestHandler):
        def _send_json(self, code: int, data: dict):
            body = json.dumps(data).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def _check_pin(self) -> bool:
            if not required_pin:
                return True
            pin = self.headers.get("X-Pair-Pin", "")
            if pin != required_pin:
                self._send_json(401, {"error": "Invalid PIN"})
                return False
            return True

        def do_GET(self):
            if self.path == "/api/info":
                self._send_json(200, {
                    "name": peer_name,
                    "platform": sys.platform,
                    "protocol": 2,
                    "requirePin": bool(required_pin),
                })
                return
            self._send_json(404, {"error": "Not found"})

        def do_POST(self):
            if not self._check_pin():
                return
            length = int(self.headers.get("Content-Length", "0"))
            if self.path == "/api/notes":
                raw = self.rfile.read(min(length, 5 * 1024 * 1024))
                note = json.loads(raw.decode("utf-8"))
                title = note.get("title", "Untitled")
                safe = "".join(c if c.isalnum() or c in " -_." else "_" for c in title).strip() or "note"
                target = notes_dir / f"{safe}.md"
                target.write_text(f"# {title}\n\n{note.get('contentBlocks', '')}\n", encoding="utf-8")
                (notes_dir / f"{safe}.hotnote.json").write_bytes(raw)
                print(f"[RECEIVED NOTE] '{title}' -> {target}")
                self._send_json(200, {"status": "ok"})
                return

            if self.path == "/api/upload":
                raw_name = urllib.parse.unquote(self.headers.get("X-File-Name", f"file_{int(time.time())}"))
                safe_name = os.path.basename(raw_name) or "received_file"
                target = out_dir / safe_name
                idx = 1
                while target.exists():
                    target = out_dir / f"{target.stem}_{idx}{target.suffix}"
                    idx += 1
                h = hashlib.sha256()
                remaining = length
                with open(target, "wb") as f:
                    while remaining > 0:
                        chunk = self.rfile.read(min(65536, remaining))
                        if not chunk:
                            break
                        f.write(chunk)
                        h.update(chunk)
                        remaining -= len(chunk)
                print(f"[RECEIVED FILE] '{target.name}' ({length} bytes, sha256={h.hexdigest()[:12]}...) -> {target}")
                self._send_json(200, {"status": "ok", "fileName": target.name, "sha256": h.hexdigest()})
                return

            self._send_json(404, {"error": "Endpoint not found"})

    return HotNoteHandler


def cmd_serve(args):
    handler = make_handler(Path(args.out), args.pin, args.name)
    server = http.server.ThreadingHTTPServer(("0.0.0.0", args.port), handler)
    ips = get_lan_ips()
    print(f"Hot Note Laptop Peer listening on port {args.port}")
    for ip in ips:
        print(f"  -> Address: {ip}:{args.port}  (PIN: {args.pin or 'none'})")
    print(f"  -> Saving files & notes to: {Path(args.out).resolve()}")

    avahi_proc = None
    try:
        avahi_proc = subprocess.Popen(
            ["avahi-publish", "-s", args.name, "_hotnote._tcp", str(args.port)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        print("  -> Announced on LAN via mDNS (avahi-publish)")
    except FileNotFoundError:
        pass

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping peer...")
    finally:
        if avahi_proc:
            avahi_proc.terminate()
        server.server_close()


def cmd_send_file(args):
    path = Path(args.file)
    if not path.is_file():
        print(f"File not found: {path}", file=sys.stderr)
        sys.exit(1)
    size = path.stat().st_size
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(65536)
            if not chunk:
                break
            h.update(chunk)
    url = f"http://{args.host}:{args.port}/api/upload"
    with open(path, "rb") as f:
        req = urllib.request.Request(
            url,
            data=f,
            method="POST",
            headers={
                "Content-Type": "application/octet-stream",
                "Content-Length": str(size),
                "X-Pair-Pin": args.pin,
                "X-File-Name": urllib.parse.quote(path.name),
                "X-File-Sha256": h.hexdigest(),
                "X-Peer-Name": urllib.parse.quote(socket.gethostname()),
            },
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            print(resp.read().decode("utf-8"))


def cmd_send_note(args):
    content = Path(args.file).read_text(encoding="utf-8") if args.file else args.text
    now = int(time.time() * 1000)
    payload = json.dumps({
        "id": f"laptop_{now}",
        "title": args.title,
        "contentBlocks": content,
        "attachmentsJson": "[]",
        "createdAt": now,
        "updatedAt": now,
        "senderName": socket.gethostname(),
    }).encode("utf-8")
    req = urllib.request.Request(
        f"http://{args.host}:{args.port}/api/notes",
        data=payload,
        method="POST",
        headers={"Content-Type": "application/json", "X-Pair-Pin": args.pin},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        print(resp.read().decode("utf-8"))


def main():
    p = argparse.ArgumentParser(description="Hot Note Laptop <-> Tablet P2P Peer")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("serve", help="Run laptop P2P receiver server")
    s.add_argument("--port", type=int, default=8765)
    s.add_argument("--pin", default="123456")
    s.add_argument("--out", default="./hotnote_received")
    s.add_argument("--name", default=f"Laptop-{socket.gethostname()}")
    s.set_defaults(func=cmd_serve)

    sf = sub.add_parser("send-file", help="Send a file to Android tablet")
    sf.add_argument("host")
    sf.add_argument("port", type=int)
    sf.add_argument("pin")
    sf.add_argument("file")
    sf.set_defaults(func=cmd_send_file)

    sn = sub.add_parser("send-note", help="Send a note to Android tablet")
    sn.add_argument("host")
    sn.add_argument("port", type=int)
    sn.add_argument("pin")
    sn.add_argument("title")
    sn.add_argument("file", nargs="?", default=None)
    sn.add_argument("--text", default="")
    sn.set_defaults(func=cmd_send_note)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

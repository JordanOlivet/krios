"""Minimal Source-RCON client for the Krios dev server.

Usage: python rcon.py "cmd1" "cmd2" ...
Prints each command's response on one line, prefixed with '> cmd'.
"""
import socket
import struct
import sys

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "krios"

AUTH, AUTH_RESPONSE, EXEC, RESPONSE = 3, 2, 2, 0


def send(sock, req_id, ptype, body):
    payload = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def recv(sock):
    raw = b""
    while len(raw) < 4:
        chunk = sock.recv(4 - len(raw))
        if not chunk:
            raise ConnectionError("closed")
        raw += chunk
    (length,) = struct.unpack("<i", raw)
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("closed")
        data += chunk
    req_id, ptype = struct.unpack("<ii", data[:8])
    return req_id, ptype, data[8:-2].decode("utf-8", "replace")


def main():
    sock = socket.create_connection((HOST, PORT), timeout=10)
    send(sock, 1, AUTH, PASSWORD)
    req_id, ptype, _ = recv(sock)
    if req_id == -1:
        print("AUTH FAILED")
        sys.exit(1)
    for i, cmd in enumerate(sys.argv[1:], start=2):
        send(sock, i, EXEC, cmd)
        rid, ptype, body = recv(sock)
        print(f"> {cmd}\n{body}")
    sock.close()


if __name__ == "__main__":
    main()

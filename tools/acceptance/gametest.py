# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""GameTests of the Krios mods on the game test servers of both loaders (ADR-035).

    uv run tools/acceptance/gametest.py [neoforge] [fabric] [--core]

Each run is `gradlew <loader>:runGameTestServer` (NeoForge) or
`gradlew <loader>:runGametest` (Fabric) in mods/industry, which loads core
too: every registered test of both mods. --core adds the same runs in
mods/core, core alone. A run passes when the server exits 0 and its log says
"All N required tests passed". Exit code 1 when any run failed.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TASKS = {"neoforge": "runGameTestServer", "fabric": "runGametest"}
TIMEOUT = 900


def gradle(mod_dir: Path, *args: str) -> list[str]:
    if os.name == "nt":
        return ["cmd", "/c", str(mod_dir / "gradlew.bat"), *args]
    return [str(mod_dir / "gradlew"), *args]


def kill_tree(process: subprocess.Popen) -> None:
    if os.name == "nt":
        subprocess.run(["taskkill", "/T", "/F", "/PID", str(process.pid)], capture_output=True)
    else:
        process.kill()


def run(mod: str, loader: str) -> tuple[bool, str]:
    mod_dir = REPO / "mods" / mod
    log = mod_dir / loader / "runs" / "gametest" / "logs" / "latest.log"
    log.unlink(missing_ok=True)
    out = REPO / "build" / "acceptance" / "logs" / f"gametest-{mod}-{loader}.console.log"
    out.parent.mkdir(parents=True, exist_ok=True)
    start = time.time()
    with open(out, "wb") as sink:
        process = subprocess.Popen(gradle(mod_dir, f"{loader}:{TASKS[loader]}", "--no-daemon", "-q"),
                                   cwd=mod_dir, stdout=sink, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        try:
            code = process.wait(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            kill_tree(process)
            return False, f"no end after {TIMEOUT} s (console: {out.relative_to(REPO)})"
    text = log.read_text(encoding="utf-8", errors="replace") if log.is_file() else ""
    passed = re.search(r"All (\d+) required tests passed", text)
    failed = re.search(r"(\d+) required tests failed", text)
    names = re.findall(r"GameTestServer.*?:\s+- (\S+)", text) or re.findall(r"\(Minecraft\)\s+- (\S+)", text)
    seconds = int(time.time() - start)
    if code == 0 and passed:
        return True, f"{passed.group(1)} tests passed ({seconds} s)"
    if failed:
        return False, f"{failed.group(1)} required tests failed: {', '.join(names)} (exit {code})"
    fatal = re.search(r"(No test functions were given!|Failed to start the minecraft server)", text)
    return False, f"exit {code}, {fatal.group(1) if fatal else 'no test summary in the log'} (console: {out.relative_to(REPO)})"


def main() -> int:
    parser = argparse.ArgumentParser(description="GameTests of the Krios mods (ADR-035).")
    parser.add_argument("loaders", nargs="*", help="neoforge, fabric (both by default)")
    parser.add_argument("--core", action="store_true", help="also run core alone (mods/core)")
    args = parser.parse_args()
    for loader in args.loaders:
        if loader not in TASKS:
            parser.error(f"unknown loader '{loader}' (neoforge or fabric)")
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8", errors="replace")
    loaders = args.loaders or list(TASKS)
    mods = ["industry"] + (["core"] if args.core else [])
    failures = 0
    for mod in mods:
        for loader in loaders:
            print(f"{mod} / {loader}: running the game test server ...", flush=True)
            ok, detail = run(mod, loader)
            failures += not ok
            print(f"{'PASS' if ok else 'FAIL'}  {mod} / {loader}: {detail}", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

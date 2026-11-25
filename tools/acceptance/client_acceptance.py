# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Client acceptance for Krios: a client driven by Rehearsal.

    uv run tools/acceptance/client_acceptance.py [neoforge|fabric] [options]

Runs command files (tools/acceptance/client/*.cmd, Rehearsal's command
language) in a client out of the user's way: its own game directory, window
in the background without the focus, sound off. Every command's reply is
checked, the screenshots are collected, and the game log is searched for
errors. Exit code 1 when a command failed, the game crashed, was disconnected
or timed out.

Two clients:
- the dev client of mods/industry (core is loaded with it), by default;
- --prod: the built jars on the loader pinned in versions.toml, launched by
  portablemc from build/acceptance/client-prod/ (its own game and launcher
  folders), with the pinned recipe viewers.

--server local starts the matching server itself (the dev server, or with
--prod the production server of server_acceptance.py), makes the client's
player an operator, runs the scenarios there and stops it.

The screenshots are the other half of the result: they are for the eye (the
agent's, then the user's for anything new). See README.md.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import tomllib
import uuid
from pathlib import Path

import server_acceptance as sa

REPO = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MOD = REPO / "mods" / "industry"
OUT = REPO / "build" / "acceptance" / "client"
PROD = REPO / "build" / "acceptance" / "client-prod"
WINDOWS = os.name == "nt"
# The player of every client the tool starts: NeoForge's dev name, set on the Fabric dev
# client and on the production one, so a server can make it an operator before it joins.
PLAYER = "Dev"
# The scenarios worth a server (what crosses the wire: menus, synced values, recipes),
# with the viewer both sides load: on Fabric a client without the server's JEI is refused.
SERVER_SUITE = [("screens.cmd", "jei"), ("viewers-jei.cmd", "jei")]

# What a quiet, unattended client needs in options.txt. guiScale 2 on the
# 1280x720 window the run asks for gives a 640x360 GUI: a terminal (256 wide)
# with its annex panels on both sides fits, and so does a recipe viewer's list.
OPTIONS = {
    "soundCategory_master": "0.0",
    "onboardAccessibility": "false",
    "pauseOnLostFocus": "false",
    "skipMultiplayerWarning": "true",
    "tutorialStep": "none",
    "narrator": "0",
    "guiScale": "2",
}

# The standard suite, run when no --scenario is given: each scenario with the
# viewer it is about. A scenario is one client launch (about two minutes).
SUITE = [
    ("screens.cmd", "jei"),
    ("screens.cmd", "emi"),
    ("world.cmd", "none"),
    ("viewers-jei.cmd", "jei"),
    ("viewers-emi.cmd", "emi"),
]


def local_property(key: str) -> str | None:
    """A key of the repository's local.properties (gitignored, machine paths)."""
    file = REPO / "local.properties"
    if not file.exists():
        return None
    for line in file.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith(key + "="):
            return line.split("=", 1)[1].strip()
    return None


def write_options(run_dir: Path) -> None:
    """Sets OPTIONS in the run dir's options.txt, keeping every other line. ASCII, no BOM."""
    file = run_dir / "options.txt"
    lines = file.read_text(encoding="utf-8").splitlines() if file.exists() else []
    seen: set[str] = set()
    out = []
    for line in lines:
        key = line.split(":", 1)[0]
        if key in OPTIONS:
            out.append(f"{key}:{OPTIONS[key]}")
            seen.add(key)
        else:
            out.append(line)
    out += [f"{key}:{value}" for key, value in OPTIONS.items() if key not in seen]
    file.write_text("\n".join(out) + "\n", encoding="ascii", newline="\n")


def rehearsal_jar(loader: str) -> Path:
    checkout = local_property("rehearsal.dir")
    if not checkout:
        sys.exit("local.properties must name a Rehearsal checkout: rehearsal.dir=<path> (see local.properties.example)")
    jars = sorted(Path(checkout, "output").glob(f"rehearsal-{loader}-1.21.1-*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        sys.exit(f"{checkout}/output has no rehearsal-{loader}-1.21.1 jar: run gradlew build there")
    return jars[-1]


def viewer_jars(versions: dict, loader: str, viewer: str) -> list[Path]:
    """The pinned third-party jars a production game of that viewer loads (Fabric API on Fabric)."""
    keep = []
    for jar in sa.third_parties(versions, loader, viewer != "none"):
        name = jar.name
        if (name.startswith("fabric-api-")
                or (viewer in ("jei", "both") and name.startswith(("jei-", "mezz_config")))
                or (viewer in ("emi", "both") and name.startswith("emi-"))):
            keep.append(jar)
    return keep


def prepare(run_dir: Path, loader: str, scenario: Path, mods: list[Path] | None) -> None:
    """The game directory before a launch. mods: the whole mods folder of a production game
    (emptied first), or None for a dev client (Rehearsal's jar only, and only on Fabric)."""
    run_dir.mkdir(parents=True, exist_ok=True)
    write_options(run_dir)
    if loader == "neoforge":
        # NeoForge's early loading window takes the focus before any mod runs.
        fml = run_dir / "config" / "fml.toml"
        fml.parent.mkdir(parents=True, exist_ok=True)
        text = fml.read_text(encoding="utf-8") if fml.exists() else ""
        if "earlyWindowControl" in text:
            text = re.sub(r"earlyWindowControl\s*=\s*\w+", "earlyWindowControl = false", text)
        else:
            text += "earlyWindowControl = false\n"
        fml.write_text(text, encoding="utf-8", newline="\n")
    folder = run_dir / "mods"
    if mods is not None:
        shutil.rmtree(folder, ignore_errors=True)
        folder.mkdir(parents=True)
        for jar in mods:
            shutil.copy2(jar, folder)
    elif loader == "fabric":
        # Loom 1.8 refuses Rehearsal's jar as a mod dependency (built with a newer Loom):
        # the loader takes it from the run's mods folder instead.
        folder.mkdir(exist_ok=True)
        for old in folder.glob("rehearsal-*.jar"):
            old.unlink()
        shutil.copy2(rehearsal_jar("fabric"), folder)
    # A fresh world each run: the scenario creates the one it names.
    for line in scenario.read_text(encoding="utf-8").splitlines():
        words = line.split()
        if len(words) >= 2 and words[0] == "world":
            shutil.rmtree(run_dir / "saves" / words[1], ignore_errors=True)
    rehearsal = run_dir / "rehearsal"
    shutil.rmtree(rehearsal / "shots", ignore_errors=True)
    (rehearsal / "outbox.jsonl").unlink(missing_ok=True)
    # The previous run's log says "inbox on" too, and two runs of a suite are seconds
    # apart: without it, only this run's log can answer.
    (run_dir / "logs" / "latest.log").unlink(missing_ok=True)


def kill_tree(process: subprocess.Popen) -> None:
    if process.poll() is not None:
        return
    if WINDOWS:
        subprocess.run(["taskkill", "/T", "/F", "/PID", str(process.pid)], capture_output=True)
    else:
        process.kill()


def game_pids(run_dir: Path) -> list[int]:
    """The game JVMs of this run dir still alive (Gradle's own JVMs left out)."""
    if not WINDOWS:
        return []
    script = (
        "Get-CimInstance Win32_Process -Filter \"Name = 'java.exe' OR Name = 'javaw.exe'\" | "
        "Where-Object { $_.CommandLine -and $_.CommandLine -notmatch 'GradleDaemon|GradleWrapperMain|GradleWorkerMain' "
        f"-and ($_.CommandLine -replace '/', '\\').Contains('{str(run_dir)}') }} | ForEach-Object {{ $_.ProcessId }}"
    )
    done = subprocess.run(["powershell", "-NoProfile", "-Command", script], capture_output=True, text=True)
    return [int(x) for x in done.stdout.split() if x.isdigit()]


# ------------------------------------------------------------ production client

def client_java(explicit: str | None) -> str:
    """Java 21+ for the production client; javaw on Windows, so no console window opens."""
    java = explicit or (str(Path(os.environ["JAVA_HOME"]) / "bin" / "java") if os.environ.get("JAVA_HOME") else
                        shutil.which("java") or "java")
    if Path(java).is_dir():
        java = str(Path(java) / "bin" / "java")
    if WINDOWS:
        javaw = Path(java).with_name("javaw.exe")
        if javaw.is_file():
            java = str(javaw)
    major, found = sa.java_major(java.replace("javaw.exe", "java.exe"))
    if major < 21:
        sys.exit(f"the production client needs Java 21 or newer; {java} is: {found}. Pass --java <path>.")
    return java


def portablemc(loader: str) -> list[str]:
    # portablemc 5 rewrote its command line: pinned to the 4 series. Its own folders:
    # the launcher's (versions, libraries, assets) and the game's.
    return ["uvx", "--from", "portablemc<5", "portablemc", "--main-dir", str(PROD / "launcher"),
            "--work-dir", str(PROD / loader), "--output", "human"]


def game_version(versions: dict, loader: str) -> str:
    if loader == "neoforge":
        return f"neoforge:{versions['neoforge']['version']}"
    return f"fabric:{versions['minecraft']}:{versions['fabric']['loader']}"


def install_prod(loader: str, versions: dict, java: str) -> None:
    """Downloads the game and the loader once, outside a run's timeout (a dry start)."""
    log = PROD / f"install-{loader}.log"
    log.parent.mkdir(parents=True, exist_ok=True)
    print(f"Installing {game_version(versions, loader)} for the production client (once; see {log}) ...")
    with log.open("w", encoding="utf-8") as sink:
        done = subprocess.run([*portablemc(loader), "start", "--dry", game_version(versions, loader), "--jvm", java],
                              stdout=sink, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    if done.returncode != 0:
        sys.exit(f"portablemc could not install {game_version(versions, loader)}: see {log}")


# ------------------------------------------------------------------ a server

def offline_uuid(name: str) -> str:
    """The UUID an offline-mode server gives a player name (Java's nameUUIDFromBytes)."""
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode("utf-8")).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def grant_operator(run_dir: Path) -> None:
    """Makes PLAYER an operator in ops.json before the server starts, by its offline UUID.

    Not `op Dev` over RCON: for a name the server has not seen, it asks Mojang and makes the
    real account of that name an operator; the offline client is then no operator, its
    commands fail, and the chat spam guard kicks it. Other entries are kept."""
    path = run_dir / "ops.json"
    try:
        ops = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else []
    except json.JSONDecodeError:
        ops = []
    ops = [op for op in ops if str(op.get("name", "")).lower() != PLAYER.lower()]
    ops.append({"uuid": offline_uuid(PLAYER), "name": PLAYER, "level": 4, "bypassesPlayerLimit": False})
    sa.write_ascii(path, json.dumps(ops, indent=2) + "\n")


def offline_mode(path: Path) -> None:
    """online-mode=false in a server.properties, every other line kept. ASCII, no BOM."""
    lines = [line for line in path.read_text(encoding="utf-8", errors="replace").splitlines()
             if line.split("=", 1)[0].strip() != "online-mode"]
    sa.write_ascii(path, "\n".join(lines + ["online-mode=false"]).encode("ascii", "replace").decode("ascii") + "\n")


class LocalServer:
    """The server a --server local run joins: started, its player made an operator, stopped."""

    def __init__(self, loader: str, prod: bool, viewer: str, versions: dict, java: str | None):
        self.loader = loader
        if prod:
            server_java = java.replace("javaw.exe", "java.exe") if java else "java"
            mods = ["krios_core", "krios_industry"]
            self.run_dir, command = sa.provision(f"{loader}-prod", loader, mods, False, versions, server_java)
            # The client's viewer on the server too (provision put Fabric API there already).
            for jar in viewer_jars(versions, loader, viewer):
                shutil.copyfile(jar, self.run_dir / "mods" / jar.name)
            cwd = self.run_dir
        else:
            self.run_dir = MOD / ("neoforge/run" if loader == "neoforge" else "fabric/runs/server")
            command = sa.gradle(MOD, f"{loader}:runServer", "--no-daemon", "-q", f"-Pdev_recipe_viewer={viewer}")
            cwd = MOD
        self.net = sa.ensure_server_files(self.run_dir)
        for port in self.net.ports:
            if sa.port_open(port):
                sys.exit(f"something already listens on port {port}: stop it first")
        sa.fresh_world(self.run_dir, self.net.level)
        # For the run only, put back by stop(): the player's operator entry, and offline mode
        # (the client has no Mojang session; a dev run dir may say online-mode=true).
        self.saved = {path: path.read_bytes() if path.is_file() else None
                      for path in (self.run_dir / "ops.json", self.run_dir / "server.properties")}
        grant_operator(self.run_dir)
        offline_mode(self.run_dir / "server.properties")
        target = f"client-{'prod' if prod else 'dev'}-{loader}"
        print(f"{target}: starting the server in {self.run_dir.relative_to(REPO)} ...")
        self.server = sa.Server(target, "server", command, cwd, self.run_dir, self.net)
        booted, why = self.server.wait_boot()
        if not booted:
            self.stop()
            sys.exit(f"the server did not start: {why}")
        print(f"{target}: server up on port {self.net.game_port}")

    @property
    def address(self) -> str:
        return f"localhost:{self.net.game_port}"

    def errors(self) -> list[str]:
        return [line for line in self.server.log_text().splitlines()
                if re.search(r"/(ERROR|FATAL)\]|\tat io\.lakio", line)]

    def stop(self) -> None:
        stopped = False
        try:
            rcon = sa.Rcon(self.net)
            rcon.stop()
            rcon.close()
            stopped = self.server.wait_exit(sa.STOP_TIMEOUT)
        except Exception as e:  # noqa: BLE001 - any failure ends in the kill below
            print(f"the server did not take the stop command: {e!r}")
        if not stopped or self.server.alive():
            self.server.kill()
        self.server.close()
        for path, content in self.saved.items():
            if content is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(content)


# ---------------------------------------------------------------------- main

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("loader", nargs="?", default="neoforge", choices=["neoforge", "fabric"])
    parser.add_argument("--scenario", help="one command file; without it, the whole suite of client/")
    parser.add_argument("--viewer", default="jei", choices=["jei", "emi", "both", "none"],
                        help="the recipe viewer the client loads with --scenario")
    parser.add_argument("--prod", action="store_true",
                        help="the built jars on the pinned loader (versions.toml), launched by portablemc")
    parser.add_argument("--java", help="the Java 21+ (or its JDK home) of a production client and server")
    parser.add_argument("--window", default="background", choices=["background", "minimized", "normal"])
    parser.add_argument("--timeout", type=int, default=900, help="seconds before one run is given up")
    parser.add_argument("--server", metavar="local|HOST:PORT",
                        help="join a server instead of opening a world: 'local' starts the matching one "
                             "(and stops it), HOST:PORT joins a running one where the player must be an "
                             "operator; the scenarios' 'world' lines are dropped")
    args = parser.parse_args()
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8", errors="replace")

    with open(HERE / "versions.toml", "rb") as f:
        versions = tomllib.load(f)
    java = client_java(args.java) if args.prod else None
    if args.prod:
        sa.mod_jar("krios_core", args.loader)  # fails early when the jars are not built
        sa.mod_jar("krios_industry", args.loader)
        install_prod(args.loader, versions, java)
    rehearsal_jar(args.loader)  # fails early with what is missing

    here = HERE / "client"
    if args.scenario:
        runs = [(Path(args.scenario), args.viewer)]
    else:
        suite = SERVER_SUITE if args.server else SUITE
        runs = [(here / file, viewer) for file, viewer in suite]
    if args.server == "local" and len({viewer for _, viewer in runs}) > 1:
        sys.exit("--server local starts one server for one viewer: give --scenario and --viewer")

    server = None
    results = []
    try:
        if args.server == "local":
            server = LocalServer(args.loader, args.prod, runs[0][1], versions, java)
        address = server.address if server else args.server
        for scenario, viewer in runs:
            results.append((scenario.stem, viewer, run_one(args, scenario.resolve(), viewer, address, versions, java)))
    finally:
        if server:
            errors = server.errors()
            server.stop()
            print(f"server log: {len(errors)} error lines" + ("" if not errors else ":"))
            for line in errors[:10]:
                print(f"SERVER {line[:220]}")
            if errors:
                results.append(("server log", "-", False))
    if len(results) > 1:
        print()
        for stem, viewer, ok in results:
            print(f"{'PASS' if ok else 'FAIL'}  {'prod ' if args.prod else ''}{args.loader} {stem} ({viewer})")
    return 0 if results and all(ok for _, _, ok in results) else 1


def run_one(args: argparse.Namespace, scenario: Path, viewer: str, server: str | None, versions: dict,
            java: str | None) -> bool:
    """One client launch on one command file; True when nothing failed."""
    if not scenario.exists():
        sys.exit(f"no scenario {scenario}")
    name = (f"{'prod-' if args.prod else ''}{args.loader}-{viewer}-{scenario.stem}"
            + ("-server" if server else ""))
    out = OUT / name
    # The folder may be open somewhere (a terminal, an image viewer): empty it, do not replace it.
    out.mkdir(parents=True, exist_ok=True)
    for old in out.iterdir():
        if old.is_file():
            old.unlink(missing_ok=True)

    if args.prod:
        run_dir = PROD / args.loader
        mods = [sa.mod_jar("krios_core", args.loader), sa.mod_jar("krios_industry", args.loader),
                rehearsal_jar(args.loader), *viewer_jars(versions, args.loader, viewer)]
        prepare(run_dir, args.loader, scenario, mods)
        jvm = "-Xmx4G -Drehearsal=true" + (f" -Drehearsal.window={args.window}" if args.window != "normal" else "")
        command = [*portablemc(args.loader), "start", game_version(versions, args.loader), "-u", PLAYER,
                   "--resolution", "1280x720", "--jvm", java, "--jvm-args", jvm]
        if server:
            host, port = server.rsplit(":", 1)
            command += ["-s", host, "-p", port]
        console = out / "launcher.log"
        cwd = PROD
    else:
        run_dir = MOD / args.loader / "runs" / "rehearsal"
        prepare(run_dir, args.loader, scenario, None)
        gradlew = str(MOD / ("gradlew.bat" if WINDOWS else "gradlew"))
        command = [gradlew, f"{args.loader}:runClient", "--no-daemon", "-q",
                   f"-Prehearsal={args.window}", f"-Pdev_recipe_viewer={viewer}"]
        if server:
            command.append(f"-Pquickplayserver={server}")
        console = out / "gradle.log"
        cwd = MOD
    start = time.time()
    print(f"{name}: starting the client")
    with console.open("w", encoding="utf-8") as sink:
        process = subprocess.Popen(command, cwd=cwd, stdout=sink, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)

    log = run_dir / "logs" / "latest.log"
    inbox = run_dir / "rehearsal" / "inbox"
    outbox = run_dir / "rehearsal" / "outbox.jsonl"

    def fresh_log() -> str:
        if not log.exists() or log.stat().st_mtime < start - 2:
            return ""
        return log.read_text(encoding="utf-8", errors="replace")

    def stopped(what: str) -> str | None:
        text = fresh_log()
        if "---- Minecraft Crash Report ----" in text or "Crash report saved to" in text:
            return "the game crashed"
        # A server that drops the client leaves it on a screen where every command fails:
        # give up at once. After the scenario's quit, a disconnection is the normal end.
        running = text.split("Rehearsal: the game was asked to stop", 1)[0]
        kicked = re.search(r"Client disconnected with reason: (.*)", running)
        if kicked:
            return f"disconnected by the server: {kicked.group(1).strip()}"
        # Refused at login logs no reason, only the screen: DisconnectedScreen, class_419 in a
        # production Fabric game (intermediary names). The scenario would wait for a player.
        refused = re.search(r"Rehearsal: screen \S*(?:DisconnectedScreen|\bclass_419) \((.*)\)", running)
        if refused:
            return f"disconnected: {refused.group(1)} (see the game log and the server's)"
        if process.poll() is not None:
            return f"the client ended before {what} (see {console})"
        if time.time() - start > args.timeout:
            return f"timed out after {args.timeout} s waiting for {what}"
        return None

    failure = None
    sent = False
    while failure is None:
        if not sent:
            if "Rehearsal inbox on" in fresh_log():
                # Opening the inbox deletes what was there: drop the file only now, whole.
                inbox.mkdir(parents=True, exist_ok=True)
                temp = inbox / "scenario.tmp"
                lines = scenario.read_text(encoding="utf-8").splitlines()
                if server:
                    # The game joins the server by itself: no world to open.
                    lines = [line for line in lines if line.split()[:1] != ["world"]]
                temp.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
                temp.rename(inbox / "scenario.cmd")
                sent = True
                print(f"{name}: inbox on after {int(time.time() - start)} s, scenario sent")
                continue
            failure = stopped("its inbox")
        else:
            if outbox.exists() and '"cmd":"quit"' in outbox.read_text(encoding="utf-8", errors="replace"):
                break
            failure = stopped("the reply to quit")
        time.sleep(2)

    if failure is None:
        try:
            process.wait(timeout=90)
        except subprocess.TimeoutExpired:
            failure = "the game did not close after quit"
    if failure is not None:
        kill_tree(process)
        for pid in game_pids(run_dir):
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(pid)], capture_output=True)

    # The replies: one JSON line per command.
    replies = []
    if outbox.exists():
        for line in outbox.read_text(encoding="utf-8", errors="replace").splitlines():
            try:
                replies.append(json.loads(line))
            except json.JSONDecodeError:
                pass
    failed = [r for r in replies if not r.get("ok")]
    shots = sorted((run_dir / "rehearsal" / "shots").glob("*.png"))
    for shot in shots:
        shutil.copy2(shot, out)
    if outbox.exists():
        shutil.copy2(outbox, out)
    text = fresh_log()
    if log.exists():
        shutil.copy2(log, out / "game.log")
    # What was logged as an error while the scenario ran, and any stack frame of ours.
    # Left out: what follows the game's stop (the single player connection closes with
    # an exception), exceptions other mods log below error level (EMI probing for JEI),
    # and three known noises that are not ours.
    running = text.split("Rehearsal: the game was asked to stop", 1)[0]
    errors = [line for line in running.splitlines()
              if re.search(r"/(ERROR|FATAL)\]|\tat io\.lakio", line)
              and not re.search(r"\[EMI\] \d+ recipes loaded with the same id|ConfigSerializer|SoundSystem|OpenAL", line)]

    report = [f"# Client acceptance: {name}", "",
              f"- scenario: `{scenario.relative_to(REPO) if scenario.is_relative_to(REPO) else scenario}`",
              f"- client: {'production, ' + game_version(versions, args.loader) if args.prod else 'dev'}"
              + (f", joined {server}" if server else ""),
              f"- commands answered: {len(replies)}, failed: {len(failed)}",
              f"- screenshots: {len(shots)}",
              f"- log lines that look like errors: {len(errors)}",
              f"- seconds: {int(time.time() - start)}", ""]
    if failure:
        report += [f"**Run failed: {failure}**", ""]
    if failed:
        report += ["## Failed commands", ""]
        report += [f"- line {r.get('line')}: `{r.get('cmd')}` — {r.get('error', r)}" for r in failed]
        report += [""]
    if errors:
        report += ["## Log", ""] + [f"    {line[:300]}" for line in errors[:40]] + [""]
    report += ["## Screens", ""]
    for r in replies:
        if str(r.get("cmd", "")).startswith("wait screen") and r.get("ok"):
            report.append(f"- line {r.get('line')}: {r.get('title')} — menu `{r.get('menu', 'none')}`, "
                          f"{r.get('slots', 0)} slots, items {json.dumps(r.get('items', {}), ensure_ascii=False)}")
    report += ["", "## Screenshots", ""] + [f"- `{shot.name}`" for shot in shots]
    (out / "report.md").write_text("\n".join(report) + "\n", encoding="utf-8")

    for r in failed:
        print(f"FAIL  line {r.get('line')}: {r.get('cmd')}  [{r.get('error', '')}]")
    for line in errors[:10]:
        print(f"LOG   {line[:220]}")
    ok = failure is None and not failed and not errors and bool(replies)
    print(f"{'PASS' if ok else 'FAIL'}  {name}: {len(replies)} commands, {len(failed)} failed, "
          f"{len(shots)} screenshots, {len(errors)} log errors"
          + (f" — {failure}" if failure else ""))
    print(f"report and screenshots: {out}")
    return ok


if __name__ == "__main__":
    sys.exit(main())

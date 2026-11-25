# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Server acceptance tests for the Krios mods, driven over RCON.

    uv run tools/acceptance/server_acceptance.py <target>... [--build] [--java <path>] [--verbose]

Targets: neoforge-dev, fabric-dev (gradlew runServer in mods/industry),
neoforge-prod, fabric-prod (the built jars on real servers with the pinned
loaders and viewers), neoforge-core, fabric-core (core alone, production),
neoforge-upgrade, fabric-upgrade (a world saved by a release, loaded by the
built jars). Groups: dev, prod, core, upgrade, all.

No player is needed: every chunk used is force-loaded and time is sprinted.
One PASS/FAIL line per check, a summary per target, a table at the end, and
build/acceptance/report.md. Exit code 1 when anything failed. See README.md.
"""
from __future__ import annotations

import argparse
import importlib.util
import os
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import time
import tomllib
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
WORK = REPO / "build" / "acceptance"
MODS = {"krios_core": REPO / "mods" / "core", "krios_industry": REPO / "mods" / "industry"}

# Written into a server.properties that lacks them; the values already in an
# existing file are used as they are.
RCON_PORT, RCON_PASSWORD, GAME_PORT = 25575, "krios", 25599

# What an unmodded Minecraft 1.21.1 server loads. Neither loader, JEI nor EMI
# adds to them; the mods' own counts are read from the repository.
VANILLA_RECIPES = 1290
VANILLA_ADVANCEMENTS = 1399

BOOT_TIMEOUT = 600
STOP_TIMEOUT = 120
SPRINT_TIMEOUT = 300

DEV_TARGETS = {"neoforge-dev": ("neoforge", "neoforge/run"), "fabric-dev": ("fabric", "fabric/runs/server")}
PROD_TARGETS = {"neoforge-prod": "neoforge", "fabric-prod": "fabric"}
CORE_TARGETS = {"neoforge-core": "neoforge", "fabric-core": "fabric"}
UPGRADE_TARGETS = {"neoforge-upgrade": "neoforge", "fabric-upgrade": "fabric"}
GROUPS = {
    "dev": list(DEV_TARGETS),
    "prod": list(PROD_TARGETS),
    "core": list(CORE_TARGETS),
    "upgrade": list(UPGRADE_TARGETS),
    "all": [*DEV_TARGETS, *PROD_TARGETS, *CORE_TARGETS, *UPGRADE_TARGETS],
}
# The prefix of a mod's release tags (ADR-009): core/v0.1.0, industry/v0.1.0.
RELEASE_TAGS = {"krios_core": "core", "krios_industry": "industry"}

VERBOSE = False


def say(text: str = "") -> None:
    print(text, flush=True)


# --------------------------------------------------------------------- checks

class Checks:
    """The PASS/FAIL lines of one target."""

    def __init__(self, target: str):
        self.target = target
        self.phase = ""
        self.rows: list[tuple[str, str, bool, str]] = []

    def check(self, name: str, ok: object, detail: object = "") -> bool:
        ok = bool(ok)
        detail = " ".join(str(detail).split())
        self.rows.append((self.phase, name, ok, detail))
        shown = f"  [{detail[:200]}]" if detail and (VERBOSE or not ok) else ""
        say(f"{'PASS' if ok else 'FAIL'}  {self.phase}: {name}{shown}")
        return ok

    @property
    def failed(self) -> list[tuple[str, str, bool, str]]:
        return [row for row in self.rows if not row[2]]


# ----------------------------------------------------------------------- rcon

class Rcon:
    def __init__(self, net: "Net") -> None:
        last: Exception | None = None
        for _ in range(20):
            try:
                self.sock = socket.create_connection(("127.0.0.1", net.rcon_port), timeout=120)
                break
            except OSError as e:  # the listener opens a moment after the "Done" line
                last = e
                time.sleep(1)
        else:
            raise ConnectionError(f"RCON port {net.rcon_port} never opened: {last}")
        self.next_id = 100
        self._send(1, 3, net.rcon_password)
        req_id, _ = self._recv()
        if req_id == -1:
            raise ConnectionError("RCON refused the password")

    def _send(self, req_id: int, ptype: int, body: str) -> None:
        payload = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _read(self, n: int) -> bytes:
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise ConnectionError("RCON closed the connection")
            data += chunk
        return data

    def _recv(self) -> tuple[int, str]:
        (length,) = struct.unpack("<i", self._read(4))
        data = self._read(length)
        req_id, _ = struct.unpack("<ii", data[:8])
        return req_id, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command: str) -> str:
        """Runs one command and returns its whole answer.

        A long answer comes in several packets, so a sentinel command marks
        its end. The sentinel is only sent once the first packet of the
        answer is back: the vanilla RCON server reads one packet per socket
        read and drops (or chokes on) whatever arrives glued to it.
        """
        cid = self.next_id
        self.next_id += 2
        self._send(cid, 2, command)
        rid, out = self._recv()
        while rid != cid:
            rid, out = self._recv()
        self._send(cid + 1, 2, "list")
        while True:
            rid, body = self._recv()
            if rid == cid + 1:
                return out
            if rid == cid:
                out += body

    def stop(self) -> None:
        """Asks the server to stop; no answer is waited for."""
        self._send(self.next_id, 2, "stop")
        self.next_id += 2

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass


# -------------------------------------------------------------------- servers

class Net:
    """What one server listens on, as its server.properties says."""

    def __init__(self, rcon_port: int, rcon_password: str, game_port: int, level: str):
        self.rcon_port = rcon_port
        self.rcon_password = rcon_password
        self.game_port = game_port
        self.level = level

    @property
    def ports(self) -> tuple[int, int]:
        return self.rcon_port, self.game_port


def port_open(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(1)
        return s.connect_ex(("127.0.0.1", port)) == 0


def pid_listening(port: int) -> int | None:
    """The process listening on a local TCP port, when the system can tell."""
    try:
        if os.name == "nt":
            # Bytes, and no state word: netstat's text follows the system language
            # and code page. A listening socket has no remote end.
            out = subprocess.run(["netstat", "-ano", "-p", "TCP"], capture_output=True).stdout
            for line in out.decode("ascii", "replace").splitlines():
                parts = line.split()
                if (len(parts) >= 5 and parts[0] == "TCP" and parts[1].endswith(f":{port}")
                        and parts[2] in ("0.0.0.0:0", "[::]:0")):
                    return int(parts[-1])
        else:
            out = subprocess.run(["ss", "-ltnpH", f"sport = :{port}"], capture_output=True).stdout.decode("utf-8", "replace")
            m = re.search(r"pid=(\d+)", out)
            if m:
                return int(m.group(1))
    except (OSError, ValueError):
        pass
    return None


def pid_alive(pid: int) -> bool:
    if os.name == "nt":
        out = subprocess.run(["tasklist", "/FI", f"PID eq {pid}", "/NH", "/FO", "CSV"], capture_output=True).stdout
        return f'"{pid}"' in out.decode("ascii", "replace")
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


class Server:
    """One launched server process: its log, its stop, its process tree."""

    launched: list["Server"] = []

    def __init__(self, target: str, phase: str, command: list[str], cwd: Path, run_dir: Path, net: Net):
        self.target = target
        self.run_dir = run_dir
        self.net = net
        self.log = run_dir / "logs" / "latest.log"
        self.server_pid: int | None = None
        (WORK / "logs").mkdir(parents=True, exist_ok=True)
        self.console = WORK / "logs" / f"{target}-{phase}.console.log"
        self.started = time.time()
        self._out = open(self.console, "wb")
        flags = {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
        self.proc = subprocess.Popen(command, cwd=cwd, stdout=self._out, stderr=subprocess.STDOUT,
                                     stdin=subprocess.DEVNULL, **flags)
        Server.launched.append(self)

    def log_text(self) -> str:
        # The previous run's log stays there until this run rewrites it.
        try:
            if self.log.stat().st_mtime < self.started - 2:
                return ""
            return self.log.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return ""

    def console_tail(self, lines: int = 12) -> str:
        try:
            text = self.console.read_text(encoding="utf-8", errors="replace")
        except OSError:
            return ""
        return " | ".join(text.strip().splitlines()[-lines:])

    def wait_boot(self) -> tuple[bool, str]:
        deadline = self.started + BOOT_TIMEOUT
        while time.time() < deadline:
            text = self.log_text()
            if re.search(r"Done \(\d", text):
                self.server_pid = pid_listening(self.net.rcon_port) or pid_listening(self.net.game_port)
                return True, ""
            if "Failed to start" in text or "Crash report" in text or "crash-reports" in text:
                return False, "the log reports a failed start: " + self.console_tail()
            if self.proc.poll() is not None:
                return False, f"the process exited with code {self.proc.returncode}: " + self.console_tail()
            time.sleep(2)
        return False, f"no 'Done' line after {BOOT_TIMEOUT} s"

    def wait_exit(self, timeout: float) -> bool:
        deadline = time.time() + timeout
        while time.time() < deadline:
            gone = self.proc.poll() is not None and not any(port_open(port) for port in self.net.ports)
            if gone and (self.server_pid is None or not pid_alive(self.server_pid)):
                return True
            time.sleep(1)
        return False

    def kill(self) -> None:
        """Kills the whole tree: a dev server is a grandchild of the Gradle wrapper."""
        if os.name == "nt":
            if self.proc.poll() is None:
                subprocess.run(["taskkill", "/T", "/F", "/PID", str(self.proc.pid)], capture_output=True)
            if self.server_pid is not None and pid_alive(self.server_pid):
                subprocess.run(["taskkill", "/T", "/F", "/PID", str(self.server_pid)], capture_output=True)
        else:
            try:
                os.killpg(self.proc.pid, signal.SIGKILL)
            except OSError:
                pass
            if self.server_pid is not None and pid_alive(self.server_pid):
                try:
                    os.kill(self.server_pid, signal.SIGKILL)
                except OSError:
                    pass
        try:
            self.proc.wait(timeout=30)
        except subprocess.TimeoutExpired:
            pass

    def close(self) -> None:
        self._out.close()

    def alive(self) -> bool:
        return self.proc.poll() is None or (self.server_pid is not None and pid_alive(self.server_pid))


# ------------------------------------------------------------ world inspection

def number(text: str, default: int = 0) -> int:
    m = re.search(r"(-?\d+)\s*$", text.strip().rstrip("bsLdf"))
    return int(m.group(1)) if m else default


def block_data(r: Rcon, pos: str, path: str) -> str:
    out = r.cmd(f"data get block {pos} {path}")
    return out.split("data:", 1)[1].strip() if "data:" in out else out.strip()


def item_count(items: str, item_id: str) -> int:
    """Total count of one item in an `Items` NBT list as `data get` prints it."""
    total = 0
    for entry in re.findall(r"\{[^{}]*\}", items):
        if f'id: "{item_id}"' in entry:
            m = re.search(r"count: (\d+)", entry)
            total += int(m.group(1)) if m else 1
    return total


def count_filled(out: str) -> int:
    m = re.search(r"filled (\d+) block", out)
    return int(m.group(1)) if m else 0


def entity_count(r: Rcon, selector: str) -> int:
    m = re.search(r"count: (\d+)", r.cmd(f"execute if entity {selector}"))
    return int(m.group(1)) if m else 0


def wait_loaded(r: Rcon, positions: tuple[str, ...], timeout: float = 90) -> bool:
    """Waits until the chunks at these positions tick entities. A chunk that was
    force-loaded a moment ago takes blocks at once, but an entity summoned
    into it stays invisible to selectors until it gets there."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if all("passed" in r.cmd(f"execute if loaded {pos}") for pos in positions):
            return True
        time.sleep(0.5)
    return False


def sprint(server: Server, r: Rcon, ticks: int) -> bool:
    seen = server.log_text().count("Sprint completed")
    r.cmd(f"tick sprint {ticks}")
    deadline = time.time() + SPRINT_TIMEOUT
    while time.time() < deadline:
        if server.log_text().count("Sprint completed") > seen:
            return True
        time.sleep(1)
    return False


# ------------------------------------------------------------------- scenario
#
# Layout (y 200, stone floor at 199). The KOIOS shell is the 3x3x3 at 4..6, a
# conduit trunk runs along z=3; line machines sit on z=2, zone machines on z=4
# facing north so their zones (9x9, behind the screen) spread south.
#
# phase_build and phase_verify are also called by the upgrade targets of later
# releases, through this file as it was at a release's tag: keep their
# signatures, (c, server, r) -> int and (c, server, r, xp), and what they use
# of their arguments (c.check, server.log_text, r.cmd).

CORE = "5 201 5"
REACTOR, PHOTON, GRINDER, GRINDER2, FORGE = "8 200 2", "10 200 2", "12 200 2", "14 200 2", "16 200 2"
FARMER, POD, FIELD = "14 200 4", "24 200 4", "34 200 4"
# A reactor away from the trunk, on no network: what it burns changes nothing in the base.
LONE_REACTOR = "8 200 -4"
# Two more, each over a hopper (in the floor): one burns a lava bucket, one coal with a
# module in its bay. A hopper may take what burning left behind, never the fuel or a module.
BUCKET_REACTOR, BUCKET_HOPPER = "10 200 -4", "10 199 -4"
FUEL_REACTOR, FUEL_HOPPER = "12 200 -4", "12 199 -4"
LINE_MACHINES = [
    (REACTOR, "krios_industry:thermal_reactor"),
    (PHOTON, "krios_industry:photon_array"),
    (GRINDER, "krios_industry:fractal_grinder"),
    (GRINDER2, "krios_industry:fractal_grinder_tier2"),
    (FORGE, "krios_industry:plasma_forge"),
]
# Placed and ticked, nothing more asserted about them than joining the network.
PLACED_ONLY = [
    ("20 200 2", "krios_industry:cyclic_forester[facing=south]"),
    ("22 200 2", "krios_industry:quantum_lure[facing=south]"),
    ("24 200 2", "krios_industry:breeding_pod[facing=south]"),
    ("26 200 2", "krios_industry:bio_synthesizer[facing=south]"),
    ("28 200 2", "krios_industry:thermal_reactor_tier2"),
    ("30 200 2", "krios_industry:plasma_forge_tier2"),
    ("32 200 2", "krios_industry:chrono_farmer_tier2[facing=south]"),
    ("34 200 2", "krios_industry:cyclic_forester_tier2[facing=south]"),
    ("36 200 2", "krios_industry:quantum_lure_tier2[facing=south]"),
    ("38 200 2", "krios_industry:breeding_pod_tier2[facing=south]"),
    ("40 200 2", "krios_industry:harvest_pod_tier2[facing=south]"),
    ("42 200 2", "krios_industry:phase_death_field_tier2[facing=south]"),
    ("44 200 2", "krios_industry:bio_synthesizer_tier2[facing=south]"),
]
ZONE_MACHINES = [
    (FARMER, "krios_industry:chrono_farmer[facing=north]"),
    (POD, "krios_industry:harvest_pod[facing=north]"),
    (FIELD, "krios_industry:phase_death_field[facing=north]"),
]
# Every machine plus the core itself.
NETWORK_MEMBERS = len(LINE_MACHINES) + len(PLACED_ONLY) + len(ZONE_MACHINES) + 1

# Wild animals live in the force-loaded chunks too: the test counts its own,
# by the zone they were summoned in (the floating base is far above ground).
POD_ZONE = "x=20,y=199,z=5,dx=9,dy=6,dz=9"
FIELD_ZONE = "x=30,y=199,z=5,dx=9,dy=6,dz=9"
FUEL = 16


def network_checks(c: Checks, r: Rcon, label: str) -> None:
    energy = r.cmd(f"execute positioned {CORE} run koios energy")
    m = re.search(r"members (\d+)", energy)
    members = int(m.group(1)) if m else 0
    c.check(f"{label}: the network holds the core and its {NETWORK_MEMBERS - 1} machines",
            members == NETWORK_MEMBERS, f"members={members} {energy[-140:]}")
    c.check(f"{label}: the network is valid with one controller",
            "valid true" in energy and "controllers 1" in energy, energy[-140:])


def farmer_checks(c: Checks, server: Server, r: Rcon) -> None:
    # A fill that changes nothing counts nothing: farmland is counted by
    # resetting its moisture, wheat by ripening it. The farmer then harvests
    # what is now mature.
    farmland = count_filled(r.cmd("fill 10 199 5 18 199 13 minecraft:farmland replace minecraft:farmland"))
    c.check("farmer tilled its zone", farmland > 0, f"farmland={farmland}")
    probe = r.cmd("execute if block 12 200 7 minecraft:wheat")
    ripened = count_filled(r.cmd("fill 10 200 5 18 200 13 minecraft:wheat[age=7] replace minecraft:wheat"))
    c.check("farmer planted wheat", ripened > 0 or "passed" in probe, f"ripened={ripened} probe={probe}")
    c.check("sprint 600 ticks completes", sprint(server, r, 600))
    harvest = block_data(r, FARMER, "Items")
    c.check("farmer harvested wheat into its buffer", item_count(harvest, "minecraft:wheat") > 0, harvest)


def penned_checks(c: Checks, r: Rcon) -> None:
    sheep = entity_count(r, f"@e[type=minecraft:sheep,{POD_ZONE}]")
    c.check("the three penned sheep are alive (hostile-only field next door)", sheep == 3,
            f"in the pod zone={sheep}, in the world={entity_count(r, '@e[type=minecraft:sheep]')}")
    moo = entity_count(r, f"@e[type=minecraft:mooshroom,{POD_ZONE}]")
    arma = entity_count(r, f"@e[type=minecraft:armadillo,{POD_ZONE}]")
    c.check("the mooshroom and the armadillo stand in the pod zone (mixin targets loaded)",
            moo == 1 and arma == 1, f"mooshroom={moo} armadillo={arma}")


def phase_build(c: Checks, server: Server, r: Rcon) -> int:
    """Builds the base, runs it, checks it. Returns the core's XP at the end."""
    r.cmd("forceload add -16 -16 47 31")
    c.check("the force-loaded chunks are ticking",
            wait_loaded(r, ("-16 200 -16", "47 200 -16", "-16 200 31", "47 200 31", "16 200 8", "32 200 8")))
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false", "time set noon", "weather clear",
                    "fill 0 199 -12 47 210 15 minecraft:air", "fill 0 199 -12 47 199 15 minecraft:stone"):
        r.cmd(command)

    # KOIOS multiblock and the network trunk.
    r.cmd("fill 4 200 4 6 202 6 krios_core:neural_casing")
    out = r.cmd(f"setblock {CORE} krios_core:koios_core")
    c.check("core placed in its shell", "Changed the block" in out, out)
    c.check("core is FORMED", "passed" in r.cmd(f"execute if block {CORE} krios_core:koios_core[formed=true]"))
    c.check("casings are FORMED", "passed" in r.cmd("execute if block 4 200 4 krios_core:neural_casing[formed=true]"))
    r.cmd("fill 4 200 3 44 200 3 krios_core:neural_conduit")

    for pos, block in LINE_MACHINES + PLACED_ONLY + ZONE_MACHINES:
        out = r.cmd(f"setblock {pos} {block}")
        c.check(f"placed {block.split('[')[0]}", "Changed the block" in out, out)

    # Something for the zone machines to work on.
    r.cmd("fill 10 199 5 18 199 13 minecraft:dirt")
    r.cmd("setblock 14 199 9 minecraft:water")
    for i in range(3):
        r.cmd(f"summon minecraft:sheep {22 + 2 * i} 200 8 {{NoAI:1b,PersistenceRequired:1b}}")
        r.cmd(f"summon minecraft:husk {32 + 2 * i} 200 8 {{NoAI:1b,PersistenceRequired:1b}}")
    # The classes two mixins target: they must load, mixins applied, on this runtime.
    r.cmd("summon minecraft:mooshroom 24 200 10 {NoAI:1b,PersistenceRequired:1b}")
    r.cmd("summon minecraft:armadillo 26 200 10 {NoAI:1b,PersistenceRequired:1b}")
    # No machine has energy yet: the husks must all be there before the run,
    # or "none left" after it would prove nothing.
    husks = entity_count(r, f"@e[type=minecraft:husk,{FIELD_ZONE}]")
    c.check("three husks stand in the field zone before the run", husks == 3, f"husks={husks}")

    # Feed the machines the way a hopper would (Container.setItem).
    feeds = ((REACTOR, f"minecraft:coal_block {FUEL}"), (GRINDER, "minecraft:cobblestone 16"),
             (GRINDER2, "krios_core:chronite_ore 4"), (FORGE, "minecraft:raw_iron 8"),
             (FARMER, "minecraft:wheat_seeds 64"))
    fed: dict[str, bool] = {}
    for pos, stack in feeds:
        out = r.cmd(f"item replace block {pos} container.0 with {stack}")
        fed[pos] = c.check(f"fed {stack.split(' ')[0]} into the machine at {pos}", "Replaced a slot" in out, out)

    # A fuel that leaves something behind when it burns.
    out = r.cmd(f"setblock {LONE_REACTOR} krios_industry:thermal_reactor")
    c.check("placed a reactor off the network", "Changed the block" in out, out)
    out = r.cmd(f"item replace block {LONE_REACTOR} container.0 with minecraft:lava_bucket")
    fed_lava = c.check("fed it a lava bucket", "Replaced a slot" in out, out)
    hoppers = True
    for reactor, hopper in ((BUCKET_REACTOR, BUCKET_HOPPER), (FUEL_REACTOR, FUEL_HOPPER)):
        hoppers &= "Changed the block" in r.cmd(f"setblock {hopper} minecraft:hopper")
        hoppers &= "Changed the block" in r.cmd(f"setblock {reactor} krios_industry:thermal_reactor")
    hoppers &= "Replaced a slot" in r.cmd(f"item replace block {BUCKET_REACTOR} container.0 with minecraft:lava_bucket")
    hoppers &= "Replaced a slot" in r.cmd(f"item replace block {FUEL_REACTOR} container.0 with minecraft:coal_block 4")
    hoppers &= "Replaced a slot" in r.cmd(f"item replace block {FUEL_REACTOR} container.1 with krios_industry:speed_module")
    c.check("placed two reactors over hoppers, fed lava and coal with a module", hoppers)

    xp_before = number(block_data(r, CORE, "DataXp"))
    c.check("sprint 3000 ticks completes", sprint(server, r, 3000))

    info = r.cmd(f"execute positioned {CORE} run krios_core koios info")
    c.check("/krios_core koios info finds the core", "K.O.I.O.S." in info and "No formed" not in info, info)
    network_checks(c, r, "after the run")

    fuel = block_data(r, REACTOR, "Items")
    left = item_count(fuel, "minecraft:coal_block")
    c.check("reactor burned fuel", fed[REACTOR] and left < FUEL, f"coal blocks left={left} of {FUEL}")
    lava = block_data(r, LONE_REACTOR, "Items")
    c.check("the reactor burned the lava and kept the empty bucket",
            fed_lava and item_count(lava, "minecraft:lava_bucket") == 0 and item_count(lava, "minecraft:bucket") == 1,
            lava)
    taken = block_data(r, BUCKET_HOPPER, "Items")
    left = block_data(r, BUCKET_REACTOR, "Items")
    c.check("a hopper under the reactor took the empty bucket",
            item_count(taken, "minecraft:bucket") == 1 and item_count(left, "minecraft:bucket") == 0,
            f"hopper={taken} reactor={left}")
    taken = block_data(r, FUEL_HOPPER, "Items")
    kept = block_data(r, FUEL_REACTOR, "Items")
    c.check("a hopper under the reactor took neither its fuel nor its module",
            item_count(taken, "minecraft:coal_block") == 0 and item_count(taken, "krios_industry:speed_module") == 0
            and item_count(kept, "krios_industry:speed_module") == 1,
            f"hopper={taken} reactor={kept}")
    gravel = block_data(r, GRINDER, "Items")
    c.check("grinder turned cobblestone into gravel", item_count(gravel, "minecraft:gravel") > 0, gravel)
    shards = block_data(r, GRINDER2, "Items")
    c.check("tier 2 grinder turned chronite ore into shards", item_count(shards, "krios_core:chronite_shard") > 0, shards)
    ingots = block_data(r, FORGE, "Items")
    c.check("forge smelted raw iron", item_count(ingots, "minecraft:iron_ingot") > 0, ingots)

    xp = number(block_data(r, CORE, "DataXp"))
    c.check("the core gained data XP", xp > xp_before, f"DataXp {xp_before} -> {xp}")

    farmer_checks(c, server, r)

    wool = block_data(r, POD, "Items")
    c.check("harvest pod sheared the sheep", "_wool" in wool, wool)
    husks = entity_count(r, f"@e[type=minecraft:husk,{FIELD_ZONE}]")
    c.check("phase death field killed the husks", husks == 0, f"husks left={husks}")
    penned_checks(c, r)

    # The core keeps its XP as an item: what mining it would drop.
    r.cmd("kill @e[type=minecraft:item]")
    r.cmd(f"loot spawn 5 206 5 mine {CORE} minecraft:netherite_pickaxe")
    drop = r.cmd("execute positioned 5 206 5 run data get entity @e[type=minecraft:item,distance=..4,limit=1] Item")
    c.check("mined core drops with its koios_data",
            "krios_core:koios_core" in drop and "krios_core:koios_data" in drop, drop)
    r.cmd("kill @e[type=minecraft:item]")

    conf = r.cmd("krios_core config")
    c.check("/krios_core config answers", "energy_cost" in conf and "xp_rate" in conf, conf)
    return number(block_data(r, CORE, "DataXp"))


def phase_verify(c: Checks, server: Server, r: Rcon, xp_saved: int) -> None:
    """After a restart: what must persist, and that work resumes by itself."""
    c.check("core still FORMED", "passed" in r.cmd(f"execute if block {CORE} krios_core:koios_core[formed=true]"))
    xp = number(block_data(r, CORE, "DataXp"))
    c.check("core kept its data XP", xp_saved > 0 and xp >= xp_saved, f"before the restart={xp_saved}, now={xp}")
    gravel_before = item_count(block_data(r, GRINDER, "Items"), "minecraft:gravel")
    c.check("grinder kept its output", gravel_before > 0, f"gravel={gravel_before}")
    # More work for the grinder: the network must re-form by itself after a load.
    out = r.cmd(f"item replace block {GRINDER} container.0 with minecraft:cobblestone 8")
    c.check("fed the grinder again", "Replaced a slot" in out, out)
    c.check("sprint 1200 ticks completes", sprint(server, r, 1200))
    network_checks(c, r, "after the restart")
    gravel_after = item_count(block_data(r, GRINDER, "Items"), "minecraft:gravel")
    c.check("grinder works again", gravel_after > gravel_before, f"gravel {gravel_before} -> {gravel_after}")
    xp2 = number(block_data(r, CORE, "DataXp"))
    c.check("core gains XP again", xp2 > xp, f"DataXp {xp} -> {xp2}")
    penned_checks(c, r)
    farmer_checks(c, server, r)


def phase_core(c: Checks, server: Server, r: Rcon) -> None:
    """Core installed alone: its content must work without any content mod."""
    r.cmd("forceload add 0 0 31 31")
    c.check("the force-loaded chunks are ticking", wait_loaded(r, ("0 0 0", "31 0 0", "0 0 31", "31 0 31")))
    # Ores are counted by replacing them (the world is thrown away).
    ores: dict[str, int] = {}
    for ore, low, high in (("chronite_ore", -64, 0), ("deepslate_chronite_ore", -64, 0),
                           ("neurite_ore", -8, 72), ("deepslate_neurite_ore", -8, 72)):
        ores[ore] = sum(
            count_filled(r.cmd(f"fill {cx} {low} {cz} {cx + 15} {high} {cz + 15} minecraft:stone replace krios_core:{ore}"))
            for cx in (0, 16) for cz in (0, 16))
    c.check("chronite ore generates (4 chunks)", ores["chronite_ore"] + ores["deepslate_chronite_ore"] > 0, ores)
    c.check("neurite ore generates (4 chunks)", ores["neurite_ore"] + ores["deepslate_neurite_ore"] > 0, ores)
    r.cmd("fill 3 199 2 8 204 7 minecraft:air")
    r.cmd("fill 4 200 4 6 202 6 krios_core:neural_casing")
    out = r.cmd(f"setblock {CORE} krios_core:koios_core")
    c.check("core placed in its shell", "Changed the block" in out, out)
    c.check("core is FORMED", "passed" in r.cmd(f"execute if block {CORE} krios_core:koios_core[formed=true]"))
    out = r.cmd("setblock 5 200 3 krios_core:neural_conduit")
    c.check("conduit placed", "Changed the block" in out, out)
    info = r.cmd(f"execute positioned {CORE} run koios info")
    c.check("/koios info finds the core", "K.O.I.O.S." in info and "No formed" not in info, info)
    conf = r.cmd("krios_core config")
    c.check("/krios_core config answers", "energy_cost" in conf and "xp_rate" in conf, conf)
    r.cmd(f"loot spawn 5 206 5 mine {CORE} minecraft:netherite_pickaxe")
    drop = r.cmd("execute positioned 5 206 5 run data get entity @e[type=minecraft:item,distance=..4,limit=1] Item")
    c.check("mined core drops as its item", "krios_core:koios_core" in drop, drop)


# --------------------------------------------------------------- log checking

def shipped_count(mods: list[str], kind: str, loader: str) -> int:
    total = 0
    for mod in mods:
        for project in ("common", loader):
            data = MODS[mod] / project / "src" / "main" / "resources" / "data"
            for folder in data.glob(f"*/{kind}"):
                total += sum(1 for _ in folder.rglob("*.json"))
    return total


def log_checks(c: Checks, server: Server, mods: list[str], loader: str, counts: bool = True) -> None:
    """`counts` is off for a server running released jars: the files counted
    are the repository's, which may have gained some since."""
    text = server.log_text()
    for kind, word, vanilla in (("recipe", "recipes", VANILLA_RECIPES),
                                ("advancement", "advancements", VANILLA_ADVANCEMENTS)) if counts else ():
        want = vanilla + shipped_count(mods, kind, loader)
        got = re.findall(rf"Loaded (\d+) {word}", text)
        c.check(f"every {kind} file is loaded ({want})", bool(got) and int(got[-1]) == want,
                f"loaded={got[-1] if got else 'no such line'}, vanilla {vanilla} + {want - vanilla} shipped")
    bad = [line for line in text.splitlines()
           if re.search(r"/ERROR\]|\[(Server thread|main)/ERROR|Exception|Caused by|\tat io\.lakio", line)
           and "RCON" not in line]
    c.check("no error or exception in the server log", not bad, " | ".join(line[-160:] for line in bad[:4]))


# ---------------------------------------------------------------- provisioning

def read_properties(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


def write_ascii(path: Path, text: str) -> None:
    # Run-dir files must be ASCII without a BOM, or the server misreads the first key.
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("ascii"))


def ensure_server_files(run_dir: Path) -> Net:
    """eula.txt, and the RCON keys of server.properties when they are missing;
    nothing else in the run dir is touched, and values already there win."""
    eula = run_dir / "eula.txt"
    if "eula=true" not in (eula.read_text(errors="replace") if eula.is_file() else ""):
        write_ascii(eula, "eula=true\n")
    path = run_dir / "server.properties"
    props = read_properties(path)
    if not path.is_file():
        fresh = {"enable-rcon": "true", "rcon.password": RCON_PASSWORD, "rcon.port": str(RCON_PORT),
                 "server-port": str(GAME_PORT), "online-mode": "false", "level-name": "world",
                 "spawn-protection": "0"}
        write_ascii(path, "".join(f"{k}={v}\n" for k, v in fresh.items()))
    else:
        missing = {}
        if props.get("enable-rcon") != "true":
            missing["enable-rcon"] = "true"
        if not props.get("rcon.password"):
            missing["rcon.password"] = RCON_PASSWORD
        if not props.get("rcon.port"):
            missing["rcon.port"] = str(RCON_PORT)
        if missing:
            lines = [line for line in path.read_text(encoding="utf-8", errors="replace").splitlines()
                     if line.split("=", 1)[0].strip() not in missing]
            lines += [f"{k}={v}" for k, v in missing.items()]
            write_ascii(path, "\n".join(lines).encode("ascii", "replace").decode("ascii") + "\n")
    props = read_properties(path)
    return Net(int(props.get("rcon.port", RCON_PORT)), props.get("rcon.password", RCON_PASSWORD),
               int(props.get("server-port") or 25565), props.get("level-name") or "world")


def fresh_world(run_dir: Path, level: str) -> None:
    world = run_dir / level
    if world.is_dir():
        shutil.rmtree(world)


def download(url: str, dest: Path) -> Path:
    if dest.is_file() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    say(f"  downloading {dest.name}")
    part = dest.with_suffix(dest.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (acceptance-tests)"})
    with urllib.request.urlopen(request, timeout=120) as response, open(part, "wb") as out:
        shutil.copyfileobj(response, out)
    part.replace(dest)
    return dest


def gradle_property(mod_dir: Path, key: str) -> str:
    value = read_properties(mod_dir / "gradle.properties").get(key)
    if not value:
        raise SystemExit(f"{mod_dir / 'gradle.properties'} has no '{key}'")
    return value


def mod_jar(mod: str, loader: str) -> Path:
    mod_dir = MODS[mod]
    name = f"{mod}-{loader}-{gradle_property(mod_dir, 'minecraft_version')}-{gradle_property(mod_dir, 'version')}.jar"
    jar = mod_dir / loader / "build" / "libs" / name
    if not jar.is_file():
        raise SystemExit(f"missing {jar.relative_to(REPO)}: build the mods first, or pass --build")
    return jar


def third_parties(v: dict, loader: str, viewers: bool) -> list[Path]:
    mc = v["minecraft"]
    d = WORK / "downloads"
    jars: list[Path] = []
    if loader == "fabric":
        api = v["fabric"]["api"]
        jars.append(download(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar",
                             d / f"fabric-api-{api}.jar"))
    if viewers:
        w = v["viewers"]
        blame = "https://maven.blamejared.com"
        jars.append(download(f"{blame}/mezz/jei/jei-{mc}-{loader}/{w['jei']}/jei-{mc}-{loader}-{w['jei']}.jar",
                             d / f"jei-{mc}-{loader}-{w['jei']}.jar"))
        jars.append(download(
            f"https://maven.terraformersmc.com/dev/emi/emi-{loader}/{w['emi']}/emi-{loader}-{w['emi']}.jar",
            d / f"emi-{loader}-{w['emi']}.jar"))
        for artifact, version in (("mezz_config", w["mezz_config"]), ("mezz_config_gui", w["mezz_config_gui"])):
            name = f"{artifact}-{mc}-{loader}-{version}.jar"
            jars.append(download(f"{blame}/net/mezzdev/config/{artifact}-{mc}-{loader}/{version}/{name}", d / name))
    return jars


def java_major(java: str) -> tuple[int, str]:
    try:
        out = subprocess.run([java, "-version"], capture_output=True, text=True)
    except OSError as e:
        return 0, str(e)
    text = (out.stderr + out.stdout).strip()
    m = re.search(r'version "(\d+)', text)
    return (int(m.group(1)) if m else 0), text.splitlines()[0] if text else "no output"


def find_java(explicit: str | None) -> str:
    if explicit:
        candidates = [explicit]
    elif os.environ.get("JAVA_HOME"):
        candidates = [str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))]
    else:
        candidates = [shutil.which("java") or "java"]
    java = candidates[0]
    if Path(java).is_dir():
        java = str(Path(java) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    major, found = java_major(java)
    if major < 21:
        raise SystemExit(f"the production servers need Java 21 or newer; {java} is: {found}. Pass --java <path>.")
    say(f"Java for the production servers: {java} ({found})")
    return java


def provision(target: str, loader: str, mod_jars: list[Path], viewers: bool, v: dict,
              java: str) -> tuple[Path, list[str]]:
    """Installs or refreshes one production server; returns its dir and its command."""
    server_dir = WORK / target
    (server_dir / "mods").mkdir(parents=True, exist_ok=True)
    d = WORK / "downloads"
    if loader == "neoforge":
        version = v["neoforge"]["version"]
        marker = server_dir / "installed-neoforge.txt"
        args_file = f"libraries/net/neoforged/neoforge/{version}/{'win_args.txt' if os.name == 'nt' else 'unix_args.txt'}"
        if (marker.read_text().strip() if marker.is_file() else "") != version or not (server_dir / args_file).is_file():
            installer = download(
                f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar",
                d / f"neoforge-{version}-installer.jar")
            say(f"  installing NeoForge {version} into {server_dir.relative_to(REPO)}")
            with open(server_dir / "install.log", "wb") as log:
                done = subprocess.run([java, "-jar", str(installer), "--install-server", "."], cwd=server_dir,
                                      stdout=log, stderr=subprocess.STDOUT)
            if done.returncode != 0:
                raise SystemExit(f"the NeoForge installer failed, see {server_dir / 'install.log'}")
            marker.write_text(version + "\n")
        command = [java, "-Xmx3G", f"@{args_file}", "nogui"]
    else:
        f = v["fabric"]
        launcher = download(
            f"https://meta.fabricmc.net/v2/versions/loader/{v['minecraft']}/{f['loader']}/{f['installer']}/server/jar",
            d / f"fabric-server-launch-{v['minecraft']}-{f['loader']}-{f['installer']}.jar")
        shutil.copyfile(launcher, server_dir / "fabric-server-launch.jar")
        command = [java, "-Xmx3G", "-jar", "fabric-server-launch.jar", "nogui"]
    for old in (server_dir / "mods").glob("*.jar"):
        old.unlink()
    for jar in mod_jars + third_parties(v, loader, viewers):
        shutil.copyfile(jar, server_dir / "mods" / jar.name)
    return server_dir, command


# -------------------------------------------------------------------- running

def gradle(mod_dir: Path, *args: str) -> list[str]:
    if os.name == "nt":
        # The full path: cmd does not always look in the current directory.
        return ["cmd", "/c", str(mod_dir / "gradlew.bat"), *args]
    return [str(mod_dir / "gradlew"), *args]


def build_mods() -> None:
    for mod in ("krios_core", "krios_industry"):
        log = WORK / "logs" / f"build-{mod}.log"
        log.parent.mkdir(parents=True, exist_ok=True)
        say(f"Building {MODS[mod].relative_to(REPO)} ...")
        with open(log, "wb") as out:
            done = subprocess.run(gradle(MODS[mod], "build", "--no-daemon", "-q"), cwd=MODS[mod],
                                  stdout=out, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
        if done.returncode != 0:
            tail = log.read_text(encoding="utf-8", errors="replace").strip().splitlines()[-15:]
            raise SystemExit(f"gradlew build failed in {MODS[mod]} (see {log}):\n" + "\n".join(tail))


def run_phase(c: Checks, phase: str, command: list[str], cwd: Path, run_dir: Path, net: Net, mods: list[str],
              loader: str, body, counts: bool = True, log_extra=None) -> object:
    """Launches the server, runs one phase in it, stops it. Returns what the phase returned."""
    c.phase = phase
    for port in net.ports:
        if port_open(port):
            c.check("the ports of the server are free", False, f"something already listens on {port}: stop it first")
            return None
    say(f"--- {c.target} / {phase}: starting the server")
    server = Server(c.target, phase, command, cwd, run_dir, net)
    result = None
    stopped = False
    try:
        booted, why = server.wait_boot()
        if not c.check("server boots", booted, why):
            return None
        rcon = Rcon(net)
        try:
            result = body(server, rcon)
        except Exception as e:  # a broken connection or an unexpected answer is a failed run, not a crash
            c.check("the phase ran to its end", False, repr(e))
        try:
            rcon.cmd("save-all flush")
            rcon.stop()
            stopped = True
        except Exception as e:
            c.check("the server took the stop command", False, repr(e))
        rcon.close()
        if stopped:
            stopped = server.wait_exit(STOP_TIMEOUT)
            c.check("the server stops and its process exits", stopped, f"still running after {STOP_TIMEOUT} s")
        log_checks(c, server, mods, loader, counts)
        if log_extra is not None:
            log_extra(c, server)
    finally:
        if not stopped or server.alive():
            server.kill()
        server.close()
    return result


# -------------------------------------------------------------------- upgrade
#
# A world saved by a release must load on the current jars: only a MAJOR bump
# may break it (ADR-009). The world is built by the released jars, with the
# scenario of this file as it was at the release's tag; the built jars then
# load it and that same scenario's restart phase checks it. What the release
# promised after a restart is what must still hold after an update.

def release_jar(v: dict, mod: str, loader: str, version: str) -> Path:
    name = f"{mod}-{loader}-{v['minecraft']}-{version}.jar"
    return download(f"{v['upgrade']['releases']}/{RELEASE_TAGS[mod]}/v{version}/{name}",
                    WORK / "downloads" / "releases" / name)


def release_scenario(ref: str):
    """This file as it was at a git ref, loaded as a module for its phases."""
    done = subprocess.run(["git", "-C", str(REPO), "show", f"{ref}:tools/acceptance/server_acceptance.py"],
                          capture_output=True)
    if done.returncode != 0:
        raise SystemExit(f"no tools/acceptance/server_acceptance.py at the git ref '{ref}' (are the tags fetched?): "
                         + done.stderr.decode("utf-8", "replace").strip())
    name = "scenario_" + re.sub(r"\W+", "_", ref)
    path = WORK / "fixtures" / f"{name}.py"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(done.stdout)
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def upgrade_log_checks(c: Checks, server: Server) -> None:
    """What a loader or the game says about an id it no longer knows is a
    warning or an error naming it; a healthy load names none."""
    bad = [line for line in server.log_text().splitlines()
           if re.search(r"/(WARN|ERROR)\]", line) and re.search(r"krios_\w+:\w", line)]
    c.check("no warning or error names a Krios id", not bad, " | ".join(line[-200:] for line in bad[:4]))


def run_upgrade(c: Checks, loader: str, v: dict, java: str) -> None:
    mods = ["krios_core", "krios_industry"]
    current = [mod_jar(mod, loader) for mod in mods]
    for release in v["upgrade"]["from"]:
        core, industry = release["core"], release["industry"]
        label = industry if core == industry else f"core-{core}-industry-{industry}"
        scenario = release_scenario(release.get("scenario", f"{RELEASE_TAGS['krios_industry']}/v{industry}"))

        # The release's world is built once and kept: delete its folder to build it again.
        fixture = WORK / "fixtures" / f"{loader}-core-{core}-industry-{industry}"
        xp_file = fixture / "xp.txt"
        if xp_file.is_file():
            say(f"--- {c.target}: the world of {label} is kept in {fixture.relative_to(REPO)}")
        else:
            released = [release_jar(v, "krios_core", loader, core), release_jar(v, "krios_industry", loader, industry)]
            run_dir, command = provision(c.target, loader, released, True, v, java)
            net = ensure_server_files(run_dir)
            fresh_world(run_dir, net.level)
            failed = len(c.failed)
            xp = run_phase(c, f"release-{label}", command, run_dir, run_dir, net, mods, loader,
                           lambda s, r: scenario.phase_build(c, s, r), counts=False)
            if not isinstance(xp, int) or len(c.failed) > failed:
                c.phase = f"upgrade-{label}"
                c.check("the upgrade phase ran", False, "skipped: the release did not build its world cleanly")
                continue
            if fixture.is_dir():
                shutil.rmtree(fixture)
            shutil.copytree(run_dir / net.level, fixture / "world")
            xp_file.write_text(f"{xp}\n", encoding="ascii")

        xp = int(xp_file.read_text(encoding="ascii").strip())
        run_dir, command = provision(c.target, loader, current, True, v, java)
        net = ensure_server_files(run_dir)
        fresh_world(run_dir, net.level)
        shutil.copytree(fixture / "world", run_dir / net.level)
        run_phase(c, f"upgrade-{label}", command, run_dir, run_dir, net, mods, loader,
                  lambda s, r: scenario.phase_verify(c, s, r, xp), log_extra=upgrade_log_checks)


def run_target(target: str, v: dict, java: str | None) -> Checks:
    c = Checks(target)
    say(f"\n=== {target}")
    if target in UPGRADE_TARGETS:
        run_upgrade(c, UPGRADE_TARGETS[target], v, java or "java")
        say(f"=== {target}: {len(c.rows)} checks, {len(c.failed)} failed")
        return c
    if target in DEV_TARGETS:
        loader, rel = DEV_TARGETS[target]
        cwd = MODS["krios_industry"]
        run_dir = cwd / rel
        mods = ["krios_core", "krios_industry"]
        command = gradle(cwd, f"{loader}:runServer", "--no-daemon", "-q")
        full = True
    else:
        full = target in PROD_TARGETS
        loader = (PROD_TARGETS if full else CORE_TARGETS)[target]
        mods = ["krios_core", "krios_industry"] if full else ["krios_core"]
        run_dir, command = provision(target, loader, [mod_jar(mod, loader) for mod in mods], full, v, java or "java")
        cwd = run_dir
    net = ensure_server_files(run_dir)
    fresh_world(run_dir, net.level)
    if full:
        xp = run_phase(c, "build", command, cwd, run_dir, net, mods, loader, lambda s, r: phase_build(c, s, r))
        if isinstance(xp, int):
            run_phase(c, "restart", command, cwd, run_dir, net, mods, loader,
                      lambda s, r: phase_verify(c, s, r, xp))
        else:
            c.phase = "restart"
            c.check("the restart phase ran", False, "skipped: the build phase did not finish")
    else:
        run_phase(c, "core", command, cwd, run_dir, net, mods, loader, lambda s, r: phase_core(c, s, r))
    say(f"=== {target}: {len(c.rows)} checks, {len(c.failed)} failed")
    return c


def write_report(results: list[Checks], leftovers: list[str]) -> str:
    lines = ["| Target | Checks | Failed | Result |", "|---|---|---|---|"]
    for c in results:
        lines.append(f"| {c.target} | {len(c.rows)} | {len(c.failed)} | {'FAIL' if c.failed else 'pass'} |")
    table = "\n".join(lines)
    out = [f"# Server acceptance — {time.strftime('%Y-%m-%d %H:%M')}", "", table, ""]
    if leftovers:
        out += ["## Processes left behind", ""] + [f"- {text}" for text in leftovers] + [""]
    for c in results:
        if c.failed:
            out += [f"## {c.target}: failures", ""]
            out += [f"- **{phase}: {name}** — {detail or 'no detail'}" for phase, name, _, detail in c.failed]
            out.append("")
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "report.md").write_text("\n".join(out), encoding="utf-8")
    return table


def main() -> int:
    global VERBOSE
    parser = argparse.ArgumentParser(description="Server acceptance tests for the Krios mods (see README.md).")
    parser.add_argument("targets", nargs="+", help="targets or groups: " + ", ".join([*GROUPS["all"], *GROUPS]))
    parser.add_argument("--build", action="store_true", help="run gradlew build in mods/core then mods/industry first")
    parser.add_argument("--java", help="the java (or its JDK home) running the production servers; 21 or newer")
    parser.add_argument("--verbose", action="store_true", help="print the details of the passes too")
    args = parser.parse_args()
    VERBOSE = args.verbose
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(encoding="utf-8", errors="replace")

    targets: list[str] = []
    for name in args.targets:
        expanded = GROUPS.get(name, [name])
        for target in expanded:
            if target not in GROUPS["all"]:
                parser.error(f"unknown target '{target}'")
            if target not in targets:
                targets.append(target)

    with open(HERE / "versions.toml", "rb") as f:
        versions = tomllib.load(f)
    if args.build:
        build_mods()
    java = find_java(args.java) if any(t not in DEV_TARGETS for t in targets) else None

    results: list[Checks] = []
    try:
        for target in targets:
            results.append(run_target(target, versions, java))
    finally:
        leftovers = []
        for server in Server.launched:
            if server.alive():
                server.kill()
            if server.alive():
                leftovers.append(f"{server.target}: pid {server.server_pid or server.proc.pid} survived the kill")
        for port in sorted({port for server in Server.launched for port in server.net.ports}):
            if port_open(port):
                leftovers.append(f"port {port} is still open (pid {pid_listening(port)})")

    table = write_report(results, leftovers)
    say("\n" + table)
    for text in leftovers:
        say("LEFT BEHIND: " + text)
    failed = sum(len(c.failed) for c in results)
    say(f"\n{failed} failed check(s); report: {(WORK / 'report.md').relative_to(REPO)}")
    return 1 if failed or leftovers or len(results) < len(targets) else 0


if __name__ == "__main__":
    sys.exit(main())

# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Client acceptance for Krios: a dev client driven by Rehearsal.

    uv run tools/acceptance/client_acceptance.py [neoforge|fabric] [options]

Runs one command file (tools/acceptance/client/*.cmd, Rehearsal's command
language) in a dev client of mods/industry (core is loaded with it), out of
the user's way: its own run directory (<loader>/runs/rehearsal), window in
the background without the focus, sound off. Every command's reply is
checked, the screenshots are collected, and the game log is searched for
errors. Exit code 1 when a command failed, the game crashed or timed out.

The screenshots are the other half of the result: they are for the eye (the
agent's, then the user's for anything new). See README.md.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
MOD = REPO / "mods" / "industry"
OUT = REPO / "build" / "acceptance" / "client"
WINDOWS = os.name == "nt"

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


def prepare(run_dir: Path, loader: str, scenario: Path) -> None:
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
    if loader == "fabric":
        # Loom 1.8 refuses Rehearsal's jar as a mod dependency (built with a newer Loom):
        # the loader takes it from the run's mods folder instead.
        mods = run_dir / "mods"
        mods.mkdir(exist_ok=True)
        for old in mods.glob("rehearsal-*.jar"):
            old.unlink()
        shutil.copy2(rehearsal_jar("fabric"), mods)
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


def rehearsal_jar(loader: str) -> Path:
    checkout = local_property("rehearsal.dir")
    if not checkout:
        sys.exit("local.properties must name a Rehearsal checkout: rehearsal.dir=<path> (see local.properties.example)")
    jars = sorted(Path(checkout, "output").glob(f"rehearsal-{loader}-1.21.1-*.jar"), key=lambda p: p.stat().st_mtime)
    if not jars:
        sys.exit(f"{checkout}/output has no rehearsal-{loader}-1.21.1 jar: run gradlew build there")
    return jars[-1]


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


# The standard suite, run when no --scenario is given: each scenario with the
# viewer it is about. A scenario is one client launch (about two minutes).
SUITE = [
    ("screens.cmd", "jei"),
    ("screens.cmd", "emi"),
    ("world.cmd", "none"),
    ("viewers-jei.cmd", "jei"),
    ("viewers-emi.cmd", "emi"),
]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("loader", nargs="?", default="neoforge", choices=["neoforge", "fabric"])
    parser.add_argument("--scenario", help="one command file; without it, the whole suite of client/")
    parser.add_argument("--viewer", default="jei", choices=["jei", "emi", "both", "none"],
                        help="the recipe viewer the client loads with --scenario (dev_recipe_viewer)")
    parser.add_argument("--window", default="background", choices=["background", "minimized", "normal"])
    parser.add_argument("--timeout", type=int, default=900, help="seconds before one run is given up")
    args = parser.parse_args()

    here = Path(__file__).parent / "client"
    runs = [(Path(args.scenario), args.viewer)] if args.scenario else [(here / file, viewer) for file, viewer in SUITE]
    results = [(scenario.stem, viewer, run_one(args, scenario.resolve(), viewer)) for scenario, viewer in runs]
    if len(results) > 1:
        print()
        for stem, viewer, ok in results:
            print(f"{'PASS' if ok else 'FAIL'}  {args.loader} {stem} ({viewer})")
    return 0 if all(ok for _, _, ok in results) else 1


def run_one(args: argparse.Namespace, scenario: Path, viewer: str) -> bool:
    """One client launch on one command file; True when nothing failed."""
    if not scenario.exists():
        sys.exit(f"no scenario {scenario}")
    args = argparse.Namespace(**{**vars(args), "viewer": viewer})
    run_dir = MOD / args.loader / "runs" / "rehearsal"
    rehearsal_jar(args.loader)  # fails early with what is missing
    prepare(run_dir, args.loader, scenario)

    name = f"{args.loader}-{args.viewer}-{scenario.stem}"
    out = OUT / name
    # The folder may be open somewhere (a terminal, an image viewer): empty it, do not replace it.
    out.mkdir(parents=True, exist_ok=True)
    for old in out.iterdir():
        if old.is_file():
            old.unlink(missing_ok=True)
    console = out / "gradle.log"
    gradlew = str(MOD / ("gradlew.bat" if WINDOWS else "gradlew"))
    command = [gradlew, f"{args.loader}:runClient", "--no-daemon", "-q",
               f"-Prehearsal={args.window}", f"-Pdev_recipe_viewer={args.viewer}"]
    start = time.time()
    print(f"{name}: starting the client ({' '.join(command[1:])})")
    with console.open("w", encoding="utf-8") as sink:
        process = subprocess.Popen(command, cwd=MOD, stdout=sink, stderr=subprocess.STDOUT)

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
                temp = inbox / "scenario.tmp"
                shutil.copyfile(scenario, temp)
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

# Acceptance tests

Two tools exercise the mods the way a release is used, with nobody at the
keyboard: `server_acceptance.py` on dedicated servers (below), and
`client_acceptance.py` in a real client driven by Rehearsal (at the end).
`tools/check_assets.py` is the static check that goes before both. The
reasoning behind the layers is in `docs/analysis/test-automation.md`.

# GameTests

`gametest.py` runs the mods' GameTests (ADR-035) on the game test server of
each loader: `gradlew neoforge:runGameTestServer` and `gradlew
fabric:runGametest` in `mods/industry`, which loads core too, so both mods'
tests run. `--core` adds the same runs in `mods/core`, core alone.

```
uv run tools/acceptance/gametest.py                 # both loaders
uv run tools/acceptance/gametest.py neoforge --core
```

A run passes when the server exits 0 and its log says "All N required tests
passed"; a failure names the tests. About a minute a run. The servers live
in `<loader>/runs/gametest` of each mod (ignored by git); Fabric also writes
a JUnit report there (`junit.xml`).

CI runs it (and `check_assets.py`) after Industry's build, on every pull
request that touches a mod (ADR-036): a red game test fails the pull
request. The server and client tools below stay local.

Where a test belongs: a rule of a machine, one block or a few, read through
the block entity (a grinder off the network works at furnace pace, a tier 2
machine under a tier 1 core works at tier 1). What only a whole base shows
(a restart, a network of 22 machines, the production jars) stays in the
server acceptance below; what needs no world is a unit test (ADR-034).

Writing one: a `TestFunction` in the mod's `gametest` class (`CoreGameTests`,
`IndustryGameTests`), structure `CoreGameTests.ARENA` (an empty 8x8x8 arena in
core's data), the arena's own coordinates (`helper.setBlock(x, y, z, ...)`),
`helper.succeedWhen(...)` for a rule that settles over ticks (a core placed
after its casings forms one tick later, not at once).

# Server acceptance tests

`server_acceptance.py` starts a dedicated server, drives it over RCON with no
player, and reads the world back. It is the pre-release check of what the
mods do on a server: on the dev runs, and on the **built jars** running on
real, current loaders.

```
uv run tools/acceptance/server_acceptance.py all --build
uv run tools/acceptance/server_acceptance.py neoforge-prod fabric-prod
uv run tools/acceptance/server_acceptance.py upgrade
uv run tools/acceptance/server_acceptance.py dev --verbose
```

Python 3.11+, standard library only; `uv run` reads the script's header and
picks the interpreter. One PASS/FAIL line per check, a summary per target, a
table at the end, the same table and the failures' details in
`build/acceptance/report.md`. Exit code 1 when a check failed or a process
was left behind.

## Targets

| Target | Server | Mods |
|---|---|---|
| `neoforge-dev`, `fabric-dev` | `gradlew <loader>:runServer` in `mods/industry` | core and Industry from source, the dev viewer |
| `neoforge-prod`, `fabric-prod` | the loader pinned in `versions.toml`, installed under `build/acceptance/` | the built jars of core and Industry, JEI, EMI (and Fabric API) |
| `neoforge-core`, `fabric-core` | same | the built jar of core alone |
| `neoforge-upgrade`, `fabric-upgrade` | same | a release's jars to build a world, then the built jars to load it |

Groups: `dev`, `prod`, `core`, `upgrade`, `all`. Targets run one after the
other.

Options: `--build` runs `gradlew build` in `mods/core` then `mods/industry`
first (without it a missing jar stops the run); `--java <path>` names the Java
of the production servers (else `JAVA_HOME`, else `java` on the PATH; 21 or
newer); `--verbose` prints the details of the passes too.

## What a run does

A full target starts from a fresh world, twice:

1. **build**: force-loads the chunks, builds the KOIOS multiblock, a conduit
   trunk and 21 machines, summons animals and husks in the zones, feeds the
   machines the way a hopper would (`item replace block`), sprints 3600
   ticks, and checks: the core and casings form, `/krios_core koios info`,
   `/koios energy` and `/krios_core config` answer, the network holds its 22
   members and is valid, the reactor burns (one off the network keeps the
   empty bucket of the lava bucket it burned, a hopper under another takes
   that bucket, and a hopper under a third takes neither its fuel nor its
   module), the grinders and the forge
   produce (and, in a second run of 800 ticks, a grinder turns 4 raw iron
   into 8 iron dusts while the forge smelts iron dust), the core gains XP, the Chrono Farmer tills, plants and harvests,
   the Harvest Pod shears, the Phase Death Field kills the husks and leaves
   the sheep next door, a mined core drops with its `koios_data`.
2. **restart**: stops the server, starts it again on the same world, and
   checks that the core kept its form and XP, that the network re-forms by
   itself and that the machines work again.

A core target runs one phase: both ores generate, the multiblock forms, the
commands answer, a mined core drops.

Every boot also checks the log: the server loaded exactly the recipes and
advancements the mods ship (the files are counted in the repository and
added to vanilla's 1290 and 1399), and logged no error or exception.

## Upgrade targets

A world saved by a release must still load after an update: only a MAJOR
bump may break saves (ADR-009). An upgrade target checks it for each release
listed under `[[upgrade.from]]` in `versions.toml`:

1. **release-\<version>**: the jars of that release, downloaded from its
   GitHub Releases, build the base on a fresh world. The scenario is this
   tool **as it was at the release's tag** (`git show
   industry/v<version>:tools/acceptance/server_acceptance.py`, loaded as a
   module): a later scenario may place machines the release did not have.
   The world is then kept in `build/acceptance/fixtures/`, and this phase is
   skipped while it is there.
2. **upgrade-\<version>**: the built jars load a copy of that world, and the
   restart phase of the same old scenario checks it: the core kept its form
   and XP, the network re-forms with every machine, the grinder kept its
   output, the machines work again. The log must also hold no warning or
   error naming a Krios id (`krios_core:…`, `krios_industry:…`): that is how
   a loader reports an id the new jars no longer register.

What it catches: a renamed or removed block, item or block entity id, a
renamed NBT field, a changed `serverconfig` key, a data component that no
longer decodes. What it does not: anything the release's scenario did not
build (modules in a bay, a configured face, a blueprint), and a player's
inventory.

List a release when its saves differ from the ones already listed (a new
machine, a changed block entity); a release that only changed code or art
adds nothing. A check of the old scenario that the current jars fail on
purpose (a rule that changed) is a decision to take, not a line to delete:
the tag's scenario cannot be edited, so name another git ref for that
release with `scenario = "<ref>"`.

The phase functions are the contract: `phase_build(c, server, r) -> int`
and `phase_verify(c, server, r, xp)` are called by every later release
through the file at its tag. Keep their signatures.

## What it does not test

- Anything a client shows: screens, the Codex, JEI and EMI categories,
  textures. The viewers are installed, but a server does not load their
  plugins.
- The machines the scenario only places: Cyclic Forester, Quantum Lure,
  Breeding Pod, Bio Synthesizer, the Photon Array's output, tier 2 beyond
  one grinder. Placing them proves they register, join the network and tick
  without an error, nothing more.
- Modules, the Calibrator, the breakthrough ritual, face configuration.
- Worldgen in the full targets (the core targets cover it).

## What it touches

- Dev targets use the usual run dirs (`mods/industry/neoforge/run`,
  `mods/industry/fabric/runs/server`). The tool **deletes the server world**
  there (`level-name`, `world` by default) and writes `eula.txt` and the RCON
  keys of `server.properties` when they are missing. Nothing else: a client's
  `saves/` and `options.txt` are left alone. It uses the ports and the RCON
  password the file already has.
- Production targets live in `build/acceptance/` (ignored by git): downloads,
  one folder per target, console logs in `logs/`. Delete the folder to start
  over.
- Upgrade targets also keep `build/acceptance/fixtures/`: one world per
  release and loader, and the old scenarios. Delete a world's folder to have
  the release build it again.
- Servers stop on RCON `stop`. On a failure or a timeout the whole process
  tree is killed, and the run ends by checking that nothing still listens on
  the servers' ports.

## Adding a check

The scenario is plain functions in the script: `phase_build`, `phase_verify`
(after the restart), `phase_core`. A check is one line:

```python
c.check("forge smelted raw iron", item_count(ingots, "minecraft:iron_ingot") > 0, ingots)
```

Rules that the first version learned the hard way:

- **A check must be able to fail.** `recipe give @a <id>` answers "No player
  was found" for a recipe that does not exist too. When the absence of
  something is the proof (no husk left), first assert that it was there.
- **A fill that changes nothing counts nothing.** `fill ... wheat replace
  wheat` reports zero blocks: count by changing a state (`wheat[age=7]`).
- **Count the test's own entities by position.** The force-loaded chunks
  have wild sheep and armadillos.
- **Wait for the chunks before summoning.** A chunk force-loaded a moment
  ago takes blocks at once, but an entity summoned into it is invisible to
  selectors until the chunk ticks entities (`wait_loaded`, on `execute if
  loaded`).
- Helpers: `block_data` (one NBT path of a block entity), `item_count` (an
  item in an `Items` list), `count_filled`, `entity_count`, `sprint` (waits
  for the log's "Sprint completed").

## The RCON pitfall

Minecraft's RCON server reads one packet per socket read. Two packets sent
back to back can arrive glued together: the second one is dropped, or the
connection is closed if it was cut in two. A long answer comes back in
several packets with nothing marking the last one, so `Rcon.cmd` sends a
sentinel command after the real one and reads until the sentinel answers,
but only sends it once the first packet of the real answer is back.
`tools/rcon.py` (one command, one packet) is fine for quick manual use.

## Bumping versions.toml

Change a version and run the matching targets; a new file name is
downloaded, a new NeoForge is installed over the old one. Things that moved
before:

- JEI gained library mods of its own (`mezz_config`, `mezz_config_gui`): a
  newer viewer may need another jar added in `third_parties()`. The server
  log names a missing dependency in its first lines.
- The newest JEI needs a recent NeoForge (19.76 asks for 21.1.238 or newer).
- If the Minecraft version changes, the two vanilla counts at the top of the
  script change with it: run a server without mods and read "Loaded N
  recipes" and "Loaded N advancements" in its log.

# Client acceptance tests

`client_acceptance.py` starts a dev client of `mods/industry` under
**Rehearsal**, the client test mod, and feeds it a command file: open a
world, build a base, right-click each machine, read the screen that opens,
click panels, press a recipe viewer's keys, take screenshots.

```
uv run tools/acceptance/client_acceptance.py                 # the suite, NeoForge
uv run tools/acceptance/client_acceptance.py fabric          # the suite, Fabric
uv run tools/acceptance/client_acceptance.py neoforge --scenario tools/acceptance/client/world.cmd --viewer none
```

The client stays out of the way: its own game directory
(`mods/industry/<loader>/runs/rehearsal`, so the dev client's options and
saves are never touched), a 1280x720 window behind the others without the
focus (`--window minimized` or `normal` to change that), sound off. A run
is one launch, about two minutes.

Setup, once per machine: a Rehearsal checkout built with `gradlew build`,
named in `local.properties` at the repository root (copy
`local.properties.example`). `-Prehearsal` is what turns a dev client into a
test client; nothing of it exists in a normal run or in the jars.

## The suite

| Scenario | Viewer | What it covers |
|---|---|---|
| `client/screens.cmd` | JEI, then EMI | every machine terminal and what it says (title, voice line, tier, effective tier), the faces panel, the distribution toggle, a slot tooltip, the KOIOS screen, the Codex (its entries in order, the locked ones, the unread badge), the formed core in the world; the viewer's list beside the panels |
| `client/world.cmd` | none | the ores by day and by night, the Calibrator's overlay, diagnostic screen and blueprint, the core's screen side |
| `client/viewers-jei.cmd` | JEI | the Grinding category and its yield tooltip, the machines as catalysts, the information pages, recipe transfer into the grinder, the forge and the reactor: a click, a shift-click, tier 2's two columns, the balance mode |
| `client/viewers-emi.cmd` | EMI | the same through EMI |

## Production clients

`--prod` runs the **built jars** instead of the dev client: the loader and
the recipe viewers pinned in `versions.toml`, Rehearsal's jar, nothing else,
launched by portablemc (offline, player `Dev`). The whole suite or one
scenario, as with the dev client:

```
uv run tools/acceptance/client_acceptance.py neoforge --prod
uv run tools/acceptance/client_acceptance.py fabric --prod --scenario tools/acceptance/client/world.cmd --viewer none
```

Build the jars first (`server_acceptance.py ... --build` or `gradlew build`
in both mods). Everything lives in `build/acceptance/client-prod/`: the
launcher's folder (`launcher/`, Minecraft, the loader, libraries, assets) and
one game folder per loader, whose `mods/` is emptied and refilled each run.
The first run installs the game outside the run's timeout (a few minutes,
`install-<loader>.log`). Java: `--java <path>`, else `JAVA_HOME`, else
`java` (21 or newer; `javaw` is used next to it). portablemc comes through
`uvx`, pinned below version 5.

This is what a player runs: Fabric's remapped production names, the client
classes (screens, the KOIOS head) loaded from the jars, the loader and viewer
versions players have.

## A client that joins a server

Single player hands packets over in memory: nothing is encoded. What a
server really sends (the grinding recipes, a data component, a menu's
values) is only exercised by a client that joins one:

```
uv run tools/acceptance/client_acceptance.py neoforge --server local            # dev client, dev server
uv run tools/acceptance/client_acceptance.py fabric --prod --server local       # production jars on both sides
```

`--server local` starts the matching server itself: the dev server of
`mods/industry` (its world is deleted, as `server_acceptance.py` does), or
with `--prod` the production server of `build/acceptance/<loader>-prod`
(installed when missing). It makes the player an operator, runs `screens`
and `viewers-jei` there (or `--scenario` with `--viewer`: one viewer per
server), then stops the server and reports the errors of its log. The client
joins at launch and the scenarios' `world` lines are dropped.

`--server HOST:PORT` joins a server already running instead. The player must
be an operator there, or the commands of the scenarios fail. **Do not `op
Dev` over RCON before the player has joined**: for a name it has not seen,
the server asks Mojang and makes the real account named Dev an operator.
Write the entry into `ops.json` before starting the server, with the offline
UUID (`380df991-f603-344c-a090-369bad2a924a` for `Dev`); `--server local`
does exactly that.

On a server, Rehearsal paces `command` and `tp` under vanilla's chat spam
guard (a burst of six, then one every 24 ticks), so a scenario takes about
half a minute longer there. Before that pacing, a player who was not an
operator was kicked "for spamming" at the tenth command of a tick.

On Fabric, a client without the server's JEI is refused at login (JEI
registers recipe serializers on the server): both sides load the same viewer.

## What a run proves, and what it leaves to the eye

A run passes when every command was answered `ok` (a screen that does not
open, a slot that does not exist, a block too far are failed replies), every
`#expect` line is met, the game did not crash, and the log holds no error of
ours. That proves the
screens open and the input path works. It does not prove they *look* right:
the screenshots do, to whoever looks at them. They land in
`build/acceptance/client/<loader>-<viewer>-<scenario>/` with a `report.md`
(each screen's title, menu type and items) and the game log.

The agent reads the screenshots after a run and says what it sees; a new
screen or new art is for the user to approve once.

## Writing a scenario

A command file is Rehearsal's language, one command per line (its README
lists the verbs). The ones the scenarios lean on:

```
world <name> create <seed>      a fresh creative world (deleted before each run)
command <text>                  a chat command
tp <x> <y> <z> <yaw> <pitch>
use <x> <y> <z> [face]          right-click a block, within 5 blocks
wait screen                     until a screen is open; replies with its title, menu and items
cursor in <x> <y> | cursor slot <n> | cursor <x> <y>     a virtual mouse cursor
click [shift], key <name>, slot <n>     input through the game's own path
screenshot <name> [hud]
state                           the game's state, and under "krios" what the open terminal shows
krios set <control> on|off      a terminal's toggle by its name (panel.faces, codex, distribution)
krios press <control>           a terminal's button by its name (close, codex.entry.progression)
```

The two `krios` verbs and the `krios` part of `state` come from core's
Rehearsal extension (`integration/rehearsal/TerminalExtension`, named to the
run by `-Drehearsal.extensions`). It knows nothing itself: each terminal says
what it shows (`KriosTerminalScreen.readout()`) and names its controls
(`controls()`). `state` lists the controls a terminal has in view; a wrong
name fails with that list. A control out of view (a Codex entry below the
sidebar's viewport) is scrolled into view by `krios press` itself, with the
wheel, before the click. A new screen overrides both methods, and its scenario step
reads and presses by name.

A scenario can also say what a screen must hold. After a `screen` or
`wait screen` line:

```
#expect slot 0 minecraft:cobblestone x1     the slot holds exactly that
#expect slot 9 empty
```

After a `state` line, a dotted path into its reply:

```
#expect state krios.voice gui.krios_industry.voice.starved
#expect state krios.panels.faces true
#expect state krios.codex.locked modules,critical
```

Rehearsal reads these lines as comments; the tool checks them against the
reply once the run is over, and an unmet line fails the run. Voice lines are
compared by their lang key, not their text. Assert
the state before an action as well as after it: "one item in the input" only
proves a transfer when the input was empty a moment earlier.

A line that starts with `@dev` or `@prod` is sent to that kind of client only
(`@prod cursor 390 136`): the dev clients load the JEI the mods compile
against, the production ones the pinned current JEI, and a category's layout
can differ between the two. Keep it to cursor lines: `screen` and `state`
lines are counted for the expectations as the file has them.

Learned on the first runs:

- **Load the chunks before building.** A new world spawns the player
  anywhere; `tp` to the test area and `forceload` it first, or every
  `setblock` silently does nothing.
- **A teleport takes a few ticks to land** (it is a server command, and
  `tp` answers as soon as it is sent): `wait ticks 15` before a `use`, or
  under load the click leaves from the old place and fails as too far.
- **A terminal opens with a short boot flicker**: `wait ticks 8` after
  `wait screen`, or the picture is dim.
- **A terminal's own controls are pressed by name** (`krios set`, `krios
  press`), never at a pixel: the layout can move, and a panel's open state
  is kept between runs (`config/krios_core-client.properties`), so a blind
  click toggles from wherever the last run left it. Pixels are left for what
  is not ours: `cursor <x> <y>` on a viewer's recipe screen, which depends on
  the 640x360 GUI the run fixes.
- **A terminal swallows the first click of its boot flicker**: `krios press`
  refuses to click during it. Wait the usual 8 ticks after `wait screen`.
- **A value that goes through the server takes a few ticks to come back**
  (the distribution toggle, the Codex badge): `wait ticks 10` before the
  `state` that checks it.
- **`click shift` and `key <name> shift` hold the modifier for whoever asks
  the keyboard** (`Screen.hasShiftDown()`, which both viewers do for "move all
  that fits"), from the press to the release. It needs a Rehearsal built from
  its `main` of 2026-11-25 or later: an older jar refuses `click shift` as a
  failed command.
- **A machine that works changes its slots under the test.** For a count to
  hold, the machine has no energy (no network, no reactor beside it); the
  reactor lights whatever it is given, so its proof is the inventory.
- With JEI and EMI both loaded, EMI leaves out JEI's plugin of a mod that
  has an EMI plugin: test each viewer alone.
- **On Fabric, Rehearsal's jar goes in the run's `mods` folder** (the tool
  copies it): it is built with a newer Loom than this repository's, which
  refuses it as a mod dependency. The loader remaps it at launch.
- **Two runs seconds apart share a log file name**: the tool deletes the
  previous `latest.log` before a launch, or it would read "inbox on" from
  the run before.

## Not there yet

- A production client in a launcher instance with other mods around it (a
  modpack): Rehearsal's `neoforge_pack.py` and `fabric_pack.py` do that.
- Creative tabs, modules in the bay, the breakthrough ritual, the zone
  holograms.
- A locked Codex entry (above the core's level) is not a control: a click on
  its row does nothing.

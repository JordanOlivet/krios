# Test automation — what can run without the user

*Analysis of 2026-11-23, asked by the user: "test as much as possible
automatically, without me having to step in". It states what each kind of
test can and cannot catch, what exists today, and what to build next.
The first public release (0.1.0) does not go through this; the following
ones do.*

## The goal, stated as a rule

A release candidate is built, exercised and judged by scripts and by the
agent. The user is asked only for what no tool can decide: whether the game
*feels* right (balance, pacing, art direction), and anything the checks
flag as doubtful. "Tested" means a check that can fail did not fail; a
check that cannot fail is removed (one was, on 2026-11-23: `recipe give`
answers the same for a recipe that does not exist).

## What can go wrong, and which layer sees it

| Risk | Example | Seen by |
|---|---|---|
| A file is missing or dangling | a block with no loot table, a model naming a texture that moved to core | L0 static check |
| A rule of the game logic is wrong | level curve, tier ladder, face contracts, zone geometry | L1 unit tests |
| A machine misbehaves in the world | a grinder that stops after a reload, a network that does not re-form | L2 GameTest, L3 server acceptance |
| The shipped jar is not what dev runs | a mixin that only applies in dev, a class missing from the remapped jar, a dependency declared wrong | L3 on production jars |
| A current loader or viewer breaks us | NeoForge 21.1.267, JEI 19.76 with its new library, EMI | L3 and L4 on pinned "current" versions |
| A world saved by the previous release does not load | a renamed id, a changed NBT field | L3 upgrade test on archived worlds |
| A screen crashes, is empty or overlaps | a terminal after the core split, a panel under JEI's list | L4 client acceptance |
| Something looks wrong | a missing texture, an emissive ore that is not, a hologram at the wrong height | L5 visual review of screenshots |
| A change costs frames or ticks | a per-tick `setChanged()`, a renderer allocating per frame | L6 performance |
| Server and client disagree | a menu value truncated by sync, a block event not sent | L4 against a dedicated server |
| The release pipeline itself | a wrong tag prefix, a token without the right scope | L7 pipeline dry run |

## The layers

### L0 — Static checks (built, `tools/check_assets.py`)

Reads the registries' source and the resources of both mods: every block
and item has its blockstate, models, loot table, EN and FR names; every
`krios_*:` reference in a JSON file names something that exists; EN and FR
keys match. Seconds, no game. Belongs in CI on every push.

To add: recipe ingredients and results resolved against registered ids
and tags, lang keys referenced from Java (`Component.translatable`) all
present, every recipe with its unlock advancement.

### L1 — Unit tests (not built)

JUnit on the logic that needs no world: `KoiosBalance` (levels, caps,
projections), `MachineTiers`, `ZoneGeometry`, `FaceConfig` and
`AutoConfig`, `EnergyBuffer`, `KriosConfig` parsing, the diversity window,
`CodexEntries` ordering. These classes take vanilla value types at most
(`BlockPos`, `Direction`), which load without bootstrapping the game.
Cheap to write, milliseconds to run, and the only layer that explores
edge cases exhaustively (every tier, every face mask).

Cost: a test source set in each mod's `common` project and JUnit on its
classpath. Rehearsal's `core` module shows the pattern.

### L2 — GameTest (not built; ADR-010 planned it)

Vanilla's in-world test framework: a test places blocks in its own corner
of a void world, waits, asserts in Java with direct access to the block
entities, and the server exits with the number of failures. NeoForge has
a `gameTestServer` run, Fabric a `fabric-gametest` entrypoint.

This is where per-machine behaviour should live: one test per rule (a
grinder offline halves its yield; a tier 2 machine under a tier 1 core
works at tier 1; a configured face refuses extraction; a full buffer
pauses and resumes; a core keeps the higher XP when re-placed). The
scenario of L3 checks the same things from outside, through NBT printed
as text, which is coarse and slow to extend.

Open points to settle when building it: where the tests live (a test
source set per mod, or a separate `krios_tests` mod that can also be
dropped next to production jars), the empty structure template both
loaders need, and whether the same tests can run on a production server
(NeoForge reads `-Dneoforge.gameTestServer`, Fabric `-Dfabric-api.gametest`;
not tried).

### L3 — Server acceptance (built, `tools/acceptance/server_acceptance.py`)

A real dedicated server, driven over RCON with no player: force-loaded
chunks, time sprinted, the world read back with commands. It builds the
KOIOS multiblock, a conduit trunk and 21 machines, feeds them the way a
hopper does, and checks formation, the network, every line machine's
output, the farmer, the pod, the field, XP, the core's drop, the commands,
the datapack counts; then restarts the server and checks again.

Its value is the target, not the depth: it runs the **production jars** on
**current** loaders and viewers (pinned in `versions.toml`), and core
alone. It found nothing wrong in the mods on 2026-11-23 and three things
wrong in itself, which is the expected ratio for a first run.

To add:

- **Upgrade test.** Keep the world each release's run leaves
  (`build/acceptance/<target>/world`) as a fixture, and make the next
  release load it and run the restart checks. This is the only automatic
  guard for ADR-009's promise that a MAJOR bump is what breaks worlds.
  *Built on 2026-11-25 as the `upgrade` targets (ADR-031), with one change:
  the world is not archived but built again from the release's published
  jars and the tool at its tag, so nothing binary is kept in git.*
- The machines it only places: forester (grow a tree), lure (a water
  zone), breeding pod (two cows and wheat), bio synthesizer (count the
  spawns), photon array output, tier 2 behaviour, modules inserted by
  command.
- A bump routine: move `versions.toml` to the latest loader and viewer
  versions and run; a failure there is news before players report it.

### L4 — Client acceptance with Rehearsal (built for dev clients, `tools/acceptance/client_acceptance.py`)

Rehearsal (the user's own client test mod) drives a real client from
files: commands, teleports, screenshots, worlds. Until today it could not
touch a screen. It now can (see below), so a script can open every
terminal, read its slots, click its panels, press a recipe viewer's keys,
and photograph each step. The client runs out of the user's way: window
in the background, no focus, no sound.

The first suite (`tools/acceptance/client/*.cmd`) opens the eleven machine
terminals, the faces panel, the KOIOS screen and the Codex; shows the ores
by day and by night, the Calibrator's overlay, diagnostic screen and
blueprint, the core's screen side; and walks JEI and EMI through the
Grinding category, the machines as catalysts and the information pages.
About 300 commands and 50 screenshots, two minutes a launch.

Two ways to run it:

- **Dev client** (`gradlew runClient -Prehearsal`): works on NeoForge and
  on Fabric. It tests the code, not the shipped jar.
- **Production client**: Rehearsal's `neoforge_pack.py` and
  `fabric_pack.py` launch a real instance with the mods under test. They
  need Python 3 (now on this machine through uv), `portablemc`, and an
  instance folder named in `local.properties`. Not set up for Krios yet;
  this is the test "the jars in a real client" the user still does by
  hand.

Against a dedicated server (the L3 one, left running) the same scripts
check what single player hides: menu sync, block events, permissions.
Rehearsal has no `connect` verb; a dev client can join with
`--quickPlayMultiplayer`.

### L5 — Visual review (works, not yet a routine)

Screenshots are files, and the agent reads images: a missing texture, an
empty panel, text running out of its frame or a tooltip in the wrong
place are visible to it. On its first day it confirmed what the user was
about to check by hand (terminals after the core split, the unlock list,
the Codex order, the viewers keeping off the panels, the emissive ores on
both loaders) and found one defect nobody had seen: in EMI the Grinding
category's energy and duration were white shadowed text on a light
background, and "800 E" read "899 E". Fixed and photographed again the
same hour.

Two uses, to combine:

- **Review**: after a change, the agent looks at the screens the change
  touches and says what it sees. Good at "something is clearly off", weak
  at "is this the intended shade".
- **Baselines**: `scripts/compare.py` in Rehearsal scores two runs'
  screenshots (SSIM). Approved screenshots kept as references turn "looks
  the same as last release" into a number. It needs a fixed window size,
  GUI scale, time of day and a still frame (the terminals animate: KOIOS
  blinks, arrows fill; either compare with a tolerance or freeze the
  animation for the shot).

What it does not replace: the user's eye on new art and on how a screen
*reads*. A new screen is approved once by the user; from then on the
baseline guards it.

### L6 — Performance (tools exist, no routine)

ADR-012 says "measured, not assumed: profile before each release". The
pieces: Rehearsal's `measure` (frame times to CSV, compared between runs),
`/tick` on the L3 server (the scenario already sprints: its tick rate is
printed by the game), and the method of ADR-027's bench. A routine would
be: a base of N machines of each kind, sprinted, with the mean tick time
compared to the previous release's and a threshold.

### L7 — Release pipeline (not built)

The tag-triggered workflows have never run. A dry run needs either a
`workflow_dispatch` input that stops before `mc-publish`, or a throwaway
tag on a branch with the store ids emptied (an empty id skips the store).
Worth doing once, before `core/v0.1.0`.

## Rehearsal: what it has, what was added, what it lacks

It had: chat commands, teleport, camera, screenshots, `wait stable`, frame
time measures, flights (recorded camera paths), shader switches, opening
or creating a world, window modes, extensions (a mod under test adds its
own state and verbs with one class and no dependency).

Added on 2026-11-23 (local commit in the Rehearsal checkout, Minecraft
1.20.1 and 1.21.1):

| Verb | Use |
|---|---|
| `use <x> <y> <z> [face]` | right-click a block: open its screen |
| `hold` / `release` | keep sneak, use, attack… down (a sneak-click) |
| `hotbar <n>` | select a held item |
| `screen`, `wait screen` | the open screen: title, menu type, frame, every item in its slots |
| `slot <n> [shift…]` | click a container slot |
| `cursor` | a virtual mouse cursor: hover, tooltips |
| `click`, `scroll` | mouse input through the game's own path, at the cursor |
| `key`, `type` | keyboard input through the game's own path |
| `close` | close the screen |

Input enters where the window's callbacks do, so loader events fire and
other mods (JEI, EMI) react as to a real user.

Still missing, by usefulness for Krios:

1. **The newer Minecraft versions.** Mouse and key events changed shape
   after 1.21.1; on 1.21.11 and 26.x the new verbs answer "not on this
   version yet". Krios does not need it; Rehearsal's other users do.
2. **Reading what is drawn.** The tooltip under the cursor and the text
   widgets of a screen as text, so a test asserts "the tooltip says tier
   1" without a picture.
3. **Waiting on a condition** (`wait until <extension key> <op> <value>`):
   today a script waits a fixed number of ticks and hopes.
4. **Entities and the held item.** `use entity` (imprinting an Engram),
   `use` in the air, `attack <x> <y> <z>` (breaking a block as a player:
   drops, tool requirements).
5. **`connect <host:port>`**, for the client-against-server runs.
6. **Drag** (JEI's ghost items, slot dragging) and double click.
7. **A still frame** for baselines: freeze animation time for one shot.
8. **Assertions in the command file** (`expect screen menu = …`), so a
   run fails by itself instead of a script reading the outbox afterwards.

## What Krios must add on its side

- **An extension class** (one per mod, JDK types only): the state a
  picture does not give — the terminal's status, a machine's synced
  values, the Codex entries in order, the unlock list — and verbs that
  would be brittle as pixel clicks (open the Codex, toggle an annex
  panel, select a creative tab). *Built on 2026-11-25 (ADR-033): one class
  in core serves every mod, because each terminal describes itself.
  Creative tabs are vanilla's and stay out of it.*
- Done on 2026-11-23: a test run directory of its own
  (`<loader>/runs/rehearsal`), scenario files per area, and Rehearsal in
  the Fabric dev run (its jar, built with a newer Loom than Krios's 1.8
  accepts as a mod dependency, goes in the run's `mods` folder; the
  loader remaps it).
- **Coordinates are the weak point of the scenarios**: the Codex button,
  the panel tabs and the viewers' category tabs are clicked at pixels of
  a 640x360 GUI. The extension verbs above would replace the first two;
  the viewers' tabs stay pixel clicks until Rehearsal can find a widget.

## What stays with the user

- Balance and pacing (the numbers pass), which is play, not test.
- Art: a new texture or screen is judged once by eye.
- The first look at any new feature. Automation guards what was approved;
  it does not approve.
- Store pages and tokens.

## Where each layer can run

| Layer | This machine | GitHub runner |
|---|---|---|
| L0 static, L1 unit, L2 GameTest | yes | yes, on every push |
| L3 server acceptance | yes (about 15 min for all six targets) | yes in principle (headless, downloads cached); not wired |
| L4, L5 client | yes (about 10 min a loader for the suite) | no GPU; a software renderer under a virtual display is possible in principle, untried |
| L6 performance | yes | no (numbers need the same hardware) |

## Order of work proposed

1. Done: L0, L3, Rehearsal's screen verbs, a first L4 suite on dev
   clients of both loaders with its visual review.
2. **Grow the L4 suite** with what it leaves out (creative tabs, modules
   in the bay, zone holograms, the breakthrough ritual), and the Krios
   extension class that makes it less dependent on pixels.
3. **L3 upgrade test**, from the first release on: archive 0.1.0's world.
4. **L1 unit tests**: cheap, and they make L2 smaller.
5. **L2 GameTest**, growing with each machine instead of the scenario.
6. L4 on production clients (`*_pack.py`), then against a server.
7. L5 baselines, L6 routine, L0–L3 in CI.

## Decisions for the user

- GameTest: tests inside each mod's jar, or a separate test mod.
- Whether release tags should *require* a green L3 run (a script that
  refuses to tag without a fresh `report.md`), or stay a convention.
- Krios on a newer Loom and NeoForge for development: the mods run
  unchanged on NeoForge 21.1.267, and the old Loom is what blocks current
  JEI and Rehearsal jars in Fabric dev runs.

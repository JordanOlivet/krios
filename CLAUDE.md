# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Monorepo for **Krios**, an ecosystem of original Minecraft mods (MC 1.21.1, Java 21, NeoForge + Fabric via the MultiLoader pattern — no Forge, no Architectury). Two mods so far: `mods/core` (Krios: Core, the spine every Krios mod requires — ADR-018/028) and `mods/industry` (Krios: Industry, the machines). Each mod is a standalone Gradle build; there is no root build. Industry includes `../core` as a composite build, so building or running Industry builds and loads core too.

Read first, in order: the latest `docs/progress/YYYY-MM-DD.md` (session resume point), `docs/DECISIONS.md` (ADRs — binding), `docs/mods/01-industry-design.md` (gameplay design), `docs/VISION.md`.

## Commands

All Gradle work happens inside a mod directory (`mods/industry` for day-to-day work: its dev runs load core as well; `mods/core` to build or run core alone):

```powershell
cd mods\industry
.\gradlew.bat build --no-daemon          # builds common + fabric + neoforge jars
.\gradlew.bat neoforge:runServer         # dev server (run dir: neoforge\run, eula.txt required)
.\gradlew.bat fabric:runServer           # dev server (run dir: fabric\runs\server)
tools\run-client.bat [neoforge|fabric]   # from repo root: launch a dev client for manual testing
```

Dev runs load a recipe viewer (ADR-029): `dev_recipe_viewer` in each mod's `gradle.properties` (`jei`, `emi`, `both`, `none`), or `-Pdev_recipe_viewer=emi` for one run. `-Pquickplay=<world folder>` opens a dev client straight into that singleplayer world.

Tests (ADR-030, details in `tools/acceptance/README.md`, the reasoning in `docs/analysis/test-automation.md`). Python 3 goes through **uv** (`uv run <script>`; bare `python` may be 2.7):

```powershell
uv run tools/check_assets.py                                  # static: registries against resources, both mods
uv run tools/acceptance/server_acceptance.py all --build      # servers over RCON: dev, production jars, core alone, upgrade
uv run tools/acceptance/server_acceptance.py upgrade          # a world saved by a release, loaded by the built jars
uv run tools/acceptance/client_acceptance.py                  # clients under Rehearsal: screens, world, JEI, EMI
uv run tools/acceptance/client_acceptance.py fabric --prod    # the same on the built jars (portablemc, pinned loader)
uv run tools/acceptance/client_acceptance.py neoforge --prod --server local   # client and server, both on the jars
```

The server tool builds a base, runs it and reads the world back, on the dev servers and on the built jars with current loaders and viewers (`tools/acceptance/versions.toml`). Its upgrade targets build that base with a release's jars (from its GitHub Releases, scenario from the tool at the release's tag) and load the world with the built jars: run them after any change to a registered id, a block entity's NBT, a data component or the server config, and list a release in `versions.toml` when its saves differ from the ones listed (ADR-031). The client tool needs a Rehearsal checkout (`rehearsal.dir` in `local.properties`, see `local.properties.example`) and runs a dev client with `-Prehearsal` in its own game directory (or with `--prod` the built jars through portablemc, in `build/acceptance/client-prod/`), window in the background, sound off; `--server local` starts the matching server and makes the player an operator through `ops.json` (never `op Dev` over RCON: the server resolves the name at Mojang). To change or rebuild Rehearsal while another session may use its checkout, work in a git worktree of it. Its screenshots land in `build/acceptance/client/` and are meant to be looked at (read them after a run and say what they show). Add a check or a scenario step with the feature it covers. No unit tests and no GameTest yet (ADR-010 still plans GameTest); a manual client play test remains the first look at anything new.

Windows gotchas: a backgrounded/killed gradle `runServer` orphans the child JVM. After automated smoke tests, sweep leftovers: `Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -match 'krios' }`. Also, run-dir config files (`eula.txt`, `server.properties`) must be ASCII/no-BOM — a PowerShell `>` redirect writes a UTF-8 BOM and the server then reads the first key as `﻿eula` and refuses the EULA. The same trap has a second door: never round-trip source files through Windows PowerShell 5.1 text cmdlets (`Get-Content -Raw` without `-Encoding` reads UTF-8 as ANSI and mangles non-ASCII like em-dashes; `Set-Content -Encoding utf8` writes a BOM javac rejects) — use the dedicated edit tools or Python for bulk text surgery.

## Architecture (mods/core + mods/industry)

MultiLoader layout: `common/` (99% of the code, compiled against pure vanilla via NeoForm), `neoforge/` and `fabric/` (thin platform layers). Common cannot touch loader APIs; the bridge is **Java ServiceLoader** services declared in `platform/services/` and implemented per loader (`META-INF/services` files in each loader's resources — file *name* must be the fully qualified interface name).

Who owns what (ADR-028): **core** (`io.lakio.krios.core`, ids `krios_core:*`) has the network engine, KOIOS, the Neural Conduit, the Calibrator and blueprints, the ores and materials, the device scheduler (`tick/`), `EnergyBuffer`, the config and commands, the platform services, and the client chrome shared by every terminal (`KriosTerminalScreen`, annex/stats/faces/Codex panels). **Industry** (`io.lakio.krios.industry`, ids `krios_industry:*`) has the machines, modules, kits, the Engram, the grinding recipe type and `IndustryBalance`. Industry depends on core, never the reverse; a mod contributes to core's screens through `CodexEntries` and `KoiosUnlocks` (registered in its `CommonClass`).

- `IRegistrar` (core's `Services.REGISTRAR`, used by every mod under its own namespace): all registration goes through it. Fabric registers immediately; NeoForge routes through `DeferredRegister`s that the entry point attaches to the mod bus (`NeoForgeRegistrar.attach(bus, modid)`, one deferred register per registry and namespace). It also constructs `CreativeModeTab.Builder`, `BlockEntityType` and `MenuType`, because vanilla keeps their factories package-private and each loader widens them differently — never construct these in common.
- `IPlatformHelper` (core): platform queries + `getBurnTime` (NeoForge extends fuels per-stack; Fabric uses the vanilla fuel map).
- Registries holding wildcard generics (`BlockEntityType<?>`, `MenuType<?>`, `RecipeType<?>`) need the cast-helper pattern used in `registry/ModBlockEntities`/`ModMenus`/`ModRecipeTypes`.

Machine pattern (Thermal Reactor and Fractal Grinder are the references; names per ADR-021):

- **Ticking (ADR-027, amends ADR-012/017/022)**: no vanilla tickers or scheduled ticks on machines. Every machine is a device on the per-level `tick/TickManager` (hooked per loader on the level tick): awake = visited every tick, `sleepFor(n)` = probe/cadence, `sleep()` = costs nothing until an alert (`alertDevice`, `ItemFlow.alertMachine`). LIT machines (reactor, grinder, forge, photon) use `machine/LitDevice`: awake while `LIT`, which is now purely visual. Register in `setLevel`, remove in `setRemoved`. Idle still costs nothing; `setChanged()` only when persistent state changes, never per tick.
- **Energy**: common `EnergyBuffer` (primitive ints) is the only thing game logic touches. Platform adapters wrap it: extract-only FE capability (neoforge entry) and TRE `SnapshotParticipant` adapter (fabric). The Fractal Grinder pulls from *adjacent* reactors directly — v0 transport, to be replaced by a proper network with scheduled wake-ups.
- **GUI sync**: `ContainerData` truncates to signed 16-bit shorts. Any value that can exceed 32767 (energy!) ships as a low/high pair reassembled in the menu; constants are never synced.
- **Recipes**: everything is datapack (`data/krios_industry/recipe/` for machines and modules, `data/krios_core/recipe/` for core content). Custom type `krios_industry:grinding`. Material inputs use `c:` tags (ADR-008); we also provide `c:` tags for our materials.
- **Server config (ADR-015)**: per-world `serverconfig/krios_core-server.properties` via core's `config/KriosConfig` (lazy per-server cache, permille ints in logic, decimals in the file). Difficulty multipliers (`energy_cost`, `xp_rate`) apply only through `KoiosBalance.scaled*`; in-game editing is `/krios_core config` (ADR-019 per-mod overrides are not built yet). No loader config APIs, no Cloth Config/ModMenu hard deps.
- **Commands (ADR-016)**: canonical root = modid (`/krios_core config`, `/krios_core koios …`; Industry has no command yet); `/koios` is a diegetic alias of the KOIOS subtree only. Aliases rebuild the subtree (fresh builder per registration), no Brigadier redirects.
- **KOIOS** (core, `koios/` package): the progression knobs (level curve, tiers, diversity, `scaled*`) in `KoiosBalance`; content balance (per-operation XP and energy, pacing, module rungs) in Industry's `IndustryBalance`. The core block entity never ticks — multiblock revalidation is event-driven (placement, casing place/remove, GUI open) and machines push operations to it via `KoiosNetwork`, a v0 static per-dimension index (closest formed core ≤32 blocks; registered in `setLevel`, unregistered in `setRemoved`, lazily purged). Diversity = 64-op ring buffer + per-key counts. The core's XP survives breaking via the `krios_core:koios_data` data component (loot `copy_components` + `applyImplicitComponents` on place, keeping max). Module gating happens at slot insertion (server-authoritative `mayPlace`), never in recipes. Once formed, the core flips a FORMED state on all 26 casings — the structure acts as one block (casing right-click opens the core UI; Calibrator sneak-click on any face sets the screen side). The AI head is a client-only BER on the core: animations ride vanilla block events (`EVENT_PULSE/LEVEL_UP/BOOT` via `triggerEvent`), state rides `getUpdateTag`/`sendBlockUpdated` — no custom networking. Casing `onPlace` must ignore same-block property flips or shell updates would recurse into revalidation.
- **Zone machines** (Chrono Farmer, Cyclic Forester, Quantum Lure, Breeding Pod, Harvest Pod, Phase Death Field, Bio Synthesizer): `machine/zone/ZoneMachineBlockEntity` is the shared core (zone behind the screen, ADR-022 pacing on the ADR-027 device scheduler — push-only visits drain the buffer between work wakes, alerts wake at the next tick, a watched terminal refreshes live — energy, buffer, faces, modules); `PlantingZoneBlockEntity` adds plant slots, cell scan and routing (ADR-025); `CreatureZoneBlockEntity<E extends Mob>` adds the 5-tall creature zone, scan, census (shared Census annex) and time acceleration; the Phase Death Field kills through the `IFakePlayers` platform service (ADR-026). No machine takes a consumable tool: enchantment effects are modules (ADR-024).
- **Worldgen**: configured/placed features are common datapack JSONs; biome injection is per-loader (neoforge `biome_modifier` JSON vs `BiomeModifications` code in the fabric entry).

Recipe viewers (ADR-029): JEI and EMI plugins are optional and live in common, `integration/jei` and `integration/emi` of each mod (found by annotation on NeoForge, by the `jei_mod_plugin`/`emi` entrypoints of `fabric.mod.json` on Fabric). Core's plugins cover the terminals' off-frame areas (`KriosTerminalScreen.extraAreas()`, override it when a screen draws outside its frame); a mod's own plugins register its recipe categories and catalysts — a new machine recipe type needs a category in both.

Client code lives in `common/.../client/` of each mod (only referenced client-side); each mod registers its own screens per loader (`RegisterMenuScreensEvent` on NeoForge, `MenuScreens.register` in the fabric client entrypoint).

Textures started as script-generated placeholders (`tools/gen_textures.py`, pure-stdlib PNG writer), but the user has since hand-polished most of them in Aseprite (nearly all item textures as of 2026-09-29). **Never overwrite or regenerate any texture without asking the user first.** `gen_textures.py` (in `mods/industry/tools`, it writes core-owned textures into `mods/core`) refuses to run without an explicit filter argument; beware that substring filters can over-match (`ore` also matches `koios_core_*`) — check `git status` on the textures after any run.

## Conventions and process

- Language: docs/code/commits in English; conversation with the user in French.
- ADRs in `docs/DECISIONS.md`: append a new entry to change a decision, don't edit history. New structural choices get an ADR.
- Progress log: one `docs/progress/YYYY-MM-DD.md` per working day, written at session end (state, what was validated, prioritized next steps).
- Git: Conventional Commits, no AI attribution trailers. The bootstrap is over (user, 2026-11-25, after 0.1.0): docs, skills and other minor changes that cannot disturb another session working on the project may still go straight to `main`; everything else (code, build, workflows, assets) goes by default through a git worktree on a dedicated branch and a PR with green CI, so concurrent sessions never conflict. Versioning per ADR-009 (SemVer per mod, tags `industry/v0.1.0`).
- CI: thin callers in `.github/workflows/` delegating to reusable workflows in `JordanOlivet/ci-workflows` (pinned `@main` until that repo's next proper release, then `@v1`). Never direct-push ci-workflows once its CI shape is finalized — PR + `VERSION` bump there.
- License: LGPL-3.0-or-later, code and assets (ADR-011).
- `templates/multiloader/` is the pristine upstream template for bootstrapping new mods — never edit it in place.

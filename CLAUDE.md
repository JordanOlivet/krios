# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Monorepo for **Krios**, an ecosystem of original Minecraft mods (MC 1.21.1, Java 21, NeoForge + Fabric via the MultiLoader pattern — no Forge, no Architectury). First mod: `mods/industry` (Krios: Industry). Each mod is a standalone Gradle build; there is no root build.

Read first, in order: the latest `docs/progress/YYYY-MM-DD.md` (session resume point), `docs/DECISIONS.md` (ADRs — binding), `docs/mods/01-industry-design.md` (gameplay design), `docs/VISION.md`.

## Commands

All Gradle work happens inside a mod directory (e.g. `mods/industry`):

```powershell
cd mods\industry
.\gradlew.bat build --no-daemon          # builds common + fabric + neoforge jars
.\gradlew.bat neoforge:runServer         # dev server (run dir: neoforge\run, eula.txt required)
.\gradlew.bat fabric:runServer           # dev server (run dir: fabric\runs\server)
tools\run-client.bat [neoforge|fabric]   # from repo root: launch a dev client for manual testing
```

No unit tests yet; verification = build green + dev-server boot (registration and datapack errors surface in `logs/latest.log`) + manual client play. GameTest is planned once machines warrant it.

Windows gotchas: a backgrounded/killed gradle `runServer` orphans the child JVM. After automated smoke tests, sweep leftovers: `Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -match 'krios' }`. Also, run-dir config files (`eula.txt`, `server.properties`) must be ASCII/no-BOM — a PowerShell `>` redirect writes a UTF-8 BOM and the server then reads the first key as `﻿eula` and refuses the EULA. The same trap has a second door: never round-trip source files through Windows PowerShell 5.1 text cmdlets (`Get-Content -Raw` without `-Encoding` reads UTF-8 as ANSI and mangles non-ASCII like em-dashes; `Set-Content -Encoding utf8` writes a BOM javac rejects) — use the dedicated edit tools or Python for bulk text surgery.

## Architecture (mods/industry)

MultiLoader layout: `common/` (99% of the code, compiled against pure vanilla via NeoForm), `neoforge/` and `fabric/` (thin platform layers). Common cannot touch loader APIs; the bridge is **Java ServiceLoader** services declared in `platform/services/` and implemented per loader (`META-INF/services` files in each loader's resources — file *name* must be the fully qualified interface name).

- `IRegistrar`: all registration goes through it. Fabric registers immediately; NeoForge routes through `DeferredRegister`s that the entry point attaches to the mod bus (`NeoForgeRegistrar.attach`). It also constructs `CreativeModeTab.Builder`, `BlockEntityType` and `MenuType`, because vanilla keeps their factories package-private and each loader widens them differently — never construct these in common.
- `IPlatformHelper`: platform queries + `getBurnTime` (NeoForge extends fuels per-stack; Fabric uses the vanilla fuel map).
- Registries holding wildcard generics (`BlockEntityType<?>`, `MenuType<?>`, `RecipeType<?>`) need the cast-helper pattern used in `registry/ModBlockEntities`/`ModMenus`/`ModRecipeTypes`.

Machine pattern (Thermal Reactor and Fractal Grinder are the references; names per ADR-021):

- **Ticking (ADR-027, amends ADR-012/017/022)**: no vanilla tickers or scheduled ticks on machines. Every machine is a device on the per-level `tick/TickManager` (hooked per loader on the level tick): awake = visited every tick, `sleepFor(n)` = probe/cadence, `sleep()` = costs nothing until an alert (`alertDevice`, `ItemFlow.alertMachine`). LIT machines (reactor, grinder, forge, photon) use `machine/LitDevice`: awake while `LIT`, which is now purely visual. Register in `setLevel`, remove in `setRemoved`. Idle still costs nothing; `setChanged()` only when persistent state changes, never per tick.
- **Energy**: common `EnergyBuffer` (primitive ints) is the only thing game logic touches. Platform adapters wrap it: extract-only FE capability (neoforge entry) and TRE `SnapshotParticipant` adapter (fabric). The Fractal Grinder pulls from *adjacent* reactors directly — v0 transport, to be replaced by a proper network with scheduled wake-ups.
- **GUI sync**: `ContainerData` truncates to signed 16-bit shorts. Any value that can exceed 32767 (energy!) ships as a low/high pair reassembled in the menu; constants are never synced.
- **Recipes**: everything is datapack (`data/krios_industry/recipe/`). Custom type `krios_industry:crushing`. Material inputs use `c:` tags (ADR-008); we also provide `c:` tags for our materials.
- **Server config (ADR-015)**: per-world `serverconfig/krios_industry-server.properties` via common `config/IndustryConfig` (lazy per-server cache, permille ints in logic, decimals in the file). Difficulty multipliers (`energy_cost`, `xp_rate`) apply only through `KoiosBalance.scaled*`; in-game editing is `/krios_industry config`. No loader config APIs, no Cloth Config/ModMenu hard deps.
- **Commands (ADR-016)**: canonical root = modid (`/krios_industry config`, `/krios_industry koios …`); `/koios` is a diegetic alias of the KOIOS subtree only. Aliases rebuild the subtree (fresh builder per registration), no Brigadier redirects.
- **KOIOS** (`koios/` package): all balance knobs in `KoiosBalance`. The core block entity never ticks — multiblock revalidation is event-driven (placement, casing place/remove, GUI open) and machines push operations to it via `KoiosNetwork`, a v0 static per-dimension index (closest formed core ≤32 blocks; registered in `setLevel`, unregistered in `setRemoved`, lazily purged). Diversity = 64-op ring buffer + per-key counts. The core's XP survives breaking via the `krios_industry:koios_data` data component (loot `copy_components` + `applyImplicitComponents` on place, keeping max). Module gating happens at slot insertion (server-authoritative `mayPlace`), never in recipes. Once formed, the core flips a FORMED state on all 26 casings — the structure acts as one block (casing right-click opens the core UI; Calibrator sneak-click on any face sets the screen side). The AI head is a client-only BER on the core: animations ride vanilla block events (`EVENT_PULSE/LEVEL_UP/BOOT` via `triggerEvent`), state rides `getUpdateTag`/`sendBlockUpdated` — no custom networking. Casing `onPlace` must ignore same-block property flips or shell updates would recurse into revalidation.
- **Zone machines** (Chrono Farmer, Cyclic Forester, Quantum Lure, Breeding Pod, Harvest Pod, Phase Death Field, Bio Synthesizer): `machine/zone/ZoneMachineBlockEntity` is the shared core (zone behind the screen, ADR-022 pacing on the ADR-027 device scheduler — push-only visits drain the buffer between work wakes, alerts wake at the next tick, a watched terminal refreshes live — energy, buffer, faces, modules); `PlantingZoneBlockEntity` adds plant slots, cell scan and routing (ADR-025); `CreatureZoneBlockEntity<E extends Mob>` adds the 5-tall creature zone, scan, census (shared Census annex) and time acceleration; the Phase Death Field kills through the `IFakePlayers` platform service (ADR-026). No machine takes a consumable tool: enchantment effects are modules (ADR-024).
- **Worldgen**: configured/placed features are common datapack JSONs; biome injection is per-loader (neoforge `biome_modifier` JSON vs `BiomeModifications` code in the fabric entry).

Client code lives in `common/.../client/` (only referenced client-side); screens are registered per loader (`RegisterMenuScreensEvent` on NeoForge, `MenuScreens.register` in the fabric client entrypoint).

Textures started as script-generated placeholders (`tools/gen_textures.py`, pure-stdlib PNG writer), but the user has since hand-polished most of them in Aseprite (nearly all item textures as of 2026-09-29). **Never overwrite or regenerate any texture without asking the user first.** `gen_textures.py` refuses to run without an explicit filter argument; beware that substring filters can over-match (`ore` also matches `koios_core_*`) — check `git status` on the textures after any run.

## Conventions and process

- Language: docs/code/commits in English; conversation with the user in French.
- ADRs in `docs/DECISIONS.md`: append a new entry to change a decision, don't edit history. New structural choices get an ADR.
- Progress log: one `docs/progress/YYYY-MM-DD.md` per working day, written at session end (state, what was validated, prioritized next steps).
- Git: Conventional Commits, no AI attribution trailers. Direct pushes to `main` are for the bootstrap phase only — once the user declares the base done, switch to branches + PRs. Versioning per ADR-009 (SemVer per mod, tags `industry/v0.1.0`).
- CI: thin callers in `.github/workflows/` delegating to reusable workflows in `JordanOlivet/ci-workflows` (pinned `@main` until that repo's next proper release, then `@v1`). Never direct-push ci-workflows once its CI shape is finalized — PR + `VERSION` bump there.
- License: LGPL-3.0-or-later, code and assets (ADR-011).
- `templates/multiloader/` is the pristine upstream template for bootstrapping new mods — never edit it in place.

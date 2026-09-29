# Architecture Decision Records

Lightweight ADR log. One entry per structural decision; newest at the bottom. Revisit only with a written superseding entry.

## ADR-001 — Multi-loader via MultiLoader Template

**Date**: 2026-09-20 · **Status**: accepted

Target NeoForge + Fabric from a shared `common` source set, following the MultiLoader Template pattern (Gradle subprojects `common` / `neoforge` / `fabric`).

- Rejected: single-loader (halves the audience for a public ecosystem).
- Rejected: Architectury (imposes a runtime API dependency on every player; less control).
- Cost accepted: platform abstraction layer maintained by us, notably around energy.
- Pristine template kept in `templates/multiloader/` (branch `1.21.1` of jaredlll08/MultiLoader-Template); the template's `forge/` subproject is dropped when instantiating — we ship NeoForge + Fabric only.

## ADR-002 — Minecraft 1.21.1, Java 21

**Date**: 2026-09-20 · **Status**: accepted

Start on MC 1.21.1 (existing experience: neo-voxy, mc-test-harness; rich surrounding mod ecosystem for compat testing). Multi-version support (Stonecutter) deliberately deferred until the first mod is stable.

## ADR-003 — Java, not Kotlin

**Date**: 2026-09-20 · **Status**: accepted

Runtime performance is identical (same bytecode); the decision is about the dependency chain. Kotlin would add a language adapter per loader (Kotlin for Forge + Fabric Language Kotlin), meaning two external dependencies to keep aligned and extra lag on every MC update. Mixins are Java regardless. Not worth it for a public multi-loader ecosystem.

## ADR-004 — Monorepo

**Date**: 2026-09-20 · **Status**: accepted

Single git repository holding docs, shared library, all mods, templates and tools. Each mod stays a standalone Gradle build. Rationale: solo development with a fast-moving shared library — cross-cutting refactors in one commit, no version dance. Splitting later with `git filter-repo` remains possible and history-preserving.

## ADR-005 — Energy: internal system exposed as FE / TRE

**Date**: 2026-09-20 · **Status**: accepted

Internal energy abstraction in `krios_core`, exposed natively as Forge Energy capabilities on NeoForge and Team Reborn Energy on Fabric. Immediate interop with the tech mods present in modpacks. Rejected: closed custom energy (isolates the ecosystem).

## ADR-006 — Batch processing as a core constraint

**Date**: 2026-09-20 · **Status**: accepted

Machine logic computes "N operations elapsed" arithmetically per tick (or lazily on interaction) instead of iterating per item. Extreme speeds are a headline feature; per-item ticking would melt servers and is forbidden from day one. The batch engine lives in `krios_core`.

## ADR-007 — Datapack-driven recipes + difficulty config

**Date**: 2026-09-20 · **Status**: accepted

All machine recipes are standard datapack recipe types. Progression difficulty (costs, gating) is configurable. Pack makers must be able to rebalance everything without touching code.

## ADR-008 — Material compat through `c:` tags

**Date**: 2026-09-20 · **Status**: accepted

New ores are few and theme-specific; common metals (tin, steel, titanium, …) are consumed through `c:` convention tags so any modpack's extended ore set satisfies recipes automatically.

## ADR-009 — Versioning: SemVer per mod, monorepo tags

**Date**: 2026-09-20 · **Status**: accepted

- Each mod versions independently (SemVer) in its own `gradle.properties`; one version number covers both loaders.
- MAJOR bumps also cover **saved-world compat breakage** and addon-facing API breakage — what players and addon authors actually track.
- The Minecraft version lives in file names and metadata only, never in the mod version: `krios_industry-<loader>-<mc>-<version>.jar`. Multi-version builds (Stonecutter, later) reuse the same mod version across MC targets.
- Git tags are mod-prefixed: `industry/v0.1.0`, `core/v0.3.2`. Tags drive releases.
- `krios_core` is versioned separately and shipped jar-in-jar inside each mod; players never download it directly.

## ADR-010 — CI: reusable workflows in ci-workflows

**Date**: 2026-09-20 · **Status**: accepted

- GitHub Actions. Build logic lives in `JordanOlivet/ci-workflows` (public) as the reusable `build-gradle-mod.yml`; krios keeps thin per-mod callers with path filters (`mods/<mod>/**` + `libs/core/**`).
- Gradle/Loom/NeoForm caches handled by `gradle/actions/setup-gradle`; `ubuntu-latest` runners (mod builds are cross-platform).
- Releases: deferred until the first publishable version — planned as tag-triggered (`<mod>/v*`) workflow building both jars and publishing via `mc-publish` (Modrinth + CurseForge + GitHub Release, `MODRINTH_TOKEN`/`CURSEFORGE_TOKEN` secrets).
- In-game testing: GameTest framework (`runGameTestServer`, headless-friendly) wired once the first machines exist; heavy client tests stay local via mc-test-harness.

## ADR-011 — License: LGPL-3.0, code and assets alike

**Date**: 2026-09-20 · **Status**: accepted

- Whole ecosystem under **LGPL-3.0-or-later** (SPDX `LGPL-3.0-or-later`), one license for code and assets — no split terms to maintain.
- Effect: modpacks redistribute freely, addons link freely, forks/derivatives must stay open source. Same choice as AE2.
- Rejected: MIT (allows closed forks of original mechanics), GPL-3.0 (legal grey zone for third-party addons), ARR (blocks modpack redistribution).
- Root `LICENSE` (LGPL-3.0) + `LICENSE.GPL` (GPL-3.0, incorporated by reference — both must ship).
- TODO when build conventions solidify: bundle `LICENSE` into each built jar.

## ADR-012 — Performance is a founding constraint

**Date**: 2026-09-20 · **Status**: accepted

Ultra-high performance is a product pillar, not an optimization pass. Rules applying to every mod in the ecosystem:

- **No per-tick, per-item work in hot paths** — machine logic computes batches arithmetically (extends ADR-006).
- **Idle costs nothing**: block entities never tick while idle — schedule wake-ups (next completion time) instead of polling every tick.
- **Zero allocation in hot paths**: no boxing, no streams, no lambda captures inside tick/render loops; reuse mutable state where the JVM cannot elide it.
- **Data-oriented where it counts**: prefer primitive arrays/flat structures over object graphs for per-machine runtime state.
- **Network discipline**: sync deltas, throttle block entity sync, never broadcast full state per tick.
- **Measured, not assumed**: profile with spark/JFR before each release; a feature that tanks TPS/FPS does not ship until fixed.

## ADR-013 — Network engine lives in Industry behind strict isolation

**Date**: 2026-09-25 · **Status**: accepted

The network engine (connectivity graph + transfers — the ecosystem's future backbone) is implemented inside `mods/industry` common as a self-contained `network/` package, instead of bootstrapping `krios_core` now. Extraction to `krios_core` happens when a second mod needs it: by then the API has a real consumer instead of guesses, and `templates/multiloader` makes the bootstrap cheap.

Three rules keep that extraction mechanical (a package move, not a refactor), enforced from the first commit:

1. **One-way dependency**: `network/` never imports Industry code (machines, registries, KOIOS, `KoiosBalance`). Machines implement the engine's interfaces (node and flow contracts); the KOIOS core is just a registered node type like any other.
2. **The graph is never persisted**: it rebuilds from world state, event-driven (same philosophy as the multiblock revalidation). Blocks save only their own state (e.g. per-face config in their block entity) — so extraction implies zero save-format migration.
3. **No balance in the engine**: rates, caps and unlocks are supplied by the consuming mod, never hardcoded in the engine.

- Rejected: creating `krios_core` immediately (freezes a public API with zero consumers; adds build/CI/version dance before any second client exists).

## ADR-014 — Ecosystem-wide art direction: "cold signal / warm metal", diegetic

**Date**: 2026-09-28 · **Status**: accepted

One art direction for the whole ecosystem, recorded in `docs/ART-DIRECTION.md` (binding reference; palette files in `docs/art/`):

- **Palette direction A**: cyan = software/AI, amber + copper = hardware/heat, gunmetal metal, near-black screens. Color roles are contracts — one color means one thing everywhere, across all mods.
- **Diegetic interfaces**: machine UIs are KOIOS terminals (shared chrome component), not vanilla-style menus; vanilla spirit kept (low-res, simple, uncluttered). Hi-res art allowed only inside framed diegetic displays (e.g. the KOIOS head video feed).
- **Distinct silhouettes**: every machine identifiable by shape alone; 1-block unit with small visual overhangs allowed (hitbox unchanged); structural blocks stay full cubes.
- **Multiblock look ≠ mechanic**: build mechanics never change for looks — only the FORMED representation is sculpted (per-position JSON shell + BER animated centerpiece).
- **In-house guide** ("KOIOS Archives", phased): no Ponder/Patchouli dependency — multiloader/license risk, and the guide's identity is KOIOS explaining its own ecosystem, entries growing with its level.
- Rejected: per-mod divergent styles; strict low-res dogma; external guide libraries.

## ADR-015 — Per-world server config in common, edited in game by command

**Date**: 2026-09-29 · **Status**: accepted

Mod configuration (first content: the ADR-007 difficulty multipliers `energy_cost` and `xp_rate`) lives in a per-world properties file, `<world>/serverconfig/krios_industry-server.properties`, owned by common code (`config/IndustryConfig`): lazy per-server cache (reloads when the singleplayer world changes), human decimals in the file, permille ints in game logic, failure-tolerant IO, file materialized on first use. Application points route through `KoiosBalance.scaled*` — it stays the single balance hookup. In-game editing is `/koios config [key] [value]` (ops, applied live, persisted immediately).

- Per-world (the NeoForge serverconfig location, implemented loader-agnostically via `getWorldPath`): balance is a property of a world like vanilla difficulty; saves can carry different tunings.
- No loader config API and no external library: NeoForge `ModConfigSpec` + Fabric Cloth Config/ModMenu would split config into per-loader code and formats, add hard Fabric dependencies, and produce generic screens against the diegetic AD (ADR-014). Rejected.
- Client display never reads config directly; anything a GUI shows keeps riding ContainerData (the constants-are-never-synced rule holds because scaled values are synced, thresholds stay constants).
- Later, without new dependencies: a codex-style config screen in common, hooked through NeoForge's native mod-list config button and a ModMenu soft entrypoint on Fabric (compile-only).

## ADR-016 — Command namespace: canonical root = modid, diegetic aliases

**Date**: 2026-09-29 · **Status**: accepted

User-facing commands root at the modid — `/krios_industry config`, `/krios_industry koios <info|xp|level|energy>` — so a player who knows the mod's name can discover them by completion; lore names are not an interface. Short diegetic aliases are allowed on top when they carry flavor: `/koios ...` stays as an alias of the KOIOS subtree only (talking to the AI; `config` is a mod knob, not a core one, and is not aliased). Every Krios mod follows the same shape.

- Deferred: a shared `/krios <mod> ...` ecosystem root once a second mod ships — Brigadier merges same-name literals, so each mod can graft its section; permission checks must then sit on the mod-section nodes, not the shared root.

## Open questions

*(none — next ones will come from design work)*

## Resolved outside ADRs

- Base Java package: **`io.lakio.krios.<mod>`** — user owns lakio.io (2026-09-20).
- Remote: https://github.com/JordanOlivet/krios, private until first release (2026-09-20).

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

## ADR-017 — Scheduled wake-ups ride vanilla scheduled block ticks

**Date**: 2026-10-01 · **Status**: accepted

ADR-012's tickless idle always promised "scheduled wake-ups" for the few jobs that must poll a passive neighbor — first client: pulling items from an adjacent chest (item transport, tier 2). The mechanism is the vanilla scheduler: `Level.scheduleTick(pos, block, delay)` + `BlockBehaviour.tick`. It is per-block, allocation-free, **persisted with the chunk save** (a pending wake survives save/load for free), and costs exactly zero for every machine that has no manual item face.

- Only machines with at least one MANUAL item face reschedule (~30 ticks); the chain is entered from player actions (face-config edits, neighbor changes) and from the wake itself — never from chunk load, where tick scheduling is unsafe; persistence of pending ticks covers reloads.
- Rejected: a custom per-level wake queue (same semantics, more code, not persisted) and any always-on ticker (ADR-012).

## ADR-018 — krios_core is a content mod: the three-mod ecosystem architecture

**Date**: 2026-10-01 · **Status**: accepted

The item-transport withdrawal (see the industry design doc, revision 2026-10-01) exposed the real boundary: Industry kept fencing features off as "krios_storage territory", and KOIOS has grown into the ecosystem's spine (tiers, ritual, network governance) — no longer an Industry subsystem. The ecosystem is therefore three mods with a settled ownership map, and `krios_core` is a standalone **content mod** ("Krios: Core"), not the jar-in-jar library ADR-009 imagined — registered content cannot be embedded twice, and core owns blocks and items. It is a hard dependency of every Krios content mod, auto-resolved by launchers; ADR-009's versioning and tagging (`core/vX.Y.Z`) are unchanged, its jar-in-jar line is superseded.

- **Ownership map** — `krios_core`: network engine, KOIOS complete (structure, casings, XP/levels/tiers, ritual, diversity, AI head), face-config system, Neural Conduit (backbone), Calibrator, blueprint/projection system, chronite/neurite ores + worldgen, compute chip, Codex framework, the ADR-014 UI chrome, command and config infrastructure. `krios_industry`: machines, modules, upgrade kits, batteries, machine recipes, LOCAL item flow (adjacency + manual passive-container exchange). `krios_storage`: transport cable, run-scoped routing, then buses/digitization/remote access (opening brief consigned 2026-10-01).
- **KOIOS tiers are the ecosystem's progression spine**: each content mod hangs its features on core tiers (cable transport = a core tier 2 unlock, shipped by storage). "Tiers ship as complete slices" becomes per-mod slices aligned on the shared skeleton.
- **Content mods never depend on each other** — only on core. Cross-mod recipes ride `c:` tags (ADR-008).
- **Inter-mod contract**: plain Java API in core — report operations (key, count, base XP), read level/effective tier, declare flow ports and face config, energy port/sink interfaces, scheduled-wake helper (ADR-017). The progression curve and diversity live in core; per-operation XP values and machine rates stay in each mod.
- **Sequencing**: finish Industry's tier-2 queue first (solar, Mk II modules, energy module, Codex), then extract core — the ADR-013 trigger honored at last: the second consumer is krios_storage's start — then storage v0. Builds stay per-mod in the monorepo, linked by Gradle composite build (`includeBuild`); CI keeps one thin caller per mod.
- **Accepted cost**: KOIOS content changes namespace (`krios_industry:*` → `krios_core:*`); pre-release test worlds break, assumed knowingly.

## ADR-019 — Layered difficulty config: global in core, per-mod overrides

**Date**: 2026-10-01 · **Status**: accepted

Extends ADR-015 (mechanism untouched: per-world properties, decimals in file / permille ints in logic, lazy per-server cache, in-game editing per ADR-016). The **shared difficulty vocabulary** (`energy_cost`, `xp_rate`, later the yield curve) lives in core's `serverconfig/krios_core-server.properties` — the global layer. Each mod's own file may **override** any shared knob; an absent key means *inherit*. Mod-private knobs exist only in their mod's file.

- Resolution order: mod override → global → built-in default. The `scaled*` helpers move to core and resolve per calling mod.
- `/krios_core config <knob>` edits the global; `/krios_<mod> config <knob>` edits that mod's override, with an `inherit` action to drop it; displays always show the effective value and its origin.
- One knob turns the whole ecosystem for pack makers; full per-mod granularity stays available (the P3 trade-off dissolved, session 2026-10-01).

## ADR-020 — Machine lines span t1→t4; simulated forms stand beside them

**Date**: 2026-10-06 · **Status**: accepted

Tier 1 was the MVP and tier 2 only validated the t1→t2 upgrade path; neither defines the content model. Settled in the machine roadmap session (user vision):

- **Every machine is a line.** Its base version lands at tier 1 (nearly every vanilla function gets a basic automation there — the solar panel moves to t1), and each tier brings an improved version of the same line through the existing upgrade kit and `min(machine tier, core tier)` rule — one interface across t1→t4, give or take exceptions. A machine may debut later than t1 only when it truly justifies it (tier-2 candidates: the quarry, whose draw and reach would break the t1 energy economy; the magmatic generator, gated by nether lava).
- **The simulated form is a separate block beside the line, not its last tier.** At tier 4 the player unlocks both the line's t4 machine (fastest, still acting on the world — for players who want to see it happen) and the line's *simulated* counterpart: the process is containerized inside the block (Docker as the reference) and run through space-time manipulation at outsized speed and yield. Different needs → different interface (no zone; inputs are the process's seeds — seed + soil, a mob's neural imprint...). Informally "t5", gated as a tier-4 unlock. It is the concrete meaning of the tier-4 name, Transcendence: KOIOS stops acting on the world and simulates it. Pure computation, no world interaction — the most ADR-012-friendly machine there is; energy scales with throughput, the single balance lever. Energy *generators* get an analogous concept, not yet designed.
- **Network energy only**: process machines run on network energy, never on fuel, unless explicitly specified (generators are the exception by nature).
- **No cross-mod crutch**: a machine's feature set never waits on another content mod (ADR-018's no-dependency rule applied to design, not just code). A quarry throttled by hopper throughput still ships whole; storage only makes it better.
- **Tool-scale counterparts** (a handheld excavator) belong to a future Krios: Technologies, never to Industry blocks.

## ADR-021 — Naming register: advanced-tech line names, cosmic simulated forms

**Date**: 2026-10-06 · **Status**: accepted

The roadmap names (ADR-020) and the MVP's plain names (Generator, Crusher, Induction Furnace, Solar Panel) were placeholders. They read as low-tech and generic, while the fiction is an AI coupling space-time mechanics to process matter. Settled in a dedicated naming pass (user calls throughout):

- **Line machines (t1→t4)**: names that read as advanced technology, with sci-fi allusions to AI, quantum mechanics and physics. Invented words are allowed. Varied word shapes, no uniform suffix: an earlier "Latinate -or" proposal was rejected as heavy and artificial. Families are welcome where the function is shared (Reactor, Pod).
- **Simulated forms (t5)**: a deliberate register shift from engineering to cosmology. KOIOS no longer builds machines, it bottles worlds. Each form gets a distinct, non-generic name that reads as end-game at a glance. One simulated block may serve several lines: each form is still a block of its own (ADR-020 holds).
- **Kept**: Calibrator, Codex, Neural Casing/Conduit, Compute Chip, Upgrade Kit, the modules, the ores (Chronite, Neurite), and the `Mk II/III/IV` suffix (t1 carries no suffix).
- **Everything is renamed, ids included** (pre-release, dev worlds only): registry ids, packages, classes, lang keys, assets, and the recipe type `krios_industry:crushing` → `krios_industry:grinding`. French names agree in gender in Codex prose (Matrice and Forge are feminine).

| Line (EN / FR / id) | Simulated form (EN / FR / id) |
|---|---|
| Thermal Reactor / Réacteur thermique / `thermal_reactor` | — (generators' analog not designed) |
| Magma Reactor / Réacteur magmatique / `magma_reactor` | — |
| Photon Array / Matrice photonique / `photon_array` | — |
| Fractal Grinder / Broyeur fractal / `fractal_grinder` | — |
| Plasma Forge / Forge à plasma / `plasma_forge` | — |
| Chrono Farmer / Chronoferme / `chrono_farmer` | Dyson Garden / Serre de Dyson / `dyson_garden` |
| Cyclic Forester / Sylviculteur cyclique / `cyclic_forester` | Timeless Forest / Forêt intemporelle / `timeless_forest` |
| Quantum Lure / Leurre quantique / `quantum_lure` | Abyss Aggregator / Agrégateur abyssal / `abyss_aggregator` |
| Quantum Mining Rig / Excavateur quantique / `quantum_mining_rig` | Entangled Graviton Core / Noyau à gravitons intriqués / `entangled_graviton_core` |
| Breeding Pod / Capsule d'élevage / `breeding_pod` | Genesis Engine / Moteur de Genèse / `genesis_engine` (shared) |
| Harvest Pod / Capsule de récolte / `harvest_pod` | Genesis Engine (shared) |
| Bio Synthesizer / Bio-synthétiseur / `bio_synthesizer` | Oblivion Engine / Moteur du Néant / `oblivion_engine` (shared) |
| Phase Death Field / Champ de mort phasique / `phase_death_field` | Oblivion Engine (shared) |
| Reaper Array / Faucheuse matricielle / `reaper_array` | Oblivion Engine (shared) |

- **Neural imprint → Engram / Engramme / `engram`.** Genesis and Oblivion Engine share one word for opposite functions (life's loop vs. death's loop), and the pairing is intended.
- **Watch points**: *Entangled Graviton Core* (23 characters) may overflow GUI titles and JEI, so shorten it to *Graviton Core* if it does. *Excavateur quantique* means the future Krios: Technologies handheld tool must not be named "excavateur". *Killing Field* was rejected because of its Cambodian-genocide connotation.
- Supersedes the design doc's "Naming: sober, no prefix" line.

## Open questions

*(none — next ones will come from design work)*

## Resolved outside ADRs

- Base Java package: **`io.lakio.krios.<mod>`** — user owns lakio.io (2026-09-20).
- Remote: https://github.com/JordanOlivet/krios, private until first release (2026-09-20).

## ADR-022 — Zone machines pace themselves on scheduled ticks with idle backoff

**Date**: 2026-10-08 · **Status**: accepted

Zone machines (Chrono Farmer first; Cyclic Forester, Quantum Lure, creature lines next) must watch the world: crop growth rides random ticks nobody can observe, so "wake on input" (ADR-012) alone cannot drive them. Extending ADR-017, their only clock is the vanilla scheduler:

- **No ticker, ever.** Each `scheduleTick` wake processes a bounded batch of zone cells (a cursor sweeping the zone), then reschedules itself.
- **Idle backoff**: a full pass with nothing to do stretches the delay (order of 20 → 200 ticks, knobs in balance constants); the first action found restores the fast pace.
- **True stop** (no reschedule) when work is impossible, not merely absent: output buffer full, no energy, or a zone with no workable cell. The chain re-enters from player actions and state changes (item extracted, energy received, configuration edited) — never from chunk load (ADR-017); persisted pending ticks cover reloads.
- `LIT` means "working" (visual), no longer "ticker attached".
- Cells in unloaded chunks are skipped, never force-loaded.

Cost: one persisted, allocation-free scheduled tick per zone machine. Rejected: an always-on ticker scanning the zone (ADR-012), and a global zone-scan manager (same semantics, more code, not persisted).

**Amended at implementation (2026-10-08)**: the only true stop is a **full output buffer**. "No energy" cannot stop the chain — a reactor paused on a full buffer never pushes, so a stopped, starving machine would deadlock; it backs off and keeps pulling at each wake (the pull relights the producers). "No workable cell" cannot stop it either — nothing notifies the machine when the player tills its zone. Both ride the idle backoff. Shipped numbers: 10 ticks after a wake that acted, 100 after a wake with nothing to do; at most 4 x effective-tier speed factor actions per wake.

## ADR-023 — Mixins: common, minimal, only where no loader API exists

**Date**: 2026-10-08 · **Status**: accepted

The Chrono Farmer must keep its zone's farmland moist. Topping moisture up on each pass left random dry blocks between passes (vanilla shows the moist texture only at moisture 7, and every random tick away from water decrements it). The robust fix is to make the zone count as near water: NeoForge has `FarmlandWaterManager` tickets, Fabric has nothing.

- **First mixin of the codebase**: `FarmBlockMixin` injects at the head of `FarmBlock.isNearWater` and answers true for soil inside a registered zone (`HydrationZones`, a v0 static per-level index like `KoiosNetwork`). It lives in the **common** mixin config — one behavior, both loaders, no divergent paths.
- **Rule for future mixins**: only when no loader API covers the need on both sides; common config by default; a HEAD inject or the narrowest injection point, never an overwrite; each one documents its reason in its Javadoc and is listed here.
- Rejected: NeoForge tickets + a Fabric-only mixin (two code paths for one behavior), and faster re-moisturizing passes (still flickers, costs scans).

## ADR-024 — Autonomous machines take no consumable prerequisite; tool enchantments become modules

**Date**: 2026-10-09 · **Status**: accepted

The roadmap gave the Quantum Lure a rod slot whose enchantments apply and whose durability wears. The user rejected it at design time: a line machine exists to make a process autonomous, and one that must be refilled with a tool on a schedule loses most of its point.

- **No consumable is indispensable to run a machine.** Inputs that ARE the process (seeds, a generator's fuel, ores) stay; a tool the machine wears out to function does not.
- **A tool's enchantments become machine modules** on the existing points model (Mk I = 1 pt, Mk II = 2), unlocked on the KOIOS ladder like every module. The player's effort moves from refilling to crafting upgrades. First instance: the Lure and Luck modules of the Quantum Lure (enchantments with no effect once there is no tool, such as Unbreaking or Mending, get no module).
- **Machine-specific axes share the one module bay** (user call: choosing what to slot is part of the game) and are refused elsewhere by the server-side `mayPlace` gate — the energy-module precedent.
- Applies to future lines with a natural tool (Mining Rig, Reaper Array...): their enchantments come as modules, never a tool slot. Supersedes the design doc's rod-slot line and the Cyclic Forester's "shears/silk slot may come later" note.

## ADR-025 — The zone-machine core splits: zone core, planting layer, direct clients

**Date**: 2026-10-09 · **Status**: accepted

The Quantum Lure, the third zone machine, plants nothing, scans no cells and paces itself on catch timers. The core extracted with the Cyclic Forester assumed plantables throughout (plant slots, plantables-first routing, voiding toggle, cell scan, forced growth).

- **`ZoneMachineBlockEntity`** keeps what every zone machine shares: the zone (behind the screen; edge per machine — tier-driven and resizable, or fixed), the ADR-022 wake chain (a machine supplies its work step, its next delay and its idle state), energy, the output buffer, faces, the module bay, KOIOS reporting and hologram sync. Its input area is generic: each machine declares how many input slots it has (zero allowed) and what they accept, and which module axes its bay takes.
- **`PlantingZoneBlockEntity`** carries the plant slots, the cell scan with its cursor and pass bookkeeping, Speed as forced random ticks, and the plantables-first routing with the warned voiding toggle. The Chrono Farmer and the Cyclic Forester extend it; their behavior does not change.
- **Direct clients** (the Quantum Lure first; the creature lines likely) extend the core and own their work model.
- Menus and terminals follow the same split (zone menu/screen base, planting menu/screen, per-machine work areas).
- Rejected: reusing the plant slots as a rod or input slot behind flags (routing and voiding logic would leak into machines with no plantables), and a separate non-zone base for the Lure (it still has a zone, a hologram and the zone pacing).

**Amended with the Breeding Pod (2026-10-09)**: the output buffer's size is declared per machine like the inputs, zero allowed — the Breeding Pod produces animals, not items. The core also offers push-only wakes (a non-empty buffer drains between work wakes) and an immediate wake on a module swap (user play-test calls on the Lure).

**Amended with the Harvest Pod (2026-10-13)**: the creature lines get their own layer, as the planting machines did. **`CreatureZoneBlockEntity`** extends the core and carries the three-block creature zone box, the animal scan grouped by species, the synced census (rendered by one shared Census annex panel on the terminals) and the time acceleration (Speed on animals). The Breeding Pod and the Harvest Pod extend it; the Reaper Array is expected to. Extracted at the second client (rule of two); the Breeding Pod's behavior, NBT keys and slot layout do not change.

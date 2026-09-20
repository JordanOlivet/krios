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

## Open questions

- Base Java package: `dev.krios.<mod>` vs `io.github.jordanolivet.krios.<mod>` (Maven-safe). Decide before first code.
- License (required before public release; monorepo may need per-mod licensing).
- CI wiring into `JordanOlivet/ci-workflows` (path-filtered per mod).
- Git remote / repository publication timing.

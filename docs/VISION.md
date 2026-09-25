# Krios — Vision

## One-liner

Krios is an ecosystem of futuristic-technology Minecraft mods built around **K.O.I.O.S.**, an in-game AI core that manipulates quantum and temporal mechanics to automate, accelerate and multiply everything Minecraft does.

## Lore foundation

Krios and Koios are Titan brothers of Greek mythology:

- **Krios** (Κριός) — Titan of the constellations and the celestial measure of time. He names the ecosystem and embodies the **hardware axis**: quantum/temporal machinery.
- **Koios** (Κοῖος) — Titan of the inquisitive intellect. In-game he becomes **K.O.I.O.S.** — *Kernel for Optimized Industrial Operations & Synthesis* — the AI core the player builds, trains and upgrades. He embodies the **software axis**.

The fiction: machines do not merely process matter — they compress time around it and recompose it at the quantum level, all orchestrated by an ever-learning AI. This justifies the two signature mechanics: near-infinite processing speed (time compression) and resource yield multiplication (quantum matter recomposition).

## Design pillars

1. **Dual progression axis** — every meaningful upgrade path splits into *software* (KOIOS algorithms, learned optimizations) and *hardware* (quantum components, better materials). Players progress on both fronts; the interesting choices live in the tension between them.
2. **Infinite speed, zero lag** — machines are designed for extreme late-game throughput. All processing is batch-computed (N operations per tick as arithmetic, never per-item iteration), so a "million operations per second" machine costs the server the same as a slow one. This is a hard technical requirement, not an optimization to do later.
3. **More from less** — ore crushing at x2 yield is the entry point of a deeper chain: higher tiers extract progressively more from the same input. The fantasy is mastery over matter itself.
4. **Modpack-first configurability** — all recipes are datapack-driven, progression difficulty is configurable. The mod must work as light vanilla+ solo content *and* as the industrial backbone of an expert modpack, with pack makers fully autonomous.
5. **Interoperability by default** — energy is exposed as Forge Energy (NeoForge) / Team Reborn Energy (Fabric); materials use `c:` convention tags so the extended modpack ore set (tin, steel, titanium, …) plugs in automatically.

## The network (vision, 2026-09-25)

- The KOIOS core multiblock is the **central controller** of every Krios system: the mod's machines and functional blocks need a core connection to reach their real performance. Disconnected, they run at **vanilla parity** (as efficient as the vanilla baseline, upgrades inert); and the first core is craftable through vanilla means (furnace-smelted ores), so nothing hard-blocks the early game.
- **One universal conduit**: a single cable carries everything — energy, items, fluids, data. No per-type cable zoo. Wireless connectivity arrives later in the progression (software-axis unlock and/or relay hardware); cables are the early answer, the AI outgrows them.
- **Machines are network nodes**: touching mod blocks propagate the network by adjacency (AE2-controller style) — a chain of glued machines is networked as soon as one element reaches a core. Cables only bridge gaps.
- Connected machines share what they hold across the network; storage (batteries, AE2-like item storage) plugs into the same network, some of it as satellite mods.
- The graph/transfer engine is ecosystem infrastructure destined for `krios_core`, per-tick-free by construction (event-driven graph updates, scheduled wake-ups, batch transfers — pillar 2). It replaces Industry's v0 stopgaps (adjacent-block energy pull, radius-based XP reporting). It is first implemented inside Industry as an isolated `network/` package and extracted once a second mod consumes it (ADR-013).

## Ecosystem map

| Mod | Working name | Scope | Status |
|---|---|---|---|
| **Krios: Industry** | `krios_industry` | Machines, energy generation, new ores, processing/yield chains, speed upgrades | **MVP — in design** |
| Krios: Storage | `krios_storage` | Digital storage & logistics network (AE2-like) | idea |
| Krios: Exo | `krios_exo` | Upgradeable modular futuristic armor | idea |
| Krios: Void | `krios_void` | Space exploration (No Man's Sky / EverSpace / Starsector inspiration) | idea |
| Krios: Construct | `krios_construct` | Futuristic building blocks with function (force-field doors, …) | idea |
| Krios: Vehicles | `krios_vehicles` | Futuristic vehicles | idea |

Energy generation ships inside **Krios: Industry** for now; it may split into its own mod later if it grows enough.

The shared library (`libs/core`, working name `krios_core`) hosts what every mod needs: energy abstraction over FE/TRE, registration helpers, batch-processing engine, config/datapack utilities, KOIOS UI language (holograms, data aesthetic).

## Naming conventions

- Mod display names: `Krios: <Name>`
- Mod ids: `krios_<name>` (snake_case, `krios_` prefix reserved)
- Ecosystem brand verified free on Modrinth and CurseForge (2026-09-20)

# Krios: Industry — MVP Design

**Status**: draft — iterating through Q&A. Names marked *(placeholder)* are open.

## Pitch

The industrial heart of the Krios ecosystem. Automate, optimize and accelerate every classic Minecraft process (crafting, smelting, …) to absurd speeds, and extract more from the same resources — ore crushing starts at x2 yield. All of it orchestrated by K.O.I.O.S., the AI core, and powered by quantum/temporal machinery.

## Core loop (MVP)

1. Mine new theme ores + vanilla/tagged metals.
2. Build a first generator → power first machines.
3. Crusher doubles ore yield; enhanced furnace smelts faster.
4. Upgrades push speed/efficiency per machine — hardware modules and, later, KOIOS software.
5. Higher tiers unlock better ratios and throughput.

## MVP scope

### Machines

| Machine | Role | Notes |
|---|---|---|
| Generator (tier 1) | Burns fuel → energy | Simple, cheap, boring on purpose |
| Crusher | Ore → 2x dust | The hook: immediate visible gain |
| Enhanced furnace | Faster smelting, energy-powered | Accepts speed upgrades |
| *(stretch)* Assembler | Automated crafting | Only if MVP timeline allows |

### Energy

Internal unit exposed as FE (NeoForge) / TRE (Fabric) via `krios_core`. Tier-1 generator + internal machine buffers. No cables in MVP — machines charge from adjacent generator or item-based transport; logistics comes later (Krios: Storage territory).

### Ores / materials

- 2–3 new ores, theme-bound *(placeholders)*: **Chronite** (time-infused crystal — hardware axis), **Neurite** (data-conductive mineral — software axis).
- Common metals via `c:` tags (copper, tin, steel, titanium, …) — no duplication of what modpacks already add.

### Upgrades

- Hardware modules slotted into machines: speed, efficiency, yield.
- Speed scaling is multiplicative and uncapped by design — batch engine (ADR-006) makes it lag-free.
- Software axis in MVP: minimal presence (KOIOS terminal block as crafting/lore anchor). Full software progression tree is post-MVP.

### Configurability

- Every recipe = datapack recipe type.
- Config: progression difficulty multipliers (energy costs, upgrade costs, yield curve).

## Non-goals (MVP)

- Pipes/cables/logistics networks
- Multiblocks
- Armor, tools
- World generation beyond the 2–3 ores
- KOIOS software progression tree (anchor block only)

## Technical constraints

- Batch processing everywhere (ADR-006).
- Multiloader: common-first code, platform code only for energy exposure and registration (ADR-001, ADR-005).
- Recipes/tags data-driven from day one (ADR-007, ADR-008).

## Open questions

- Ore identity: final names, worldgen placement (overworld? deepslate? end-game dimension tie-in?).
- Machine naming language (Quantum Crusher? Temporal Furnace? consistent prefix or free naming?).
- Yield chain depth at MVP: crusher x2 only, or already a second processing step (x3)?
- KOIOS presence in MVP: terminal block only, or first software upgrade slot?
- Tier count for the full mod (MVP ships tier 1 only).

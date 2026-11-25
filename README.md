# Krios

Minecraft tech mods where the factory has a mind. You build **K.O.I.O.S.** (Kernel for Optimized Industrial Operations & Synthesis), an AI core, and connect machines to it: every operation they run teaches it something, and what it learns unlocks their upgrades.

Minecraft **1.21.1**, **NeoForge** and **Fabric**.

## The mods

| Mod | What it is | Get it |
|---|---|---|
| **Krios: Core** | The K.O.I.O.S. core, the network, the Calibrator and the two Krios ores. Required by every Krios mod; adds no machines by itself. | [Modrinth](https://modrinth.com/mod/krios-core) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/krios-core) |
| **Krios: Industry** | Eleven machines on the network: generators, an ore-doubling grinder, an electric forge, and automated farming, fishing, breeding and mob farms. Modules and Mk II tiers unlocked by the core's levels. | [Modrinth](https://modrinth.com/mod/krios-industry) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/krios-industry) |

Install both, on the client and on the server. On Fabric, Fabric API is needed too. JEI and EMI are supported.

## Getting started

1. Mine **neurite** (Y -8 to 72) and **chronite** (below Y 0). Both ores glow in the dark.
2. Craft a **K.O.I.O.S. Core** and 26 **Neural Casings**, and build the 3×3×3 shell around the core. The **Calibrator** projects a hologram of it (sneak-click the ground).
3. Put a **Thermal Reactor** and a machine against the structure, or link them with **Neural Conduits**. Fuel the reactor.
4. Open the core's screen (right-click the structure): its level, what the next levels unlock, and the **Codex**, the in-game guide.

The mods are at 0.1.0: tiers 1 and 2 are playable, balance and some art will still move. Bugs and ideas are welcome in the [issues](https://github.com/JordanOlivet/krios/issues).

## For pack makers

Every recipe is datapack JSON (the grinder has its own type, `krios_industry:grinding`), materials use the `c:` tags, the generators expose Forge Energy and Team Reborn Energy, and `/krios_core config` scales energy costs and data XP gain per world. The license is LGPL-3.0-or-later: use the mods in any pack.

## For developers

Each mod is a standalone Gradle multiloader build (`common` against vanilla, thin `neoforge` and `fabric` layers, no runtime dependency on a multiloader library).

| Path | Purpose |
|---|---|
| `mods/core`, `mods/industry` | The mods. Industry includes core as a composite build |
| `docs/` | [Vision](docs/VISION.md), [decisions](docs/DECISIONS.md), per-mod design, progress logs, [store listings](docs/listing/README.md) |
| `tools/` | Asset checks and the [acceptance tests](tools/acceptance/README.md) (servers over RCON, clients under Rehearsal) |
| `templates/multiloader/` | The pristine MultiLoader Template (1.21.1) new mods start from |

```
cd mods/industry
./gradlew build                    # common, fabric and neoforge jars of both mods
./gradlew neoforge:runClient       # a dev client with both mods
```

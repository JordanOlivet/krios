**Krios: Core** is the spine of the Krios mods: **K.O.I.O.S.** (Kernel for Optimized Industrial Operations & Synthesis), the AI core you build and raise, the network that connects machines to it, the Calibrator, and the two Krios ores. Every Krios mod requires it.

**On its own it adds no machines.** Install it with **Krios: Industry**, which brings the generators, the processing machines and the farms that give the core something to learn from.

NeoForge and Fabric · single player and servers

## What it adds

- **The K.O.I.O.S. Core.** A core block inside a 3×3×3 shell of Neural Casing. Once formed, the structure acts as one block with a face that watches you work. It gains data XP from the machines on its network, climbs levels and tiers, and keeps all of it when you break it and move it.
- **The network.** Krios machines that touch each other are networked, and the **Neural Conduit** bridges the gaps. One core per network. Each machine face can be left to configure itself or set by hand, for energy and for items.
- **The Calibrator.** Sneak-click the ground to project a hologram of the core's structure and build inside it. Hold it to see how nearby machines have their faces set. Right-click any network block for a live diagnostic: members, energy produced and consumed, machines out of power.
- **The Codex.** The in-game guide, opened from the core's screen. Other Krios mods add their own entries to it.
- **Two ores.** Neurite between Y -8 and 72, chronite below Y 0, in stone and deepslate, both glowing in the dark. Their materials (shards, dust, ingots, the Compute Chip) are what the other Krios mods are built from. Core also holds the dusts of iron, gold and copper, which smelt into their ingots.

## Packs and servers

- Install it on the server and on the players' clients.
- Recipes are datapack JSON, and the materials carry the `c:` tags (`c:ores/neurite`, `c:ingots/neurite`, …).
- `/krios_core config` sets a world's difficulty: energy costs and data XP gain. `/koios info` reports on the nearest core.
- LGPL-3.0-or-later: use it in any modpack.

## Compatibility

JEI and EMI: an information page on the multiblock, and the viewers stay clear of the terminals' side panels. Tested with JEI 19.76 and EMI 1.1.24 on NeoForge 21.1.267 and Fabric Loader 0.20.1, on clients and on dedicated servers.

## Requirements

- Fabric: Fabric API.

In English and French.

## Not there yet

- **Tiers 3 and 4** of the core: four are planned, and the first two are playable with Krios: Industry.
- These are the first public versions: balance and some of the art will still move.

## Feedback

Bugs and ideas: [GitHub issues](https://github.com/JordanOlivet/krios/issues). Source: [github.com/JordanOlivet/krios](https://github.com/JordanOlivet/krios).

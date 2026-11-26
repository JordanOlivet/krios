**Krios: Industry** is a tech mod where the factory has a mind. You build **K.O.I.O.S.** (Kernel for Optimized Industrial Operations & Synthesis), an AI core, and connect machines to it. Every operation they run teaches it something, and what it learns unlocks their upgrades.

> *"I am the factory. I learn from every operation your machines run, and my levels unlock their upgrades."* — K.O.I.O.S.

NeoForge and Fabric · single player and servers · requires **Krios: Core**

## How it plays

1. **Mine the two Krios ores.** Neurite sits between Y -8 and 72, chronite below Y 0. Both glow in the dark.
2. **Build the core.** A K.O.I.O.S. Core inside a 3×3×3 shell of Neural Casing. The Calibrator projects a hologram of the structure to build in.
3. **Connect machines.** Machines that touch each other form a network, and Neural Conduits bridge the gaps. A generator anywhere on the network powers all of it.
4. **Run a varied factory.** The core earns data XP from each operation. Repeating the same one is worth less and less, so a factory that does many things levels it faster than one that does a single thing a thousand times.
5. **Spend what it learned.** Levels unlock modules. At level 5, a Compute Chip pays the breakthrough to tier 2: Mk II machines and Mk II modules.

A machine cut off from the core keeps working in a reduced mode: the forge smelts like a plain furnace, the grinder gives one for one, and modules sleep. Nothing locks you out of the early game.

## The machines

| Machine | What it does |
|---|---|
| **Thermal Reactor** | Burns furnace fuel into energy for the network |
| **Photon Array** | Solar energy, with a buffer deep enough to carry the day into the night |
| **Fractal Grinder** | Doubles ores: chronite and neurite, the vanilla ones (iron, gold and copper into dusts to smelt; coal, diamond, emerald, quartz, redstone, lapis) and the metals other mods bring (tin, lead, silver, nickel, zinc and more). Turns cobblestone into gravel and gravel into sand |
| **Plasma Forge** | Smelts everything a furnace does, on energy, twice as fast on the network |
| **Chrono Farmer** | Tills, plants, harvests and replants a field, keeps it moist and hurries the crops |
| **Cyclic Forester** | Plants saplings and fells grown trees from the canopy down, leaves included |
| **Quantum Lure** | Fishes a 5×5 pond with no rod to feed; treasure when the water is open |
| **Breeding Pod** | Breeds the animals penned behind it, up to a cap per species |
| **Harvest Pod** | Shears, milks into buckets, fills bowls at mooshrooms, picks up eggs and scutes |
| **Phase Death Field** | Kills what its filter names, with a player's drops; can spare the breeders of a pen |
| **Bio Synthesizer** | Spawns the monsters of its biome, or a single species imprinted on an Engram |

The last seven work a zone behind the machine: hold the Calibrator to see it. All but the Quantum Lure can shrink or grow theirs from the machine's screen. None of them takes a tool or wears one out.

## Modules and tiers

- **Modules** slot into machines: Speed, Efficiency, Yield, Energy (reactor), Lure and Luck (Quantum Lure). Each machine reads them its own way: Speed hurries crops or animals, Yield is bonus babies, extra wool, or Looting on the Phase Death Field.
- Identical modules stack, and a machine accepts a module only once the core has reached the level that unlocks it.
- **Tier 2**: an Upgrade Kit turns a machine into its Mk II in place, with its configuration and contents kept. Fields and pens grow to 13×13, the grinder and the forge gain a second column, the Quantum Lure a second line, and Mk II modules count double.
- The core keeps its progression when you break it and carry it somewhere else.
- The **Codex**, opened from the core's screen, is the in-game guide. K.O.I.O.S. writes it as it levels.

## Packs and servers

- Install it on the server and on the players' clients.
- Every recipe is datapack JSON, with a `krios_industry:grinding` type for the grinder. Materials use the `c:` tags, and a grinding result can name a tag too (`c:dusts/tin`): one recipe covers whichever mod brings the material, and stays out of the way when none does.
- The generators expose Forge Energy (NeoForge) and Team Reborn Energy (Fabric) for other mods to draw from. Machine faces exchange items with the chests, hoppers and other mods' inventories that touch them.
- `/krios_core config` scales energy costs and data XP gain for a world.
- Machines sleep when they have nothing to do, instead of ticking.
- LGPL-3.0-or-later: use it in any modpack.

## Compatibility

JEI and EMI: a Grinding category, the machines listed as catalysts of the recipes they run, and recipe transfer: the viewer's fill button moves a recipe's input into the open Fractal Grinder, Plasma Forge or Thermal Reactor (a click for one item, a shift-click for all that fits). Tested with JEI 19.76 and EMI 1.1.24 on NeoForge 21.1.267 and Fabric Loader 0.20.1, on clients and on dedicated servers.

## Requirements

- **Krios: Core** 0.2.0 or newer.
- Fabric: Fabric API.

In English and French.

## Not there yet

- **Tiers 3 and 4**: the core has four tiers planned, and the first two are playable, from the first ore to Mk II machines.
- **Cable-borne item transport and digital storage**: planned as a separate mod, Krios: Storage. Today items move between machines and inventories that touch.
- Balance and some of the art will still move: these are the first public versions.

## Feedback

Bugs and ideas: [GitHub issues](https://github.com/JordanOlivet/krios/issues). Source: [github.com/JordanOlivet/krios](https://github.com/JordanOlivet/krios).

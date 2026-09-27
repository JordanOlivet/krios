# Krios — Art Direction

**Status**: settled (design sessions 2026-09-27/28) · **Scope**: the whole ecosystem — every Krios mod follows this document. Industry applies it first (`mods/01-industry-design.md` § Art direction).

Reference material: `art/palette-a.gpl` (Aseprite palette), `art/direction-a.html` (frozen visual board), design exploration artifact "Krios Industry — Identité visuelle" (claude.ai, 3 directions compared).

## Pillars

1. **The graft** — raw industry colonized by an intelligence. Two layers visible everywhere: *hardware* (gunmetal, copper, rivets, amber heat) and *software* that infiltrates it (cyan veins, screens, eyes, holograms). The deeper the KOIOS integration, the more the cyan takes over.
2. **Light holograms & terminals** — the AI manifests through projections and diegetic screens, never through intimidating technical menus. Approachable for non-tech players.
3. **KOIOS is alive everywhere** — a discreet, contextual presence (head, one-liners, micro-animations); never overwhelming.

## Palette — direction A "Cold signal / warm metal"

| Role | Hex |
|---|---|
| Metal edge | `#3A414E` |
| Metal base | `#262B35` |
| Joint / shadow | `#171B22` |
| Screen black | `#10141B` |
| AI bright / level flash | `#9FF0FF` |
| AI glow (eyes, XP, modules, data) | `#31D8FF` |
| Cyan dim (idle) | `#0A6E87` |
| Energy (FE, burn) | `#FFB03A` |
| Amber dark | `#E8843B` |
| Copper hardware | `#C9752E` |
| Copper shadow | `#7A431C` |
| Error / network fault | `#E5484D` |
| GUI background | `#1B202A` |
| GUI slot | `#12151B` |
| Text bright | `#E8EAF0` |
| Text dim | `#5A6170` |

Rules:

- **Color roles are contracts.** Cyan is reserved for intelligence (KOIOS, XP, modules, data cores); amber for heat/power; copper for passive hardware; error red for faults only. One color means one thing everywhere, across all mods.
- Energy vs error differ in **lightness**, not hue alone (color-blind safe).
- Emissive glows sit on dark metal, never the reverse — readable in dark caves.
- 16×16 textures, 3–4 values per material, no fine gradients.
- Two accent families max so everything reads as one system.

## Form language

### Blocks

- **Every machine is identifiable by silhouette alone** — no "same cube, different sticker". 1-block unit; visual overhangs up to ~8 px are fine (antenna, chimney, sensor) as long as the hitbox stays one block. The **top profile** must be distinct too (machines are often viewed from above).
- Structural blocks (casings, frames) stay full cubes — they must tile.
- **Machine state reads without opening a GUI**: side mini-screens with animated glyphs while working (`.mcmeta`), dim when idle, error pictogram when offline/faulted; inserted modules appear as physical cartridges on the machine's flank (blockstate per occupied slot).
- **Cables are thin** (4 px arms — 6 px was too bulky): copper collar at machine contacts, cyan node at junctions, animated pulses flowing toward the consumer.

### Multiblocks

- **Build mechanics never change for looks.** Only the FORMED representation is sculpted: per-position JSON models carve the static shell (chamfers, columns, insets — cheap, correct lighting/culling), the controller's BER renders the living centerpiece (hologram, floating/rotating parts).
- The KOIOS core monolith reads as a **hologram machine**, not a big screen-cube. The AI's avatar hologram gains definition as KOIOS levels up — the projection "resolves" with intelligence.

### Items

- Strict shape language: **software = crystal/cartridge with cyan glow; hardware = metal/copper**. Any Krios item classifies at a glance.
- The **Calibrator is premium**: the ecosystem's transversal tool — 3D in-hand model (spyglass-style), animated mini-screen.

## Interfaces — the KOIOS terminal

Diegetic, assumed: opening a machine UI = connecting to a KOIOS terminal. Vanilla spirit kept: low-res, simple, clear, uncluttered.

- **Shared terminal chrome** (one component, inherited by every mod): CRT-cornered frame, header with machine name + KOIOS head feed + network status, flavor footer ("NODE 04 — GRID 12").
- **KOIOS head in the header**: the head, not an abstract icon. Higher resolution is allowed **only inside framed diegetic displays** (screen-in-screen: the feed's resolution reads as a display's, not as a style break). Same states as the in-world head (blink, pulse, fault static).
- **Boot sequence** on open: 150–250 ms, skippable on click, with sound.
- **Pixel font for headers only** (vanilla font provider); body text stays vanilla font (readability + localization).
- **Per-machine terminals show real stats**: effective speed, consumption/generation, active modules, throughput sparkline (last 60 s).
- **Face-config is a hologram**: the machine rendered in 3D with adjacent blocks as translucent ghosts — left-click a face to select/configure, right-drag to rotate freely (EnderIO-style). Kills the north/south guessing entirely.
- Themed tooltips (terminal frame) for mod items; themed JEI categories.

## KOIOS alive — rules of presence

- GUI: head follows the cursor, blinks; idle 30 s triggers a bored animation.
- **Contextual one-liners in GUIs only, never the chat**, frequency capped, localized (en+fr): "new recipe observed", "diversity low — I am bored", boot lines.
- In-world: animated screen glyphs on working machines, learning pulses on the monolith (aggregated at scale — max ~1/s), signature particles (square cyan "bits" over connected machines, amber embers over burning ones).
- Every knob lives in balance/config (KoiosBalance pattern).

## Holograms

- **Face-config hologram** (above).
- **Blueprint projection via the Calibrator**: pick a structure (KOIOS core, any future multiblock), click the ground → anchored holographic blueprint; correctly placed blocks fill the ghost as you build. The construction guide *is* the hologram. Ecosystem-wide.
- **Core avatar hologram**: the monolith's centerpiece (see Multiblocks).

## In-game guide — "KOIOS Archives"

In-house, no Ponder/Patchouli dependency. Phased:

1. **v1 — terminal-style entries**: one entry per machine/mechanic (role, how it works, stats, low-fi diagrams). One screen per entry — a guide, not a wiki.
2. **v2 — blueprint holograms** for structures (above).
3. **v3 — animated scenes** (ponder-like) only if the ecosystem's size justifies it.

The guide **is KOIOS explaining its own ecosystem**: entries grow richer with the core's level — a young AI gives basics, an evolved one gives details, stats, secrets. The guide's progression is the AI's progression.

Never mandatory: JEI keeps recipes, the game stays discoverable without the Archives.

## Sound

A brand palette, shared across mods: low hum for hardware, crystalline ticks for software (XP pulses, UI clicks), discreet UI blips. Formation/level-up keep their beacon-family sounds.

## Anti-goals

- Custom mouse cursor (gadget, hurts usability).
- KOIOS messages in the chat (spam).
- Unskippable or long boot sequences.
- Wiki-walls in the guide.
- Hi-res art outside framed diegetic displays.
- Per-mod divergent styles — this document wins.

## Production

- Hand pixel art in **Aseprite** (palette: `art/palette-a.gpl`); script-generated drafts are starting points and layout records (`mods/industry/tools/gen_textures.py`), polish replaces them file by file.
- The claude.ai design board stays the exploration space; this document is the binding record.

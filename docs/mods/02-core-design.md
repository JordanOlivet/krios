# Krios: Core — design

*Settled at the ecosystem design session of 2026-10-01 (ADR-018/019); this
document grows into the full design at extraction time. Until then the
implementation lives inside `mods/industry` under the ADR-013 isolation
rules.*

## What it is

The ecosystem's spine, as a standalone **content mod** (`krios_core`,
display name "Krios: Core"): everything every Krios mod needs to speak the
same language — the network, KOIOS, the face contracts, the diagnostic
tooling and the signature materials. Hard dependency of every content mod,
auto-resolved by launchers; never a gameplay destination on its own (its
recipes may be provided by the content mods' datapacks — core does not
have to be playable alone).

## Owns (ADR-018)

- **Network engine**: graph, components, conductors, controllers, energy
  pooling interfaces (`EnergyPort`/`EnergySink`), scheduled wake-ups
  (ADR-017). Never persisted, event-driven, no balance (ADR-013 rules
  carry over as core's own charter).
- **KOIOS**: the core multiblock (casings, FORMED shell behavior, screen
  side), data component persistence, XP/levels/tiers, the breakthrough
  ritual + compute chip, diversity window, the AI head BER and its block
  events, `/koios` command subtree.
- **Face-config system**: `FaceConfig`/`FlowPorts`, contact auto-config,
  the symmetric-pair hint, the manual-contract doctrine ("what the player
  wrote on a face is what anyone gets").
- **Neural Conduit** — the pure-connectivity backbone (energy + data,
  forever; the transport-capable cable is krios_storage's).
- **Calibrator** (in-world overlay + future diagnostic UI) and the
  **blueprint/projection system**.
- **Chronite/Neurite ores**, their materials and worldgen (`c:` tags kept,
  ADR-008).
- **Codex framework** (entries contributed per mod), the ADR-014 UI
  chrome, command roots infrastructure (ADR-016) and the config
  infrastructure (ADR-015 + the ADR-019 layered difficulty).

## The inter-mod contract (plain Java API, hard dep)

A content mod can: report operations `(key, count, baseXP)` to its
network's controller; read the controller's level and its own effective
tier (min rule); declare flow ports and face config; implement energy
port/sink; schedule wake-ups. The progression curve, diversity math and
difficulty multipliers live in core (ADR-019); per-operation XP values and
machine rates stay in each mod — core never carries content balance.

## Progression spine

KOIOS tiers gate the whole ecosystem: each mod hangs its content on core
tiers (storage's cable transport = core tier 2 unlock). "Tiers ship as
complete slices" applies per mod, aligned on the shared skeleton.

## Extraction plan (trigger: krios_storage starts — ADR-013/018)

1. Finish Industry's tier-2 queue (solar, Mk II modules, energy module,
   Codex rename + Progression entry, numbers pass).
2. Bootstrap `mods/core` from `templates/multiloader`; move `network/`,
   `koios/`, faces, conduit, Calibrator, blueprint, ores/worldgen, UI
   chrome; Gradle composite build (`includeBuild`) links industry → core;
   CI thin caller; tags `core/v0.1.0`.
3. Accepted: KOIOS content namespace change (`krios_industry:*` →
   `krios_core:*`), pre-release test worlds break.
4. Then krios_storage v0 on the consigned brief (industry design doc,
   revision 2026-10-01).

## Open questions (extraction-time)

- Exact API shapes (interfaces vs service lookups) — decided against real
  consumer code, per the ADR-013 philosophy.
- Codex framework form (data-driven entries vs code contributions).
- Whether the Calibrator stays one tool or splits per-mod overlays.
- Display-name bikeshed: "Krios: Core" vs a diegetic name.

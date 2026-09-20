# Krios

Monorepo for the **Krios** ecosystem — original Minecraft mods built around futuristic technology: an in-game AI core (**K.O.I.O.S.**) driving quantum/temporal machinery.

See [docs/VISION.md](docs/VISION.md) for the full vision and [docs/DECISIONS.md](docs/DECISIONS.md) for architecture decisions.

## Stack

- Minecraft **1.21.1**, Java **21**
- **NeoForge + Fabric** via the MultiLoader Template pattern (no runtime dependency)
- Recipes datapack-driven, energy exposed as FE / Team Reborn Energy

## Layout

| Path | Purpose |
|---|---|
| `docs/` | Vision, decisions, per-mod design documents |
| `libs/core/` | Shared library (energy abstraction, registration helpers, batch processing) |
| `mods/` | One directory per mod, each a standalone Gradle multiloader build |
| `templates/multiloader/` | Pristine MultiLoader Template (1.21.1) used to bootstrap new mods |
| `tools/` | Scripts, test scenarios (see the separate `mc-test-harness` repo) |

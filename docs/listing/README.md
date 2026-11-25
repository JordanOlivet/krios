# Store listings

The texts and pictures of the Modrinth and CurseForge pages, kept here so
both stores say the same thing and a change is reviewed like any other.

```
docs/listing/
  core/description.md        the page's body, Markdown
  core/summary.txt           the short description (256 characters at most on Modrinth)
  core/screenshots/          the gallery pictures kept in the repository
  industry/...               the same for Krios: Industry
  modrinth.py                reads and writes the Modrinth pages
```

## Rules

- A listing states only what the **released** version does. What is planned
  goes under "Not there yet", never in the feature list; a feature that
  ships moves out of it with the release.
- Before writing a claim, check what shows it: the code, the lang files,
  the Codex entry, a recipe. A stale claim is worse than a missing one.
- "Compatibility" lists only what was actually run for this release (the
  loaders and viewers of `tools/acceptance/versions.toml`).
- Edit a store only after the user has validated the text. Until then,
  report what is drafted, not what is published.

## Publishing

- **Modrinth**: `uv run docs/listing/modrinth.py <core|industry> push-body`
  (and `push-summary`), then read the page back with `show` and compare.
  The token is `modrinth.token` in `local.properties` (gitignored). A
  project under review answers 404 on the public API: `show` reads it with
  the token.
- **CurseForge**: pasted by hand. Switch the description editor to Markdown
  and paste `description.md`; the summary goes in the project settings.
- Gallery: Modrinth takes PNG and JPEG (`modrinth.py ... add-image`, which
  prints the picture's URL to embed in `description.md`); CurseForge caps a
  picture at 2 MB.

## Fields set once in each project's settings

| Field | Krios: Core | Krios: Industry |
|---|---|---|
| Modrinth categories | Technology, Library, World Generation | Technology, Game Mechanics, Mobs |
| CurseForge categories | Technology; API and Library; Ores and Resources | Technology (Processing, Energy, Farming); Mobs |
| Environment | client and server, both required | client and server, both required |
| License | LGPL-3.0-or-later | LGPL-3.0-or-later |
| Source, issues | the repository; its issue tracker | the repository; its issue tracker |
| Dependencies | Fabric API (Fabric) | Krios: Core (required); Fabric API (Fabric) |

JEI and EMI can be listed as optional dependencies on both: each mod has a
plugin for them.

## Gallery plan

Pictures do more than the text. A first set, in the order a visitor should
see them:

1. The formed K.O.I.O.S. core in a base, its face lit (featured image).
2. A network at work: reactor, grinder, forge, conduits between them.
3. The core's screen: level, tier, unlocks, data XP.
4. A machine terminal with its side panels open (statistics, faces, modules).
5. A zone machine with its hologram shown by the Calibrator (a Chrono Farmer on its field).
6. The Calibrator's blueprint hologram, half built.
7. The ores at night next to a vanilla ore.
8. The Codex.
9. The Grinding category in JEI or EMI.

The client acceptance suite photographs most of these on every run
(`build/acceptance/client/<run>/`: `16-koios-world`, `14-koios-core`,
`01-thermal-reactor`, `05-blueprint`, `02-ores-night`, `15-codex`,
`02b-grinding-category`). They are taken in a flat test world at 1280x720:
fine to check a screen, plain for a store page. Pictures from a real base,
at 1920x1080, sell the mod better.

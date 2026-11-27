# Yield balance: double, then go faster

Date: 2026-11-27. Decision: ADR-038. Settled with the user in a grilling session, after the materials pass of ADR-037 put every ore through the Fractal Grinder.

## The rule in one line

**One grinding operation gives the recipe's count, plus 25% per Yield point, and a machine counts at most 2 Yield points per tier.** An ore or a raw metal gives two dusts, a dust smelts into one ingot. Nothing else multiplies.

## Why this pass

ADR-037 gave every ore a grinding recipe, and its numbers exposed what the yield model had become. Four multipliers stacked:

1. **A base multiplier per tier** in the grinder: x1, x1.5, x1.5, x2 over the recipe, free with the upgrade kit.
2. **Yield modules**, +25% per point, with no limit but the bay (2 slots at tier 1, 4 at tier 2, each Mk II worth 2 points).
3. **The chain applied both twice**: ore to raw metal, then raw metal to dust, each step multiplied.
4. **The Plasma Forge took Yield modules too**, so a dust could give up to three ingots.

What one block of iron ore gave, mined with Silk Touch and run through the whole chain:

| Setup | Before | After |
|---|---|---|
| Tier 1, no module | 4 ingots | 2 ingots |
| Tier 1, two Yield I | 9 (13 with the forge's Yield) | 3 |
| Tier 2, four Yield II | 81 (243 with the forge's Yield) | 4 |
| Tier 4, the bay full (projected) | 144 (432) | 6 |

The same block mined with a Fortune III pickaxe drops 2.2 raw iron on average. A vanilla player gets 2.2 ingots from it. At tier 2 Krios gave about forty times that: the grinder was no longer a better furnace, it replaced mining. The diamond, the emerald and, once it came in, netherite would have followed the same curve.

Two smaller problems came with it. The stack limit: at the projected tier 4 multiplier, a lapis ore would produce more than 64 items in one operation, and the grinder has one output slot per column (ADR-037 left this open). And off the network, the grinder promised vanilla parity but rounded the half down, so 13 lapis gave 6 instead of 6.5, a little under a pickaxe.

## The principle

The user's words: an ore or a raw metal gives two dusts, each dust gives an ingot, no more. The aim is not to make the chain a cheat, only more profitable and, above all, faster: speed is the mod's main point. Speed is what grows without limit across the tiers: process columns double at every tier, Speed modules shorten each operation, and a future "infinity" module would be a speed module, never a yield one.

So yield is bounded, and the bound is low.

## What changed

**The chain is linear.** A metal ore now grinds straight into dusts: iron, gold and neurite ores give 2 dusts, copper ore gives 7 (twice the 3.5 raw copper it drops on average). A raw metal still gives 2 dusts. Whichever path a player takes, the ore passes through the grinder once.

**Yield comes from modules only, under a cap.** The tier base multiplier is gone. The tier sets how many Yield points a machine counts: 2, 4, 6, 8. One raw metal gives:

| Effective tier | Yield points counted, at most | Dusts per raw metal | Lapis per ore |
|---|---|---|---|
| any, no module | 0 | 2 | 13 |
| 1 | 2 | 3 | 19.5 |
| 2 | 4 | 4 | 26 |
| 3 | 6 | 5 | 32.5 |
| 4 | 8 | 6 | 39 |

Each tier adds one dust. The cap follows the effective tier (machine tier and core tier, the lower of the two), like everything a machine performs.

**The bay shows the limit.** A Yield module that would push the bay past the cap is refused when placed, before the KOIOS level gate. The stats annex shows the bonus, then the points counted over the cap (`+50 % · 2/2`). Modules already in a bay past the cap (a world from 0.1.0, or a machine whose core tier dropped) stay where they are, inert, and the line turns amber. Nothing is moved or lost.

**The cap holds for every machine that takes Yield**: the Chrono Farmer, the Cyclic Forester, the Quantum Lure, the Breeding and Harvest Pods, the Bio Synthesizer, and the Phase Death Field, whose Yield points are its Looting level (Looting IV at most at tier 2, where four Mk II modules used to give Looting VIII). The Quantum Lure's Luck axis is a different axis and is not capped.

**The Plasma Forge smelts one for one.** It refuses Yield modules; it keeps Speed and Efficiency. The grinder is the chain's only yield machine.

**Gems, minerals and ancient debris follow the same rule.** Their ore recipe gives twice vanilla's average drop without Fortune (2 diamonds, 2 emeralds, 2 coal, 2 quartz, 9 redstone, 13 lapis), and Yield adds to it like any other recipe: a diamond ore gives 2 to 6 diamonds. Ancient debris now grinds into 2 netherite scraps, 6 at the last tier: half a netherite ingot per debris without a module, one and a half at the top.

**Fortune adds up with the grinder.** A raw metal mined with Fortune is a raw metal: 2.2 raw iron per ore become 4.4 dusts without a module, 13.2 at the last tier. The grinder doubles what it is given; the enchantment is the player's own bonus, earned apart. Silk Touch is no longer required to get the most out of an ore, and an ore block, from a quarry or a machine that mines blocks, still doubles.

**A modded metal without a dust still doubles.** A modded ore's recipe names its dust first and its raw metal second (`"result": [ {"tag": "c:dusts/tin", "count": 2}, {"tag": "c:raw_materials/tin", "count": 2} ]`). In a pack whose mods bring tin ore and raw tin but no tin dust, the ore gives 2 raw tin, as before.

**Off the network, exactly half.** The quarter-item accumulator carries the half item: two lapis ores give 13 lapis, two copper ores 7 dusts, vanilla's averages. A one-for-one recipe (cobblestone into gravel) stays one for one.

## What it brings

- **A rule a player can predict.** "Twice the ore, a quarter more per Yield point, two points per tier" fits in one Codex line and one recipe viewer tooltip. The old one needed a table.
- **Choices in the bay.** With the cap low, a full Yield bay is wasted slots: past two points per tier, the room goes to Speed and Efficiency. Before, four Yield II at tier 2 was the only sensible setup.
- **The tiers keep a reason to climb.** Each tier still adds a dust per raw metal, and doubles the columns: tier 4 grinds eight times as many ores at once, each at three times the yield.
- **Vanilla's economy survives.** Diamonds, emeralds and netherite stay worth something, and a Krios player does not out-produce every other mod's ore processing by an order of magnitude.
- **No overflow mechanism.** The largest operation is 39 lapis at the last tier: every recipe fits a stack with room to spare, so the grinder's single output slot per column needs no buffer, no extra slots, no change to its save format. A unit test checks every shipped recipe against that limit.
- **The offline promise is true.** A disconnected grinder now matches a pickaxe exactly.

## What it costs

- **A tier 2 grinder with four Yield II gives 4 dusts per raw metal, down from 9.** Players of 0.1.0 who filled their bays see their output drop; the amber stats line tells them why and which modules to move.
- **A Phase Death Field at tier 2 gives Looting IV, down from VIII.**
- **Yield modules in a Plasma Forge do nothing.** They stay in the bay until taken out.
- **Neurite ore grinds into neurite dust**, skipping raw neurite. Raw neurite still drops from mining and still grinds into 2 dusts.

## Left as it was

- **Energy and time**: every ore costs 800 E and 8 s, every raw metal 600 E and 6 s, ancient debris included. Speed comes from modules and columns, not recipes.
- **The Luck axis** of the Quantum Lure.
- **The ore counts** set by ADR-037 (twice vanilla's average without Fortune).
- Raw metal blocks (block of raw iron) have no grinding recipe.

## Tests

- Unit (`gradlew build`): the cap per tier, the points counted past it, a module fitting under it (`MachineTiersTest`); a grind's quarters online and the exact half offline (`IndustryBalanceTest`); every shipped recipe fitting a stack at the last tier, and each modded ore naming its dust then its raw metal (`GrindingRecipeFilesTest`).
- GameTests, NeoForge and Fabric: metal ores grind straight into dusts, ancient debris and nether gold ore included; a result list falls back to the raw metal; four Yield points in a tier 1 grinder give three dusts, not four; the bay refuses a Yield I beside a Yield II at tier 1; a tier 1 Harvest Pod counts two of four points; two copper ores off the network give seven dusts; the forge gives two ingots from two dusts with Yield modules in its bay. Each proven able to fail with a planted bug.
- Client (`client/screens.cmd`, NeoForge and Fabric): a tier 1 grinder holding two Yield II reads `+50 % · 2/2` on its stats line, in amber. The terminals' readout carries their stats rows for this (`krios.stats.<row>`).

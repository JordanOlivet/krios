# Krios client acceptance: JEI (run with --viewer jei). The tab clicks below aim at
# JEI's category tabs on a 640x360 GUI: 24 pixels each, the first centered on x=236.
# Items in the hotbar, the cursor on each, and the viewer's own keys: R shows
# an item's recipes, U its uses. Survival, so E opens the plain inventory
# (hotbar slots 36 to 44).
world krios_viewers create 20261125
wait ticks 60
command gamerule sendCommandFeedback false
command gamerule doMobSpawning false
command gamerule doDaylightCycle false
command time set noon
command difficulty peaceful
command gamemode survival
command clear @s
command give @s krios_industry:fractal_grinder
command give @s krios_core:chronite_ore
command give @s krios_core:koios_core
command give @s krios_industry:engram
command give @s krios_industry:plasma_forge
command give @s krios_industry:thermal_reactor
command give @s krios_core:neurite_ingot
command give @s krios_core:iron_dust
wait ticks 20

key e
wait screen
wait ticks 10
cursor slot 36
wait ticks 5
screenshot 01-inventory

# The grinder's uses: the Grinding category.
key u
wait ticks 15
screen
screenshot 02-grinder-uses
# Its third tab: the Grinding category, the grinder being its catalyst.
cursor 284 36
click
wait ticks 8
screenshot 02b-grinding-category
key escape
wait ticks 8

# Chronite ore's uses: grinding and smelting.
cursor slot 37
wait ticks 3
key u
wait ticks 15
screenshot 03-chronite-ore-uses
# Its fifth tab: the ore ground into shards.
cursor 332 36
click
wait ticks 8
# The output slot's tooltip says what the count stands for.
cursor 326 103
wait ticks 5
screenshot 03b-chronite-ore-grinding
key escape
wait ticks 8

# The core: its recipes and the page on the multiblock.
cursor slot 38
wait ticks 3
key r
wait ticks 15
screenshot 04-koios-core-recipes
# Its third tab: the information page.
cursor 284 36
click
wait ticks 8
cursor 320 300
screenshot 04b-koios-core-info
key escape
wait ticks 8

# The Engram: its recipe and the page on copying it.
cursor slot 39
wait ticks 3
key r
wait ticks 15
screenshot 05-engram-recipes
key escape
wait ticks 8

# The Plasma Forge is a catalyst of vanilla smelting, the reactor of fuels.
cursor slot 40
wait ticks 3
key u
wait ticks 15
screenshot 06-plasma-forge-uses
key escape
wait ticks 8

cursor slot 41
wait ticks 3
key u
wait ticks 15
screenshot 07-thermal-reactor-uses
key escape
wait ticks 8

# A material: every recipe that takes a neurite ingot (tag names included).
cursor slot 42
wait ticks 3
key u
wait ticks 15
screenshot 08-neurite-ingot-uses
key escape
wait ticks 8

# A dust of a vanilla metal (ADR-037): made by grinding the raw metal, smelted into the ingot.
cursor slot 43
wait ticks 3
key r
wait ticks 15
screenshot 08b-iron-dust-recipes
key escape
wait ticks 8
cursor slot 43
wait ticks 3
key u
wait ticks 15
screenshot 08c-iron-dust-uses
key escape
wait ticks 8

cursor off
close
wait ticks 5

# Recipe transfer: the viewer's "+" button moves a recipe's input from the inventory
# into the open machine. Three machines without energy on a platform (nothing is
# processed, so the counts hold), the inputs in the first inventory row, and for
# each: the recipe of an item under the cursor, a click on the button, then the
# machine's slots. The buttons are clicked at their place on a 640x360 GUI.
command forceload add 0 0 15 15
wait ticks 80
command fill 4 199 -3 16 205 6 minecraft:air
command fill 4 199 -3 16 199 6 minecraft:stone
command setblock 8 200 2 krios_industry:thermal_reactor
command setblock 10 200 2 krios_industry:fractal_grinder
command setblock 12 200 2 krios_industry:plasma_forge
tp 10.5 200 0.5 0 20
wait ticks 40
command clear @s
command item replace entity @s container.9 with minecraft:gravel 1
command item replace entity @s container.10 with minecraft:cobblestone 32
command item replace entity @s container.11 with minecraft:smooth_stone 1
command item replace entity @s container.12 with minecraft:stone 8
command item replace entity @s container.13 with minecraft:lava_bucket 1
wait ticks 20

# Fractal Grinder: gravel's recipe is cobblestone ground. One click, one item.
use 10 200 2
wait screen
#expect slot 0 empty
#expect slot 9 minecraft:cobblestone x32
wait ticks 8
cursor slot 8
wait ticks 3
key r
wait ticks 15
screenshot 09-transfer-grinding
cursor 395 114
wait ticks 3
click
wait ticks 10
screen
#expect slot 0 minecraft:cobblestone x1
#expect slot 9 minecraft:cobblestone x31
screenshot 09b-grinder-filled
cursor off
close
wait ticks 5

# Plasma Forge, on vanilla smelting: smooth stone's recipe is stone smelted.
use 12 200 2
wait screen
#expect slot 0 empty
#expect slot 11 minecraft:stone x8
wait ticks 8
cursor slot 10
wait ticks 3
key r
wait ticks 15
screenshot 10-transfer-smelting
# JEI's smelting layout is wider in the version the production runs pin (it shows the
# fuel), so its button sits further right there.
@dev cursor 373 136
@prod cursor 390 136
wait ticks 3
click
wait ticks 10
screen
#expect slot 0 minecraft:stone x1
#expect slot 11 minecraft:stone x7
screenshot 10b-forge-filled
cursor off
close
wait ticks 5

# Thermal Reactor, on vanilla fuels: a lava bucket's only use. The reactor lights
# at once, so the proof is the bucket gone from the inventory.
use 8 200 2
wait screen
#expect slot 7 minecraft:lava_bucket x1
wait ticks 8
cursor slot 7
wait ticks 3
key u
wait ticks 15
screenshot 11-transfer-fuel
cursor 404 122
wait ticks 3
click
wait ticks 10
screen
#expect slot 7 empty
screenshot 11b-reactor-filled
cursor off
close
wait ticks 5

# A shift-click on the button moves all that fits: the grinder holds one
# cobblestone from the click above, the inventory the 31 others.
use 10 200 2
wait screen
#expect slot 0 minecraft:cobblestone x1
#expect slot 9 minecraft:cobblestone x31
wait ticks 8
cursor slot 8
wait ticks 3
key r
wait ticks 15
cursor 395 114
wait ticks 3
click shift
wait ticks 10
screen
#expect slot 0 minecraft:cobblestone x32
#expect slot 9 empty
screenshot 12-grinder-shift-filled
cursor off
close
wait ticks 5

# A tier 2 grinder has two columns. Fill first (the default): the first column
# to the brim, then the second. Two stacks in the inventory, 64 and 32.
command setblock 14 200 2 krios_industry:fractal_grinder_tier2
command setblock 16 200 2 krios_industry:fractal_grinder_tier2
command item replace entity @s container.10 with minecraft:cobblestone 64
command item replace entity @s container.14 with minecraft:cobblestone 32
tp 14.5 200 0.5 0 20
wait ticks 20
use 14 200 2
wait screen
#expect slot 0 empty
#expect slot 1 empty
#expect slot 9 minecraft:cobblestone x64
#expect slot 13 minecraft:cobblestone x32
wait ticks 8
cursor slot 8
wait ticks 3
key r
wait ticks 15
cursor 395 114
wait ticks 3
click shift
wait ticks 10
screen
#expect slot 0 minecraft:cobblestone x64
#expect slot 1 minecraft:cobblestone x32
#expect slot 9 empty
#expect slot 13 empty
screenshot 13-tier2-fill-first
cursor off
close
wait ticks 5

# The other tier 2 grinder is put in balance mode with its own toggle: each item
# goes to the column that holds the least, so 32 items end 16 and 16.
command item replace entity @s container.10 with minecraft:cobblestone 32
wait ticks 10
use 16 200 2
wait screen
#expect slot 0 empty
#expect slot 1 empty
#expect slot 9 minecraft:cobblestone x32
wait ticks 8
krios set distribution on
wait ticks 10
state
#expect state krios.distribution balance
cursor slot 8
wait ticks 3
key r
wait ticks 15
cursor 395 114
wait ticks 3
click shift
wait ticks 10
screen
#expect slot 0 minecraft:cobblestone x16
#expect slot 1 minecraft:cobblestone x16
#expect slot 9 empty
screenshot 14-tier2-balance

cursor off
close
quit

# Krios client acceptance: EMI (run with --viewer emi). The tab clicks below aim at
# EMI's category tabs on a 640x360 GUI: the second one is centered on x=284, y=40.
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
# Its second tab: the Grinding category, the grinder being its workstation.
cursor 284 40
click
wait ticks 8
cursor 320 300
screenshot 02b-grinding-category
key escape
wait ticks 8

# Chronite ore's uses: grinding and smelting.
cursor slot 37
wait ticks 3
key u
wait ticks 15
screenshot 03-chronite-ore-uses
key escape
wait ticks 8

# The core: its recipes and the page on the multiblock.
cursor slot 38
wait ticks 3
key r
wait ticks 15
screenshot 04-koios-core-recipes
# Its second tab: the information page.
cursor 284 40
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

cursor off
close
wait ticks 5

# Recipe transfer: the viewer's fill button moves a recipe's input from the inventory
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
cursor 397 111
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
cursor 371 122
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
cursor 397 102
wait ticks 3
click
wait ticks 10
screen
#expect slot 7 empty
screenshot 11b-reactor-filled

cursor off
close
quit

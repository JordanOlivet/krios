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

cursor off
close
quit

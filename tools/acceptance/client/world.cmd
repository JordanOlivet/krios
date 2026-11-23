# Krios client acceptance: what the world shows. Emissive ores by day and by
# night, the Calibrator's overlay, diagnostic screen and blueprint, the KOIOS
# screen side.
world krios_world create 20261124
wait ticks 60
# A new world spawns the player anywhere: go to the test area and keep its chunks
# loaded before building (a command on an unloaded position does nothing).
# Creative: a player who joined a server is in survival and would not outlive the fall.
command gamemode creative
tp 8.5 220 0.5 0 20
command forceload add -16 -16 47 31
wait ticks 80
command gamerule sendCommandFeedback false
command gamerule doMobSpawning false
command gamerule doDaylightCycle false
command gamerule doWeatherCycle false
command time set noon
command weather clear
command kill @e[type=!minecraft:player]
command fill 0 199 -12 47 210 15 minecraft:air
command fill 0 199 -12 47 199 15 minecraft:stone
command fill 4 200 4 6 202 6 krios_core:neural_casing
command setblock 5 201 5 krios_core:koios_core
command fill 4 200 3 20 200 3 krios_core:neural_conduit
command setblock 8 200 2 krios_industry:thermal_reactor
command setblock 12 200 2 krios_industry:fractal_grinder
command item replace block 8 200 2 container.0 with minecraft:coal_block 16
command item replace block 12 200 2 container.0 with minecraft:cobblestone 32
# The four ores, and a vanilla ore next to them: it must stay dark at night.
command setblock 20 200 -6 krios_core:chronite_ore
command setblock 21 200 -6 krios_core:deepslate_chronite_ore
command setblock 22 200 -6 krios_core:neurite_ore
command setblock 23 200 -6 krios_core:deepslate_neurite_ore
command setblock 24 200 -6 minecraft:diamond_ore
command clear @s
command give @s krios_core:calibrator
wait ticks 40

tp 22.5 200 -9.5 0 8
wait ticks 20
screenshot 01-ores-day
command time set midnight
wait ticks 40
screenshot 02-ores-night
command time set noon
wait ticks 20

# The Calibrator in hand, aimed at a machine: the face overlay.
hotbar 1
tp 12.5 200 0.2 0 30
wait ticks 20
screenshot 03-calibrator-overlay hud

# A plain click on a conduit: the diagnostic screen.
use 12 200 3
wait screen
wait ticks 8
screenshot 04-calibrator-diagnostic
close
wait ticks 5

# A sneak-click on the ground: the construction blueprint.
tp 30.5 200 -6.5 0 30
wait ticks 15
hold sneak
wait ticks 6
use 30 199 -4 up
wait ticks 6
release all
tp 30.5 201 -10.5 0 5
wait ticks 20
screenshot 05-blueprint

# A sneak-click on a casing's east face: the core's screen moves to that side.
tp 8.5 200 5.5 90 0
wait ticks 15
hold sneak
wait ticks 6
use 6 201 5 east
wait ticks 6
release all
tp 12.5 201 5.5 90 0
wait ticks 20
screenshot 06-koios-screen-east
quit

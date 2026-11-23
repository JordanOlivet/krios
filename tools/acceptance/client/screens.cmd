# Krios client acceptance: every terminal opened and photographed.
# The base matches the server scenario: KOIOS shell at 4..6, a conduit trunk on z=3,
# line machines on z=2, zone machines on z=4. The player stands on z=0..1.
world krios_screens create 20261123
wait ticks 60
# A new world spawns the player anywhere: go to the test area and keep its chunks
# loaded before building (a command on an unloaded position does nothing).
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
command fill 4 200 3 44 200 3 krios_core:neural_conduit
command setblock 8 200 2 krios_industry:thermal_reactor
command setblock 10 200 2 krios_industry:photon_array
command setblock 12 200 2 krios_industry:fractal_grinder
command setblock 14 200 2 krios_industry:plasma_forge
command setblock 16 200 2 krios_industry:fractal_grinder_tier2
command setblock 10 200 4 krios_industry:chrono_farmer[facing=north]
command setblock 14 200 4 krios_industry:cyclic_forester[facing=north]
command setblock 18 200 4 krios_industry:quantum_lure[facing=north]
command setblock 22 200 4 krios_industry:breeding_pod[facing=north]
command setblock 26 200 4 krios_industry:harvest_pod[facing=north]
command setblock 30 200 4 krios_industry:phase_death_field[facing=north]
command setblock 34 200 4 krios_industry:bio_synthesizer[facing=north]
command item replace block 8 200 2 container.0 with minecraft:coal_block 16
command item replace block 12 200 2 container.0 with minecraft:cobblestone 32
command item replace block 14 200 2 container.0 with minecraft:raw_iron 16
command item replace block 16 200 2 container.0 with krios_core:chronite_ore 8
command item replace block 10 200 4 container.0 with minecraft:wheat_seeds 32
command clear @s
tp 8.5 200 0.5 0 20
wait ticks 120
state

# Line machines.
use 8 200 2
wait screen
wait ticks 8
screenshot 01-thermal-reactor
# The faces panel: its tab is the first one right of the frame.
cursor in 271 6
click
wait ticks 5
screenshot 02-thermal-reactor-faces
cursor off
close
wait ticks 5

tp 10.5 200 0.5 0 20
wait ticks 15
use 10 200 2
wait screen
wait ticks 8
screenshot 03-photon-array
close
wait ticks 5

tp 12.5 200 0.5 0 20
wait ticks 15
use 12 200 2
wait screen
wait ticks 8
cursor slot 2
wait ticks 5
screenshot 04-fractal-grinder
cursor off
close
wait ticks 5

tp 14.5 200 0.5 0 20
wait ticks 15
use 14 200 2
wait screen
wait ticks 8
screenshot 05-plasma-forge
close
wait ticks 5

tp 16.5 200 0.5 0 20
wait ticks 15
use 16 200 2
wait screen
wait ticks 8
screenshot 06-fractal-grinder-tier2
close
wait ticks 5

# Zone machines.
tp 10.5 200 1.5 0 20
wait ticks 15
use 10 200 4
wait screen
wait ticks 8
screenshot 07-chrono-farmer
close
wait ticks 5

tp 14.5 200 1.5 0 20
wait ticks 15
use 14 200 4
wait screen
wait ticks 8
screenshot 08-cyclic-forester
close
wait ticks 5

tp 18.5 200 1.5 0 20
wait ticks 15
use 18 200 4
wait screen
wait ticks 8
screenshot 09-quantum-lure
close
wait ticks 5

tp 22.5 200 1.5 0 20
wait ticks 15
use 22 200 4
wait screen
wait ticks 8
screenshot 10-breeding-pod
close
wait ticks 5

tp 26.5 200 1.5 0 20
wait ticks 15
use 26 200 4
wait screen
wait ticks 8
screenshot 11-harvest-pod
close
wait ticks 5

tp 30.5 200 1.5 0 20
wait ticks 15
use 30 200 4
wait screen
wait ticks 8
screenshot 12-phase-death-field
close
wait ticks 5

tp 34.5 200 1.5 0 20
wait ticks 15
use 34 200 4
wait screen
wait ticks 8
screenshot 13-bio-synthesizer
close
wait ticks 5

# KOIOS: a casing of the formed shell opens the core, then the Codex.
tp 5.5 200 1.5 0 10
wait ticks 15
use 5 200 4
wait screen
wait ticks 8
screenshot 14-koios-core
cursor in 212 10
click
wait ticks 5
screenshot 15-codex
cursor off
close
wait ticks 5

# The world: the formed core from the front.
tp 5.5 201 -3.5 0 0
wait ticks 20
screenshot 16-koios-world
quit

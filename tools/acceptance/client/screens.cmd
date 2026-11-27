# Krios client acceptance: every terminal opened, read and photographed. "state" gives
# what the open terminal shows as data (core's Rehearsal extension), "krios set" and
# "krios press" reach a control by its name.
# The base matches the server scenario: KOIOS shell at 4..6, a conduit trunk on z=3,
# line machines on z=2, zone machines on z=4. The player stands on z=0..1.
world krios_screens create 20261123
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
# Four Yield points in the tier 1 grinder's bay: two count (ADR-038).
command item replace block 12 200 2 container.4 with krios_industry:yield_module_mk2
command item replace block 12 200 2 container.5 with krios_industry:yield_module_mk2
command item replace block 10 200 4 container.0 with minecraft:wheat_seeds 32
command clear @s
tp 8.5 200 0.5 0 20
wait ticks 120
state

# Line machines.
use 8 200 2
wait screen
wait ticks 8
state
#expect state krios.title Thermal Reactor
#expect state krios.status 1
#expect state krios.voice gui.krios_industry.voice.thermal_reactor.working
#expect state krios.tier 1
screenshot 01-thermal-reactor
# The faces panel, by its name: where its tab sits is the layout's business, and
# whether it was left open by an earlier run no longer matters.
krios set panel.faces on
wait ticks 5
state
#expect state krios.panels.faces true
screenshot 02-thermal-reactor-faces
krios set panel.faces off
wait ticks 3
state
#expect state krios.panels.faces false
close
wait ticks 5

tp 10.5 200 0.5 0 20
wait ticks 15
use 10 200 2
wait screen
wait ticks 8
state
#expect state krios.title Photon Array
#expect state krios.status 1
screenshot 03-photon-array
close
wait ticks 5

tp 12.5 200 0.5 0 20
wait ticks 15
use 12 200 2
wait screen
wait ticks 8
state
#expect state krios.title Fractal Grinder
#expect state krios.voice gui.krios_industry.voice.fractal_grinder.working
#expect state krios.tier 1
#expect state krios.distribution fill_first
#expect state krios.stats.yield +50 % · 2/2
krios set panel.stats on
wait ticks 3
state
#expect state krios.panels.stats true
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
state
#expect state krios.title Plasma Forge
#expect state krios.voice gui.krios_industry.voice.plasma_forge.working
screenshot 05-plasma-forge
close
wait ticks 5

tp 16.5 200 0.5 0 20
wait ticks 15
use 16 200 2
wait screen
wait ticks 8
state
#expect state krios.title Fractal Grinder
#expect state krios.tier 2
#expect state krios.effectiveTier 1
#expect state krios.energyCapacity 16000
screenshot 06-fractal-grinder-tier2
# The distribution toggle goes through the server and comes back in the menu's data.
krios set distribution on
wait ticks 10
state
#expect state krios.distribution balance
krios set distribution off
wait ticks 10
state
#expect state krios.distribution fill_first
close
wait ticks 5

# Zone machines.
tp 10.5 200 1.5 0 20
wait ticks 15
use 10 200 4
wait screen
wait ticks 8
state
#expect state krios.title Chrono Farmer
#expect state krios.voice gui.krios_industry.voice.chrono_farmer.empty_zone
screenshot 07-chrono-farmer
close
wait ticks 5

tp 14.5 200 1.5 0 20
wait ticks 15
use 14 200 4
wait screen
wait ticks 8
state
#expect state krios.title Cyclic Forester
screenshot 08-cyclic-forester
close
wait ticks 5

tp 18.5 200 1.5 0 20
wait ticks 15
use 18 200 4
wait screen
wait ticks 8
state
#expect state krios.title Quantum Lure
screenshot 09-quantum-lure
close
wait ticks 5

tp 22.5 200 1.5 0 20
wait ticks 15
use 22 200 4
wait screen
wait ticks 8
state
#expect state krios.title Breeding Pod
screenshot 10-breeding-pod
close
wait ticks 5

tp 26.5 200 1.5 0 20
wait ticks 15
use 26 200 4
wait screen
wait ticks 8
state
#expect state krios.title Harvest Pod
screenshot 11-harvest-pod
close
wait ticks 5

tp 30.5 200 1.5 0 20
wait ticks 15
use 30 200 4
wait screen
wait ticks 8
state
#expect state krios.title Phase Death Field
screenshot 12-phase-death-field
close
wait ticks 5

tp 34.5 200 1.5 0 20
wait ticks 15
use 34 200 4
wait screen
wait ticks 8
state
#expect state krios.title Bio Synthesizer
screenshot 13-bio-synthesizer
close
wait ticks 5

# KOIOS: a casing of the formed shell opens the core, then the Codex.
tp 5.5 200 1.5 0 10
wait ticks 15
use 5 200 4
wait screen
wait ticks 8
state
#expect state krios.title K.O.I.O.S. Core
#expect state krios.formed true
#expect state krios.level 1
#expect state krios.tier 1
#expect state krios.unlocks.speed false
#expect state krios.codexUnread true
#expect state krios.codex.open false
screenshot 14-koios-core
# The Codex: opening it tells the core what was seen (the badge goes out), its
# entries come in the registered order, and the ones above the core's level stay locked.
krios set codex on
wait ticks 5
state
#expect state krios.codex.open true
#expect state krios.codexUnread false
#expect state krios.codex.page koios
#expect state krios.codex.entries koios,progression,diversity,thermal_reactor,photon_array,fractal_grinder,plasma_forge,chrono_farmer,cyclic_forester,quantum_lure,breeding_pod,harvest_pod,phase_death_field,bio_synthesizer,network,conduit,calibrator
#expect state krios.codex.locked modules,critical
screenshot 15-codex
krios press codex.entry.progression
wait ticks 3
state
#expect state krios.codex.page progression
# The Calibrator's entry is below the sidebar's viewport, so it is no control yet.
#expect state krios.codex.scroll 0
#expect state krios.controls.codex.entry.calibrator absent
screenshot 15b-codex-progression
# Pressing it turns the wheel over the list until its row shows, then clicks the row.
krios press codex.entry.calibrator
wait ticks 3
state
#expect state krios.codex.page calibrator
#expect state krios.controls.codex.entry.calibrator on
#expect state krios.controls.codex.entry.koios absent
screenshot 15c-codex-calibrator
krios set codex off
wait ticks 3
state
#expect state krios.codex.open false
close
wait ticks 5

# The world: the formed core from the front.
tp 5.5 201 -3.5 0 0
wait ticks 20
screenshot 16-koios-world
quit

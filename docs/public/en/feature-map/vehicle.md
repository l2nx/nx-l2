# Vehicles

Boats, airships and Essence shuttles.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### vehicle.boats — Boats

ID: `vehicle.boats` · Shared: **partial**

Scheduled boats between ports (Talking Island – Gludin, Gludin – Rune, Giran – Talking Island, Rune – Primeval Isle, Innadril
tour): a boat follows a path of points, docks at piers, announces departures; players board and move on deck; on-board
movement and position validation use vehicle packets.
Sub-features: route scripts (5 lines) · dock/depart announcements · board/leave · on-deck movement · boat broadcast radius ·
global switch (`AllowBoat`).

| Chronicle | Present | Builds (evidence)                  | Difference                                                                                                |
| --------- | ------- | ---------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, hellas | Mobius IL: 5 route scripts; aCis keeps routes as `itinerary` data; L2jFrozen as `boat.csv`/`boatpath.csv` |
| High Five | yes     | nx-l2, l2j-server, sunrise         | baseline: 5 route scripts + `Routes.xml`                                                                  |
| Essence   | differs | mobius-ess                         | `Boat`, `BoatManager`, `BoatAI` present; no route scripts under `ai/areas`/`vehicles`                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/BoatManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Boat.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Vehicle.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/BoatAI.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/vehicles/{BoatGiranTalking,BoatGludinRune,BoatInnadrilTour,BoatRunePrimeval,BoatTalkingGludin}.java`, `nx-l2:chronicles/high-five/datapack/config/General.ini` (`AllowBoat`)
- `mobius-il:java/org/l2jmobius/gameserver/managers/BoatManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/Boat.java`, `mobius-il:dist/game/data/scripts/vehicles/Boat*.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/BoatManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/Boat.java`

Packets

| Chronicle | Client → server                                                                                                                   | Server → client                                                                                                                                                                                                                                                                                 |
| --------- | --------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestGetOnVehicle` `42`, `RequestGetOffVehicle` `43`, `RequestMoveToLocationInVehicle` `5C`, `CannotMoveAnymoreInVehicle` `5D` | `VehicleInfo` `59`, `VehicleStarted` `C0`, `VehicleDeparture` `5A`, `VehicleCheckLocation` `5B`, `GetOnVehicle` `5C`, `GetOffVehicle` `5D`, `MoveToLocationInVehicle` `71`, `StopMoveInVehicle` `72`, `ValidateLocationInVehicle` `73`                                                          |
| High Five | `RequestGetOnVehicle` `53`, `RequestGetOffVehicle` `54`, `RequestMoveToLocationInVehicle` `75`, `CannotMoveAnymoreInVehicle` `76` | `VehicleInfo` `60`, `VehicleStarted` `C0`, `VehicleDeparture` `6C`, `VehicleCheckLocation` `6D`, `GetOnVehicle` `6E`, `GetOffVehicle` `6F`, `MoveToLocationInVehicle` `7E`, `StopMoveInVehicle` `7F`, `ValidateLocationInVehicle` `80`                                                          |
| Essence   | `RequestGetOnVehicle` `53`, `RequestGetOffVehicle` `54`, `RequestMoveToLocationInVehicle` `75`, `CannotMoveAnymoreInVehicle` `76` | `VehicleInfo` `60`, `VehicleStarted` `C0`, `VehicleDeparture` `6C`, `VehicleCheckLocation` `6D`, `GetOnVehicle` `6E`, `GetOffVehicle` `6F`, `MoveToLocationInVehicle` `7E`, `StopMoveInVehicle` `7F`, `ValidateLocationInVehicle` `80`, `NpcInfo` `0C` mask bit `NpcInfoType.VEHICLE_ID` (0x06) |

Notes: Interlude behaviour must be checked in aCis (itinerary data, `BoatMove`) and L2jFrozen, not only in Mobius IL; both
keep the route as data rather than as a script per route.

Known fixes: `l2j-server@d0a929bc` (2008-07-14, wrong-formula-value) — Fixed wrong wait time for boats between Rune <-> Primeval Isle, also while on the boat you hear music.... Thanks Kerberos.

### vehicle.airships — Airships

ID: `vehicle.airships` · Shared: **partial (HF+Ess)**

Gracia airships: scheduled Gludio–Gracia line with controllers/docks, and clan airships summoned with a license item;
captain controls the ship, fuel (Ethereal Fuel) is consumed and refilled, passengers move on board; teleport lists let the
captain choose a dock.
Sub-features: Gludio–Gracia line · dock controllers (Keucereus, Seed of Destruction/Infinity) · clan airship (license, fuel) ·
captain control · on-board movement and combat · teleport list.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                           |
| --------- | ------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: `AirShipManager`, `ControllableAirShip`, controller scripts                                                |
| Essence   | differs | mobius-ess                 | `AirShipManager`, `AirShip`, `airships.sql`, `RefuelAirship`, `AirshipAction` present; no controller or line scripts |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/AirShipManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/{AirShip,ControllableAirShip}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/AirShipAI.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/Gracia/vehicles/{AirShipController,AirShipGludioGracia,KeucereusNorthController,KeucereusSouthController,SoDController,SoIController}`, `nx-l2:chronicles/high-five/datapack/data/Routes.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/AirShipManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/{AirShip,ControllableAirShip}.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/AirshipAction.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/RefuelAirship.java`

Packets

| Chronicle | Client → server                                                                                        | Server → client                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| --------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| High Five | `MoveToLocationAirShip` `D0:38`, `MoveToLocationInAirShip` `D0:20`, `CannotMoveAnymoreAirShip` `D0:1F` | `ExAirShipInfo` `FE:60`, `ExGetOnAirShip` `FE:63`, `ExGetOffAirShip` `FE:64`, `ExMoveToLocationAirShip` `FE:65`, `ExStopMoveAirShip` `FE:66`, `ExMoveToLocationInAirShip` `FE:6D`, `ExStopMoveInAirShip` `FE:6E`, `ExValidateLocationInAirShip` `FE:6F`, `ExMoveToTargetInAirShip` `FE:71`, `ExAttackInAirShip` `FE:72`, `ExMagicSkillUseInAirShip` `FE:73`, `ExAirShipTeleportList` `FE:9A`                                                     |
| Essence   | `MoveToLocationAirShip` `D0:35`, `MoveToLocationInAirShip` `D0:20`                                     | `ExAirShipInfo` `FE:61`, `ExGetOnAirShip` `FE:64`, `ExGetOffAirShip` `FE:65`, `ExMoveToLocationAirShip` `FE:66`, `ExStopMoveAirShip` `FE:67`, `ExMoveToLocationInAirShip` `FE:6E`, `ExStopMoveInAirShip` `FE:6F`, `ExValidateLocationInAirShip` `FE:70`, `ExMoveToTargetInAirShip` `FE:72`, `ExAttackInAirShip` `FE:73`, `ExMagicSkillUseInAirShip` `FE:74`, `ExAirShipTeleportList` `FE:9B` (classes exist; no scripted ships use them in 09.2) |

Notes: the Essence classes are the High Five engine carried over; only the data and the line scripts are missing.

### vehicle.shuttles — Essence shuttles

ID: `vehicle.shuttles` · Shared: **chronicle-only**

Essence shuttles (Magmeld): a shuttle moves along routes between stops, with doors and per-stop boarding dimensions; players
board, move on board and get off; the engine runs shuttles by a data file.
Sub-features: shuttle data (stops, routes, doors) · engine tick · board/leave · on-board movement.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il         | —          |
| High Five | no      | nx-l2             | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ShuttleData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/shuttle/{ShuttleEngine,ShuttleDataHolder,ShuttleStop}.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/Shuttle.java`, `mobius-ess:java/org/l2jmobius/gameserver/ai/ShuttleAI.java`, `mobius-ess:dist/game/data/ShuttleData.xml`

Packets

| Chronicle | Client → server                                                                                                                        | Server → client                                                                                                                                                                                                  |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestShuttleGetOn` `D0:7D`, `RequestShuttleGetOff` `D0:7E`, `MoveToLocationInShuttle` `D0:7F`, `CannotMoveAnymoreInShuttle` `D0:80` | `ExShuttleInfo` `FE:CC`, `ExShuttleGetOn` `FE:CD`, `ExShuttleGetOff` `FE:CE`, `ExShuttleMove` `FE:CF`, `ExMoveToLocationInShuttle` `FE:D0`, `ExStopMoveInShuttle` `FE:D1`, `ExValidateLocationInShuttle` `FE:D2` |

Notes: —

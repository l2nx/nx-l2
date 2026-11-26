# Zones

Zone engine and the zone types: peace, PvP, damage and effect, restrictions, jail, residence and Essence special zones.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### zone.engine — Zone engine

ID: `zone.engine` · Shared: **engine**

Zones are shapes (cuboid, cylinder, n-polygon with min/max Z) loaded from XML; each zone has a type class that reacts to
creatures entering and leaving, plus a region index so only zones near an object are tested. Zone ids are boolean flags on
the creature (`ZoneId`), queried by the rest of the server (`isInsideZone(ZoneId.PEACE)`).
Sub-features: zone XML loader · shapes · enter/exit events · zone settings (task zone) · zone flags per creature · GM zone
builder (`AdminZoneBuild`).

| Chronicle | Present | Builds (evidence)                          | Difference                                                                                               |
| --------- | ------- | ------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (21 zone files), l2jfrozen | 33 zone files (Mobius IL)                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise                 | baseline; 43 zone files                                                                                  |
| Essence   | yes     | mobius-ess                                 | 39 files; flag set changes (see the zone type features); `ZoneBuildManager` instead of the admin command |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/ZoneManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/ZoneBuildManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/{ZoneType,ZoneId,ZoneForm,ZoneRegion,AbstractZoneSettings}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/form/*`, `nx-l2:chronicles/high-five/datapack/data/zones/*.xml`
- `mobius-il:java/org/l2jmobius/gameserver/managers/ZoneManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/zone/*`, `mobius-il:dist/game/data/zones/*.xml`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminZoneBuild.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/ZoneManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/*`, `mobius-ess:dist/game/data/zones/*.xml`

Packets

| Chronicle | Client → server | Server → client                                                                                                             |
| --------- | --------------- | --------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `ExSetCompassZoneCode` `FE:32` (compass zone icon: peace, PvP, siege, altered), `ExServerPrimitive` `FE:24` (GM debug draw) |
| High Five | —               | `ExSetCompassZoneCode` `FE:33`, `ExServerPrimitive` `FE:11`                                                                 |
| Essence   | —               | `ExSetCompassZoneCode` `FE:33`, `ExServerPrimitive` `FE:11`                                                                 |

Notes: the zone flag enum differs by chronicle (Interlude has no `LANDING`, `FORT`; Essence drops `TOWN` and adds `SAYUNE`,
`FISHING`, `UNDYING`, `TAX`, `TIMED_HUNTING`, `PRISON`), so the engine core is shared and the type set is per chronicle.

Known fixes: `l2j-server-dp@794b8563` (2008-10-17, exploit-dupe) — XML: Castle zones changes to fix CS exploit (thx GodKratos); `mobius@3333b74f` (2017-09-19, other) — Fixed Alligator Island water zones.

### zone.peace-town — Peace and town zones

ID: `zone.peace-town` · Shared: **partial**

Peace zones forbid attacks, flagging and aggro; town zones mark towns for respawn, taxes and town-bound rules. Both are
flag zones: entering sets `PEACE` / `TOWN` and optionally recovers HP/MP faster.
Sub-features: peace flag · town flag · entry messages · tax link (Essence) · custom town zones.

| Chronicle | Present | Builds (evidence)          | Difference                                                            |
| --------- | ------- | -------------------------- | --------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | `PeaceZone` + `TownZone`                                              |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline                                                              |
| Essence   | differs | mobius-ess                 | no `TownZone`; `TaxZone` carries the town/tax link; `peace.xml` stays |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/{PeaceZone,TownZone}.java`, `nx-l2:chronicles/high-five/datapack/data/zones/{peace,custom_town}.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/{PeaceZone,TownZone}.java`, `mobius-il:dist/game/data/zones/{peace,custom_town}.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{PeaceZone,TaxZone}.java`, `mobius-ess:dist/game/data/zones/{peace,tax}.xml`

Packets

| Chronicle | Client → server | Server → client                |
| --------- | --------------- | ------------------------------ |
| Interlude | —               | `ExSetCompassZoneCode` `FE:32` |
| High Five | —               | `ExSetCompassZoneCode` `FE:33` |
| Essence   | —               | `ExSetCompassZoneCode` `FE:33` |

Notes: —

### zone.pvp-arena — Arenas and PvP zones

ID: `zone.pvp-arena` · Shared: **engine**

Arena zones give arena respawn and PvP rules (no karma, forced PvP flag); flag-PvP zones flag everyone inside; no-PvP zones
forbid combat between players; derby-track zones belong to the monster race.
Sub-features: arena respawn points · forced flag · no-PvP · derby track · olympiad stadium (separate feature).

| Chronicle | Present | Builds (evidence)                        | Difference |
| --------- | ------- | ---------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis (`ArenaZone`), l2jfrozen | same       |
| High Five | yes     | nx-l2, l2j-server, sunrise               | baseline   |
| Essence   | yes     | mobius-ess                               | same       |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/{ArenaZone,FlagPvPZone,NoPvPZone,DerbyTrackZone}.java`, `nx-l2:chronicles/high-five/datapack/data/zones/{pvp,no_pvp}.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/{ArenaZone,FlagPvPZone,NoPvPZone,DerbyTrackZone}.java`, `mobius-il:dist/game/data/zones/{pvp,no_pvp}.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{ArenaZone,FlagPvPZone,NoPvPZone,DerbyTrackZone}.java`, `mobius-ess:dist/game/data/zones/{pvp,no_pvp}.xml`

Packets

| Chronicle | Client → server | Server → client                  |
| --------- | --------------- | -------------------------------- |
| Interlude | —               | — (flag packets: see `pvp.flag`) |
| High Five | —               | — (flag packets: see `pvp.flag`) |
| Essence   | —               | — (flag packets: see `pvp.flag`) |

Notes: —

### zone.damage-effect — Damage and effect zones

ID: `zone.damage-effect` · Shared: **engine**

Damage zones hurt creatures inside periodically (HP/MP per tick, with a delay and a reuse); effect zones apply a skill at
an interval; swamp zones slow movement; mother-tree zones recover HP/MP faster for the elven race.
Sub-features: damage zone · effect zone (skill list, chance) · swamp (slow) · mother tree · castle-linked swamps.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | same       |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline   |
| Essence   | yes     | mobius-ess                 | same       |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/{DamageZone,EffectZone,SwampZone,MotherTreeZone}.java`, `nx-l2:chronicles/high-five/datapack/data/zones/{damage,effect,swamp,elven_mother_tree}.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/{DamageZone,EffectZone,SwampZone,MotherTreeZone}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{DamageZone,EffectZone,SwampZone,MotherTreeZone}.java`

Packets

| Chronicle | Client → server | Server → client      |
| --------- | --------------- | -------------------- |
| Interlude | —               | `SystemMessage` `64` |
| High Five | —               | `SystemMessage` `62` |
| Essence   | —               | `SystemMessage` `62` |

Notes: —

Known fixes: `mobius@fce88c28` (2021-09-25, crash-npe) — Fixed EffectZone cancel task NPE.

### zone.restrictions — Restriction zones

ID: `zone.restrictions` · Shared: **partial**

Zones that forbid or limit one activity: no landing (strider/wyvern), no restart (no logout-to-town), no private store, no
summon-friend, no item drop, no teleport bookmark, conditional zones (restrict entry by level, race, class, quest).
Sub-features: no landing · landing (Gracia) · no restart · no store · no summon friend · no item drop · no bookmark ·
condition zone.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                         |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen | no `LANDING` zone and no airship landing; `NO_BOOKMARK` flag is defined but bookmarks are not an Interlude feature |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: `LandingZone`, `NoLandingZone`, `NoStoreZone`, `NoRestartZone`, `NoSummonFriendZone`, `ConditionZone`    |
| Essence   | yes     | mobius-ess                 | same set                                                                                                           |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/{LandingZone,NoLandingZone,NoRestartZone,NoStoreZone,NoSummonFriendZone,ConditionZone}.java`, `nx-l2:chronicles/high-five/datapack/data/zones/{landing,no_landing,no_restart,no_summon_friend,no_bookmark,no_drop_item,custom_no_store}.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/{NoLandingZone,NoRestartZone,NoStoreZone,NoSummonFriendZone,ConditionZone}.java`, `mobius-il:dist/game/data/zones/{no_landing,no_restart,no_summon_friend,no_bookmark,no_drop_item,custom_no_store}.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{LandingZone,NoLandingZone,NoRestartZone,NoStoreZone,NoSummonFriendZone,ConditionZone}.java`, `mobius-ess:dist/game/data/zones/*.xml`

Packets

| Chronicle | Client → server | Server → client                      |
| --------- | --------------- | ------------------------------------ |
| Interlude | —               | `SystemMessage` `64` (refusal texts) |
| High Five | —               | `SystemMessage` `62`                 |
| Essence   | —               | `SystemMessage` `62`                 |

Notes: the restriction is checked by the feature that is restricted (store, restart, summon), not by the zone; the zone only
sets a flag.

### zone.jail-prison — Jail and prison

ID: `zone.jail-prison` · Shared: **partial**

A jail zone holds a punished character: no leaving, no store, no teleport, no summon; GM sends a player to jail for a number
of minutes and a timer releases him. Essence adds a prison: characters with many PKs or low reputation are sent there automatically, serve a sentence timer and leave by bail or by donating marks / L-coins.
Sub-features: jail zone · jail command · release timer · prison by reputation/PK count with bail and donation (Essence).

| Chronicle | Present | Builds (evidence)                 | Difference                                                                                                |
| --------- | ------- | --------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (jail), l2jfrozen | jail only                                                                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise        | baseline; `PrisonGuards` AI script                                                                        |
| Essence   | differs | mobius-ess                        | `JailZone` and `PrisonZone`; `Prison.ini` (reputation/PK thresholds, sentence time, bail); prison packets |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/JailZone.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/PrisonGuards.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/JailZone.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{JailZone,PrisonZone}.java`, `mobius-ess:dist/game/data/zones/prison.xml`, `mobius-ess:dist/game/config/Prison.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/prison/Prisoner.java`

Packets

| Chronicle | Client → server                                                          | Server → client                                                                                                             |
| --------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                        | `SystemMessage` `64`                                                                                                        |
| High Five | —                                                                        | `SystemMessage` `62`                                                                                                        |
| Essence   | `RequestPrisonUserInfo` `D0:025D`, `RequestPrisonUserDonation` `D0:025E` | `ExPrisonUserEnter` `FE:0325`, `ExPrisonUserExit` `FE:0326`, `ExPrisonUserInfo` `FE:0327`, `ExPrisonUserDonation` `FE:0328` |

Notes: GM jail commands belong to the admin domain; this feature is the zone and its release logic. The Essence prison is driven by PK/reputation, which overlaps the pvp domain.

### zone.residence-zones — Residence zones

ID: `zone.residence-zones` · Shared: **partial**

Zones bound to a castle, fort, clan hall or siege: residence area (owner-only rules, functions), siege area, headquarters
area, teleport zones that send defenders or attackers to the right spot, tax zones.
Sub-features: castle zone · fort zone · clan hall zone · siege zone · HQ zone · residence teleport zone · siegable hall zone.

| Chronicle | Present | Builds (evidence)          | Difference                                                                          |
| --------- | ------- | -------------------------- | ----------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen | castle/clan hall/siege/HQ zones; no `FortZone` (forts are not an Interlude feature) |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: adds `FortZone`, territory-war teleport zones                             |
| Essence   | yes     | mobius-ess                 | same set; fewer clan hall zone files                                                |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/{CastleZone,FortZone,ClanHallZone,SiegeZone,HqZone,ResidenceZone,ResidenceTeleportZone,ResidenceHallTeleportZone,SiegableHallZone}.java`, `nx-l2:chronicles/high-five/datapack/data/zones/{castle_*,fortress_*,clan_hall*,clanhall_teleport,territory_war_*}.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/{CastleZone,ClanHallZone,SiegeZone,HqZone,ResidenceZone,ResidenceTeleportZone,ResidenceHallTeleportZone,SiegableHallZone}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{CastleZone,FortZone,ClanHallZone,SiegeZone,HqZone,ResidenceZone,ResidenceTeleportZone,ResidenceHallTeleportZone,SiegableHallZone}.java`

Packets

| Chronicle | Client → server | Server → client                             |
| --------- | --------------- | ------------------------------------------- |
| Interlude | —               | `ExSetCompassZoneCode` `FE:32` (siege zone) |
| High Five | —               | `ExSetCompassZoneCode` `FE:33`              |
| Essence   | —               | `ExSetCompassZoneCode` `FE:33`              |

Notes: some Interlude roots backport forts (see the residence domain), so `FortZone` appears there as custom.

### zone.essence-special — Essence special zones

ID: `zone.essence-special` · Shared: **chronicle-only**

Essence-only zones: undying zones (no death), teleport zones (move to a fixed place on entry), sayune zones (flight points), timed
hunting zones (limited-time hunting areas with their own entry packets) and tax zones.
Sub-features: undying · teleport zone · sayune zone · timed hunting zone · tax zone.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il, acis   | —          |
| High Five | no      | nx-l2             | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/{UndyingZone,TeleportZone,SayuneZone,TimedHuntingZone,TaxZone}.java`, `mobius-ess:dist/game/data/zones/{teleportzones,timed_hunting,hellbound}.xml`, `mobius-ess:dist/game/data/TimedHuntingZoneData.xml`

Packets

| Chronicle | Client → server                                                                                                                                                       | Server → client                                                                                                                                                                                                                                                                                 |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExTimedHuntingZoneList` `D0:017F`, `ExTimedHuntingZoneEnter` `D0:0180`, `ExTimedHuntingZoneLeave` `D0:0181`; `RequestFlyMove` `D0:92`, `RequestFlyMoveStart` `D0:AE` | `TimedHuntingZoneList` `FE:022A`, `TimedHuntingZoneEnter` `FE:022B`, `TimedHuntingZoneExit` `FE:022E`, `TimedHuntingZoneChargeResult` `FE:022C`, `TimeRestrictFieldUserAlarm` `FE:022D`, `TimeRestrictFieldDieLimitTime` `FE:0306`, `ExFlyMove` `FE:E9`, `ExLevelambienceChangedinfo` `FE:0393` |

Notes: the timed hunting zone is also described by the reflection domain (temporary hunting areas); the zone class is
listed here because it is a `ZoneId` flag (`TIMED_HUNTING`).

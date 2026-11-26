# Character growth systems

Character power-up systems outside level and equipment: collections, relics, spirits and other Essence-style progressions.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### growth.collections — Collections

ID: `growth.collections` · Shared: **chronicle-only**

Register specific items (with minimum enchant) into collection slots; a completed collection gives a permanent bonus
option; rewards for completion, favourites list, summary per category.
Sub-features: slot registration · collection bonus · completion reward · favourites · summary · relic collections
(see `growth.relics`).

| Chronicle | Present | Builds (evidence)                                   | Difference                                   |
| --------- | ------- | --------------------------------------------------- | -------------------------------------------- |
| Interlude | no      | mobius-il                                           | —                                            |
| High Five | no      | nx-l2, l2j-server; fork facabek l2h5java25 (custom) | retail no; custom port in one Mobius HF fork |
| Essence   | yes     | mobius-ess (from 05.2 Frostlord)                    | baseline                                     |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/CollectionData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/collection/RequestCollectionRegister.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/holders/CollectionDataHolder.java`
- `facabek-l2h5java25:dist/game/config/Custom/CollectionSystem.ini`, `facabek-l2h5java25:.../dist/game/data/collections.xml` (community-board UI)

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                     | Server → client                                                                                                                                                                                                                                                                                                                                                                            |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `RequestExCollectionOpenUI` `D0:01DB`, `RequestExCollectionList` `D0:01DD`, `RequestCollectionRegister` `D0:01E1`, `RequestCollectionReceiveReward` `D0:01E2`, `RequestCollectionFavoriteList` `D0:01DF`, `RequestCollectionUpdateFavorite` `D0:01DE`, `RequestExCollectionSummary` `D0:01E0`, `RequestCollectionCloseUI` `D0:01DC` | `ExCollectionOpenUI` `FE:0290`, `ExCollectionList` `FE:0292`, `ExCollectionInfo` `FE:028F`, `ExCollectionRegister` `FE:0296`, `ExCollectionComplete` `FE:0297`, `ExCollectionReceiveReward` `FE:0298`, `ExCollectionFavoriteList` `FE:0294`, `ExCollectionUpdateFavorite` `FE:0293`, `ExCollectionSummary` `FE:0295`, `ExCollectionActiveEvent` `FE:029A`, `ExCollectionCloseUI` `FE:0291` |

Notes: Mobius history: absent in Essence 04.2 DwellingOfSpirits, added in 05.2 Frostlord (`CollectionData`,
`PlayerCollectionData`). HF ports have to use a community-board/HTML UI — the HF client has no collection window.

### growth.relics — Relics

ID: `growth.relics` · Shared: **chronicle-only**

Summon relics with coupons, activate one, upgrade by duplicates, combine lower relics into higher grades, exchange,
relic collections with bonuses. Sub-features: summon (coupons) · activate · upgrade · combination · exchange · relic
collections · probability list.

| Chronicle | Present | Builds (evidence)                | Difference |
| --------- | ------- | -------------------------------- | ---------- |
| Interlude | no      | mobius-il                        | —          |
| High Five | no      | nx-l2                            | —          |
| Essence   | yes     | mobius-ess (from 08.3 Guardians) | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/RelicData.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/RelicCollectionData.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/RelicCouponData.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/RelicSystemConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/relics`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Server → client                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `RequestRelicsOpenUI` `D0:0286`, `RequestRelicsSummon` `D0:028A`, `RequestRelicsIdSummon` `D0:02B5`, `RequestRelicsSummonList` `D0:02B6`, `RequestRelicsActive` `D0:0289`, `RequestRelicsUpgrade` `D0:028D`, `RequestRelicsCombination` `D0:028E`, `RequestRelicsConfirmCombination` `D0:02B7`, `RequestRelicsCombinationComplete` `D0:0299`, `RequestRelicsExchange` `D0:028B`, `RequestRelicsExchangeConfirm` `D0:028C`, `RequestRelicsProbList` `D0:02B2`, `RequestRelicsSummonCloseUI` `D0:0288`, `RequestRelicsCloseUI` `D0:0287` | `ExRelicsList` `FE:035B`, `ExRelicsActiveInfo` `FE:0359`, `ExRelicsAnnounce` `FE:035A`, `ExRelicsCombination` `FE:0361`, `ExRelicsExchange` `FE:035E`, `ExRelicsExchangeConfirm` `FE:035F`, `ExRelicsExchangeList` `FE:035D`, `ExRelicsPointInfo` `FE:039F`, `ExRelicsCollectionInfo` `FE:0362`, `ExRelicsCollectionUpdate` `FE:0363`, `ExRelicsCollectionCompleteAnnounce` `FE:0373` (+ further `ExRelics*`), `ExRelicsResetAll` `FE:0372`, `RelicsProbList` `FE:0398`, `ExRelicsSummonList` `FE:039E`, `ExRelicsSummonResult` `FE:035C`, `ExRelicsUpdateList` `FE:0370`, `ExRelicsUpgrade` `FE:0360` |

Notes: IL/HF name hits (`Q00619_RelicsOfTheOldEmpire`, `Q00183_RelicExploration`, `NewSiege/Relics`) are quests / siege
scripts, not this feature.

### growth.elemental-spirits — Elemental spirits

ID: `growth.elemental-spirits` · Shared: **chronicle-only**

Four elemental spirits (fire/water/wind/earth) gain exp, evolve, absorb items, extract, distribute talent points;
one spirit is active and sets the attack element.
Sub-features: spirit exp · evolution · absorb · extract · talents · active spirit · spirit exp items.

| Chronicle | Present | Builds (evidence)       | Difference |
| --------- | ------- | ----------------------- | ---------- |
| Interlude | no      | mobius-il               | —          |
| High Five | no      | nx-l2                   | —          |
| Essence   | yes     | mobius-ess (09.2, 04.2) | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ElementalSpiritData.java`, `mobius-ess:dist/game/data/scripts/handlers/items/AddSpiritExp.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/elementalspirits`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                | Server → client                                                                                                                                                                                                                                                                                                                                                                                                    |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `ExElementalSpiritInfo` `D0:0150`, `ExElementalSpiritAbsorbInfo` `D0:0157`, `ExElementalSpiritAbsorb` `D0:0158`, `ExElementalSpiritEvolutionInfo` `D0:0153`, `ExElementalSpiritEvolution` `D0:0154`, `ExElementalSpiritExtractInfo` `D0:0151`, `ExElementalSpiritExtract` `D0:0152`, `ExElementalSpiritSetTalent` `D0:0155`, `ExElementalInitTalent` `D0:0156` | `ElementalSpiritInfo`, `ElementalSpiritAbsorbInfo` `FE:01F6`, `ElementalSpiritAbsorb` `FE:01F7`, `ElementalSpiritEvolutionInfo` `FE:01F3`, `ElementalSpiritEvolution` `FE:01F4`, `ElementalSpiritExtractInfo` `FE:01F2`, `ElementalSpiritExtract` `FE:01FA`, `ElementalSpiritSetTalent` `FE:01F5`, `ExElementalSpiritAttackType` `FE:0332`, `ExElementalSpiritGetExp` `FE:01FE`; `UserInfo` `32` bit `ATT_SPIRITS` |

Notes: UserInfo bit `ATT_SPIRITS`.

### growth.aden-lab — Aden Laboratory

ID: `growth.aden-lab` · Shared: **chronicle-only**

Boss "cells" are unlocked one by one; normal play and special play roll stat bonuses into slots; transcendent enchant
on top. Sub-features: boss list / unlock · normal play · special play · special fix · transcendent enchant ·
probabilities.

| Chronicle | Present | Builds (evidence) | Difference                |
| --------- | ------- | ----------------- | ------------------------- |
| Interlude | no      | mobius-il         | —                         |
| High Five | no      | nx-l2             | —                         |
| Essence   | yes     | mobius-ess        | baseline (absent in 04.2) |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/config/AdenLaboratoryConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/holders/AdenLabHolder.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/adenlab`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                                                                              | Server → client                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `RequestAdenLabBossList` `D0:02A0`, `RequestAdenLabBossInfo` `D0:02A2`, `RequestAdenLabBossUnlock` `D0:02A1`, `RequestAdenLabNormalSlot` `D0:02A3`, `RequestAdenLabNormalPlay` `D0:02A4`, `RequestAdenLabSpecialSlot` `D0:02A5`, `RequestAdenLabSpecialPlay` `D0:02A7`, `RequestAdenLabSpecialFix` `D0:02A8`, `RequestAdenLabSpecialProbability` `D0:02A6`, `RequestAdenLabTranscendentEnchant` `D0:02A9`, `RequestAdenLabTranscendentProbability` `D0:02AA` | `ExAdenLabBossList` `FE:037D`, `ExAdenLabBossInfo` `FE:037F`, `ExAdenLabBossUnlock` `FE:037E`, `ExAdenLabNormalSlot` `FE:0380`, `ExAdenLabNormalPlay` `FE:0381`, `ExAdenLabSpecialSlot` `FE:0382`, `ExAdenLabSpecialPlay` `FE:0384`, `ExAdenLabSpecialFix` `FE:0385`, `ExAdenLabSpecialProb` `FE:0383`, `ExAdenLabTooltipInfo` `FE:0388`, `ExAdenLabTranscendEnchant` `FE:0386`, `ExAdenLabTranscendAnnounce` `FE:0387` (+ further `ExAdenLab*`), `ExAdenLabTranscendProb` `FE:0389` |

Notes: —

### growth.magic-lamp — Magic lamp

ID: `growth.magic-lamp` · Shared: **chronicle-only**

A gauge fills with hunting exp; full gauges are spent on a lamp game with tiered rewards.
Sub-features: gauge from exp · normal / greater lamp game · rewards.

| Chronicle | Present | Builds (evidence)       | Difference |
| --------- | ------- | ----------------------- | ---------- |
| Interlude | no      | mobius-il               | —          |
| High Five | no      | nx-l2                   | —          |
| Essence   | yes     | mobius-ess (09.2, 04.2) | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/MagicLampManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/MagicLampData.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/MagicLampConfig.java`

Packets

| Chronicle | Client → server                                                   | Server → client                                            |
| --------- | ----------------------------------------------------------------- | ---------------------------------------------------------- |
| Essence   | `ExMagicLampGameInfo` `D0:016F`, `ExMagicLampGameStart` `D0:0170` | `ExMagicLampInfo` `FE:0351`, `ExMagicLampResult` `FE:0352` |

Notes: —

### growth.subjugation — Subjugation (purge)

ID: `growth.subjugation` · Shared: **chronicle-only**

Killing monsters in subjugation zones gives points per zone; points are spent on a gacha; weekly ranking per zone.
Sub-features: zone points · gacha · ranking · sidebar.

| Chronicle | Present | Builds (evidence) | Difference                |
| --------- | ------- | ----------------- | ------------------------- |
| Interlude | no      | mobius-il         | —                         |
| High Five | no      | nx-l2             | —                         |
| Essence   | yes     | mobius-ess        | baseline (absent in 04.2) |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SubjugationData.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SubjugationGacha.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/PurgeRankingManager.java`

Packets

| Chronicle | Client → server                                                                                                                                       | Server → client                                                                                                                                                     |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestSubjugationList` `D0:01EF`, `RequestSubjugationRanking` `D0:01F0`, `RequestSubjugationGachaUI` `D0:01F1`, `RequestSubjugationGacha` `D0:01F2` | `ExSubjugationList` `FE:02AA`, `ExSubjugationRanking` `FE:02AB`, `ExSubjugationGachaUI` `FE:02AC`, `ExSubjugationGacha` `FE:02AD`, `ExSubjugationSidebar` `FE:02A9` |

Notes: IL/HF name hits are quests (`Q00263_OrcSubjugation`, `Q00340_SubjugationOfLizardmen`), not this feature.

Known fixes: `mobius@c3ee7d5b` (2021-11-10, crash-npe) — Fixed probable RequestSubjugationGachaUI NPE.

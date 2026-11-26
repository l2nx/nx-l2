# Equipment and enhancement

Equipping items and everything that strengthens a specific piece of equipment.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### equipment.equip — Equip and unequip

ID: `equipment.equip` · Shared: **partial**

Wearing items into paperdoll slots: slot resolution by body part, two-handed / dual / bow-arrow rules, class and grade
checks, unequip of conflicting slots, item-set refresh after change. Essence adds pet equipment and a much wider slot
table.
Sub-features: paperdoll slots · two-handed and shield rules · unequip request · class / grade restrictions · pet equip (Ess).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                 |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | 17 slots (underwear, ears, neck, fingers, head, hands, gloves, chest, legs, feet, cloak, face, hair, hair2)                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | 25 slots: + 2 bracelets, 6 deco (talisman) slots; slot indices renumbered                                                                  |
| Essence   | yes     | mobius-ess                                    | 59 slots: + belt, brooch, 5 jewel, 5 agathion, 21 artifact (balance / spirit / protection / support); `ExPetEquipItem`, `ExPetUnequipItem` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/Inventory.java` (`PAPERDOLL_*`, `PAPERDOLL_TOTALSLOTS = 25`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestUnEquipItem.java`, equip via `UseItem`
- `mobius-il:java/org/l2jmobius/gameserver/entity/itemcontainer/Inventory.java` (`PAPERDOLL_TOTALSLOTS = 17`), `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestUnEquipItem.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/Inventory.java` (`PAPERDOLL_TOTALSLOTS = 59`), `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pet/{ExPetEquipItem,ExPetUnequipItem}.java`

Packets

| Chronicle | Client → server                                                                                     | Server → client                                                                             |
| --------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Interlude | `UseItem` `14`, `RequestUnEquipItem` `11`                                                           | `InventoryUpdate` `27`, `UserInfo` `04`/`CharInfo` `03` (paperdoll ids), `EquipUpdate` `4B` |
| High Five | `UseItem` `19`, `RequestUnEquipItem` `16`                                                           | `InventoryUpdate` `21`, `UserInfo` `32`, `CharInfo` `31`                                    |
| Essence   | `UseItem` `19`, `RequestUnEquipItem` `16`, `ExPetEquipItem` `D0:01A6`, `ExPetUnequipItem` `D0:01A7` | `InventoryUpdate` `21`, `UserInfo` `32`, `CharInfo` `31`, `ExUserInfoEquipSlot` `FE:0157`   |

Mask bits: Essence — `InventorySlot` paperdoll slots (60) in `ExUserInfoEquipSlot`; `UserInfoType` in `UserInfo`: `SLOTS` 0x12.

Notes: slot indices differ per version, so the paperdoll table must be version-specific in a shared module.

### equipment.armor-sets — Armor sets

ID: `equipment.armor-sets` · Shared: **partial**

Wearing the listed pieces of a set grants set bonus skills; an extra bonus for enchant of the whole set; shield and
cloak slots optional. Data-driven.
Sub-features: set definition (chest + pieces) · bonus skill · shield / enchant-6 bonus · listener on equip.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | aCis: `ArmorSetData` XML + `ArmorSetListener`; L2jFrozen: `ArmorSetsTable` (SQL) + `CustomArmorSetsTable` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | 24 set XML files by grade (`stats/armorsets/`); `FuncArmorSet` stat function                              |
| Essence   | differs | mobius-ess                                    | single `Sets.xml`; `ArmorsetSkillHolder`; `ArmorSetData` is a different loader (147 lines changed vs HF)  |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ArmorSetData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/holders/ArmorSet.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/functions/formulas/FuncArmorSet.java`, `nx-l2:chronicles/high-five/datapack/data/stats/armorsets/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/ArmorSetData.java` (0 diff vs Mobius HF), `mobius-il:dist/game/data/stats/armorsets`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ArmorSetData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/holders/ArmorsetSkillHolder.java`, `mobius-ess:dist/game/data/stats/armorsets/Sets.xml`

Packets

| Chronicle | Client → server | Server → client                                                        |
| --------- | --------------- | ---------------------------------------------------------------------- |
| Interlude | —               | `SkillList` `58` (set skills appear as skills), `InventoryUpdate` `27` |
| High Five | —               | `SkillList` `5F`, `InventoryUpdate` `21`                               |
| Essence   | —               | `SkillList` `5F`, `InventoryUpdate` `21`                               |

Notes: no dedicated packet in the catalog; the client sees only the granted skills.

Known fixes: `l2j-server-dp@97c2a3a7` (2020-12-10, missing-check) — Adding missing armor set bonuses

### equipment.grade-penalty — Grade penalty (Expertise)

ID: `equipment.grade-penalty` · Shared: **partial (IL+HF)**

Wearing items above the character's expertise grade applies penalties: attack speed, casting speed, evasion / accuracy
reductions per weapon and armor; recalculated after every equip change.
Sub-features: weapon penalty · armor penalty · expertise level from skill · penalty config switch.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                          |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | `ExpertisePenalty` config; refresh on equip                                                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise | `Player.refreshExpertisePenalty()`; `PlayerConfig.EXPERTISE_PENALTY`                                                |
| Essence   | no      | mobius-ess                 | no expertise-penalty code in `Player` / `Inventory`; grade checks only in `CreatureStat` for skill level difference |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Player.java` (`refreshExpertisePenalty`, ~l.2121), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/Inventory.java` (calls at ~l.985, 1009), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/PlayerConfig.java` (`ExpertisePenalty`)
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/Player.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/Player.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/status/PlayerStatus.java`; L2jFrozen: `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/model/actor/instance/L2PcInstance.java`
- grade constants only (`mobius-ess:java/org/l2jmobius/gameserver/entity/item/type/CrystalType.java`)

Packets

| Chronicle | Client → server | Server → client                                              |
| --------- | --------------- | ------------------------------------------------------------ |
| Interlude | —               | `EtcStatusUpdate` `F3` (grade-penalty icon), `UserInfo` `04` |
| High Five | —               | `EtcStatusUpdate` `F9`, `UserInfo` `32`                      |
| Essence   | —               | —                                                            |

Notes: the penalty icon is carried by `EtcStatusUpdate` (HF); not packet-verified in the catalog.

Known fixes: `l2j-server@d911fd6f` (2010-01-01, exploit-dupe) — - typo fix for previous commit - exploit fix for grade penalty.

### equipment.enchant — Item enchanting

ID: `equipment.enchant` · Shared: **partial**

Enchant weapon / armor / jewelry with scrolls (normal, blessed, crystal, safe) and support items: success chance by
group and current level, safe levels, max level per scroll, failure either destroys the item (crystal result) or
resets it; HP bonus of enchanted armor; announcements at high levels.
Sub-features: scroll types · support items (Essence: protection scroll) · chance groups · max / safe level · HP bonus ·
fail rewards (Ess) · enchant log.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                                                                                                                                                                       |
| --------- | ------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas    | aCis / Frozen: chances from config (`EnchantChanceMagicWeapon = 0.4`, `...NonMagicWeapon = 0.7`, `...15Plus`); Mobius IL: XML groups, 16 max                                                                                                     |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | XML (`EnchantItemData` 20 scroll entries, max 16; `EnchantItemGroups` flat 66% from +3 to +15); `EnchantItemHPBonusData`                                                                                                                         |
| Essence   | differs | mobius-ess                                       | `EnchantItemData` 783 lines: protecting scrolls with `safeEnchant`, per-scroll `maxEnchant`; descending chances in `FIGHTER_WEAPON_GROUP` (100% to +3, then 70 / 60 / 50 / 40 / 30 / 20%); `ExRequestEnchantFailRewardInfo`, view-result packets |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestEnchantItem.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/{EnchantItemData,EnchantItemGroupsData,EnchantItemHPBonusData}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/enchant/{EnchantScroll,EnchantSupportItem}.java`, `nx-l2:chronicles/high-five/datapack/data/{EnchantItemData,EnchantItemGroups}.xml`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java`, `mobius-il:dist/game/data/EnchantItemData.xml`, `mobius-il:dist/game/data/EnchantItemGroups.xml`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/network/clientpackets/{RequestEnchantItem,AbstractEnchantPacket}.java`, `acis:aCis_gameserver/config/players.properties`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/enchant/single/*`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/enchant/RequestEnchantItem.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/enchant/{EnchantScroll,EnchantScrollGroup,EnchantSupportItem}.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                        | Server → client                                                                                                                                                                                                                                                                                                                                                                           |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestEnchantItem` `58`                                                                                                                                                                                                                                                                                                              | `ChooseInventoryItem` `6F`, `EnchantResult` `81`, `ExRestartClient` `FE:47`                                                                                                                                                                                                                                                                                                               |
| High Five | `RequestEnchantItem` `5F`, `RequestExTryToPutEnchantTargetItem` `D0:4C`, `RequestExTryToPutEnchantSupportItem` `D0:4D`, `RequestExCancelEnchantItem` `D0:4E`                                                                                                                                                                           | `ChooseInventoryItem` `7C`, `EnchantResult` `87`, `ExRestartClient` `FE:48`, `ExPutEnchantTargetItemResult` `FE:81`, `ExPutEnchantSupportItemResult` `FE:82`                                                                                                                                                                                                                              |
| Essence   | `RequestEnchantItem` `5F`, `RequestExTryToPutEnchantTargetItem` `D0:49`, `RequestExTryToPutEnchantSupportItem` `D0:4A`, `RequestExCancelEnchantItem` `D0:4B`, `ExRequestViewEnchantResult` `D0:022B`, `ExRequestEnchantFailRewardInfo` `D0:0228`, `RequestExAddEnchantScrollItem` `D0:E4`, `RequestExRemoveEnchantSupportItem` `D0:E5` | `ChooseInventoryItem` `7C`, `EnchantResult` `87`, `ExRestartClient` `FE:49`, `ExPutEnchantTargetItemResult` `FE:82`, `ExPutEnchantSupportItemResult` `FE:83`, `ExPutEnchantScrollItemResult` `FE:0153`, `ExRemoveEnchantSupportItemResult` `FE:0154`, `ExChangedEnchantTargetItemProbList` `FE:02E4`, `ExEnchantChallengePointInfo` `FE:02E5`, `ResetEnchantItemFailRewardInfo` `FE:02E3` |

Mask bits: Essence — `UserInfoType` in `UserInfo`: `ENCHANTLEVEL` 0x05.

Notes: `RequestEnchantItem` is the largest file of the domain (≈800 lines in Mobius HF / Essence; 166 lines differ
between Mobius IL and HF, 368 between HF and Essence). Mobius IL XML max enchant 16 vs aCis config-driven: the table
is data in Mobius, code in aCis / L2jFrozen.

Known fixes: `mobius@66fc6fb9` (2022-12-11, crash-npe) — Prevent NPE from EnchantItemData null scroll or support item; `l2jfrozen@76731750` (2011-03-26, crash-npe) — Fix on enchant rate null pointer exception.

### equipment.enchant-multi — Mass enchant and challenge points

ID: `equipment.enchant-multi` · Shared: **chronicle-only**

Essence: enchant several items with one scroll batch (select items, start, view result); separately, "challenge
points" accumulate on failed enchants and can be set against an item to raise the next attempt.
Sub-features: multi-enchant item list · start / finish scroll · result view · challenge points set / reset.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                     |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                                              |
| High Five | no      | nx-l2, l2j-server, sunrise | no multi-enchant packets or data in any public HF build                                        |
| Essence   | yes     | mobius-ess                 | `enchant/multi/*` (5 client packets), `enchant/challengepoint/*`, `EnchantChallengePoints.xml` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/enchant/multi/ExRequestStartMultiEnchantScroll.java`, `mobius-ess:.../ExRequestSetMultiEnchantItemList.java`, `mobius-ess:.../ExRequestFinishMultiEnchantScroll.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/enchant/challengepoint/ExRequestSetEnchantChallengePoint.java`, `mobius-ess:dist/game/data/EnchantChallengePoints.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                 | Server → client                                                                                                                                                     |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                                                                                                                                                                                                                                               | —                                                                                                                                                                   |
| High Five | —                                                                                                                                                                                                                                                                                               | —                                                                                                                                                                   |
| Essence   | `ExReqStartMultiEnchantScroll` `D0:022C`, `ExReqSetMultiEnchantItemList` `D0:0230`, `ExReqMultiEnchantItemList` `D0:0231`, `ExReqFinishMultiEnchantScroll` `D0:022E`, `ExReqViewMultiEnchantResult` `D0:022D`, `ExSetEnchantChallengePoint` `D0:0229`, `ExResetEnchantChallengePoint` `D0:022A` | `ExResMultiEnchantItemList` `FE:02EA`, `ExResSetMultiEnchantItemList` `FE:02E9`, `ExResSelectMultiEnchantScroll` `FE:02E8`, `ExEnchantChallengePointInfo` `FE:02E5` |

Notes: mass enchant requires a client with the multi-enchant window (Essence client UI).

### equipment.enchant-options — Enchant options

ID: `equipment.enchant-options` · Shared: **partial (HF+Ess)**

Armor / weapon enchanted to +N grants bonus skill options (up to three option ids per level), defined per item and
level.
Sub-features: option per enchant level · skill options · item-specific option table.

| Chronicle | Present | Builds (evidence)                             | Difference                                              |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | no `EnchantItemOptions` in any IL root (HF-era feature) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | `EnchantItemOptions.xml` + `EnchantOptions`             |
| Essence   | yes     | mobius-ess                                    | same pair                                               |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/EnchantItemOptionsData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/options/EnchantOptions.java`, `nx-l2:chronicles/high-five/datapack/data/EnchantItemOptions.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/EnchantItemOptionsData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/options/EnchantOptions.java`, `mobius-ess:dist/game/data/EnchantItemOptions.xml`

Packets

| Chronicle | Client → server | Server → client                                         |
| --------- | --------------- | ------------------------------------------------------- |
| Interlude | —               | —                                                       |
| High Five | —               | `SkillList` `5F`/`MagicSkillUse` `48` side effects only |
| Essence   | —               | `SkillList` `5F`, `MagicSkillUse` `48`                  |

Notes: Mobius IL's engine is a backport of HF yet has no `EnchantItemOptions` (verified by file name): the feature is
absent from IL, not just disabled.

### equipment.attributes — Item attribute (elements)

ID: `equipment.attributes` · Shared: **partial (HF+Ess)**

Insert an attribute stone / crystal / jewel / energy into S-grade weapon or armor to add an elemental attack or
resistance (fire, water, wind, earth, holy, dark); remove the attribute at an NPC; Essence adds attribute change
(swap one element to another) and an `AttributeSystem.ini`.
Sub-features: insert stone · remove at NPC · armor multi-attribute · limits by grade · attribute change (Ess).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | no element attribute on items (aCis has only `ElementType` for skills)                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | `ElementalAttributeData.xml`, `RequestExEnchantItemAttribute`, NPC `ReleaseAttribute`                     |
| Essence   | differs | mobius-ess                                    | + attribute change (`RequestChangeAttributeItem`, `SendChangeAttributeTargetItem`), `AttributeSystem.ini` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExEnchantItemAttribute.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ElementalAttributeData.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/ReleaseAttribute.java`, `nx-l2:chronicles/high-five/datapack/data/ElementalAttributeData.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestExEnchantItemAttribute.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/attributechange/*`, `mobius-ess:dist/game/config/AttributeSystem.ini`, `mobius-ess:dist/game/data/ElementalAttributeData.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/attributechange/RequestChangeAttributeCancel.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                       | Server → client                                                                                                                                                                                                                                                                                              |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | —                                                                                                                                                                                                                                                     | —                                                                                                                                                                                                                                                                                                            |
| High Five | `RequestExEnchantItemAttribute` `D0:35`, `RequestExRemoveItemAttribute` `D0:23`                                                                                                                                                                       | `ExAttributeEnchantResult` `FE:61`, `ExChooseInventoryAttributeItem` `FE:62`, `ExBaseAttributeCancelResult` `FE:75`, `ExShowBaseAttributeCancelWindow` `FE:74`                                                                                                                                               |
| Essence   | `RequestExEnchantItemAttribute` `D0:32`, `RequestExRemoveItemAttribute` `D0:23`, `SendChangeAttributeTargetItem` `D0:B1`, `RequestChangeAttributeItem` `D0:B2`, `RequestChangeAttributeCancel` `D0:B3`, `RequestExRequestBaseAttributeCancel` `D0:23` | `ExAttributeEnchantResult` `FE:62`, `ExChooseInventoryAttributeItem` `FE:63`, `ExBaseAttributeCancelResult` `FE:76`, `ExShowBaseAttributeCancelWindow` `FE:75`, `ExChangeAttributeInfo` `FE:0115`, `ExChangeAttributeItemList` `FE:0114`, `ExChangeAttributeOk` `FE:0116`, `ExChangeAttributeFail` `FE:0117` |

Mask bits: Essence — `ItemListType` in `AbstractItemPacket`: `ELEMENTAL_ATTRIBUTE` 0x02.

Notes: attribute-change packets exist only in the Essence protocol; Mobius HF has none.

Known fixes: `l2j-server@662df261` (2011-01-30, exploit-dupe) — exploit fix for attributing some armor parts; `mobius@3a49b3de` (2019-10-11, crash-npe) — Fixed RequestExRemoveItemAttribute NPE. Contributed by Sahar

### equipment.augmentation — Augmentation (Variation)

ID: `equipment.augmentation` · Shared: **partial**

Add a random stat bonus or active / passive skill to a weapon (and, in later versions, accessories) with a life stone
plus gemstone at an NPC; remove it for a fee; chances by life-stone level and gemstone count.
Sub-features: life stone + gemstone refine · cancel at NPC · chance tables · skill map · accessory augment (HF+) ·
option picking and probability view (Ess).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                               |
| --------- | ------- | --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `AugmentationData` builds stat / skill from tables; `RequestRefine` / `RequestRefineCancel`; weapon only; no accessory chances           |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | + `retailchances_accessory.xml`, `Augmentation`/`Options` model, `OnPlayerAugment` event                                                 |
| Essence   | differs | mobius-ess                                    | `Variation` UI: `VariationData` (`Variations.xml`), `VariationInstance`, `VariationFee`; open / close UI, option apply, probability list |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/AugmentationData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/options/Augmentation.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestRefine,RequestRefineCancel,RequestConfirmRefinerItem,AbstractRefinePacket}.java`, `nx-l2:chronicles/high-five/datapack/data/stats/augmentation/`
- `mobius-il:java/org/l2jmobius/gameserver/data/AugmentationData.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestRefine.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/AugmentationData.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/Augmentation.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/variation/*`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/VariationData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/options/Variation*.java`, `mobius-ess:dist/game/data/stats/augmentation/Variations.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                 | Server → client                                                                                                                                                                                                                                                                                                                        |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestConfirmTargetItem` `D0:29`, `RequestConfirmRefinerItem` `D0:2A`, `RequestConfirmGemStone` `D0:2B`, `RequestRefine` `D0:2C`, `RequestConfirmCancelItem` `D0:2D`, `RequestRefineCancel` `D0:2E`                                                                                                                                                                                           | `ExShowVariationMakeWindow` `FE:50`, `ExPutItemResultForVariationMake` `FE:52`, `ExPutIntensiveResultForVariationMake` `FE:53`, `ExVariationResult` `FE:55`, `ExShowVariationCancelWindow` `FE:51`, `ExPutItemResultForVariationCancel` `FE:56`, `ExVariationCancelResult` `FE:57`                                                     |
| High Five | `RequestConfirmTargetItem` `D0:26`, `RequestConfirmRefinerItem` `D0:27`, `RequestConfirmGemStone` `D0:28`, `RequestRefine` `D0:41`, `RequestConfirmCancelItem` `D0:42`, `RequestRefineCancel` `D0:43`                                                                                                                                                                                           | `ExShowVariationMakeWindow` `FE:51`, `ExPutItemResultForVariationMake` `FE:53`, `ExPutIntensiveResultForVariationMake` `FE:54`, `ExVariationResult` `FE:56`, `ExShowVariationCancelWindow` `FE:52`, `ExPutItemResultForVariationCancel` `FE:57`, `ExVariationCancelResult` `FE:58` (+ `ExPutCommissionResultForVariationMake` `FE:55`) |
| Essence   | `ExTryToPutItemForVariationMake` `D0:26`, `ExTryToPutIntensiveForVariationMake` `D0:27`, `ExTryToPutCommissionForVariationMake` `D0:28`, `ExTryToMakeVariation` `D0:3E`, `ExVariationOpenUi` `D0:0247`, `ExVariationCloseUi` `D0:0248`, `ExApplyVariationOption` `D0:0249`, `ExVariationProbList` `D0:02B1`, `ExTryToPutItemForVariationCancel` `D0:3F`, `ExClickVariationCancelButton` `D0:40` | `ExApplyVariationOption` `FE:0307`, `ExVariationProbList` `FE:0397`, `ExVariationResult` `FE:57` + the make / cancel windows                                                                                                                                                                                                           |

Mask bits: Essence — `ItemListType` in `AbstractItemPacket`: `AUGMENT_BONUS` 0x01.

Notes: the Mobius IL and Mobius HF packet sets are identical; the Interlude client behaviour (weapon only, no accessory
table) is confirmed by aCis / L2jFrozen, which have no `retailchances_accessory`.
Forks: none changed behaviour materially (Mobius-HF forks only re-added files after package renames).

Known fixes: `l2j-server@c9612bb5` (2007-05-31, missing-check) — Fix error on Augmentation request check

### equipment.ensoul — Ensoul (special abilities)

ID: `equipment.ensoul` · Shared: **chronicle-only**

Essence: insert soul crystals ("ensoul" stones) into free slots of weapons / armor for bonus options; extract them with
a fee; an ensoul window shows options and costs.
Sub-features: insert ensoul · extraction · stone and option data · slot rules.

| Chronicle | Present | Builds (evidence)          | Difference                                                       |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                |
| High Five | no      | nx-l2, l2j-server, sunrise | no ensoul in any public HF build                                 |
| Essence   | yes     | mobius-ess (09.2, 04.2)    | `EnsoulData`, `ensoul/*` packets, `item_special_abilities` table |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ensoul/RequestItemEnsoul.java`, `mobius-ess:.../RequestTryEnSoulExtraction.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ensoul/*`, `mobius-ess:dist/db_installer/sql/game/item_special_abilities.sql`

Packets

| Chronicle | Client → server                                            | Server → client                                                                                                                            |
| --------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | —                                                          | —                                                                                                                                          |
| High Five | —                                                          | —                                                                                                                                          |
| Essence   | `ExTryEnSoul` `D0:0108`, `ExTryEnSoulExtraction` `D0:0128` | `ExShowEnsoulWindow` `FE:0181`, `ExShowEnsoulExtractionWindow` `FE:01B5`, `ExEnsoulResult` `FE:0182`, `ExEnsoulExtractionResult` `FE:01B6` |

Notes: Essence ensoul is a Gracia-Final+ era feature (client side), not in HF/IL clients.

Known fixes: `mobius@42f26344` (2022-04-07, other) — Visual bug fix for RequestTryEnSoulExtraction.

### equipment.blessing — Item blessing

ID: `equipment.blessing` · Shared: **chronicle-only**

Essence: bless an item's option with a scroll — pick a target, view the probability, apply or cancel; the item gains a
blessed state with an extra option.
Sub-features: put item · probability list · enchant (bless) · cancel.

| Chronicle | Present | Builds (evidence)          | Difference                                                     |
| --------- | ------- | -------------------------- | -------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                              |
| High Five | no      | nx-l2, l2j-server, sunrise | —                                                              |
| Essence   | yes     | mobius-ess                 | `blessing/RequestBlessOption{PutItem,ProbList,Enchant,Cancel}` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/blessing/RequestBlessOptionPutItem.java`, `mobius-ess:.../RequestBlessOptionProbList.java`, `mobius-ess:.../RequestBlessOptionEnchant.java`, `mobius-ess:.../RequestBlessOptionCancel.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/blessing/ExOpenBlessOptionScroll.java`

Packets

| Chronicle | Client → server                                                                                                                        | Server → client                                                                                                                                                             |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                                                                                      | —                                                                                                                                                                           |
| High Five | —                                                                                                                                      | —                                                                                                                                                                           |
| Essence   | `ExBlessOptionPutItem` `D0:01C2`, `ExBlessOptionProbList` `D0:02CC`, `ExBlessOptionEnchant` `D0:01C3`, `ExBlessOptionCancel` `D0:01C4` | `ExOpenBlessOptionScroll` `FE:0273`, `ExBlessOptionPutItem` `FE:0274`, `ExBlessOptionProbList` `FE:03B3`, `ExBlessOptionEnchant` `FE:0275`, `ExBlessOptionCancel` `FE:0276` |

Mask bits: Essence — `ItemListType` in `AbstractItemPacket`: `BLESSED` 0x80.

Notes: the feature is present in Mobius Essence 09.2; presence in earlier Essence folders (04.2–08.3) was not checked.

### equipment.appearance — Appearance (shape shifting, transmog)

ID: `equipment.appearance` · Shared: **partial**

Show the look of another item on the equipped one. Essence has the official Shape Shifting flow (appearance stones,
target / extraction item, cancel). Mobius ships a custom Transmog NPC in all three versions. Other engines have
Dress-me-style costumes.
Sub-features: official shape shifting (Ess) · Transmog NPC (Mobius custom) · appearance stones / data · costume sets
(forks).

| Chronicle | Present | Builds (evidence)                                                                                            | Difference                                                                                                                                |
| --------- | ------- | ------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il (custom Transmog), acis fork (`Skins.xml` item skins)                                              | no official client feature; only custom NPC / script (`custom/Transmog`, `character_transmogs.sql`)                                       |
| High Five | differs | nx-l2 (custom Transmog), sunrise (Dress-me), freejia-highfive-server (`Dress*Holder`), l2journey (`dressme`) | same custom Transmog; forks add Dress-me costume engines (armor / cloak / hat / shield / weapon)                                          |
| Essence   | yes     | mobius-ess                                                                                                   | official `appearance/*` packets, `AppearanceItemData`, `AppearanceStones.xml`, `ItemAppearanceTaskManager`; Transmog custom still present |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/Transmog/Transmog.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/TransmogConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/appearance/PlayerAppearance.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/Transmog.ini`, `nx-l2:chronicles/high-five/datapack/sql/character_transmogs.sql`
- `mobius-il:dist/game/data/scripts/custom/Transmog/Transmog.java`, `mobius-il:dist/game/config/Custom/Transmog.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/appearance/RequestShapeShiftingItem.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/AppearanceItemData.java`, `mobius-ess:dist/game/data/AppearanceStones.xml`, `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/ItemAppearanceTaskManager.java`

Packets

| Chronicle | Client → server                                                                                                                                                 | Server → client                                                                                                                                                              |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | — (custom NPC dialog)                                                                                                                                           | `CharInfo` `03`/`UserInfo` `04` with replaced item ids                                                                                                                       |
| High Five | —                                                                                                                                                               | `CharInfo` `31`, `UserInfo` `32`                                                                                                                                             |
| Essence   | `ExRequestShapeShifting` `D0:C8`, `ExTryToPutShapeShiftingTargetItem` `D0:C5`, `ExTryToPutShapeShiftingExtractionItem` `D0:C6`, `ExCancelShapeShifting` `D0:C7` | `ExChooseShapeShiftingItem` `FE:012A`, `ExPutShapeShiftingTargetItemResult` `FE:012B`, `ExPutShapeShiftingExtractionItemResult` `FE:012C`, `ExShapeShiftingResult` `FE:012D` |

Mask bits: Essence — `ItemListType` in `AbstractItemPacket`: `VISUAL_ID` 0x04; `UserInfoType` in `UserInfo`: `APPAREANCE` 0x06.

Notes: forks — Sunrise Purity: `dist/game/data/xml/sunrise/dressme/{armor,cloak,hat,shield,weapon}.xml`; JBlueHeart: `gr/sr/dressmeEngine/DressMeHandler.java`,
`DressMe*Data.java`; freejia (L2PS): `l2f/gameserver/data/xml/holder/Dress{Armor,Cloak,Shield,Weapon}Holder.java`; L2Journey: `dist/game/data/dressme/{armor,cloak}.xml`;
aCis CustomServer: `aCis_datapack/data/xml/items/Skins.xml` + `Dev/ItemSkins/Skin*.java` (50 files). None of these is in Mobius.

### equipment.compound — Compound (combination)

ID: `equipment.compound` · Shared: **chronicle-only**

Essence: combine two items of the same kind (jewels, gems) in a two-slot window with a probability list; result is
success item or failure item (`ON_SUCCESS` / `ON_FAILURE` rewards). The same flow is reused for henna combination and
relics.
Sub-features: two-item combine window · probability list · success / failure reward.

| Chronicle | Present | Builds (evidence)          | Difference                                                                      |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                               |
| High Five | no      | nx-l2, l2j-server, sunrise | —                                                                               |
| Essence   | yes     | mobius-ess                 | `compound/RequestNewEnchant*`, `CombinationItems.xml`, `CombinationItem*` types |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/compound/RequestNewEnchantTry.java`, `mobius-ess:.../RequestNewEnchantPushOne.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/combination/{CombinationItem,CombinationItemReward,CombinationItemType}.java`, `mobius-ess:dist/game/data/CombinationItems.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                               | Server → client                                                                                                                                                                                                                                                                                                                                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                                                                                                                                                                                                                                                                                                             | —                                                                                                                                                                                                                                                                                                                                                                                                                               |
| High Five | —                                                                                                                                                                                                                                                                                                                                                             | —                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Essence   | `RequestNewEnchantPushOne` `D0:F4`, `...PushTwo`, `...RemoveOne`, `...RemoveTwo`, `...RetryToPutItems`, `...Try`, `...Close`, `ExCombinationProbList` `D0:02B4`, `RequestNewEnchantClose` `D0:F8`, `RequestNewEnchantRemoveOne` `D0:F5`, `RequestNewEnchantRetryToPutItems` `D0:FA`, `RequestNewEnchantPushTwo` `D0:F6`, `RequestNewEnchantRemoveTwo` `D0:F7` | `ExEnchantOneOK/Fail`, `ExEnchantTwoOK/Fail`, `ExEnchantSucess` `FE:0171`, `ExEnchantFail` `FE:0172`, `ExCombinationProbList` `FE:039A`, `ExEnchantOneFail` `FE:016A`, `ExEnchantOneRemoveFail` `FE:016C`, `ExEnchantOneRemoveOK` `FE:016B`, `ExEnchantRetryToPutItemFail` `FE:0174`, `ExEnchantRetryToPutItemOk` `FE:0173`, `ExEnchantTwoFail` `FE:016E`, `ExEnchantTwoRemoveFail` `FE:0170`, `ExEnchantTwoRemoveOK` `FE:016F` |

Notes: the packet names say "NewEnchant" but the feature is item combination, hence `compound`.

Known fixes: `mobius@4d843dc0` (2022-12-20, other) — Fixed click on reset button after compound failed. Contributed by Fakee.

### equipment.upgrade — Equipment upgrade

ID: `equipment.upgrade` · Shared: **chronicle-only**

Essence: upgrade a piece of equipment into the next-tier item at an upgrade window, consuming materials; "normal" and
"rare" systems with probability list.
Sub-features: normal upgrade · rare upgrade · probability list.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                     |
| --------- | ------- | -------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                                                              |
| High Five | no      | nx-l2, l2j-server, sunrise | —                                                                                                              |
| Essence   | yes     | mobius-ess                 | `equipmentupgrade/*`, `equipmentupgradenormal/*`, `EquipmentUpgradeData.xml`, `EquipmentUpgradeNormalData.xml` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/equipmentupgrade/RequestUpgradeSystemResult.java`, `mobius-ess:.../RequestUpgradeSystemProbList.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/equipmentupgradenormal/ExUpgradeSystemNormalRequest.java`, `mobius-ess:dist/game/data/EquipmentUpgradeData.xml`, `mobius-ess:dist/game/data/EquipmentUpgradeNormalData.xml`

Packets

| Chronicle | Client → server                                                                                                   | Server → client                                                                                                                                                                         |
| --------- | ----------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                                                                 | —                                                                                                                                                                                       |
| High Five | —                                                                                                                 | —                                                                                                                                                                                       |
| Essence   | `ExUpgradeSystemRequest` `D0:0136`, `ExUpgradeSystemNormalRequest` `D0:0161`, `ExUpgradeSystemProbList` `D0:02B3` | `ExShowUpgradeSystem` `FE:01CE`, `ExShowUpgradeSystemNormal` `FE:0204`, `ExUpgradeSystemResult` `FE:01CF`, `ExUpgradeSystemNormalResult` `FE:0205`, `ExUpgradeSystemProbList` `FE:0399` |

Notes: the `ExUpgradeSystemProbList` request / reply is already in Mobius Essence 09.2 (`RequestUpgradeSystemProbList`).

### equipment.henna — Henna (dyes)

ID: `equipment.henna` · Shared: **partial**

Draw symbols on a character: dye item + symbol maker NPC, stat bonuses (+1/-1 pairs), up to N slots by level / class,
removal for a fee. Essence replaces it with a potential system: slots, open slots with a probability list, enchant /
reset, select, compose two dyes.
Sub-features: draw / remove · symbol maker NPC · slot count · stat bonuses · compose (Ess) · potential and enchant (Ess).

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                                                           |
| --------- | ------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server | 3 slots; `Henna` / `HennaData` (aCis `HennaList`, `HennaType`); `FuncHenna`                                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                      | same data and flow (`HennaData` 0 diff vs Mobius IL), `HennaUnequipInfo`/`HennaUnequipList` renamed packets                          |
| Essence   | differs | mobius-ess                                                | `entity/item/henna/{Henna,HennaPoten,DyePotential,DyePotentialFee,CombinationHenna}`; `newhenna/*` packets; classic packets retained |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/HennaData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/Henna.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestHennaItemList,RequestHennaItemInfo,RequestHennaEquip,RequestHennaRemoveList,RequestHennaItemRemoveInfo,RequestHennaRemove}.java`, `nx-l2:chronicles/high-five/datapack/data/stats/hennaList.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/HennaData.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/functions/formulas/FuncHenna.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/HennaData.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/container/player/HennaList.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/newhenna/RequestNewHennaEquip.java`, `mobius-ess:.../RequestNewHennaCompose.java`, `mobius-ess:.../RequestNewHennaPotenEnchant.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/henna/*`
- Also: scheduled task `HennaDurationTask` (Mobius Essence, Ess): `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/HennaDurationTask.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                          | Server → client                                                                                                                           |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestHennaItemList` `BA`, `RequestHennaItemInfo` `BB`, `RequestHennaEquip` `BC`, `RequestHennaRemoveList` `BD`, `RequestHennaItemRemoveInfo` `BE`, `RequestHennaRemove` `BF`                                                                                                                                                                                                                          | `HennaEquipList` `E2`, `HennaItemInfo` `E3`, `HennaInfo` `E4`, `HennaRemoveList` `E5`, `HennaItemRemoveInfo` `E6`, `GMViewHennaInfo` `EA` |
| High Five | `RequestHennaItemList` `C3`, `RequestHennaItemInfo` `C4`, `RequestHennaEquip` `6F`, `RequestHennaRemoveList` `70`, `RequestHennaItemRemoveInfo` `71`, `RequestHennaRemove` `72`                                                                                                                                                                                                                          | `HennaEquipList` `EE`, `HennaItemInfo` `E4`, `HennaInfo` `E5`, `HennaUnequipList` `E6`, `HennaUnequipInfo` `E7`, `GMHennaInfo` `F0`       |
| Essence   | `ExNewHennaList` `D0:0219`, `ExNewHennaEquip` `D0:021A`, `ExNewHennaUnequip` `D0:021B`, `ExNewHennaCompose` `D0:021E`, `ExNewHennaComposeProbList` `D0:02B0`, `ExNewHennaPotenSelect` `D0:021C`, `ExNewHennaPotenOpenslot` `D0:02B9`, `ExNewHennaPotenOpenslotProbInfo` `D0:02B8`, `ExNewHennaPotenEnchant` `D0:021D`, `ExNewHennaPotenEnchantReset` `D0:0281`, `ExHennaUnequipInfo` `D0:0253` + classic | `HennaEquipList` `EE`, `HennaItemInfo` `E4`, `HennaInfo` `E5`, `HennaUnequipList` `E6`, `HennaUnequipInfo` `E7`, `GMHennaInfo` `F0`       |

Notes: forks adding henna: freejia (L2PS) 17 added henna files, L2jOrion / aCis forks add extra henna tables — no new behaviour found.

Known fixes: `mobius@9c00ceca` (2026-06-07, exploit-dupe) — Improved henna compose validation and close free-compose exploit; `l2j-server@94659d7c` (2008-02-15, exploit-dupe) — Fix for henna cheat, thanks House. Also minor cleaning by Zhyla; `hl4p3x-l2-hf-hardcore@09fc160b` (2018-08-29, wrong-formula-value) — Dye display, calculation and removal fix.

### equipment.talismans — Talismans and bracelets

ID: `equipment.talismans` · Shared: **partial (HF+Ess)**

Bracelets open talisman slots (up to 6 deco slots); talismans are limited-life items worn in those slots giving passive
skills; the `TalismanSlot` effect adds slots.
Sub-features: bracelet slots · talisman slots effect · talisman lifetime.

| Chronicle | Present | Builds (evidence)                                   | Difference                                                       |
| --------- | ------- | --------------------------------------------------- | ---------------------------------------------------------------- |
| Interlude | no      | mobius-il (17 slots, no bracelets), acis, l2jfrozen | none; no bracelet / deco slots                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                | `PAPERDOLL_DECO1..6`, `TalismanSlot` effect                      |
| Essence   | yes     | mobius-ess                                          | `TalismanSlot` effect; deco slots 22–27 inside the 59-slot table |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/TalismanSlot.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/Inventory.java` (`PAPERDOLL_DECO1..6`)
- `mobius-ess:dist/game/data/scripts/handlers/skill/effects/TalismanSlot.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/Inventory.java`

Packets

| Chronicle | Client → server | Server → client                                      |
| --------- | --------------- | ---------------------------------------------------- |
| Interlude | —               | —                                                    |
| High Five | `UseItem` `19`  | `InventoryUpdate` `21`, `UserInfo` `32` (deco slots) |
| Essence   | `UseItem` `19`  | `InventoryUpdate` `21`, `UserInfo` `32`              |

Notes: Mobius IL has no `TalismanSlot` class and no deco slots (file-name search returns nothing).

### equipment.agathions — Agathions

ID: `equipment.agathions` · Shared: **partial (HF+Ess)**

Agathion pets as equipped items: use the item to summon a cosmetic pet that grants a bonus skill; slots by 5 agathion
slots in Essence; energy points limit time.
Sub-features: summon / unsummon effects · agathion condition · agathion data (Ess).

| Chronicle | Present | Builds (evidence)                                                              | Difference                                                                                                   |
| --------- | ------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | acis, l2jfrozen, l2jbrasil, hellas; Mobius IL has leftover effect classes only | no agathion slots (17-slot table), no items; `SummonAgathion` classes in Mobius IL are inherited HF code     |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                           | effect handlers `SummonAgathion` / `UnsummonAgathion`, `ConditionPlayerAgathionId`; no dedicated slots table |
| Essence   | yes     | mobius-ess                                                                     | `AgathionData` (`AgathionData.xml`), 5 agathion paperdoll slots, `OnPlayerSummonAgathion` event              |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/{SummonAgathion,UnsummonAgathion}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerAgathionId.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/AgathionData.java`, `mobius-ess:dist/game/data/AgathionData.xml`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/holders/AgathionSkillHolder.java`

Packets

| Chronicle | Client → server | Server → client                                   |
| --------- | --------------- | ------------------------------------------------- |
| Interlude | —               | —                                                 |
| High Five | `UseItem` `19`  | `CharInfo` `31`/`UserInfo` `32`                   |
| Essence   | `UseItem` `19`  | `UserInfo` `32`/`CharInfo` `31` with agathion ids |

Notes: forks — L2jMega deraldinho adds `projeto/game/data/xml/items/Agathions.xml` and `npcs/Agathion.xml` (aCis Interlude); L2Journey and
`finfan222` add agathion data files for HF.

### equipment.shadow-weapons — Shadow weapons

ID: `equipment.shadow-weapons` · Shared: **partial (IL+HF)**

Exchange of D / C-grade weapons for their shadow versions via coupons at a merchant NPC; the shadow versions have the
mana-timer behaviour of `item.timed-items`.
Sub-features: exchange coupons (D, C) · NPC dialog · shadow item data.

| Chronicle | Present | Builds (evidence)                                                 | Difference                                                       |
| --------- | ------- | ----------------------------------------------------------------- | ---------------------------------------------------------------- |
| Interlude | yes     | mobius-il (custom script), acis (shadow items in data), l2jfrozen | in cores: shadow items sold at NPC; Mobius ships a custom script |
| High Five | yes     | nx-l2 (`custom/ShadowWeapons`), l2j-server                        | coupons 8869 (D), 8870 (C)                                       |
| Essence   | no      | mobius-ess                                                        | not present (no script, no Shadow item data)                     |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/ShadowWeapons/ShadowWeapons.java`
- `mobius-il:dist/game/data/scripts/custom/ShadowWeapons/ShadowWeapons.java`

Packets

| Chronicle | Client → server | Server → client       |
| --------- | --------------- | --------------------- |
| Interlude | — (NPC dialog)  | `NpcHtmlMessage` `0F` |
| High Five | —               | `NpcHtmlMessage` `19` |
| Essence   | —               | —                     |

Notes: matched coupon ids come from the HF script (`hasQuestItems(player, 8869 / 8870)`).

Known fixes: `mobius@bd223d02` (2020-06-23, other) — Fixed shadow weapon multisell bypass.

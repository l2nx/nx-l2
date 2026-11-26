# Premium and shop

Paid and quasi-paid monetisation: premium accounts, Item Mall, VIP, services.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### premium.account — Premium account

ID: `premium.account` · Shared: **partial**

Paid (or granted) account status with rate multipliers for exp, SP, drop and spoil chance/amount, quest rewards, per
item overrides; account-wide or per character; expiry date stored in the database; admin commands and a voiced
command to check the status. Mobius custom (`PremiumSystem.ini`). Sub-features: rate multipliers · expiry ·
account-wide flag · admin grant · premium-only options (PC Café, fishing).

| Chronicle | Present | Builds (evidence)                                                     | Difference                                                                                                                 |
| --------- | ------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (custom), acis-180-devastated, L2jFrozen-family customs     | no `ExBrPremiumState` packet; 24 config keys                                                                               |
| High Five | yes     | nx-l2, l2j-server, sunrise (`PremiumService.ini`), eternity, advanced | baseline: `PremiumManager` (197 lines), table `account_premium`, `EnablePremiumSystem = False`, `ExBrPremiumState`         |
| Essence   | yes     | mobius-ess                                                            | `PremiumManager` (238 lines); two more keys (`PcCafeOnlyVip`, `PremiumOnlyFishing`), VIP-aware, `ExPremiumManagerShowHtml` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/PremiumManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/PremiumSystemConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Premium.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminPremium.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/PremiumSystem.ini`, `nx-l2:chronicles/high-five/datapack/sql/account_premium.sql`
- `mobius-il:java/org/l2jmobius/gameserver/managers/PremiumManager.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/voiced/Premium.java`, `mobius-il:dist/game/config/Custom/PremiumSystem.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/PremiumManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExPremiumManagerShowHtml.java`

Packets

| Chronicle | Client → server | Server → client                                                  |
| --------- | --------------- | ---------------------------------------------------------------- |
| Interlude | —               | — (the status is shown by chat and HTML)                         |
| High Five | —               | `ExBrPremiumState` `FE:D9`                                       |
| Essence   | —               | `ExBrPremiumState` `FE:DB`, `ExPremiumManagerShowHtml` `FE:021A` |

Notes: the multiplier keys are `PremiumRateXp`, `PremiumRateSp`, `PremiumRateDropChance/Amount`,
`PremiumRateSpoilChance/Amount`, `PremiumRateQuestXp/Sp`, `PremiumRateDropChanceByItemId`,
`PremiumRateDropAmountByItemId` — identical in HF and IL.

Known fixes: `mobius@96440706` (2018-05-02, wrong-formula-value) — Corrected .premium command bonus calculation. Contributed by Stayway

### premium.item-mall — Item Mall

ID: `premium.item-mall` · Shared: **partial (HF+Ess)**

In-client shop (BR product window): categories, product info, recent products, buying products for a prime/game
points balance (or an item), gifts, delivery as "premium items" collected from a list.
Sub-features: product list · product info · buy · recent list · gift (Essence) · premium item box · points balance.

| Chronicle | Present | Builds (evidence)                                     | Difference                                                                                                                                                                 |
| --------- | ------- | ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen                            | not in the Interlude client                                                                                                                                                |
| High Five | yes     | nx-l2, sunrise (`ItemMall.ini`), eternity, lineage2ts | baseline: `PrimeShop.xml` with 227 products (`points`, `item`, `count`, category), `EnablePrimeShop = False`, `PrimeShopItemId = -1` (balance is the player's game points) |
| Essence   | differs | mobius-ess, l2emu-ess                                 | `PrimeShopData` with group objects (40 entries), a prime-points balance, gift request `RequestBRPresentBuyProduct`, VIP points added on purchase (`PrimeAffectPoints`)     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/PrimeShopData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/holders/PrimeShopProductHolder.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestBrProductList,RequestBrProductInfo,RequestBrBuyProduct,RequestBrRecentProductList,RequestBrGamePoint,RequestWithDrawPremiumItem}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/ReceivePremium.java`, `nx-l2:chronicles/high-five/datapack/data/PrimeShop.xml`, `nx-l2:chronicles/high-five/datapack/sql/character_premium_items.sql`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/PrimeShopData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/primeshop/{PrimeShopGroup,PrimeShopItem}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/primeshop/*.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/request/PrimeShopRequest.java`, `mobius-ess:dist/game/data/PrimeShop.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                               | Server → client                                                                                                                                                                                                                       |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | `RequestBrGamePoint` `D0:89`, `RequestBrProductList` `D0:8A`, `RequestBrProductInfo` `D0:8B`, `RequestBrBuyProduct` `D0:8C`, `RequestBrRecentProductList` `D0:8D`, `RequestWithDrawPremiumItem` `D0:52`                                       | `ExBrGamePoint` `FE:D5`, `ExBrProductList` `FE:D6`, `ExBrProductInfo` `FE:D7`, `ExBrBuyProduct` `FE:D8`, `ExBrRecentProductList` `FE:DC`, `ExGetPremiumItemList` `FE:86`, `ExNotifyPremiumItem` `FE:85`, `ExBrPremiumState` `FE:D9`   |
| Essence   | `RequestBRProductList` `D0:88`, `RequestBRProductInfo` `D0:89`, `RequestBRBuyProduct` `D0:8A`, `RequestBRPresentBuyProduct` `D0:B4`, `RequestBRRecentProductList` `D0:8B`, `RequestWithDrawPremiumItem` `D0:4F`, `RequestBRVersion` `D0:024B` | `ExBRProductList` `FE:D8`, `ExBRProductInfo` `FE:D9`, `ExBRBuyProduct` `FE:DA`, `ExGetPremiumItemList` `FE:87`, `ExNotifyPremiumItem` `FE:86`, `ExBrPremiumState` `FE:DB`, `ExBRNewIconCashBtnWnd` `FE:013B`, `ExBRVersion` `FE:0309` |

Notes: Mobius HF already ships `RequestBrBuyProduct`; L2J Server has the BR extra packets but not the product
window. Essence lists VIP product list and "interested product" packets in its opcode table without handlers.

### premium.vip — VIP

ID: `premium.vip` · Shared: **chronicle-only**

Essence VIP levels (1–7): VIP points accumulate with purchases (item mall, limit shop), each level gives bonuses
(drop, exp, shop discounts, a gift), a daily lucky game, VIP-only features, an attendance calendar (see `reward.*`).

| Chronicle | Present | Builds (evidence)                                                                            | Difference                                                                                                                                                     |
| --------- | ------- | -------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il; forks have only per-build "VIP" customs (Frozen `VIP.java`, Hellas `VipTeleport`) | unrelated to the Essence mechanic                                                                                                                              |
| High Five | no      | nx-l2; L2_Advanced has a different "advipsystem" custom                                      | no retail VIP                                                                                                                                                  |
| Essence   | yes     | mobius-ess, l2emu-ess                                                                        | baseline: `VipSystem.ini` (`VipEnabled = False`, `MaxVipLevel = 7`, `PrimeAffectPoints`, `LShopAffectPoints`), `Vip.xml` with 11 entries, skill effect `VipUp` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/vip/{VipManager,VipInfo}.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/VipData.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/VipSystemConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/vip/{ExRequestVipInfo,RequestVipLuckGameInfo}.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/VipUp.java`, `mobius-ess:dist/game/data/Vip.xml`, `mobius-ess:dist/game/config/Custom/VipSystem.ini`

Packets

| Chronicle | Client → server                                                                                                                                                                                                   | Server → client                                                                                                                                                                                                                               |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExRequestVipInfo` `D0:010E`, `RequestVipLuckGameInfo` `D0:010B`, `RequestVipAttendanceCheck` `D0:0107`/`D0:027E`, `RequestVipAttendanceItemList` `D0:0106`/`D0:027D`, `RequestVipAttendanceItemReward` `D0:027F` | `ReceiveVipInfo` `FE:018A`, `ReceiveVipLuckyGameInfo` `FE:0187`, `ExVipAttendanceCheck` `FE:034E`, `ExVipAttendanceItemList` `FE:017F`, `ExVipAttendanceList` `FE:034D`, `ExVipAttendanceNotify` `FE:0350`, `ExVipAttendanceReward` `FE:034F` |

Notes: the attendance packets belong to `reward.*`; they are listed here because they carry the VIP name.

Known fixes: `mobius@f52b1e1b` (2021-05-09, crash-npe) — Fixed probable VipManager NPE.

### premium.hunt-pass — Hunt Pass

ID: `premium.hunt-pass` · Shared: **chronicle-only**

Essence seasonal pass: points for steps, a free and a premium reward track, rewards claimable one by one or all at once,
premium track bought for L-Coin, Sayha's support info.

| Chronicle | Present | Builds (evidence)     | Difference                                                                                                                                       |
| --------- | ------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | mobius-il             | —                                                                                                                                                |
| High Five | no      | nx-l2                 | —                                                                                                                                                |
| Essence   | yes     | mobius-ess, l2emu-ess | baseline: `HuntPass.ini` (`EnabledHuntPass = False`, `PremiumItemId = 91663`, `PremiumItemCount = 3600`, `PointsForStep = 2400`), `HuntPass.xml` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/HuntPassData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/player/HuntPass.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/HuntPassConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/huntpass/*.java`, `mobius-ess:dist/game/data/HuntPass.xml`, `mobius-ess:dist/game/config/HuntPass.ini`

Packets

| Chronicle | Client → server                                                                                                                                 | Server → client                                                                                 |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Essence   | `RequestHuntPassInfo` `D0:0223`, `RequestHuntPassReward` `D0:0224`, `RequestHuntPassRewardAll` `D0:0225`, `RequestHuntPassBuyPremium` `D0:0226` | `HuntPassInfo` `FE:02E1`, `HuntPassSimpleInfo` `FE:02E0`, `HuntPassSayhasSupportInfo` `FE:02E2` |

Notes: the opcode table names these packets `L2Pass*`.

Known fixes: `mobius@6a5f394e` (2022-12-26, other) — Fixed reseting last Hunt Pass reward. Contributed by Fakee.

### premium.paid-services — Paid services

ID: `premium.paid-services` · Shared: **partial (IL+HF)**

Services sold for donation currency by custom shops: change of name/sex/colour, noblesse, clan penalty removal,
item sale, skill packs. Not a Mobius feature; every fork implements its own.

| Chronicle | Present | Builds (evidence)                                                                                                             | Difference                      |
| --------- | ------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| Interlude | differs | l2jfrozen (`AdminDonator`), hellas (`L2DonateInstance`), mobius-c6 (`AdminDonator`)                                           | custom only; Mobius IL has none |
| High Five | differs | sunrise (`CommunityDonate.ini`, `CommunityServices.ini`, `Donate.ini`), advanced (`donation.xml`, `DonationStore.properties`) | custom only; Mobius HF has none |
| Essence   | no      | mobius-ess                                                                                                                    | —                               |

Entry points

- `sunrise:L2J_SunriseProject_Core/dist/game/config/sunrise/{CommunityServices,CommunityDonate}.ini`
- `advanced:dist/gameserver/data/donation/donation.xml`, `advanced:dist/gameserver/config/services/DonationStore.properties`

Packets

| Chronicle | Client → server     | Server → client         |
| --------- | ------------------- | ----------------------- |
| High Five | — (Community Board) | `ShowBoard` `7B` (HTML) |

Notes: the mechanics are a shop over an HTML window; no protocol footprint.

### premium.vote-reward — Vote reward

ID: `premium.vote-reward` · Shared: **partial (IL+HF)**

Reward for voting on top-server lists: a periodic poll of the list API (HopZone, TopZone, etc.), a per-player delay,
item rewards. Not a Mobius feature; many forks carry a variant.

| Chronicle | Present | Builds (evidence)                                                                                                               | Difference                      |
| --------- | ------- | ------------------------------------------------------------------------------------------------------------------------------- | ------------------------------- |
| Interlude | differs | l2jfrozen (`AutoVoteRewardHandler`), hellas (`VoteRewardHopzone`, `VoteRewardTopzone`), acis-180-devastated (`vote.properties`) | custom only; Mobius IL has none |
| High Five | differs | eternity (`vote.ini`, `Vote.java`), advanced (`vote.xml`), lineage2ts (`VoteSystemInfo`)                                        | custom only; Mobius HF has none |
| Essence   | no      | mobius-ess                                                                                                                      | —                               |

Entry points

- `eternity:core/dist/game/config/mods/vote.ini`, `eternity:core/java/l2e/gameserver/handler/voicedcommandhandlers/{Vote,VoteReward}.java`
- `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/handler/AutoVoteRewardHandler.java`

Packets

| Chronicle | Client → server    | Server → client    |
| --------- | ------------------ | ------------------ |
| High Five | — (voiced command) | `CreatureSay` `4A` |

Notes: the in-client "recommendation" vote (`VoteSociality`, `RequestNewVoteSystem`) is a different feature
(`social.*`).

Known fixes: `l2jfrozen@8646c17e` (2011-05-10, crash-npe) — Little fix to AutoVoteReward to fix some log exceptions

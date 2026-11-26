# Mail

Player and system letters with attachments.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### mail.send — Send mail

ID: `mail.send` · Shared: **partial (HF+Ess)**

Player sends a letter to another character: subject (128), body (512), receiver name (16), up to 8 attachments,
optional COD (payment on receipt, requires attachments); fee 100 adena + 1000 per attachment slot; undelivered mail
returns to the sender when it expires.
Sub-features: attachments · COD · send fee · block-list check · inbox/outbox limits · flood protection · jail rule.

| Chronicle | Present | Builds (evidence)                              | Difference                                                                                                                                                                                   |
| --------- | ------- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen                     | only the Community Board mail (`MailBBSManager`, `ExMailArrived`); no in-game postbox in the Interlude client                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, advanced | baseline: attachments only in a peace zone (`isInsideZone(PEACE)` check), `AllowMail`, `AllowAttachments`                                                                                    |
| Essence   | differs | mobius-ess, l2emu-ess                          | same limits and fee; instead of the peace-zone rule it refuses attachments in combat or when dead, refuses fake players, adds `RequestPostItemFee` / `ExPostItemFee` and `ExUnReadMailCount` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestSendPost,RequestPostItemList}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/MailManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/holders/MailMessage.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/Mail.java`, `nx-l2:chronicles/high-five/datapack/config/General.ini` (`AllowMail`, `AllowAttachments`)
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{RequestSendPost,RequestPostItemFee,RequestPostItemList}.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/MailManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/holders/MailMessage.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                     | Server → client                                                                                                                                                                                                                                        |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| High Five | `RequestSendPost` `D0:66`, `RequestPostItemList` `D0:65`, `RequestSentPostList` `D0:6C`, `RequestSentPost` `D0:6E`, `RequestExFriendListForPostBox` `D0:87`, `RequestExAddPostFriendForPostBox` `D0:84`, `RequestExDeletePostFriendForPostBox` `D0:85`, `RequestExShowPostFriendForPostBox` `D0:86` | `ExReplyPostItemList` `FE:B2`, `ExNoticePostSent` `FE:B4`, `ExShowSentPostList` `FE:AC`, `ExReplySentPost` `FE:AD`, `ExNoticePostArrived` `FE:A9`, `ExChangePostState` `FE:B3`, `ExConfirmAddingPostFriend` `FE:D2`, `ExReceiveShowPostFriend` `FE:D3` |
| Essence   | `RequestSendPost` `D0:63`, `RequestPostItemList` `D0:62`, `RequestPostItemFee` `D0:6D`, `RequestSentPostList` `D0:69`, `RequestSentPost` `D0:6B`                                                                                                                                                    | `ExReplyPostItemList` `FE:B3`, `ExPostItemFee` `FE:B6`, `ExNoticePostSent` `FE:B5`, `ExShowSentPostList` `FE:AD`, `ExReplySentPost` `FE:AE`, `ExUnReadMailCount` `FE:013D`, `ExNoticePostArrived` `FE:AA`, `ExChangePostState` `FE:B4`                 |

Notes: the post-friend list packets are in the High Five opcode table; nx-l2 has no handler classes for them (they
appear in the protocol catalog only). Interlude confirmed against aCis (`Mail.java` is the Community Board mail) and
L2jFrozen (`ExMailArrived`, `Post`, no postbox).

Known fixes: `l2j-server@663b9d70` (2010-01-22, concurrency) — Sorry Dr.House for wrong report it was corrupted db record, not concurrency issue, adding check to prevent

### mail.receive — Receive mail

ID: `mail.receive` · Shared: **partial (HF+Ess)**

Inbox and outbox lists, reading a letter, taking attachments (single or all), rejecting attachments (returns the
letter), paying COD, deleting letters, cancelling a sent letter with attachments.
Sub-features: inbox/outbox · read · take attachments · COD payment · reject · delete · cancel.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                                                                             |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | mobius-il, acis, l2jfrozen           | —                                                                                                                                                                                                      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                                                                                                                                                               |
| Essence   | differs | mobius-ess, l2emu-ess                | same handlers plus `MailType` (11 kinds: regular, news, NPC, birthday, commission sold/returned, mentor, prime-shop gift, purge reward, Balok ranking reward, pledge donation) and `ExUnReadMailCount` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestReceivedPostList,RequestReceivedPost,RequestPostAttachment,RequestRejectPostAttachment,RequestDeleteReceivedPost,RequestDeleteSentPost,RequestCancelPostAttachment}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/MailManager.java`
- same class names under `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets`, plus `mobius-ess:java/org/l2jmobius/gameserver/network/enums/MailType.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                       | Server → client                                                                                                                                            |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | `RequestReceivedPostList` `D0:67`, `RequestReceivedPost` `D0:69`, `RequestPostAttachment` `D0:6A`, `RequestRejectPostAttachment` `D0:6B`, `RequestDeleteReceivedPost` `D0:68`, `RequestDeleteSentPost` `D0:6D`, `RequestCancelPostAttachment` `D0:6F` | `ExShowReceivedPostList` `FE:AA`, `ExReplyReceivedPost` `FE:AB`, `ExChangePostState` `FE:B3`, `ExNoticePostArrived` `FE:A9`                                |
| Essence   | `RequestReceivedPostList` `D0:64`, `RequestReceivedPost` `D0:66`, `RequestPostAttachment` `D0:67`, `RequestRejectPostAttachment` `D0:68`, `RequestDeleteReceivedPost` `D0:65`, `RequestDeleteSentPost` `D0:6A`, `RequestCancelPostAttachment` `D0:6C` | `ExShowReceivedPostList` `FE:AB`, `ExReplyReceivedPost` `FE:AC`, `ExChangePostState` `FE:B4`, `ExNoticePostArrived` `FE:AA`, `ExUnReadMailCount` `FE:013D` |

Notes: nx-l2 names the sender kind `MessageSenderType` (`S:network/enums/`); the Essence tree uses `MailType`.

Known fixes: `l2j-server@b534a1f8` (2011-06-12, other) — Memory Leak Fix (by JIV)

### mail.return-expire — Return and expiry

ID: `mail.return-expire` · Shared: **partial (HF+Ess)**

Letters expire after 15 days (360 hours), COD letters after 12 hours; on expiry a letter with attachments returns to
the sender (marked returned) and is deleted afterwards; deletion is scheduled by a task manager.
Sub-features: expiry timer · COD timeout · return to sender · deferred deletion.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                         |
| --------- | ------- | ------------------------------------ | -------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen           | —                                                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `EXPIRATION = 360`, `COD_EXPIRATION = 12` in `MailMessage`; `MessageDeletionTaskManager` |
| Essence   | yes     | mobius-ess                           | same constants, same task                                                                          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/MessageDeletionTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/holders/MailMessage.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/MailManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/MessageDeletionTaskManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/holders/MailMessage.java`

Packets

| Chronicle | Client → server                                                              | Server → client                                            |
| --------- | ---------------------------------------------------------------------------- | ---------------------------------------------------------- |
| High Five | `RequestRejectPostAttachment` `D0:6B`, `RequestCancelPostAttachment` `D0:6F` | `ExChangePostState` `FE:B3`, `ExNoticePostArrived` `FE:A9` |
| Essence   | `RequestRejectPostAttachment` `D0:68`, `RequestCancelPostAttachment` `D0:6C` | `ExChangePostState` `FE:B4`, `ExNoticePostArrived` `FE:AA` |

Notes: no special path for a sender who is offline was found in the Mobius `MailManager`.

Known fixes: `l2j-server@b534a1f8` (2011-06-12, other) — Memory Leak Fix (by JIV).

### mail.system-mail — System mail

ID: `mail.system-mail` · Shared: **partial**

Mail created by the server, not by a player: rewards, auction/commission payouts, custom mailing from the
`custom_mail` SQL table on a timer. Sub-features: custom mail table · system sender type · reward mail.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                                                         |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il (custom)                   | `CustomMailManager` does not create a mail: it whispers the subject and text and adds the items straight into the inventory of an online player                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `CustomMailManager` builds a `MailMessage` with `MessageSenderType` and attaches items from the `items` column (`id count enchant;...`), timer from `DatabaseQueryDelay` |
| Essence   | yes     | mobius-ess                           | same manager plus typed system mail (`MailType`) used by commission, prime-shop gifts, rewards                                                                                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CustomMailManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/CustomMailManagerConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/CustomMailManager.ini`, `nx-l2:chronicles/high-five/datapack/sql/custom_mail.sql`
- `mobius-il:java/org/l2jmobius/gameserver/managers/CustomMailManager.java`, `mobius-il:dist/game/config/Custom/CustomMailManager.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/CustomMailManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/holders/MailMessage.java`

Packets

| Chronicle | Client → server | Server → client               |
| --------- | --------------- | ----------------------------- |
| Interlude | —               | `CreatureSay` `4A` (whisper)  |
| High Five | —               | `ExNoticePostArrived` `FE:A9` |
| Essence   | —               | `ExNoticePostArrived` `FE:AA` |

Notes: `CustomMailManagerEnabled = False` by default in all three.

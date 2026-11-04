# 010 — Mail: High Five client protocol

How the High Five client (protocol 267) shows mail, read from the client's own interface scripts (the postbox, mail
detail and mail write windows), and how the `mail` view events map onto it. Part of [spec 010](spec.md). Field names
in the tables are the parameter names the client scripts read.

## 1. Packets

Client to server (extended `D0:xx`), each a command of the module:

| Packet                        | Opcode  | Command              | Payload the client sends                  |
| ----------------------------- | ------- | -------------------- | ----------------------------------------- |
| `RequestPostItemList`         | `D0:65` | `ListMailableItems`  | —                                         |
| `RequestSendPost`             | `D0:66` | `SendMail`           | receiver, subject, body, items, COD adena |
| `RequestReceivedPostList`     | `D0:67` | `ListReceivedMail`   | —                                         |
| `RequestDeleteReceivedPost`   | `D0:68` | `DeleteReceivedMail` | mail ids                                  |
| `RequestReceivedPost`         | `D0:69` | `OpenReceivedMail`   | mail id                                   |
| `RequestPostAttachment`       | `D0:6A` | `ReceiveAttachments` | mail id                                   |
| `RequestRejectPostAttachment` | `D0:6B` | `ReturnMail`         | mail id                                   |
| `RequestSentPostList`         | `D0:6C` | `ListSentMail`       | —                                         |
| `RequestDeleteSentPost`       | `D0:6D` | `DeleteSentMail`     | mail ids                                  |
| `RequestSentPost`             | `D0:6E` | `OpenSentMail`       | mail id                                   |
| `RequestCancelPostAttachment` | `D0:6F` | `CancelMail`         | mail id                                   |

Every request about a mail carries only the mail id; the client never sends back the object ids of attachments. The
mail id on the wire is a session-local number: the adapter keeps, per session, a table from the numbers it showed to
the mail UUIDs (at most the two boxes, 480 entries), so mail takes no handles from the global allocator and nothing
outlives the session.

Server to client (`FE:xx`), each the mapping of a view event:

| Packet                   | Opcode  | View event           |
| ------------------------ | ------- | -------------------- |
| `ExNoticePostArrived`    | `FE:A9` | `MailArrived`        |
| `ExShowReceivedPostList` | `FE:AA` | `ReceivedMailList`   |
| `ExReplyReceivedPost`    | `FE:AB` | `ReceivedMailOpened` |
| `ExShowSentPostList`     | `FE:AC` | `SentMailList`       |
| `ExReplySentPost`        | `FE:AD` | `SentMailOpened`     |
| `ExReplyPostItemList`    | `FE:B2` | `MailableItems`      |
| `ExChangePostState`      | `FE:B3` | `MailStateChanged`   |
| `ExNoticePostSent`       | `FE:B4` | `MailSent`           |

## 2. Fields and their meaning in the client

`ExShowReceivedPostList`, per mail: `MailID`, `Title`, `SenderName`, `Trade` (cash on delivery pending),
`expiration` (Unix seconds; the client counts the time left from the server time at the head of the list), `NotOpend`, `ReturnAble`, `WithItem` (the attachment icon), `Returnd`, `SentBySystem`.

`ExShowSentPostList`, per mail: `MailID`, `Title`, `ReceiverName`, `Trade`, `expiration`, `NotOpend`, `ReturnAble`,
`WithItem`.

`ExReplyReceivedPost`: `MailID`, `trade`, `Returnd`, `SenderName`, `Title`, `Content`, items (item block + object id
each), `TradeMoney`, `ReturnAble`, `Sentbysystem`. `ExReplySentPost`: `MailID`, `trade`, `ReceiverName`, `Title`,
`Content`, items, `TradeMoney`, `NotOpend`.

What the server sends:

- `Trade` / `trade` = 1 only while cash on delivery is still to be paid (`cod_adena` set and `ATTACHED`); after the
  receive it is 0, or the client asks for the payment again.
- `WithItem` = 1 only while the attachments can be taken (`ATTACHED`).
- `Returnd` = 1 for a returned mail (`returned_from_id` set).
- `ReturnAble` = 1 when the receiver may return the mail: `ATTACHED`, a `PLAYER` mail, not a returned mail. It
  drives the client's Return button only.
- `SentBySystem` comes from `MailType`: `PLAYER` 0 (the client shows `SenderName`), `SYSTEM` 1 (the client shows its
  system sender string, or its returned-mail string when `Returnd` = 1), `BIRTHDAY` 3 (the client shows its birthday
  sender, Alegria). For 1 and 3 the client hides Return and Reply; system mail is not returnable.
- `Title` in a list keeps what one frame of at most 65535 bytes leaves after the fixed fields and the names of the
  shown mails, shared evenly: 100 characters in a full box of 240 with 16-character names, whole subjects in a short
  list; the opened mail shows it whole (240 mails with 128-character subjects and 16-character names take about
  80 KB, which would close the connection). The adapter refuses to start with a `mailbox-limit` whose full list no
  longer leaves 16 characters per subject (above 630).
- `ExReplySentPost` always ends with `NotOpend`, with or without items.
- `ExChangePostState` carries the client's states: deleted, read, and attachments taken; the last one clears the
  attachment icon in the open list.

## 3. Item block of an attachment

Attachments are written from the snapshot rows, never from live items: object id, display id, location slot, count,
type 2, custom type 1, equipped (0), body part, enchant, custom type 2, augmentation id, mana, remaining time, attack
element type and power, six defence values, three enchant options, then the object id again.

- **Object id:** a synthetic handle per attachment of the shown mail, `slot + 1`. Live objects never get a handle below
  `0x10000000` ([persistence.md §2](../003-engine-core/persistence.md#2-wire-handles)), so a snapshot can never be
  mistaken for an item in the inventory, and the client does not send attachment ids back.
- **Remaining time** of a time-limited item is computed from `item_expires_at` when the mail is shown.
- The chronicle's mapper builds the item block from the snapshot view; type 2 and body part come from the item
  template.

## 4. Snapshot in the mail window

The mail detail window adds every item it receives and shows the Receive button (Cancel in the sent mail) whenever
the item list is not empty; the buttons are enabled or disabled only by the zone. There is no flag for "attachments
already taken" in protocol 267. So a settled mail shows its snapshot with an active Receive (or Cancel) button:

- Receive on a settled mail answers `MailRefusal.ALREADY_RECEIVED`, shown as system message 3382 ("The product has
  already been received."), the closest message the client has.
- Cancel on a settled mail answers system message 3030 ("You cannot cancel sent mail since the recipient received
  it.").
- Return stays hidden: `ReturnAble` is 0 once the mail is settled.

A client patch could hide both buttons for a settled mail; it is deferred (spec §8).

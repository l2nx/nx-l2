# Mail animations

Sources of the animated diagrams on the mail page. The outputs are
`docs/public/{en,ru}/features/mail-{snapshot,transaction,race}.svg`.

**Never hand-edit the outputs.** Change a template, a strings file or `generate.py`, then regenerate; `--check`
catches outputs that drifted from their sources.

| File                   | Role                                                                                        |
| ---------------------- | ------------------------------------------------------------------------------------------- |
| `snapshot.svg.tmpl`    | a received attachment: the letter forgets it on a classic build, keeps its snapshot on L2NX |
| `transaction.svg.tmpl` | saving a receive: a crash between two writes dupes, one transaction with a key does not     |
| `race.svg.tmpl`        | receive and cancel on one letter at the same moment, one conditional update decides         |
| `strings.en.json`      | English texts, including `<title>` and `<desc>`                                             |
| `strings.ru.json`      | Russian texts, same keys                                                                    |
| `generate.py`          | timelines and layout checks of these scenes                                                 |
| `../svgkit.py`         | shared with the other generators: tokens, font subsetting, chips, output checks, CLI        |

## Regenerate

Same requirements as the [Zero-Tick generator](../zero-tick/README.md#regenerate): Python 3.10+ with the packages
of `../zero-tick/requirements.txt` and a `node_modules` holding the two fontsource packages.

```sh
.venv/bin/python -I docs/diagrams/mail/generate.py --fonts ../nx-dev-web/node_modules
.venv/bin/python -I docs/diagrams/mail/generate.py --fonts ../nx-dev-web/node_modules --check
```

The checks are those of the Zero-Tick generator (well-formed XML, 80 KB, font size at least 22, no `<text>` without
a size) plus a width check of every label against its slot: captions, statuses, chips, the Receive button, the
database rows and the letter's details.

## Design decisions

Palette, fonts and motion follow the [Zero-Tick generator](../zero-tick/README.md#design-decisions); the subsets are
exposed under the neutral family names `Mail Head` and `Mail Text`. Colours here: turquoise is the live item and a write,
violet the snapshot glow and the second request, amber a crash or a dupe, green a committed result.

| Animation   | Loop | Timeline                                                                                                                                                                                                        | Reduced motion                      |
| ----------- | ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| snapshot    | 8 s  | Receive pressed at 0.8 s; the live sword flies to the inventory 1.1-2.1 s; classic: the letter empties at 1.2 s and shows "no attachments"; L2NX: the letter keeps the sword and its details, received at 2.2 s | 5.0 s: both letters after receive   |
| transaction | 8 s  | classic: receiver written at 1.4 s, crash at 2.2 s before the letter is written, two swords at 2.7 s; L2NX: crash at 1.4 s, nothing changes, retry commits both rows at 4.25 s, a second retry replays at 6.1 s | 7.0 s: a dupe against one committed |
| race        | 6 s  | Receive reaches the row at 1.75 s and settles it RECEIVED; Cancel arrives at 1.95 s and is refused                                                                                                              | 4.0 s: one winner, one refusal      |

The High Five client keeps the Receive button on a settled mail (protocol 267 has no flag for it), so the hero keeps
it on the L2NX letter too.

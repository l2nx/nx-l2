# Zero-Tick Engine animations

Sources of the animated diagrams on the Zero-Tick Engine page. The outputs are
`docs/public/{en,ru}/zero-tick-{hero,tick,race,dupe,spaces}.svg`.

**Never hand-edit the outputs.** Change a template, a strings file or `generate.py`, then regenerate; `--check`
catches outputs that drifted from their sources.

| File              | Role                                                                                 |
| ----------------- | ------------------------------------------------------------------------------------ |
| `hero.svg.tmpl`   | hero: heart-monitor traces, the same events on a ticking build and on Zero-Tick      |
| `tick.svg.tmpl`   | fixed 100 ms tick versus Zero-Tick: idle ticks and added delay                       |
| `race.svg.tmpl`   | thread race: a mage's Sleep and a warrior's hit on one monster                       |
| `dupe.svg.tmpl`   | thread race: one rare sword sold to an NPC and traded away at the same moment        |
| `spaces.svg.tmpl` | under the hood: open world, instances and olympiad, each space on its own thread     |
| `strings.en.json` | English texts, including `<title>` and `<desc>`                                      |
| `strings.ru.json` | Russian texts, same keys                                                             |
| `generate.py`     | timelines, repeated elements and layout checks of these scenes                       |
| `../svgkit.py`    | shared with the other generators: tokens, font subsetting, chips, output checks, CLI |

## Regenerate

Needs Python 3.10+ with `fonttools` and `brotli`, and a `node_modules` that holds `@fontsource-variable/onest` and
`@fontsource-variable/space-grotesk` (the dev portal checkout has them after `pnpm install`; pnpm's virtual store is
searched too).

```sh
python -m venv .venv && .venv/bin/pip install -r docs/diagrams/zero-tick/requirements.txt
.venv/bin/python -I docs/diagrams/zero-tick/generate.py --fonts ../nx-dev-web/node_modules
.venv/bin/python -I docs/diagrams/zero-tick/generate.py --fonts ../nx-dev-web/node_modules --check
.venv/bin/python -I docs/diagrams/zero-tick/generate.py --fonts ../nx-dev-web/node_modules --palette
```

The build fails when an output is not well-formed XML, when it exceeds 80 KB, when any `font-size` is below 22 (12 px at a 360 px wide render of a
640 wide viewBox), when a `<text>` has no `font-size` (it would fall back to 16 px), when a tick lane graphic leaves
its plot area for the counters column, or when a label is too wide for its slot (counters, chips, outcomes, the
Zero-Tick queues, space labels). The output is byte-for-byte reproducible.

## Template syntax

- `{{h:key}}` / `{{t:key}}`: a string from the locale file set in the heading / text font; its characters go into
  that font's subset.
- `{{s:key}}`: a string outside the fonts (`<title>`, `<desc>`).
- `{{name}}`: a value or fragment computed by the builder in `generate.py` (keyframe class, geometry, markup).
- `/*STYLE*/`: replaced by the `@font-face` rules, the palette custom properties, the font classes `.h` / `.t`, the
  generated keyframes and the reduced-motion rule.

## Design decisions

### Palette

`@l2nx/ui` dark tokens, dark only like the portal. `generate.py` keeps the kit's oklch values and converts them to
sRGB hex with the CSS Color 4 gamut mapping; `accent` and `accent-low` are slightly outside sRGB and land on their
clipped value, which is within one JND of the source. `surface` is the kit's opaque `--surface-solid`.

| Token           | Kit value                 | Hex                     | Use                                         |
| --------------- | ------------------------- | ----------------------- | ------------------------------------------- |
| `bg`            | oklch(0.168 0.009 264.3)  | `#0d0f13`               | outer cards, text on badges                 |
| `card`          | oklch(0.23 0.017 264.17)  | `#191d25`               | panels, cards, the spaces map               |
| `surface`       | oklch(0.255 0.013 258.37) | `#1f2329`               | spaces, chips, monster, inventory               |
| `fg`            | oklch(0.958 0.004 271.37) | `#f0f1f4`               | headings, chip text                         |
| `muted`         | oklch(0.82 0.014 264.44)  | `#c0c4cd`               | captions, event labels                    |
| `faint`         | oklch(0.7 0.012 262.34)   | `#9a9fa6`               | secondary labels, idle work, waiting events |
| `accent`        | oklch(0.8 0.14 195)       | `#00d9d9`               | input, active partition, processed events   |
| `accent-low`    | oklch(0.3 0.06 195)       | `#003737`               | fill of an awake partition                  |
| `accent-high`   | oklch(0.91 0.12 192)      | `#71fbf6`               | event icons, swords, write head             |
| `success`       | oklch(0.789 0.159 161.84) | `#38d99a`               | Zero-Tick counters and outcome              |
| `warning`       | oklch(0.765 0.14 73.13)   | `#e8a33d`               | the ticking build, waits, dupe, clash   |
| `violet`        | oklch(0.606 0.219 292.72) | `#8b5cf6`               | Sleep, the trade operation (kit accent-violet) |
| `border`        | white at 8 %              | `rgba(255,255,255,.08)` | card borders                                |
| `border-strong` | white at 18 %             | `rgba(255,255,255,.18)` | outlines, lanes, tick marks       |

Turquoise means "the engine reacts", amber means "a timer or a wait", green means "the Zero-Tick result". Glows are
blurred copies at low opacity, no gradients beyond the kit's faint top glow on the hero. Violet is Sleep and the trade operation.

### Fonts

Space Grotesk 600 for headings and numbers, Onest 500 for captions, as in the kit. Space Grotesk has no Cyrillic, so
the heading family falls back per glyph to Onest 600 (the kit's `--font-number` stack does the same): Russian headings
are Onest, their digits Space Grotesk. Each SVG embeds only the glyphs it uses: the variable fonts are pinned to the
used weight, subset per `unicode-range` file and inlined as base64 woff2.

Both fonts are under the SIL Open Font License 1.1: Onest, Copyright 2021 The Onest Project Authors
(https://github.com/simpals/onest); Space Grotesk, Copyright 2020 The Space Grotesk Project Authors
(https://github.com/floriankarsten/space-grotesk). The subsets carry no name records and are exposed under neutral
family names (`ZT Head`, `ZT Text`), so a cut-down copy is never presented as the original font.

### Motion

Pure CSS keyframes, no script. Every track lasts the whole loop and has no delay, so all tracks stay in phase and one
negative `animation-delay` freezes the picture at any moment. `prefers-reduced-motion: reduce` uses that to show a
fixed key frame instead of stopping the animation at its first frame.

| Animation | Loop | Timeline | Reduced motion |
| --------- | ---- | -------- | -------------- |
| hero | 8 s | traces scroll 100 px/s past a write head; events at 1.2 s (hit), 3.9 s (buff ends), 6.1 s (player enters) on both lanes; classic beats every 0.4 s from 0.2 s, so each event waits for the next beat (the highlighted one) and the beats in between are bracketed as idle; Zero-Tick spikes exactly at each event | 6.3 s: two events on both lanes |
| tick | 6 s | 0.3-5.3 s sweep over 16 ticks of 100 ms; events at 2.6, 6.3, 7.8, 12.4 ticks (waits 20-70 ms, 12 idle ticks); 5.75 s reset | 5.5 s: the whole run with its waits |
| race | 6 s | 0.4-2.0 s Sleep and hit travel; classic: clash at 2.0 s, the outcome (asleep / awake, Sleep wasted) flips every 0.4 s; Zero-Tick: hit (1) applied at 2.3 s, Sleep (2) at 3.1 s, the monster sleeps | 4.0 s: two outcomes against one |
| dupe | 6 s | classic: both inventory checks pass at 1.0 s, two swords and DUPE at 2.3 s; Zero-Tick: the sale (1) takes the sword at 2.1 s, the trade (2) is rejected at 3.0 s, check mark at 3.4 s | 4.4 s: a dupe against one sword |
| spaces | 8 s | a mass battle keeps the open world awake 1.0-6.2 s; an olympiad match runs 2.4-4.8 s on its own thread meanwhile; a boss wakes one instance 6.6-7.5 s; the other instance never wakes; a thread mark (lane with a dot) runs in 0.8 s laps while its space is awake, the legend explains it | 3.6 s: world and olympiad both awake |

### Layout

A 640 wide viewBox, `width="100%"` and no fixed height, so the page scales the SVG to its column. The smallest text
is 22 units, 12.4 px at 360 px wide. Cards use the kit radii (14-16) and 1 px borders.

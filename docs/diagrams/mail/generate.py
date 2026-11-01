"""Builds the mail page animations.

Output: docs/public/<locale>/features/mail-<name>.svg, standalone SVGs with CSS keyframes and an embedded glyph subset
of Onest and Space Grotesk.

Run with `python -I generate.py --fonts <node_modules>`; see README.md.
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
# Run with -I, which leaves the script's directory off sys.path.
sys.path.insert(0, str(HERE.parent))

from svgkit import (  # noqa: E402
    BADGE_R,
    ORIGIN_CENTER,
    PUBLIC_DIR,
    Animations,
    Fonts,
    Svg,
    build,
    check_mark,
    chip,
    chip_w,
    fade,
    main,
    num,
    sword,
    wrap,
)

# Neutral names: a cut-down copy is never presented as the original font.
FAMILIES = {"h": "Mail Head", "t": "Mail Text"}

BOLT = "M3 -17L-8 2H0L-4 17L9 -3H1z"
ENVELOPE = "M-13 -9h26v18h-26zM-13 -9l13 10l13-10"


def bolt(x: float, y: float) -> str:
    return f'<path class="bolt" transform="translate({num(x)} {num(y)}) scale(1.3)" d="{BOLT}"/>'


def badge_ok(cx: float, cy: float) -> str:
    return (
        f'<circle class="badge-ok" cx="{num(cx)}" cy="{num(cy)}" r="{BADGE_R}"/>'
        f'<path class="tick" d="M{num(cx - 6)} {num(cy)}l4 4l8-9"/>'
    )


def text(svg: Svg, role: str, key: str, x: float, y: float, cls: str, size: int = 22, anchor: str = "") -> str:
    a = f' text-anchor="{anchor}"' if anchor else ""
    return f'<text class="{role} {cls}" x="{num(x)}" y="{num(y)}" font-size="{size}"{a}>{svg.text(role, svg.s[key])}</text>'


def limit(svg: Svg, role: str, key: str, size: int, width: float) -> None:
    if svg.width(role, svg.s[key], size) > width:
        raise ValueError(f"{svg.name}: text too wide: {key}")


def move(
    svg: Svg,
    appear: float,
    stops: list[tuple[float, float, float, float]],
    gone: float,
    timing: str = "ease-in-out",
) -> str:
    """A track that fades in at `appear`, passes (time, dx, dy, scale) stops and is hidden from `gone` to the loop end."""
    first = stops[0]
    track = [
        (max(appear - 0.15, 0), f"opacity:0;transform:translate(0px,0px) scale({num(first[3])})"),
        (appear, f"opacity:1;transform:translate(0px,0px) scale({num(first[3])})"),
    ]
    track += [(t, f"opacity:1;transform:translate({num(dx)}px,{num(dy)}px) scale({num(s)})") for t, dx, dy, s in stops]
    last = stops[-1]
    track += [
        (gone, f"opacity:0;transform:translate({num(last[1])}px,{num(last[2])}px) scale({num(last[3])})"),
        (svg.dur - 0.01, "opacity:0;transform:translate(0px,0px) scale(1)"),
    ]
    return svg.anim(track, timing, ORIGIN_CENTER)


# --- snapshot: the letter keeps a picture of what it carried ------------------------------------------------------

SNAP_DUR = 8.0
SNAP_PANELS = (("classic", 16), ("l2nx", 272))
SNAP_PANEL_H = 240
PRESS, FLY, LAND, RESET = 0.8, 1.1, 2.1, 7.4
SLOT = (84.5, 136.5)  # sword centre inside the letter, relative to the panel top
INV_X = 412.5
CELL0 = (434, 98)  # top-left inventory cell, relative to the panel top
CELL, GAP = 44, 8


def _cell(i: int, j: int) -> tuple[float, float]:
    return CELL0[0] + (CELL + GAP) * j, CELL0[1] + (CELL + GAP) * i


def build_snapshot(svg: Svg) -> dict:
    ctx = {}
    for key, width in (("subject", 270), ("empty", 210), ("inventory", 180)):
        limit(svg, "t", key, 22, width)
    receive_w = svg.width("t", svg.s["receive"], 22) + 28
    bx = 381 - receive_w
    limit(svg, "t", "detail", 22, bx - 8 - 130)
    press = svg.anim(
        [(PRESS, "fill:var(--surface)"), (PRESS + 0.1, "fill:var(--accent-low)"), (FLY + 0.2, "fill:var(--surface)")]
    )
    for kind, top in SNAP_PANELS:
        out = []
        limit(svg, "t", f"{kind}_caption", 22, 568)
        out.append(f'<rect class="panel" x="16.5" y="{top + 0.5}" width="607" height="{SNAP_PANEL_H}" rx="14"/>')
        out.append(text(svg, "h", f"{kind}_title", 36, top + 36, "title" + (" accent" if kind == "l2nx" else ""), 24))
        # The letter as the mail window shows it.
        out.append(f'<rect class="letter" x="36.5" y="{top + 54.5}" width="360" height="136" rx="12"/>')
        out.append(f'<path class="envelope" transform="translate(66 {top + 80})" d="{ENVELOPE}"/>')
        out.append(text(svg, "t", "subject", 92, top + 88, "text"))
        out.append(f'<rect class="slot" x="52.5" y="{top + 104.5}" width="64" height="64" rx="10"/>')
        button = (
            f'<rect class="button {press}" x="{num(bx)}" y="{top + 140.5}" width="{num(receive_w)}" height="36" rx="10"/>'
            f'<text class="t text" x="{num(bx + receive_w / 2)}" y="{top + 166}" font-size="22" text-anchor="middle">'
            f"{svg.text('t', svg.s['receive'])}</text>"
        )
        details = text(svg, "h", "enchant", 130, top + 128, "enchant") + text(
            svg, "t", "detail", 130, top + 158, "muted"
        )
        sx, sy = SLOT[0], top + SLOT[1]
        if kind == "classic":
            # The item leaves the mail and the mail forgets it.
            gone = svg.anim(
                [(0, "opacity:1"), (FLY, "opacity:1"), (FLY + 0.1, "opacity:0")] + fade(RESET + 0.3, None, 0.3)
            )
            out.append(f'<g class="{gone}">{sword(sx, sy, "")}{details}{button}</g>')
            empty = svg.anim(fade(LAND + 0.2, RESET, 0.3))
            out.append(
                f'<g class="{empty}"><rect class="slot-empty" x="60.5" y="{top + 112.5}" width="48" height="48" rx="8"/>'
                + text(svg, "t", "empty", 130, top + 144, "faint")
                + "</g>"
            )
        else:
            # The snapshot stays in the mail; the live item travels to the inventory. The High Five client keeps
            # showing Receive for a settled mail; the button answers "already received".
            out.append(f'<ellipse class="frozen" cx="{sx}" cy="{sy}" rx="14" ry="22" filter="url(#glow)"/>')
            out.append(sword(sx, sy, "") + details + button)
            stamp = svg.anim(
                [
                    (LAND + 0.1, "opacity:0;transform:scale(.3)"),
                    (LAND + 0.3, "opacity:1;transform:scale(1.25)"),
                    (LAND + 0.5, "opacity:1;transform:scale(1)"),
                    (RESET, "opacity:1;transform:scale(1)"),
                    (RESET + 0.3, "opacity:0;transform:scale(1)"),
                ],
                extra=ORIGIN_CENTER,
            )
            out.append(f'<g class="{stamp}">{badge_ok(110, top + 110)}</g>')
        # The receiver's inventory.
        out.append(text(svg, "t", "inventory", INV_X, top + 76, "faint"))
        out.append(f'<rect class="inv" x="{INV_X}" y="{top + 88.5}" width="{604 - INV_X}" height="114" rx="12"/>')
        for i in range(2):
            for j in range(3):
                cx, cy = _cell(i, j)
                out.append(
                    f'<rect class="cell" x="{cx + 0.5}" y="{top + cy + 0.5}" width="{CELL}" height="{CELL}" rx="8"/>'
                )
        for i, j in ((0, 0), (1, 2)):
            cx, cy = _cell(i, j)
            out.append(f'<circle class="stuff" cx="{cx + CELL / 2}" cy="{top + cy + CELL / 2}" r="9"/>')
        tx, ty = _cell(0, 1)
        dx, dy = tx + CELL / 2 - sx, top + ty + CELL / 2 - sy
        fly = svg.anim(
            [
                (FLY - 0.01, "opacity:0;transform:translate(0px,0px) scale(1)"),
                (FLY, "opacity:1;transform:translate(0px,0px) scale(1)"),
                (LAND, f"opacity:1;transform:translate({num(dx)}px,{num(dy)}px) scale(.8)"),
                (RESET, f"opacity:1;transform:translate({num(dx)}px,{num(dy)}px) scale(.8)"),
                (RESET + 0.3, f"opacity:0;transform:translate({num(dx)}px,{num(dy)}px) scale(.8)"),
                (SNAP_DUR - 0.01, "opacity:0;transform:translate(0px,0px) scale(1)"),
            ],
            extra=ORIGIN_CENTER,
        )
        out.append(f'<g class="{fly}">{sword(sx, sy, "live")}</g>')
        out.append(
            f'<text class="t caption {svg.anim(fade(LAND + 0.4, RESET, 0.3))}" x="36" y="{top + 224}" font-size="22">'
            f"{svg.text('t', svg.s[f'{kind}_caption'])}</text>"
        )
        ctx[f"{kind}_panel"] = "".join(out)
    return ctx


# --- transaction: one save point --------------------------------------------------------------------------------

TX_DUR = 8.0
TX_PANELS = (("classic", 16), ("l2nx", 288))
TX_PANEL_H = 256
LANE_Y, LANE_X0, LANE_X1, CRASH_X = 100, 36, 392, 300
DB_X = 400.5
ROW_Y = {"receiver": 118, "mail": 160}
DB_SWORD_X = 576


def _lane_chip(svg: Svg, top: float, key: str, kind: str, appear: float, at_db: float, stop_x: float | None) -> str:
    """A write travelling along the lane into the database; `stop_x` stops it there instead (a crash)."""
    w = chip_w(svg, key, False)
    if w > LANE_X1 - LANE_X0:
        raise ValueError(f"transaction chip too wide: {key}")
    sx = LANE_X0 + w / 2
    y = top + LANE_Y
    if stop_x is None:
        end_x = LANE_X1 - w / 2
        stops = [(appear, 0, 0, 1), (at_db, end_x - sx, 0, 1), (at_db + 0.25, LANE_X1 + 40 - sx, 0, 0.2)]
        cls = move(svg, appear, stops, at_db + 0.3)
    else:
        end_x = min(stop_x, LANE_X1) - w / 2 - 6
        stops = [(appear, 0, 0, 1), (at_db, end_x - sx, 0, 1)]
        cls = move(svg, appear, stops, at_db + 0.35)
    return chip(svg, key, kind, sx, y, cls)


def _bolt_at(svg: Svg, top: float, at: float) -> str:
    cls = svg.anim(
        [
            (at - 0.01, "opacity:0;transform:scale(.4)"),
            (at + 0.1, "opacity:1;transform:scale(1.2)"),
            (at + 0.3, "opacity:1;transform:scale(1)"),
            (at + 0.9, "opacity:0;transform:scale(1)"),
        ],
        extra=ORIGIN_CENTER,
    )
    return f'<g class="{cls}">{bolt(CRASH_X, top + LANE_Y)}</g>'


def _row_sword(svg: Svg, top: float, row: str, track: list[tuple[float, str]], cls: str = "") -> str:
    y = top + ROW_Y[row] - 6
    return f'<g class="{svg.anim(track, "step-end")}">{sword(DB_SWORD_X, y, cls)}</g>'


# Status line 1 sits beside the database card, line 2 below it.
STATUS_Y, STATUS_W = (150, 198), (LANE_X1 - LANE_X0, 568)


def _status(
    svg: Svg, top: float, line: int, key: str, on: float, off: float, cls: str, x: float = 36, mark: str = ""
) -> str:
    limit(svg, "t", key, 22, STATUS_W[line] - (x - 36) - (24 if mark else 0))
    y = top + STATUS_Y[line]
    pre, tx = "", x
    if mark == "ok":
        pre, tx = check_mark(x, y - 2), x + 24
    return (
        f'<g class="{svg.anim(fade(on, off, 0.2))}">{pre}<text class="t {cls}" x="{num(tx)}" y="{y}" font-size="22">'
        f"{svg.text('t', svg.s[key])}</text></g>"
    )


def _database(svg: Svg, top: float) -> str:
    out = [f'<rect class="db" x="{DB_X}" y="{top + 54.5}" width="203" height="126" rx="12"/>']
    out.append(text(svg, "t", "database", DB_X + 15.5, top + 82, "faint"))
    for row in ROW_Y:
        limit(svg, "t", f"row_{row}", 22, 140)
        out.append(
            f'<line class="rowline" x1="{DB_X + 15.5}" y1="{top + ROW_Y[row] - 28.5}"'
            f' x2="{DB_X + 187.5}" y2="{top + ROW_Y[row] - 28.5}"/>'
        )
        out.append(text(svg, "t", f"row_{row}", DB_X + 15.5, top + ROW_Y[row], "text"))
    return "".join(out)


def build_transaction(svg: Svg) -> dict:
    ctx = {}
    s = svg.s
    reset = 7.5
    for kind, top in TX_PANELS:
        limit(svg, "t", f"{kind}_caption", 22, 568)
        out = [f'<rect class="panel" x="16.5" y="{top + 0.5}" width="607" height="{TX_PANEL_H}" rx="14"/>']
        out.append(text(svg, "h", f"{kind}_title", 36, top + 36, "title" + (" accent" if kind == "l2nx" else ""), 24))
        out.append(f'<path class="lane" d="M{LANE_X0} {top + LANE_Y}H{LANE_X1}"/>')
        out.append(_database(svg, top))
        if kind == "classic":
            # Two save points: the receiver is written, the crash hits before the mail is.
            out.append(_lane_chip(svg, top, "save1", "accent", 0.3, 1.2, None))
            out.append(_lane_chip(svg, top, "save2", "violet", 1.6, 2.2, CRASH_X))
            out.append(_bolt_at(svg, top, 2.2))
            out.append(_row_sword(svg, top, "receiver", [(0, "opacity:0"), (1.45, "opacity:1"), (reset, "opacity:0")]))
            out.append(_row_sword(svg, top, "mail", [(0, "opacity:1"), (2.7, "opacity:0"), (reset, "opacity:1")]))
            for row in ("mail", "receiver"):
                out.append(
                    _row_sword(svg, top, row, [(0, "opacity:0"), (2.7, "opacity:1"), (reset, "opacity:0")], "dupe")
                )
            dupe_w = svg.width("h", s["dupe"], 22) + 20
            out.append(
                f'<g class="{svg.anim(fade(2.7, reset, 0.2))}"><rect class="dupe" x="36" y="{top + STATUS_Y[1] - 23}"'
                f' width="{num(dupe_w)}" height="32" rx="8"/><text class="h dupetext" x="{num(36 + dupe_w / 2)}"'
                f' y="{top + STATUS_Y[1]}" font-size="22" text-anchor="middle">{svg.text("h", s["dupe"])}</text></g>'
            )
            out.append(_status(svg, top, 1, "two", 2.7, reset, "warn", 36 + dupe_w + 14))
        else:
            # One save point: the crash leaves nothing behind, the retry commits once, a second retry replays.
            out.append(_lane_chip(svg, top, "tx", "accent", 0.3, 1.4, CRASH_X))
            out.append(_bolt_at(svg, top, 1.4))
            out.append(_status(svg, top, 1, "nothing", 1.8, 3.0, "muted"))
            out.append(_lane_chip(svg, top, "retry", "accent", 3.0, 4.0, None))
            committed = 4.25
            out.append(_row_sword(svg, top, "mail", [(0, "opacity:1"), (committed, "opacity:0"), (reset, "opacity:1")]))
            out.append(
                _row_sword(svg, top, "receiver", [(0, "opacity:0"), (committed, "opacity:1"), (reset, "opacity:0")])
            )
            ring = svg.anim(
                [
                    (committed - 0.01, "opacity:0;transform:scale(.9)"),
                    (committed + 0.1, "opacity:1;transform:scale(1)"),
                    (committed + 0.7, "opacity:0;transform:scale(1.08)"),
                ],
                extra=ORIGIN_CENTER,
            )
            out.append(f'<rect class="commit {ring}" x="{DB_X}" y="{top + 54.5}" width="203" height="126" rx="12"/>')
            out.append(_status(svg, top, 0, "committed", committed + 0.2, reset, "ok", mark="ok"))
            out.append(_lane_chip(svg, top, "again", "violet", 5.0, 5.9, LANE_X1))
            out.append(_status(svg, top, 1, "replayed", 6.1, reset, "muted"))
        out.append(text(svg, "t", f"{kind}_caption", 36, top + 238, "caption"))
        ctx[f"{kind}_panel"] = "".join(out)
    return ctx


# --- race: receive against cancel ---------------------------------------------------------------------------------

RACE_DUR = 6.0
ROW = (372.5, 134.5, 231, 80)
RACE_LANES = {"receive": 124, "cancel": 224}
BEND_X = 300
WIN_AT, LOSE_AT, END = 1.75, 1.95, 5.5


def build_race(svg: Svg) -> dict:
    ctx = {}
    s = svg.s
    target = (ROW[0] + 4, ROW[1] + ROW[3] / 2)
    chips = []
    for key, kind, appear in (("receive", "accent", 0.2), ("cancel", "violet", 0.25)):
        y = RACE_LANES[key]
        w = chip_w(svg, key, False)
        if w > BEND_X - 36:
            raise ValueError(f"race chip too wide: {key}")
        sx = 36 + w / 2
        if key == "receive":
            stops = [(appear, 0, 0, 1), (1.3, BEND_X - w / 2 - sx, 0, 1), (WIN_AT, target[0] - sx, target[1] - y, 0.2)]
            cls = move(svg, appear, stops, WIN_AT + 0.05)
        else:
            # The loser reaches the row a moment later, finds it settled and stays out.
            near = (target[0] - w / 2 - 14 - sx, target[1] - y + 22)
            stops = [(appear, 0, 0, 1), (1.4, BEND_X - w / 2 - sx, 0, 1), (LOSE_AT, near[0], near[1], 0.9)]
            cls = move(svg, appear, stops, LOSE_AT + 0.6)
        chips.append(chip(svg, key, kind, sx, y, cls))
    ctx["chips"] = "".join(chips)
    for key in ("receiver", "sender", "mail"):
        limit(svg, "t", key, 22, 220)
    limit(svg, "t", "condition", 22, 604 - 372)
    limit(svg, "h", "attached", 24, 200)
    limit(svg, "h", "received", 24, 200)
    ctx["attached"] = svg.anim([(0, "opacity:1"), (WIN_AT, "opacity:0"), (END + 0.2, "opacity:1")], "step-end")
    ctx["received"] = svg.anim([(0, "opacity:0"), (WIN_AT, "opacity:1"), (END + 0.2, "opacity:0")], "step-end")
    ctx["ring"] = svg.anim(
        [
            (WIN_AT - 0.01, "opacity:0;transform:scale(.95)"),
            (WIN_AT + 0.1, "opacity:1;transform:scale(1)"),
            (WIN_AT + 0.7, "opacity:0;transform:scale(1.08)"),
        ],
        extra=ORIGIN_CENTER,
    )
    win_y, lose_y = 312, 348
    for key in ("win", "lose"):
        limit(svg, "t", key, 22, 520)
    ctx["win"] = (
        f'<g class="{svg.anim(fade(WIN_AT + 0.2, END, 0.2))}">{check_mark(36, win_y - 2)}'
        f'<text class="t ok" x="60" y="{win_y}" font-size="22">{svg.text("t", s["win"])}</text></g>'
    )
    ctx["lose"] = (
        f'<g class="{svg.anim(fade(LOSE_AT + 0.4, END, 0.2))}"><path class="cross" d="M38 {lose_y - 14}l12 12M50 {lose_y - 14}l-12 12"/>'
        f'<text class="t muted" x="60" y="{lose_y}" font-size="22">{svg.text("t", s["lose"])}</text></g>'
    )
    lines = wrap(svg, "t", s["caption"], 22, 568)
    if len(lines) > 1:
        raise ValueError("race caption needs more than one line")
    return ctx


# --- driver ------------------------------------------------------------------------------------------------------

ANIMATIONS: Animations = {
    "snapshot": (SNAP_DUR, build_snapshot, 5.0),
    "transaction": (TX_DUR, build_transaction, 7.0),
    "race": (RACE_DUR, build_race, 4.0),
}


def render(fonts: Fonts) -> dict[Path, bytes]:
    return build(HERE, ANIMATIONS, fonts, lambda locale, name: PUBLIC_DIR / locale / "features" / f"mail-{name}.svg")


if __name__ == "__main__":
    sys.exit(main(__doc__.splitlines()[0], FAMILIES, render))

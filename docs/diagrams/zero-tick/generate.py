"""Builds the Zero-Tick Engine page animations.

Output: docs/public/<locale>/zero-tick-<name>.svg, standalone SVGs with CSS keyframes and an embedded glyph subset of
Onest and Space Grotesk.

Run with `python -I generate.py --fonts <node_modules>`; see README.md.
"""

from __future__ import annotations

import itertools
import math
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
# Run with -I, which leaves the script's directory off sys.path.
sys.path.insert(0, str(HERE.parent))

from svgkit import (  # noqa: E402
    ORIGIN_CENTER,
    PUBLIC_DIR,
    SWORD,
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
FAMILIES = {"h": "ZT Head", "t": "ZT Text"}

# --- hero: heart-monitor traces ---------------------------------------------------------------------------------

PULSE_DUR = 8.0
PULSE_SPEED = 100  # px per second
PULSE_NOW = 560  # x of the write head, traces are drawn only to its left
PULSE_X0 = 36
BEAT_PX = 40  # one classic beat every 0.4 s of animation, standing for its 100 ms tick
# Zero-Tick events at irregular moments: (time, icon, label key).
PULSE_EVENTS = ((1.2, "sword", "ev_hit"), (3.9, "glass", "ev_buff"), (6.1, "player", "ev_enter"))
# Classic beats sit 0.2 s off the event moments, so every event visibly waits for the next beat.
PULSE_BEAT_PHASE = 0.2
PULSE_CLASSIC_Y, PULSE_ZERO_Y = 282, 528


def _trace(base: float, spikes: list[float], shape: list[tuple[float, float]]) -> str:
    """A flat line with a spike shape (dx, dy pairs from the spike start) at each x in `spikes`."""
    span = PULSE_SPEED * PULSE_DUR
    d, x = [f"M{PULSE_X0 - BEAT_PX} {base}"], PULSE_X0 - BEAT_PX
    for sx in sorted(spikes):
        if sx < x:
            continue
        d.append(f"H{num(sx)}")
        d += [f"L{num(sx + dx)} {num(base + dy)}" for dx, dy in shape]
        x = sx + shape[-1][0]
    d.append(f"H{PULSE_NOW + span + BEAT_PX}")
    return "".join(d)


CLASSIC_SPIKE = [(3, -16), (6, 8), (9, 0)]
ZERO_SPIKE = [(4, -36), (8, 14), (12, 0)]
PULSE_ICONS = {
    "sword": "M-2.2 -13L0 -16L2.2 -13V3H-2.2zM-7 3H7V6H-7zM-1.4 6H1.4V11H-1.4zM0 10.5a2.3 2.3 0 1 1 0 4.6a2.3 2.3 0 1 1 0-4.6z",
    "glass": "M-8 -12h16M-8 12h16M-5 -12c0 8 5 9 5 12s-5 4-5 12M5 -12c0 8-5 9-5 12s5 4 5 12",
    "player": "M0 -12a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9zM-8 12c1-7 4-10 8-10s7 3 8 10",
}


def build_hero(svg: Svg) -> dict:
    ctx = {}
    s = svg.s
    span = PULSE_SPEED * PULSE_DUR
    ctx["scroll"] = svg.anim(
        [(0, "transform:translateX(0)"), (PULSE_DUR, f"transform:translateX(-{span}px)")], "linear"
    )
    period = BEAT_PX / PULSE_SPEED

    marks = {"classic": [], "zero": []}
    bases = {"classic": PULSE_CLASSIC_Y, "zero": PULSE_ZERO_Y}
    for te, icon, key in PULSE_EVENTS:
        if svg.width("t", s[key], 22) > 190:
            raise ValueError(f"pulse label too wide: {key}")
        label = svg.text("t", s[key])
        drop = svg.anim(
            [(te - 0.35, "opacity:0;transform:translateY(-34px)"), (te, "opacity:1;transform:translateY(0)")]
        )
        # Copy one loop to the left: the previous loop's event, still on screen until it scrolls out.
        held = svg.anim([(0, "opacity:1"), (te - 0.35, "opacity:1"), (te - 0.34, "opacity:0")], "step-end")
        x = PULSE_NOW + PULSE_SPEED * te
        icon_cls = "evicon solid" if icon == "sword" else "evicon"
        for lane, base in bases.items():
            for dx, cls in ((0, drop), (-span, held)):
                ix = x + dx + 6
                marks[lane].append(
                    f'<g class="{cls}"><path class="{icon_cls}" transform="translate({num(ix)} {base - 62})"'
                    f' d="{PULSE_ICONS[icon]}"/><text class="t evlabel" x="{num(ix - 16)}" y="{base - 54}"'
                    f' font-size="22" text-anchor="end">{label}</text></g>'
                )

    # Classic: the beat runs regardless; an event waits for the next beat, which is the only useful one.
    base = PULSE_CLASSIC_Y
    beats = [
        PULSE_NOW + PULSE_SPEED * PULSE_BEAT_PHASE + k * BEAT_PX - span for k in range(int(2 * span / BEAT_PX) + 3)
    ]
    handled, waits = [], []
    for te, _, _ in PULSE_EVENTS:
        th = PULSE_BEAT_PHASE + math.ceil((te - PULSE_BEAT_PHASE) / period - 1e-9) * period
        xe, xh = PULSE_NOW + PULSE_SPEED * te, PULSE_NOW + PULSE_SPEED * th
        for dx in (-span, 0, span):
            handled.append(xh + dx)
            waits.append(
                f'<rect class="wait" x="{num(xe + dx)}" y="{base - 2}" width="{num(xh - xe)}" height="4" rx="2"/>'
                f'<circle class="evdot" cx="{num(xe + dx)}" cy="{base}" r="4"/>'
            )
    idle = [b for b in beats if all(abs(b - h) > 1 for h in handled)]
    ctx["classic_trace"] = _trace(base, idle, CLASSIC_SPIKE)
    ctx["classic_useful"] = "".join(
        f"M{num(h)} {base}" + "".join(f"L{num(h + dx)} {num(base + dy)}" for dx, dy in CLASSIC_SPIKE)
        for h in handled
    )
    runs = []
    idle_w = svg.width("t", s["idle"], 22)
    edges = sorted(handled)
    for a, b in itertools.pairwise(edges):
        x1, x2 = a + BEAT_PX - 2, b - BEAT_PX + 11
        if x2 - x1 < BEAT_PX:
            continue
        y = base + 22
        runs.append(f'<path class="bracket" d="M{num(x1)} {y - 6}V{y}H{num(x2)}V{y - 6}"/>')
        if idle_w + 16 <= x2 - x1:
            runs.append(
                f'<text class="t idle" x="{num((x1 + x2) / 2)}" y="{y + 26}" font-size="22" text-anchor="middle">'
                f"{svg.text('t', s['idle'])}</text>"
            )
    ctx["classic_waits"] = "".join(waits) + "".join(runs)
    ctx["classic_marks"] = "".join(marks["classic"])
    head = [(0, "transform:translateY(0)")]
    for k in range(int(PULSE_DUR / period)):
        t = PULSE_BEAT_PHASE + k * period
        head += [
            (t, "transform:translateY(0)"),
            (t + 0.03, "transform:translateY(-16px)"),
            (t + 0.06, "transform:translateY(8px)"),
            (t + 0.09, "transform:translateY(0)"),
        ]
    ctx["classic_head"] = svg.anim(head, "linear")
    ctx["classic_y"] = base

    base = PULSE_ZERO_Y
    spikes = []
    zhead = [(0, "transform:translateY(0)")]
    for te, _, _ in PULSE_EVENTS:
        x = PULSE_NOW + PULSE_SPEED * te
        spikes += [x, x - span]
        zhead += [
            (te, "transform:translateY(0)"),
            (te + 0.04, "transform:translateY(-36px)"),
            (te + 0.08, "transform:translateY(14px)"),
            (te + 0.12, "transform:translateY(0)"),
        ]
    ctx["zero_y"] = base
    ctx["zero_trace"] = _trace(base, spikes, ZERO_SPIKE)
    ctx["zero_marks"] = "".join(marks["zero"])
    ctx["zero_head"] = svg.anim(zhead, "linear")
    ctx["head_x"] = PULSE_NOW
    ctx["clip_w"] = PULSE_NOW - PULSE_X0 + 2
    for key in ("classic_sub", "zero_sub"):
        if svg.width("t", s[key], 22) > 568:
            raise ValueError(f"pulse text too wide: {key}")
    return ctx


# --- tick --------------------------------------------------------------------------------------------------------

TICK_DUR = 6.0
LANE_X0, LANE_STEP, MARKS = 40, 22.5, 16
# Plot area of a lane: card padding on the left, a 16 px gap before the counters divider at x 424 on the right.
TICK_PLOT = (36, 408)
SWEEP_START, SWEEP_LEN = 0.3, 5.0
RESET = 5.75
# Arrival points in tick units; four distinct next marks leave 12 of the 16 ticks idle, waits are 20-70 ms.
EVENTS = (2.6, 6.3, 7.8, 12.4)


def _tick_x(k: float) -> float:
    return LANE_X0 + LANE_STEP * k


def _tick_t(k: float) -> float:
    return SWEEP_START + SWEEP_LEN * k / MARKS


def build_tick(svg: Svg) -> dict:
    ctx = {}
    lanes = {"top": 16, "bottom": 212}
    reset = [(RESET, "opacity:1"), (RESET + 0.2, "opacity:0")]
    event_marks = {math.ceil(k) for k in EVENTS}
    assert MARKS - len(event_marks) == 12, "the idle-ticks counter text says 12"

    for lane, top in lanes.items():
        axis = top + 118
        parts = [f'<line class="axis" x1="{LANE_X0}" y1="{axis}" x2="{_tick_x(MARKS)}" y2="{axis}"/>']
        if lane == "top":
            for k in range(1, MARKS + 1):
                x, t = _tick_x(k), _tick_t(k)
                flash = svg.anim(
                    [
                        (t - 0.05, "stroke:var(--border-strong)"),
                        (t, "stroke:var(--fg)"),
                        (t + 0.25, "stroke:var(--border-strong)"),
                    ]
                )
                parts.append(f'<line class="mark {flash}" x1="{x}" y1="{axis - 10}" x2="{x}" y2="{axis + 10}"/>')
                parts.append(_blip(svg, x, axis, t, "busy" if k in event_marks else "idle", reset))
        for k in EVENTS:
            xa, ta = _tick_x(k), _tick_t(k)
            if lane == "top":
                km = math.ceil(k)
                xm, tm = _tick_x(km), _tick_t(km)
                drop = svg.anim(
                    [
                        (ta - 0.25, "opacity:0;transform:translateY(-34px);fill:var(--accent-high)"),
                        (ta - 0.2, "opacity:1;transform:translateY(-34px);fill:var(--accent-high)"),
                        (ta, "opacity:1;transform:translateY(0);fill:var(--accent-high)"),
                        (ta + 0.15, "opacity:1;transform:translateY(0);fill:var(--faint)"),
                    ]
                    + _hold_reset("transform:translateY(0);fill:var(--faint)"),
                )
                wait = svg.anim(
                    [(ta, "opacity:1;transform:scaleX(0)"), (tm, "opacity:1;transform:scaleX(1)")]
                    + _hold_reset("transform:scaleX(1)"),
                    "linear",
                    "transform-box:fill-box;transform-origin:0 50%",
                )
                done = svg.anim(
                    [
                        (tm - 0.01, "opacity:0;transform:scale(.3)"),
                        (tm + 0.08, "opacity:1;transform:scale(1.35)"),
                        (tm + 0.25, "opacity:1;transform:scale(1)"),
                    ]
                    + _hold_reset("transform:scale(1)"),
                    extra="transform-box:fill-box;transform-origin:center",
                )
                parts.append(
                    f'<rect class="wait {wait}" x="{xa}" y="{axis - 19}" width="{num(xm - xa)}" height="5" rx="2.5"/>'
                )
                parts.append(f'<circle class="ev {drop}" cx="{xa}" cy="{axis}" r="6"/>')
                parts.append(f'<circle class="done {done}" cx="{xm}" cy="{axis}" r="7"/>')
            else:
                drop = svg.anim(
                    [
                        (ta - 0.25, "opacity:0;transform:translateY(-34px)"),
                        (ta - 0.2, "opacity:1;transform:translateY(-34px)"),
                        (ta, "opacity:1;transform:translateY(0)"),
                    ]
                    + _hold_reset("transform:translateY(0)"),
                )
                ring = svg.anim(
                    [
                        (ta - 0.01, "opacity:0;transform:scale(.6)"),
                        (ta + 0.02, "opacity:1;transform:scale(.6)"),
                        (ta + 0.5, "opacity:0;transform:scale(2.2)"),
                    ],
                    extra="transform-box:fill-box;transform-origin:center",
                )
                parts.append(f'<circle class="ring {ring}" cx="{xa}" cy="{axis}" r="9"/>')
                parts.append(f'<circle class="done {drop}" cx="{xa}" cy="{axis}" r="7"/>')
                parts.append(_blip(svg, xa, axis, ta, "busy", reset))
        head = svg.anim(
            [
                (SWEEP_START, "opacity:1;transform:translateX(0)"),
                (SWEEP_START + SWEEP_LEN, f"opacity:1;transform:translateX({LANE_STEP * MARKS}px)"),
                (RESET, f"opacity:1;transform:translateX({LANE_STEP * MARKS}px)"),
                (RESET + 0.2, f"opacity:0;transform:translateX({LANE_STEP * MARKS}px)"),
                (TICK_DUR - 0.01, "opacity:0;transform:translateX(0)"),
            ],
            "linear",
        )
        parts.append(f'<line class="head {head}" x1="{LANE_X0}" y1="{axis - 50}" x2="{LANE_X0}" y2="{axis + 34}"/>')
        ctx[f"{lane}_lane"] = "".join(parts)

    for lane in lanes:
        markup = ctx[f"{lane}_lane"]
        right = [float(x) + float(w) for x, w in re.findall(r' x="([\d.]+)"[^>]*?width="([\d.]+)"', markup)]
        xs = [float(v) for v in re.findall(r' (?:x|x1|x2|cx)="([\d.]+)"', markup)]
        xs += right + [LANE_X0 + LANE_STEP * MARKS + 1]
        if min(xs) < TICK_PLOT[0] or max(xs) > TICK_PLOT[1]:
            raise ValueError(f"tick {lane} lane leaves its plot area: {min(xs)}..{max(xs)}")
    for key in ("idle_label", "delay_label", "top_delay"):
        if svg.width("h" if key == "top_delay" else "t", svg.s[key], 24 if key == "top_delay" else 22) > 176:
            raise ValueError(f"tick counter text too wide: {key}")
    return ctx


def _hold_reset(decl: str) -> list[tuple[float, str]]:
    return [(RESET, f"opacity:1;{decl}"), (RESET + 0.2, f"opacity:0;{decl}"), (TICK_DUR, f"opacity:0;{decl}")]


def _blip(svg: Svg, x: float, axis: float, t: float, kind: str, reset) -> str:
    cls = svg.anim([(t - 0.02, "opacity:0"), (t + 0.02, "opacity:1"), (t + 0.3, "opacity:.55")] + reset)
    return f'<rect class="blip {kind} {cls}" x="{num(x - 5)}" y="{axis + 24}" width="10" height="10" rx="3"/>'


# --- race: Sleep and a hit on one monster -----------------------------------------------------------------------

RACE_DUR = 6.0
END = [(5.6, "opacity:1"), (5.9, "opacity:0")]
# Classic-build outcome flips: (start, stop, period).
FLICKER = (2.3, 5.5, 0.4)

def _converge(svg: Svg, sx: float, y: float, target: tuple[float, float]) -> str:
    """A lane chip that runs to the lane bend at x 180 and shrinks into the target instead of sliding past it."""
    cx, cy = target[0] - sx, target[1] - y
    return svg.anim(
        [
            (0.2, "opacity:0;transform:translate(0,0) scale(1)"),
            (0.4, "opacity:1;transform:translate(0,0) scale(1)"),
            (1.6, f"opacity:1;transform:translate({num(180 - sx)}px,0px) scale(1)"),
            (2.0, f"opacity:0;transform:translate({num(cx)}px,{num(cy)}px) scale(.2)"),
            (5.99, "opacity:0;transform:translate(0,0) scale(1)"),
        ],
        extra=ORIGIN_CENTER,
    )


def _pop(svg: Svg, at: float) -> str:
    return svg.anim(
        [
            (at, "opacity:0;transform:scale(.3)"),
            (at + 0.15, "opacity:1;transform:scale(1.3)"),
            (at + 0.35, "opacity:1;transform:scale(1)"),
            (5.6, "opacity:1;transform:scale(1)"),
            (5.9, "opacity:0;transform:scale(1)"),
        ],
        extra=ORIGIN_CENTER,
    )


def _ring(svg: Svg, at: float) -> str:
    return svg.anim(
        [
            (at, "opacity:0;transform:scale(.8)"),
            (at + 0.15, "opacity:1;transform:scale(1)"),
            (at + 0.65, "opacity:0;transform:scale(1.6)"),
        ],
        extra=ORIGIN_CENTER,
    )


def _queue_moves(svg: Svg, target: float, head_x: float, first: tuple, second: tuple) -> tuple[str, str]:
    """first = (start x, queue x), second = (start x, queue x, width); each shrinks into the target when applied."""
    (a_s, a_q), (b_s, b_q, b_w) = first, second
    a = svg.anim(
        [
            (0.2, "opacity:0;transform:translateX(0) scale(1)"),
            (0.4, "opacity:1;transform:translateX(0) scale(1)"),
            (1.4, f"opacity:1;transform:translateX({num(a_q - a_s)}px) scale(1)"),
            (2.0, f"opacity:1;transform:translateX({num(a_q - a_s)}px) scale(1)"),
            (2.3, f"opacity:0;transform:translateX({num(target - a_s)}px) scale(.2)"),
            (5.99, "opacity:0;transform:translateX(0) scale(1)"),
        ],
        extra=ORIGIN_CENTER,
    )
    b = svg.anim(
        [
            (0.6, "opacity:0;transform:translateX(0) scale(1)"),
            (0.8, "opacity:1;transform:translateX(0) scale(1)"),
            (1.8, f"opacity:1;transform:translateX({num(b_q - b_s)}px) scale(1)"),
            (2.3, f"opacity:1;transform:translateX({num(b_q - b_s)}px) scale(1)"),
            (2.8, f"opacity:1;transform:translateX({num(head_x - b_w / 2 - b_s)}px) scale(1)"),
            (3.1, f"opacity:0;transform:translateX({num(target - b_s)}px) scale(.2)"),
            (5.99, "opacity:0;transform:translateX(0) scale(1)"),
        ],
        extra=ORIGIN_CENTER,
    )
    return a, b


LEFT_MOB, RIGHT_MOB = (240, 166), (584, 166)


def _mob(svg: Svg, cx: float, cy: float, calm: str, asleep: str, angry: str) -> str:
    """The monster in three moods; exactly one mood track is visible at any moment."""
    body = (
        f"M{cx - 18} {cy + 15}C{cx - 22} {cy - 4} {cx - 14} {cy - 16} {cx} {cy - 16}"
        f"C{cx + 14} {cy - 16} {cx + 22} {cy - 4} {cx + 18} {cy + 15}Z"
        f"M{cx - 11} {cy - 13}l-5-9l10 5zM{cx + 11} {cy - 13}l5-9l-10 5z"
    )
    dots = f'<circle cx="{cx - 6}" cy="{cy - 3}" r="2.6"/><circle cx="{cx + 6}" cy="{cy - 3}" r="2.6"/>'
    fangs = f'<path class="eye" d="M{cx - 7} {cy + 5}l2.5 5l2.5-5zM{cx + 2} {cy + 5}l2.5 5l2.5-5z"/>'
    return (
        f'<circle class="char" cx="{cx}" cy="{cy}" r="30"/>'
        f'<g class="{calm}"><path class="mob" d="{body}"/><g class="eye">{dots}</g>{fangs}</g>'
        f'<g class="{asleep}"><path class="mob" d="{body}"/>'
        f'<path class="lid" d="M{cx - 9} {cy - 4}q3 3 6 0M{cx + 3} {cy - 4}q3 3 6 0"/>'
        f'<text class="t zz" x="{cx - 44}" y="{cy - 14}" font-size="22">{svg.text("t", "zz")}</text></g>'
        f'<g class="{angry}"><path class="mob angry" d="{body}"/><g class="eye">{dots}</g>{fangs}'
        f'<path class="lid" d="M{cx - 10} {cy - 11}l7 4M{cx + 10} {cy - 11}l-7 4"/></g>'
    )


def _moon(x: float, y: float) -> str:
    return f'<path class="moon" d="M{x + 13} {y - 18}a9 9 0 1 0 6 15a7 7 0 0 1-6-15z"/>'


def _alarm(x: float, y: float) -> str:
    return f'<circle class="alarm" cx="{x + 10}" cy="{y - 8}" r="10"/><path class="alarm-mark" d="M{x + 10} {y - 14}v6M{x + 10} {y - 3}v.5"/>'


def build_race(svg: Svg) -> dict:
    ctx = {}
    s = svg.s
    lane_y = {"sleep_chip": 116, "hit_chip": 216}
    chips = []
    for key, kind in (("sleep_chip", "sleep"), ("hit_chip", "hit")):
        y = lane_y[key]
        sx = 36 + chip_w(svg, key, False) / 2
        chips.append(chip(svg, key, kind, sx, y, _converge(svg, sx, y, LEFT_MOB)))
    ctx["left_chips"] = "".join(chips)

    # Without a lock deciding the order, the result flips between the two orders from run to run.
    start, stop, period = FLICKER
    asleep_a, awake_b = [(0, "opacity:0")], [(0, "opacity:0")]
    row_a, row_b = [(0, "opacity:.45")], [(0, "opacity:.45")]
    t, first = start, True
    while t < stop:
        asleep_a.append((t, f"opacity:{1 if first else 0}"))
        awake_b.append((t, f"opacity:{0 if first else 1}"))
        row_a.append((t, f"opacity:{1 if first else 0.35}"))
        row_b.append((t, f"opacity:{0.35 if first else 1}"))
        t, first = t + period, not first
    for track, rest in (
        (asleep_a, "opacity:0"),
        (awake_b, "opacity:0"),
        (row_a, "opacity:.45"),
        (row_b, "opacity:.45"),
    ):
        track.append((stop, rest))
    calm_left = [(0, "opacity:1"), (start, "opacity:0"), (stop, "opacity:1")]
    ctx["left_mob"] = _mob(
        svg, *LEFT_MOB, svg.anim(calm_left, "step-end"), svg.anim(asleep_a, "step-end"), svg.anim(awake_b, "step-end")
    )
    ctx["row_a"] = svg.anim(row_a, "step-end")
    ctx["row_b"] = svg.anim(row_b, "step-end")
    ctx["clash"] = svg.anim([(1.95, "opacity:0"), (2.05, "opacity:1")] + END)
    ctx["clash_badge"] = _pop(svg, 1.95)
    ctx["left_result"] = svg.anim([(2.15, "opacity:0"), (2.3, "opacity:1")] + END)

    # Zero-Tick: the hit arrived first, so the partition thread applies it, then Sleep; the monster always sleeps.
    lane = RIGHT_MOB[1]
    hit_w, sleep_w = chip_w(svg, "hit_chip", True), chip_w(svg, "sleep_chip", True)
    head_x, lane_x0 = 550, 324
    hit_q = head_x - hit_w / 2
    sleep_q = head_x - hit_w - 8 - sleep_w / 2
    if sleep_q - sleep_w / 2 < lane_x0:
        raise ValueError("Zero-Tick queue does not fit the lane")
    hit_s, sleep_s = lane_x0 + hit_w / 2, lane_x0 + sleep_w / 2
    target = RIGHT_MOB[0]
    hit_move, sleep_move = _queue_moves(svg, target, head_x, (hit_s, hit_q), (sleep_s, sleep_q, sleep_w))
    ctx["right_chips"] = chip(svg, "hit_chip", "hit", hit_s, lane, hit_move, "1") + chip(
        svg, "sleep_chip", "sleep", sleep_s, lane, sleep_move, "2"
    )
    ctx["right_mob"] = _mob(
        svg,
        *RIGHT_MOB,
        svg.anim([(0, "opacity:1"), (2.3, "opacity:0"), (5.6, "opacity:1")], "step-end"),
        svg.anim([(0, "opacity:0"), (3.1, "opacity:1"), (5.6, "opacity:0")], "step-end"),
        svg.anim([(0, "opacity:0"), (2.3, "opacity:1"), (3.1, "opacity:0")], "step-end"),
    )
    ctx["apply_hit"] = _ring(svg, 2.15)
    ctx["apply_sleep"] = _ring(svg, 2.95)
    ctx["right_result"] = svg.anim([(3.15, "opacity:0"), (3.35, "opacity:1")] + END)
    ctx["ok_badge"] = _pop(svg, 3.25)
    ctx["moon_a"], ctx["moon_r"] = _moon(36, 338), _moon(324, 338)
    ctx["alarm_b"] = _alarm(36, 374)
    for key, limit in (
        ("asleep", 214),
        ("awake1", 214),
        ("awake2", 214),
        ("thread1", 244),
        ("thread2", 244),
        ("owner", 220),
        ("same", 280),
    ):
        if svg.width("t", s[key], 22) > limit:
            raise ValueError(f"race text too wide: {key}")
    return ctx


# --- dupe: one rare sword sold to an NPC and traded away at the same moment ---------------------------------------

CLASSIC_TOP, ZERO_TOP = 16, 308
DUPE_LANE_X0, DUPE_CHECK_X, DUPE_LANE_END = 124, 292, 380
COINS = "M-9 2a9 4 0 1 0 18 0a9 4 0 1 0-18 0zM-9 2v6a9 4 0 0 0 18 0v-6M-9 -6a9 4 0 1 0 18 0a9 4 0 1 0-18 0z"


def _gain(svg: Svg, x: float, y: float, sword_cls: str = "") -> tuple[str, float]:
    """Sword now at the NPC, adena to the seller: the result of a completed sale."""
    s = svg.s
    npc_w = svg.width("t", s["at_npc"], 22)
    coin_x = x + 24 + npc_w + 26
    return (
        sword(x, y, sword_cls)
        + f'<text class="t text" x="{num(x + 22)}" y="{y + 8}" font-size="22">{svg.text("t", s["at_npc"])}</text>'
        + f'<path class="coins" transform="translate({num(coin_x)} {y})" d="{COINS}"/>'
        + f'<text class="t text" x="{num(coin_x + 16)}" y="{y + 8}" font-size="22">{svg.text("t", s["adena"])}</text>'
    ), coin_x + 16 + svg.width("t", s["adena"], 22)


def build_dupe(svg: Svg) -> dict:
    ctx = {}
    s = svg.s
    # Classic build: the sale and the trade run on two pool threads and both read the inventory before either
    # removes the sword, so both succeed.
    lanes = {"sell": CLASSIC_TOP + 104, "trade": CLASSIC_TOP + 176}
    chips = []
    for key, y in lanes.items():
        w = chip_w(svg, key, False)
        sx = DUPE_LANE_X0 + 10 + w / 2
        move = svg.anim(
            [
                (0.2, "opacity:0;transform:translateX(0) scale(1)"),
                (0.4, "opacity:1;transform:translateX(0) scale(1)"),
                (1.0, f"opacity:1;transform:translateX({num(DUPE_CHECK_X - sx)}px) scale(1)"),
                (1.5, f"opacity:1;transform:translateX({num(DUPE_CHECK_X - sx)}px) scale(1)"),
                (2.0, f"opacity:0;transform:translateX({num(DUPE_LANE_END - sx)}px) scale(.3)"),
                (5.99, "opacity:0;transform:translateX(0) scale(1)"),
            ],
            extra=ORIGIN_CENTER,
        )
        chips.append(chip(svg, key, key, sx, y, move))
        # Both threads see the sword at the same moment.
        label_y = y - 27 if key == "sell" else y + 46
        tw = svg.width("t", s["check"], 22)
        x0 = DUPE_CHECK_X - (tw + 22) / 2
        chips.append(
            f'<g class="{svg.anim(fade(1.0, 5.6, 0.12))}"><text class="t ok" x="{num(x0)}" y="{label_y}" font-size="22">'
            f"{svg.text('t', s['check'])}</text>{check_mark(x0 + tw + 6, label_y - 2)}</g>"
        )
    ctx["left_chips"] = "".join(chips)
    sy = CLASSIC_TOP + 140
    # The sword leaves the inventory once: in the classic build two copies come out of it anyway.
    ctx["inv_sword_l"] = ctx["inv_sword_r"] = svg.anim(
        [(0, "opacity:1"), (2.0, "opacity:1"), (2.2, "opacity:.15"), (5.7, "opacity:.15"), (5.95, "opacity:1")]
    )
    ctx["sword_y_l"], ctx["sword_y_r"] = sy, ZERO_TOP + 140
    ctx["sword_d"] = SWORD

    rx = DUPE_LANE_END + 30
    gain, gain_end = _gain(svg, rx, lanes["sell"], "dupe")
    p2 = sword(rx, lanes["trade"], "dupe") + (
        f'<text class="t text" x="{rx + 22}" y="{lanes["trade"] + 8}" font-size="22">{svg.text("t", s["at_p2"])}</text>'
    )
    if gain_end > 604:
        raise ValueError("classic sale result does not fit")
    twin = svg.anim(
        [(2.05, "opacity:0"), (2.25, "opacity:1")]
        + [(2.6 + 0.5 * k + d, f"opacity:{o}") for k in range(6) for d, o in ((0, 1), (0.25, 0.6))]
        + END
    )
    ctx["left_result"] = f'<g class="{twin}">{gain}{p2}</g>'
    dupe_w = round(svg.width("h", s["dupe"], 22) + 28)
    ctx["dupe"] = (
        f'<g class="{_pop(svg, 2.35)}">'
        f'<rect class="dupe" x="{rx - 12}" y="{sy - 17}" width="{dupe_w}" height="34" rx="17"/>'
        f'<text class="h dupetext" x="{rx - 12 + dupe_w / 2}" y="{sy + 8}" font-size="22" text-anchor="middle">{svg.text("h", s["dupe"])}</text></g>'
    )

    # Zero-Tick: both operations queue on the partition thread that owns the inventory; the first takes the
    # sword, the second finds it gone.
    lane = ZERO_TOP + 140
    sell_w, trade_w = chip_w(svg, "sell", True), chip_w(svg, "trade", True)
    head = DUPE_LANE_X0 + 16
    sell_q = head + sell_w / 2
    trade_q = head + sell_w + 8 + trade_w / 2
    sell_s, trade_s = 600 - sell_w / 2, 600 - trade_w / 2 + 40
    if trade_q + trade_w / 2 > 604:
        raise ValueError("Zero-Tick queue does not fit the lane")
    sell_move = svg.anim(
        [
            (0.2, "opacity:0;transform:translateX(0) scale(1)"),
            (0.35, "opacity:1;transform:translateX(0) scale(1)"),
            (1.2, f"opacity:1;transform:translateX({num(sell_q - sell_s)}px) scale(1)"),
            (1.8, f"opacity:1;transform:translateX({num(sell_q - sell_s)}px) scale(1)"),
            (2.1, f"opacity:0;transform:translateX({num(78 - sell_s)}px) scale(.2)"),
            (5.99, "opacity:0;transform:translateX(0) scale(1)"),
        ],
        extra=ORIGIN_CENTER,
    )
    trade_move = svg.anim(
        [
            (0.5, "opacity:0;transform:translateX(0)"),
            (0.65, "opacity:1;transform:translateX(0)"),
            (1.6, f"opacity:1;transform:translateX({num(trade_q - trade_s)}px)"),
            (2.4, f"opacity:1;transform:translateX({num(trade_q - trade_s)}px)"),
            (2.9, f"opacity:1;transform:translateX({num(head + trade_w / 2 - trade_s)}px)"),
            (5.6, f"opacity:1;transform:translateX({num(head + trade_w / 2 - trade_s)}px)"),
            (5.9, f"opacity:0;transform:translateX({num(head + trade_w / 2 - trade_s)}px)"),
            (5.99, "opacity:0;transform:translateX(0)"),
        ]
    )
    rejected = svg.anim(fade(3.0, 5.6, 0.12))
    ctx["right_chips"] = chip(svg, "sell", "sell", sell_s, lane, sell_move, "1") + chip(
        svg,
        "trade",
        "trade",
        trade_s,
        lane,
        trade_move,
        "2",
        f'<rect class="reject {svg.anim(fade(3.0, 5.6, 0.12, 0.6))}" RECT/>',
    )
    nw = svg.width("t", s["no_item"], 22)
    ctx["no_item"] = (
        f'<g class="{rejected}"><text class="t label" x="{num(head + trade_w / 2 - nw / 2)}" y="{lane - 26}"'
        f' font-size="22">{svg.text("t", s["no_item"])}</text></g>'
    )
    row_y = ZERO_TOP + 216
    gain_r, gain_r_end = _gain(svg, 50, row_y)
    ctx["right_result"] = (
        f'<g class="{svg.anim([(2.2, "opacity:0"), (2.4, "opacity:1")] + END)}">{gain_r}</g>'
        f'<g class="{_pop(svg, 3.4)}">'
        f'<circle class="badge-ok" cx="{num(gain_r_end + 26)}" cy="{row_y}" r="14"/>{check_mark(gain_r_end + 19, row_y + 1).replace("check", "tick")}</g>'
    )
    for key, limit in (("left_caption", 568), ("right_caption", 568), ("owner", 300)):
        if svg.width("t", s[key], 22) > limit:
            raise ValueError(f"race text too wide: {key}")
    if svg.width("t", s["check"], 22) + 22 > 2 * (DUPE_CHECK_X - DUPE_LANE_X0):
        raise ValueError("race check label too wide")
    return ctx


# --- spaces: every space on its own thread ----------------------------------------------------------------------

SPACES_DUR = 8.0
SPACES_LEGEND_Y, SPACES_CAPTION_Y = 384, 428
# name -> (cx, cy, r, label key, wake window or None, status key)
SPACES = {
    "world": (320, 181, 112, "world", (1.0, 6.2), "fight"),
    "inst1": (112, 98, 54, "instance", None, None),
    "inst2": (112, 264, 54, "instance", (6.6, 7.5), "boss"),
    "olymp": (532, 181, 74, "olympiad", (2.4, 4.8), "match"),
}


def _running(svg: Svg, on: float, off: float) -> str:
    """The thread-mark dot running along its lane in 0.8 s laps between `on` and `off`, still otherwise."""
    track = [(0, "transform:translateX(0)")]
    t = on
    while t + 0.8 <= off + 1e-9:
        track += [(t, "transform:translateX(0)"), (t + 0.79, "transform:translateX(28px)")]
        t += 0.8
    track.append((min(t + 0.01, SPACES_DUR), "transform:translateX(0)"))
    return svg.anim(track, "linear")


def build_spaces(svg: Svg) -> dict:
    ctx = {}
    s = svg.s
    out = []
    breathe_zzz = svg.anim(
        [(0, "opacity:.4"), (2, "opacity:.85"), (4, "opacity:.4"), (6, "opacity:.85"), (8, "opacity:.4")]
    )
    for i, (key, (cx, cy, r, label_key, wake, status_key)) in enumerate(SPACES.items()):
        label_size = 24 if key == "world" else 22
        role = "h" if key == "world" else "t"
        if svg.width(role, s[label_key], label_size) > 2 * r - 16:
            raise ValueError(f"space label too wide: {label_key}")
        # Each space breathes on its own phase so the sleeping scene does not pulse in unison.
        phase = i * 0.9
        breathe = svg.anim(
            [
                ((phase + k) % 4 + loop, f"opacity:{num(0.12 + 0.2 * (0.5 - 0.5 * math.cos(math.pi * k / 2)))}")
                for loop in (0, 4)
                for k in range(4)
            ]
        )
        out.append(f'<circle class="glow {breathe}" cx="{cx}" cy="{cy}" r="{r}" filter="url(#blur)"/>')
        out.append(f'<circle class="space" cx="{cx}" cy="{cy}" r="{r}"/>')
        status_y = cy + (44 if key == "world" else 18)
        lane_y = cy + (70 if key == "world" else 34)
        zzz = f'<text class="t zzz {breathe_zzz}" x="{cx}" y="{num(status_y)}" font-size="22" text-anchor="middle">{svg.text("t", s["zzz"])}</text>'
        dot_track = None
        if wake:
            on, off = wake
            lit = svg.anim(fade(on, off, 0.15))
            out.append(
                f'<g class="{lit}"><circle class="litglow" cx="{cx}" cy="{cy}" r="{r}" filter="url(#blur)"/>'
                f'<circle class="lit" cx="{cx}" cy="{cy}" r="{r}"/></g>'
            )
            out.append(f'<g class="{svg.anim(fade(on - 0.05, off, 0.15, 0, 1))}">{zzz}</g>')
            out.append(
                f'<text class="t woke {lit}" x="{cx}" y="{num(status_y)}" font-size="22" text-anchor="middle">'
                f"{svg.text('t', s[status_key])}</text>"
            )
            if svg.width("t", s[status_key], 22) > 2 * r - 24:
                raise ValueError(f"space status too wide: {status_key}")
            dot_track = _running(svg, on, off)
            if key == "world":
                throb = svg.anim(
                    [(on, "opacity:0")]
                    + [
                        (t, f"opacity:{o}")
                        for k in range(int((off - on) / 0.5))
                        for t, o in ((on + 0.5 * k + 0.1, 0.9), (on + 0.5 * k + 0.4, 0.2))
                    ]
                    + [(off, "opacity:0")]
                )
                out.append(f'<circle class="throb {throb}" cx="{cx}" cy="{cy}" r="{r}" filter="url(#blur)"/>')
                out.append(
                    f'<g class="{lit}"><path class="swords" d="M{cx - 14} {cy - 66}l28 28M{cx + 14} {cy - 66}l-28 28M{cx - 18} {cy - 46}l8 8M{cx + 18} {cy - 46}l-8 8"/></g>'
                )
        else:
            out.append(zzz)
        label_y = cy + (8 if key == "world" else -10)
        out.append(
            f'<text class="{role} sname" x="{cx}" y="{num(label_y)}" font-size="{label_size}" text-anchor="middle">'
            f"{svg.text(role, s[label_key])}</text>"
        )
        dot_cls = f" {dot_track}" if dot_track else ""
        out.append(
            f'<line class="thread" x1="{cx - 14}" y1="{num(lane_y)}" x2="{cx + 18}" y2="{num(lane_y)}"/>'
            f'<circle class="tdot{dot_cls}" cx="{cx - 14}" cy="{num(lane_y)}" r="4"/>'
        )
    ctx["spaces"] = "".join(out)
    legend, x = [], 32
    head = svg.text("t", s["legend"])
    legend.append(f'<text class="t note" x="{x}" y="{SPACES_LEGEND_Y}" font-size="22">{head}</text>')
    x += svg.width("t", s["legend"], 22) + 20
    running = _running(svg, 0, SPACES_DUR)
    for key, cls in (("legend_sleep", ""), ("legend_work", f" {running}")):
        ly = SPACES_LEGEND_Y - 7
        legend.append(
            f'<line class="thread" x1="{num(x)}" y1="{ly}" x2="{num(x + 32)}" y2="{ly}"/>'
            f'<circle class="tdot{cls}" cx="{num(x)}" cy="{ly}" r="4"/>'
            f'<text class="t note" x="{num(x + 44)}" y="{SPACES_LEGEND_Y}" font-size="22">{svg.text("t", s[key])}</text>'
        )
        x += 44 + svg.width("t", s[key], 22) + 28
    if x > 640:
        raise ValueError("spaces legend too wide")
    ctx["legend"] = "".join(legend)
    lines = wrap(svg, "t", s["caption"], 22, 576)
    if len(lines) > 2:
        raise ValueError("spaces caption needs more than two lines")
    ctx["caption"] = "".join(
        f'<text class="t caption" x="32" y="{SPACES_CAPTION_Y + 30 * i}" font-size="22">{svg.text("t", line)}</text>'
        for i, line in enumerate(lines)
    )
    ctx["note_y"] = SPACES_CAPTION_Y + 30 * len(lines) + 8
    ctx["height"] = ctx["note_y"] + 26
    ctx["card_h"] = ctx["height"] - 1
    return ctx


# --- driver ------------------------------------------------------------------------------------------------------

ANIMATIONS: Animations = {
    "hero": (PULSE_DUR, build_hero, 6.3),
    "tick": (TICK_DUR, build_tick, 5.5),
    "race": (RACE_DUR, build_race, 4.0),
    "dupe": (RACE_DUR, build_dupe, 4.4),
    "spaces": (SPACES_DUR, build_spaces, 3.6),
}


def render(fonts: Fonts) -> dict[Path, bytes]:
    return build(HERE, ANIMATIONS, fonts, lambda locale, name: PUBLIC_DIR / locale / f"zero-tick-{name}.svg")


if __name__ == "__main__":
    sys.exit(main(__doc__.splitlines()[0], FAMILIES, render))

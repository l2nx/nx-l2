"""Shared pipeline of the dev portal animations: kit tokens, font subsetting, keyframe tracks, output checks, CLI.

Each generator (`zero-tick/`, `mail/`) keeps only its scenes and imports this module; see their README files.
"""

from __future__ import annotations

import argparse
import base64
import html
import io
import json
import math
import re
import sys
import xml.etree.ElementTree as ElementTree
from collections.abc import Callable
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

PUBLIC_DIR = Path(__file__).resolve().parents[1] / "public"
LOCALES = ("en", "ru")
MAX_BYTES = 80 * 1024
# 12 px is the smallest text at a 360 px wide render; the viewBox is 640 wide, so 22 units render at 12.4 px.
MIN_FONT = 22

# @l2nx/ui dark tokens. Hex is derived here so the kit's oklch stays the single source of truth.
TOKENS = {
    "bg": (0.168, 0.009, 264.3),
    "card": (0.23, 0.017, 264.17),
    "surface": (0.255, 0.013, 258.37),
    "fg": (0.958, 0.004, 271.37),
    "muted": (0.82, 0.014, 264.44),
    "faint": (0.7, 0.012, 262.34),
    "accent": (0.8, 0.14, 195),
    "accent-low": (0.3, 0.06, 195),
    "accent-high": (0.91, 0.12, 192),
    "success": (0.789, 0.159, 161.84),
    "warning": (0.765, 0.14, 73.13),
    "violet": (0.606, 0.219, 292.72),
}
ALPHA_TOKENS = {"border": "rgba(255,255,255,.08)", "border-strong": "rgba(255,255,255,.18)"}


def _oklch_to_linear(lightness: float, chroma: float, hue: float) -> tuple[float, float, float]:
    a = chroma * math.cos(math.radians(hue))
    b = chroma * math.sin(math.radians(hue))
    l_ = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m_ = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s_ = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return (
        4.0767416621 * l_ - 3.3077115913 * m_ + 0.2309699292 * s_,
        -1.2684380046 * l_ + 2.6097574011 * m_ - 0.3413193965 * s_,
        -0.0041960863 * l_ - 0.7034186147 * m_ + 1.7076147010 * s_,
    )


def _linear_to_oklab(rgb: tuple[float, float, float]) -> tuple[float, float, float]:
    r, g, b = rgb
    l_ = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b) ** (1 / 3)
    m_ = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b) ** (1 / 3)
    s_ = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b) ** (1 / 3)
    return (
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    )


def oklch_to_hex(lightness: float, chroma: float, hue: float) -> str:
    """oklch -> sRGB hex with CSS Color 4 gamut mapping; kit tokens like accent sit just outside sRGB (negative red)."""

    def clip(rgb):
        return tuple(min(max(c, 0.0), 1.0) for c in rgb)

    def inside(rgb):
        return all(-1e-6 <= c <= 1 + 1e-6 for c in rgb)

    rgb = _oklch_to_linear(lightness, chroma, hue)
    if not inside(rgb):
        lo, hi, jnd = 0.0, chroma, 0.02
        rgb = clip(_oklch_to_linear(lightness, chroma, hue))
        if math.dist(_linear_to_oklab(rgb), _linear_to_oklab(_oklch_to_linear(lightness, chroma, hue))) >= jnd:
            while hi - lo > 1e-4:
                mid = (lo + hi) / 2
                cand = _oklch_to_linear(lightness, mid, hue)
                if inside(cand):
                    lo = mid
                    continue
                rgb = clip(cand)
                if math.dist(_linear_to_oklab(rgb), _linear_to_oklab(cand)) < jnd:
                    lo = mid
                else:
                    hi = mid
    out = []
    for c in clip(rgb):
        srgb = 12.92 * c if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055
        out.append(round(srgb * 255))
    return "#" + "".join(f"{v:02x}" for v in out)


PALETTE = {name: oklch_to_hex(*lch) for name, lch in TOKENS.items()} | ALPHA_TOKENS


# --- fonts -------------------------------------------------------------------------------------------------------

# Role -> (weight, fontsource packages in fallback order). Space Grotesk has no Cyrillic, so Russian headings fall
# back to Onest per glyph, the same stack the kit's --font-number uses.
ROLES = {
    "h": (600, ("space-grotesk", "onest")),
    "t": (500, ("onest",)),
}
SUBSET_FILES = ("latin", "cyrillic", "latin-ext", "cyrillic-ext", "symbols", "math")


def _store_version(files_dir: Path) -> tuple[int, ...]:
    # A plain string sort puts 5.2.9 after 5.2.10; the newest version must win so outputs match across machines.
    version = files_dir.parents[3].name.rsplit("@", 1)[1].split("_", 1)[0]
    return tuple(int(part) if part.isdigit() else 0 for part in version.split("."))


class Fonts:
    """@param families role -> the neutral CSS family name the subsets are exposed under"""

    def __init__(self, node_modules: Path, families: dict[str, str]):
        self.root = node_modules
        self.families = families
        self._cache: dict[tuple[str, str, int], TTFont | None] = {}

    def _files_dir(self, package: str) -> Path:
        direct = self.root / "@fontsource-variable" / package / "files"
        if direct.is_dir():
            return direct
        # pnpm keeps transitive packages only in its virtual store.
        hits = sorted(
            self.root.glob(f".pnpm/@fontsource-variable+{package}@*/node_modules/@fontsource-variable/{package}/files"),
            key=_store_version,
        )
        if not hits:
            sys.exit(f"@fontsource-variable/{package} not found under {self.root}")
        return hits[-1]

    def static(self, package: str, part: str, weight: int) -> TTFont | None:
        key = (package, part, weight)
        if key not in self._cache:
            path = self._files_dir(package) / f"{package}-{part}-wght-normal.woff2"
            font = None
            if path.is_file():
                font = instancer.instantiateVariableFont(TTFont(path), {"wght": weight})
                font.recalcTimestamp = False
            self._cache[key] = font
        return self._cache[key]

    def source_for(self, role: str, char: str) -> tuple[str, str]:
        weight, packages = ROLES[role]
        for package in packages:
            for part in SUBSET_FILES:
                font = self.static(package, part, weight)
                if font is not None and ord(char) in font.getBestCmap():
                    return package, part
        sys.exit(f"no font covers {char!r} (U+{ord(char):04X}) for role {role}")

    def measure(self, role: str, text: str, size: float) -> float:
        weight, _ = ROLES[role]
        width = 0
        for ch in text:
            font = self.static(*self.source_for(role, ch), weight)
            glyph = font.getBestCmap()[ord(ch)]
            width += font["hmtx"][glyph][0] / font["head"].unitsPerEm
        return width * size

    def face_css(self, role: str, chars: set[str]) -> str:
        weight, _ = ROLES[role]
        family = self.families[role]
        groups: dict[tuple[str, str], set[str]] = {}
        for ch in chars:
            groups.setdefault(self.source_for(role, ch), set()).add(ch)
        css = []
        for (package, part), group in sorted(groups.items()):
            font = TTFont(io.BytesIO(_save(self.static(package, part, weight))))
            font.recalcTimestamp = False
            options = subset.Options()
            options.flavor = "woff2"
            options.layout_features = ["kern"]
            options.name_IDs = []
            options.notdef_outline = False
            options.hinting = False
            subsetter = subset.Subsetter(options)
            subsetter.populate(unicodes=[ord(c) for c in group])
            subsetter.subset(font)
            font.flavor = "woff2"
            data = base64.b64encode(_save(font)).decode("ascii")
            ranges = ",".join(f"U+{ord(c):04X}" for c in sorted(group))
            css.append(
                f"@font-face{{font-family:'{family}';font-weight:{weight};"
                f"src:url(data:font/woff2;base64,{data}) format('woff2');unicode-range:{ranges}}}"
            )
        return "\n".join(css)


def _save(font: TTFont) -> bytes:
    buf = io.BytesIO()
    font.save(buf)
    return buf.getvalue()


# --- rendering ---------------------------------------------------------------------------------------------------


class Svg:
    """One animation in one locale: collects keyframes and the characters each font role must carry."""

    def __init__(self, name: str, dur: float, strings: dict, fonts: Fonts):
        self.name, self.dur, self.s, self.fonts = name, dur, strings, fonts
        self.chars = {role: set() for role in ROLES}
        self.css: list[str] = []
        self._n = 0

    def text(self, role: str, value: str) -> str:
        self.chars[role].update(value)
        return html.escape(value, quote=False)

    def width(self, role: str, value: str, size: float) -> float:
        return self.fonts.measure(role, value, size)

    def anim(self, stops: list[tuple[float, str]], timing: str = "ease-in-out", extra: str = "") -> str:
        """Every track runs the full loop with no delay, so all stay in phase and one negative animation-delay pauses the
        whole picture at a chosen moment."""
        self._n += 1
        cls = f"a{self._n}"
        stops = sorted(stops, key=lambda s: s[0])
        if stops[0][0] > 0:
            stops.insert(0, (0.0, stops[0][1]))
        if stops[-1][0] < self.dur:
            stops.append((self.dur, stops[-1][1]))
        frames = "".join(f"{_pct(t, self.dur)}{{{decl}}}" for t, decl in stops)
        self.css.append(f"@keyframes k{cls}{{{frames}}}")
        self.css.append(f".{cls}{{animation:k{cls} {num(self.dur)}s {timing} infinite;{extra}}}")
        return cls

    def render(self, template: str, ctx: dict, static_at: float) -> str:
        def sub(m: re.Match) -> str:
            role, key = m.group(1), m.group(2)
            if role in ROLES:
                return self.text(role, self.s[key])
            if role == "s":
                return html.escape(self.s[key])
            if key in ctx:
                return str(ctx[key])
            raise KeyError(f"{self.name}: unknown placeholder {m.group(0)}")

        body = re.sub(r"\{\{(?:(h|t|s):)?([\w.-]+)\}\}", sub, template)
        tokens = ";".join(f"--{k}:{v}" for k, v in PALETTE.items())
        fonts = "\n".join(self.fonts.face_css(role, chars) for role, chars in self.chars.items() if chars)
        head, text = self.fonts.families["h"], self.fonts.families["t"]
        style = (
            f"{fonts}\nsvg{{{tokens}}}\n"
            f".h{{font-family:'{head}',sans-serif;font-weight:600}}"
            f".t{{font-family:'{text}',sans-serif;font-weight:500}}\n"
            + "\n".join(self.css)
            + f"\n@media (prefers-reduced-motion:reduce){{*{{animation-delay:-{num(static_at)}s!important;"
            f"animation-play-state:paused!important}}}}"
        )
        return body.replace("/*STYLE*/", style)


def _pct(t: float, dur: float) -> str:
    return f"{num(round(t / dur * 100, 3))}%"


def num(v: float) -> str:
    return f"{v:.3f}".rstrip("0").rstrip(".")


def fade(on: float, off: float | None, ramp: float = 0.15, hi: float = 1, lo: float = 0) -> list[tuple[float, str]]:
    """Opacity track: lo, ramps to hi at `on`, back to lo at `off` (None keeps it up to the loop end)."""
    track = [(max(on - ramp, 0), f"opacity:{lo}"), (on, f"opacity:{hi}")]
    if off is not None:
        track += [(off, f"opacity:{hi}"), (off + ramp, f"opacity:{lo}")]
    return track


def wrap(svg: Svg, role: str, text: str, size: float, width: float) -> list[str]:
    lines, line = [], ""
    for word in text.split(" "):
        cand = f"{line} {word}".strip()
        if line and svg.width(role, cand, size) > width:
            lines.append(line)
            line = word
        else:
            line = cand
    return lines + [line]


# --- shared shapes ----------------------------------------------------------------------------------------------

ORIGIN_CENTER = "transform-box:fill-box;transform-origin:center"
CHIP_H, CHIP_PAD, BADGE_R = 34, 14, 13
SWORD = "M-2.6 -15L0 -19L2.6 -15V4H-2.6zM-8.5 4H8.5V7.5H-8.5zM-1.7 7.5H1.7V13H-1.7zM0 12.5a2.8 2.8 0 1 1 0 5.6a2.8 2.8 0 1 1 0-5.6z"


def chip_w(svg: Svg, key: str, numbered: bool) -> int:
    return round(svg.width("t", svg.s[key], 22) + 2 * CHIP_PAD + (2 * BADGE_R + 6 if numbered else 0))


def chip(
    svg: Svg, key: str, kind: str, cx: float, cy: float, cls: str, number: str | None = None, extra: str = ""
) -> str:
    w = chip_w(svg, key, number is not None)
    x = cx - w / 2
    badge, tx = "", x + CHIP_PAD
    if number is not None:
        bx = x + 6 + BADGE_R
        badge = (
            f'<circle class="num {kind}" cx="{num(bx)}" cy="{cy}" r="{BADGE_R}"/>'
            f'<text class="h numtext" x="{num(bx)}" y="{cy + 7.5}" font-size="22" text-anchor="middle">'
            f"{svg.text('h', number)}</text>"
        )
        tx = bx + BADGE_R + 6
    rect = f'x="{num(x)}" y="{cy - CHIP_H / 2}" width="{w}" height="{CHIP_H}" rx="{CHIP_H / 2}"'
    return (
        f'<g class="{cls}"><rect class="chip {kind}" {rect}/>{badge}<text class="t chiptext" x="{num(tx)}"'
        f' y="{cy + 7.5}" font-size="22">{svg.text("t", svg.s[key])}</text>{extra.replace("RECT", rect)}</g>'
    )


def sword(x: float, y: float, cls: str = "") -> str:
    return f'<path class="sword {cls}" transform="translate({num(x)} {num(y)})" d="{SWORD}"/>'


def check_mark(x: float, y: float) -> str:
    return f'<path class="check" d="M{num(x)} {num(y - 6)}l5 5l9-10"/>'


# --- driver ------------------------------------------------------------------------------------------------------

# name -> (loop seconds, builder, moment shown under prefers-reduced-motion)
Animations = dict[str, tuple[float, Callable[[Svg], dict], float]]


def build(here: Path, animations: Animations, fonts: Fonts, output: Callable[[str, str], Path]) -> dict[Path, bytes]:
    """Renders every animation in every locale; `output(locale, name)` is the path of one output."""
    out = {}
    for locale in LOCALES:
        strings = json.loads((here / f"strings.{locale}.json").read_text(encoding="utf-8"))
        for name, (dur, builder, static_at) in animations.items():
            svg = Svg(name, dur, strings[name], fonts)
            template = (here / f"{name}.svg.tmpl").read_text(encoding="utf-8")
            data = svg.render(template, builder(svg), static_at).encode("utf-8")
            _validate(f"{locale}/{name}", data)
            out[output(locale, name)] = data
    return out


def _validate(label: str, data: bytes) -> None:
    try:
        ElementTree.fromstring(data)
    except ElementTree.ParseError as e:
        raise ValueError(f"{label}: not well-formed XML: {e}") from e
    text = data.decode("utf-8")
    if re.search(r"<text(?![^>]*font-size=)[^>]*>", text):
        raise ValueError(f"{label}: a <text> without font-size falls back to 16 px")
    for size in map(float, re.findall(r'font-size="([\d.]+)"', text)):
        if size < MIN_FONT:
            raise ValueError(f"{label}: font-size {size} is below {MIN_FONT}")
    if len(data) > MAX_BYTES:
        raise ValueError(f"{label}: {len(data)} bytes exceeds {MAX_BYTES}")


def main(description: str, families: dict[str, str], render: Callable[[Fonts], dict[Path, bytes]]) -> int:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument(
        "--fonts", type=Path, required=True, help="node_modules holding @fontsource-variable/onest and space-grotesk"
    )
    parser.add_argument(
        "--check", action="store_true", help="fail when the committed outputs differ from a fresh build"
    )
    parser.add_argument("--palette", action="store_true", help="print the token table and exit")
    args = parser.parse_args()
    if args.palette:
        for name, value in PALETTE.items():
            lch = TOKENS.get(name)
            print(f"| {name} | {'oklch({} {} {})'.format(*lch) if lch else 'white, alpha'} | `{value}` |")
        return 0
    stale = []
    for path, data in render(Fonts(args.fonts, families)).items():
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                stale.append(path)
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        print(f"{path.relative_to(PUBLIC_DIR.parents[1])}  {len(data) / 1024:.1f} KB")
    if stale:
        print("stale outputs, run generate.py:", *stale, sep="\n  ")
        return 1
    return 0

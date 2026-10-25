#!/usr/bin/env python3
"""Generate replica/design/tokens.json + Compose theme (EverlyTokens.kt) from a base palette.
Placeholder palette = iOS-style system colours as seen in the original's screenshots.
replica-brand later edits BASE / PALETTE below and re-runs this; roles never change."""
import json, sys, os

OUT = sys.argv[1]

def hx(rgb): return "#%02x%02x%02x" % tuple(max(0, min(255, round(c))) for c in rgb)
def rgb(h): h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))
def mix(a, b, t):  # t = amount of a over b
    a, b = rgb(a), rgb(b); return hx(tuple(a[i]*t + b[i]*(1-t) for i in range(3)))
def lum(h):
    def c(v):
        v /= 255; return v/12.92 if v <= 0.04045 else ((v+0.055)/1.055)**2.4
    r, g, b = (c(v) for v in rgb(h)); return 0.2126*r + 0.7152*g + 0.0722*b
def ratio(a, b):
    la, lb = lum(a), lum(b); return (max(la, lb)+0.05)/(min(la, lb)+0.05)
def ink_for(fill, bg, target=4.6, dark=False):
    """Shift fill toward black (light theme) or white (dark theme) until it reads as text on bg."""
    toward = "#ffffff" if dark else "#000000"
    for i in range(0, 101):
        c = mix(toward, fill, i/100)
        if ratio(c, bg) >= target: return c
    return toward
def on_fill(fill):
    return "#ffffff" if ratio("#ffffff", fill) >= ratio("#1c1c1e", fill) else "#1c1c1e"

# ---- placeholder base roles (measured from screenshots, iOS grouped style) ----
BASE = {
  "light": {
    "bg": "#f2f2f7", "surface": "#ffffff", "surface-raised": "#ffffff", "border": "#e5e5ea",
    "border-input": "#8a8a8e", "text": "#000000", "text-muted": "#6c6c70",
    "accent": "#5856d6", "on-accent": "#ffffff", "danger": "#d70015", "success": "#1e7b34",
    "warning": "#a05a00", "today": "#5856d6", "weekend": "#d70015", "scrim": "#000000",
  },
  "dark": {
    "bg": "#000000", "surface": "#1c1c1e", "surface-raised": "#2c2c2e", "border": "#38383a",
    "border-input": "#8e8e93", "text": "#ffffff", "text-muted": "#aeaeb2",
    "accent": "#7d7aff", "on-accent": "#000000", "danger": "#ff6961", "success": "#30d158",
    "warning": "#ffb340", "today": "#7d7aff", "weekend": "#ff6961", "scrim": "#000000",
  },
}
# 12 habit colours, same order as the colour picker in S03
PALETTE = {
  "blue":   ("#007aff", "#0a84ff"), "teal":   ("#30b0c7", "#40c8e0"),
  "green":  ("#34c759", "#30d158"), "mint":   ("#00c7be", "#63e6e2"),
  "cyan":   ("#32ade6", "#64d2ff"), "indigo": ("#5856d6", "#5e5ce6"),
  "purple": ("#af52de", "#bf5af2"), "pink":   ("#ff2d55", "#ff375f"),
  "red":    ("#ff3b30", "#ff453a"), "orange": ("#ff9500", "#ff9f0a"),
  "yellow": ("#ffcc00", "#ffd60a"), "brown":  ("#a2845e", "#ac8e68"),
}

def build(theme):
    dark = theme == "dark"
    c = dict(BASE[theme]); surface = c["surface"]
    for name, (lf, df) in PALETTE.items():
        fill = df if dark else lf
        c[f"habit-{name}-fill"] = fill                                   # done cell, dot, swatch
        c[f"habit-{name}-on-fill"] = on_fill(fill)                       # check mark / "1/2" on done cell
        c[f"habit-{name}-row"] = mix(fill, surface, 0.22 if dark else 0.14)    # habit name row tint
        c[f"habit-{name}-covered"] = mix(fill, surface, 0.28 if dark else 0.18)  # covered-by-interval cell
        c[f"habit-{name}-idle"] = mix(fill, surface, 0.10 if dark else 0.06)    # faint future cell
        c[f"habit-{name}-outline"] = ink_for(fill, c[f"habit-{name}-idle"], 3.0, dark)  # due-cell outline (ui 3:1)
        c[f"habit-{name}-ink"] = ink_for(fill, c[f"habit-{name}-row"], 4.6, dark)       # subtitle text on row
    return c

light, dark = build("light"), build("dark")
pairs = [
  ["text", "bg"], ["text", "surface"], ["text-muted", "bg"], ["text-muted", "surface"],
  ["on-accent", "accent"], ["accent", "surface"], ["accent", "bg"], ["danger", "surface"],
  ["success", "surface"], ["warning", "surface"], ["today", "surface"], ["weekend", "surface"],
  ["border-input", "surface", "ui"],
]
for n in PALETTE:
    pairs += [[f"habit-{n}-on-fill", f"habit-{n}-fill", "ui"],      # check glyph is a graphic: 3:1
              [f"habit-{n}-ink", f"habit-{n}-row"],                 # "every 3 d. · next Oct 18"
              ["text", f"habit-{n}-row"],                           # habit name
              [f"habit-{n}-outline", f"habit-{n}-idle", "ui"],      # due cell border
              [f"habit-{n}-outline", "surface", "ui"]]              # 1dp edge on done cell / dot / swatch so light fills (yellow) stay visible

common = {
  "font": {"sans": "platform default (SF Pro on iOS, Roboto on Android) via FontFamily.Default",
           "mono": "FontFamily.Monospace"},
  "type": {
    "caption":  {"size": 11, "line": 13, "weight": 500, "use": "weekday labels, cell n/m"},
    "footnote": {"size": 13, "line": 18, "weight": 400, "use": "habit subtitle, section captions"},
    "subhead":  {"size": 15, "line": 20, "weight": 400, "use": "form section labels, folder headers (600)"},
    "body":     {"size": 17, "line": 22, "weight": 400, "use": "habit names (500), form rows, notes"},
    "title":    {"size": 17, "line": 22, "weight": 600, "use": "sheet titles"},
    "date":     {"size": 20, "line": 24, "weight": 600, "use": "day numbers in the grid header"},
    "headline": {"size": 22, "line": 28, "weight": 700, "use": "month label on the grid"},
    "display":  {"size": 34, "line": 41, "weight": 700, "use": "empty state / onboarding"}
  },
  "space": [0, 4, 8, 12, 16, 20, 24, 32, 48],
  "radius": {"cell": 10, "sm": 8, "md": 12, "card": 20, "sheet": 32, "pill": 999},
  "size": {"grid-cell": 40, "grid-column": 48, "grid-row": 52, "habit-column": 172,
           "touch-min": 44, "bottom-bar": 56, "swatch": 32, "dot": 10, "outline": 2},
  "shadow": {"bar": "0 4 16 rgba(0,0,0,.10)", "sheet": "0 -2 24 rgba(0,0,0,.12)"},
  "motion": {"fast": "120ms", "base": "220ms", "ease": "cubic-bezier(.2,.8,.2,1)",
             "check": "spring(dampingRatio=0.6, stiffness=600) scale 0.85->1"},
}

def write(theme, cols):
    t = {"_about": f"Design tokens ({theme}). PLACEHOLDER palette borrowed from the original's iOS-system colours "
                   "at the user's request; /replica-brand replaces BASE + PALETTE in gen_tokens.py. "
                   "Role names are stable. Check: python3 contrast.py tokens.json",
         "color": cols, "pairs": pairs, **common}
    with open(os.path.join(OUT, f"tokens{'' if theme=='light' else '.dark'}.json"), "w") as f:
        json.dump(t, f, indent=2, ensure_ascii=False)

os.makedirs(OUT, exist_ok=True)
write("light", light); write("dark", dark)

# ---- Compose theme (commonMain) ----
def kname(k): return "".join(p.capitalize() if i else p for i, p in enumerate(k.split("-")))
def argb(h): return "0xFF" + h.lstrip('#').upper()
base_keys = list(BASE["light"].keys())
lines = ["// GENERATED by replica/design/gen_tokens.py — do not edit by hand. Placeholder palette.",
         "package app.design", "", "import androidx.compose.runtime.Immutable",
         "import androidx.compose.ui.graphics.Color", "",
         "@Immutable", "data class HabitColors(", "    val fill: Color, val onFill: Color, val row: Color,",
         "    val covered: Color, val idle: Color, val outline: Color, val ink: Color,", ")", "",
         "enum class HabitColorKey { " + ", ".join(k.upper() for k in PALETTE) + " }", "",
         "@Immutable", "data class AppColors(",
         *[f"    val {kname(k)}: Color," for k in base_keys],
         "    val habit: Map<HabitColorKey, HabitColors>,", ") {",
         "    fun habit(key: HabitColorKey): HabitColors = habit.getValue(key)", "}", ""]
for theme, cols in (("Light", light), ("Dark", dark)):
    lines.append(f"val {theme}AppColors = AppColors(")
    lines += [f"    {kname(k)} = Color({argb(cols[k])})," for k in base_keys]
    lines.append("    habit = mapOf(")
    for n in PALETTE:
        g = lambda s: argb(cols[f'habit-{n}-{s}'])
        lines.append(f"        HabitColorKey.{n.upper()} to HabitColors(Color({g('fill')}), Color({g('on-fill')}), "
                     f"Color({g('row')}), Color({g('covered')}), Color({g('idle')}), Color({g('outline')}), Color({g('ink')})),")
    lines += ["    ),", ")", ""]
with open(os.path.join(OUT, "AppColors.kt"), "w") as f: f.write("\n".join(lines))
print("ok")

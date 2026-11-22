# /// script
# requires-python = ">=3.11"
# dependencies = ["numpy", "pillow", "matplotlib"]
# ///
"""Render a Forevercraft demo export (dev/DemoWorld.java) as a map of every era and its base.

    uv run tools/demo-map/render.py <export dir> <out.png> --jar <minecraft client jar>
"""

import argparse
import json
import math
import sys
import textwrap
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib import font_manager, patheffects
from matplotlib.patches import Circle, FancyArrowPatch

sys.path.insert(0, str(Path(__file__).parent))
from palette import BiomeColours, Textures  # noqa: E402
from terrain import inset, overview  # noqa: E402

BG = "#0e1116"
FG = "#e6edf3"
MUTED = "#8b949e"
LINE = "#30363d"
ERA = ["#7c5cd6", "#b04aa8", "#de4f7a", "#f2834e", "#fbbf24"]
ERA_TEXT = ["white", "white", "white", "white", "#1c1917"]
INSET_BLOCKS = 80
SCALE = 5
HALO = [patheffects.withStroke(linewidth=3.2, foreground=BG)]

for f in ("NotoSans-Regular.ttf", "NotoSans-Medium.ttf", "NotoSans-Bold.ttf"):
    p = Path("/usr/share/fonts/noto") / f
    if p.exists():
        font_manager.fontManager.addfont(str(p))
plt.rcParams["font.family"] = ["Noto Sans", "DejaVu Sans"]


def shorts(path: Path, count: int, n: int) -> np.ndarray:
    return np.fromfile(path, dtype=">i2").reshape(count, n, n).astype(np.int32)


def num(v: float) -> str:
    return f"{v:,.0f}".replace("-", "−")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("export", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("--jar", required=True)
    ap.add_argument("--seed-label", help="seed as typed, if it was text")
    args = ap.parse_args()

    meta = json.loads((args.export / "meta.json").read_text())
    meta["_dir"] = str(args.export)
    seed = args.seed_label or str(meta["seed"])
    tex = Textures(args.jar)
    colours = BiomeColours(meta["biomes"], tex)
    eras = meta["eras"]
    cfg = meta["config"]
    current = eras[-1]

    ov = meta["overview"]
    height, biome = shorts(args.export / ov["file"], 2, ov["size"])
    terrain = overview(height, biome, ov["step"], meta["seaLevel"], colours, tex)

    # Crop to the spawns plus a margin, portrait or square.
    xs = [e["spawn"][0] for e in eras]
    zs = [e["spawn"][2] for e in eras]
    pad = 3200
    w = max(xs) - min(xs) + 2 * pad
    h = max(zs) - min(zs) + 2 * pad
    w, h = max(w, int(h * 0.72)), max(h, w)
    cx, cz = (max(xs) + min(xs)) / 2, (max(zs) + min(zs)) / 2
    x0, x1, z0, z1 = cx - w / 2, cx + w / 2, cz - h / 2, cz + h / 2
    step = ov["step"]
    c0 = int((x0 - ov["x0"]) / step)
    r0 = int((z0 - ov["z0"]) / step)
    c1, r1 = c0 + int(w / step), r0 + int(h / step)
    terrain = terrain[max(r0, 0):r1, max(c0, 0):c1]
    x0, z0 = ov["x0"] + max(c0, 0) * step, ov["z0"] + max(r0, 0) * step
    x1, z1 = x0 + terrain.shape[1] * step, z0 + terrain.shape[0] * step

    # Layout in pixels.
    M, HEADER, MAP_H, FOOT = 56, 150, 1500, 64
    LEFT = 96  # room for Z tick labels
    map_w = int(MAP_H * (x1 - x0) / (z1 - z0))
    CELL, GAP, CAP = 400, 28, 92
    W = LEFT + map_w + 48 + 2 * CELL + GAP + M
    H = HEADER + MAP_H + FOOT
    fig = plt.figure(figsize=(W / 100, H / 100), dpi=100, facecolor=BG)

    def axes(x, y, w_, h_):
        return fig.add_axes([x / W, 1 - (y + h_) / H, w_ / W, h_ / H])

    fig.text(M / W, 1 - 52 / H, "Five eras, one world", color=FG, fontsize=30, fontweight="bold", va="center")
    fig.text(M / W, 1 - 100 / H,
             f"A real Forevercraft world after {len(eras) - 1} /resetworld runs (seed \"{seed}\"). "
             f"Each new spawn landed {num(cfg['minSpawnDistance'])}–{num(cfg['maxSpawnDistance'])} blocks from the last "
             f"and at least {num(cfg['minSpawnDistance'])} from every earlier one; the old bases and stashes stayed put.",
             color=MUTED, fontsize=13.5, va="center")

    ax = axes(LEFT, HEADER, map_w, MAP_H)
    draw_map(ax, terrain, (x0, x1, z0, z1), eras, cfg)

    gx = LEFT + map_w + 48
    for i, era in enumerate(eras):
        col, row = i % 2, i // 2
        x = gx + col * (CELL + GAP)
        y = HEADER + row * (CELL + CAP + 12)
        draw_inset(fig, axes(x, y, CELL, CELL), era, meta, colours, tex, current, (x, y + CELL), W, H)
    col, row = len(eras) % 2, len(eras) // 2
    draw_legend(fig, axes(gx + col * (CELL + GAP), HEADER + row * (CELL + CAP + 12), CELL, CELL + CAP), cfg)

    fig.text(M / W, 22 / H,
             f"Terrain sampled every {step} blocks from the world generator; base views are block-for-block from the "
             f"saved world. Coordinates on the map are true coordinates; players only ever see their era's spawn as 0, 0.",
             color=MUTED, fontsize=11, va="bottom")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(args.out, dpi=100, facecolor=BG)
    print(f"wrote {args.out} ({W}x{H})")


def draw_map(ax, terrain, bounds, eras, cfg):
    x0, x1, z0, z1 = bounds
    ax.imshow(terrain, extent=(x0, x1, z1, z0), interpolation="lanczos", zorder=0)
    ax.set_xlim(x0, x1)
    ax.set_ylim(z1, z0)
    ax.set_facecolor(BG)
    for s in ax.spines.values():
        s.set_color(LINE)
    ax.tick_params(colors=MUTED, labelsize=10, length=4, direction="out")
    ticks = 5000
    ax.set_xticks(np.arange(math.ceil(x0 / ticks) * ticks, x1, ticks))
    ax.set_yticks(np.arange(math.ceil(z0 / ticks) * ticks, z1, ticks))
    ax.xaxis.set_major_formatter(lambda v, _: num(v))
    ax.yaxis.set_major_formatter(lambda v, _: num(v))
    ax.xaxis.tick_top()
    ax.grid(color="white", alpha=0.09, lw=0.8)

    lo, hi = cfg["minSpawnDistance"], cfg["maxSpawnDistance"]
    spawns = np.array([(e["spawn"][0], e["spawn"][2]) for e in eras], dtype=float)
    prev, last = spawns[:-1], spawns[-2]

    # Where the newest era could have gone: the ring around the one before it, minus every earlier exclusion.
    gx, gz = np.meshgrid(np.linspace(x0, x1, 500), np.linspace(z0, z1, 500))
    d_last = np.hypot(gx - last[0], gz - last[1])
    ok = (d_last >= lo) & (d_last <= hi)
    for p in prev:
        ok &= np.hypot(gx - p[0], gz - p[1]) >= lo
    if cfg["preferredMaxRadius"] > 0:
        ok &= np.hypot(gx, gz) <= cfg["preferredMaxRadius"]
    ax.contourf(gx, gz, ok.astype(float), levels=[0.5, 1.5], colors=[(1, 1, 1, 0.16)], zorder=2)
    ax.contour(gx, gz, ok.astype(float), levels=[0.5], colors=[(1, 1, 1, 0.9)], linewidths=1.4, zorder=2)
    margin = np.where(ok, np.minimum(d_last - lo, hi - d_last), -1)
    for p in prev:
        margin = np.minimum(margin, np.hypot(gx - p[0], gz - p[1]) - lo)
    iz, ix = np.unravel_index(np.argmax(margin), margin.shape)
    ax.text(gx[iz, ix], gz[iz, ix], f"where era {len(eras) - 1}\ncould land", color="white", fontsize=11.5,
            ha="center", va="center", style="italic", path_effects=HALO, zorder=6)

    for i, p in enumerate(prev):
        ax.add_patch(Circle(p, lo, fill=False, ec=ERA[i], lw=1.8, ls=(0, (6, 4)), zorder=3,
                            path_effects=[patheffects.withStroke(linewidth=3.4, foreground=(0, 0, 0, 0.35))]))
    if cfg["preferredMaxRadius"] > 0:
        ax.add_patch(Circle((0, 0), cfg["preferredMaxRadius"], fill=False, ec="white", lw=1, ls=(0, (2, 4)),
                            alpha=0.5, zorder=3))
    if x0 < 0 < x1 and z0 < 0 < z1 and min(np.hypot(*p) for p in spawns) > 2500:
        ax.plot([0], [0], marker="+", ms=13, mew=2, color="white", zorder=7, path_effects=HALO)
        ax.annotate("true 0, 0", (0, 0), xytext=(9, -9), textcoords="offset points", color="white",
                    fontsize=10, path_effects=HALO, zorder=7)

    for i in range(len(spawns) - 1):
        a, b = spawns[i], spawns[i + 1]
        ax.add_patch(FancyArrowPatch(a, b, arrowstyle="-|>", mutation_scale=20, shrinkA=17, shrinkB=17, lw=2.2,
                                     color="white", alpha=0.95, connectionstyle="arc3,rad=0.12", zorder=8,
                                     path_effects=[patheffects.withStroke(linewidth=4.5, foreground=BG)]))
        mid = (a + b) / 2
        normal = np.array([b[1] - a[1], -(b[0] - a[0])]) / np.hypot(*(b - a))
        label = mid - normal * np.hypot(*(b - a)) * 0.06 - normal * 650
        ax.text(*label, f"{num(np.hypot(*(b - a)))}", color="white", fontsize=11, ha="center", va="center",
                path_effects=HALO, zorder=9)

    centre = spawns.mean(axis=0)
    for i, (era, p) in enumerate(zip(eras, spawns)):
        ax.plot(*p, "o", ms=26, mfc=ERA[i], mec="white", mew=2.6, zorder=10)
        ax.text(*p, str(era["index"]), color=ERA_TEXT[i], fontsize=13, fontweight="bold", ha="center",
                va="center", zorder=11)
        away = p - centre
        away = away / (np.hypot(*away) or 1)
        dx, dz = away * 30
        ha = "left" if dx >= 0 else "right"
        title = f"Era {era['index']}" + ("  (now)" if i == len(eras) - 1 else "")
        ax.annotate(title, p, xytext=(dx, -dz + 8), textcoords="offset points", ha=ha, va="center",
                    color="white", fontsize=14.5, fontweight="bold", path_effects=HALO, zorder=11)
        ax.annotate(f"{num(p[0])}, {num(p[1])}", p, xytext=(dx, -dz - 10), textcoords="offset points", ha=ha,
                    va="center", color=FG, fontsize=10.5, path_effects=HALO, zorder=11)

    sx = x0 + (x1 - x0) * 0.06
    sz = z1 - (z1 - z0) * 0.04
    ax.plot([sx, sx + 5000], [sz, sz], color="white", lw=3, solid_capstyle="butt", zorder=9, path_effects=HALO)
    ax.text(sx + 2500, sz - (z1 - z0) * 0.012, "5,000 blocks", color="white", ha="center", va="bottom",
            fontsize=11, path_effects=HALO, zorder=9)
    ax.text(x1 - (x1 - x0) * 0.03, z1 - (z1 - z0) * 0.03, "N ↑", color="white", ha="right", va="bottom",
            fontsize=12, fontweight="bold", path_effects=HALO, zorder=9)


def draw_inset(fig, ax, era, meta, colours, tex, current, caption_at, W, H):
    i = era["index"]
    inset_meta = next(s for s in meta["insets"] if s["era"] == i)
    n = inset_meta["size"]
    data = np.fromfile(Path(meta["_dir"]) / inset_meta["file"], dtype=">i2").reshape(5, n, n).astype(np.int32)
    img = inset(data, meta["blocks"], colours, tex, INSET_BLOCKS, SCALE)
    off = (n - INSET_BLOCKS) // 2
    bx0, bz0 = inset_meta["x0"] + off, inset_meta["z0"] + off
    ax.imshow(img, extent=(bx0, bx0 + INSET_BLOCKS, bz0 + INSET_BLOCKS, bz0), interpolation="nearest")
    ax.set_xlim(bx0, bx0 + INSET_BLOCKS)
    ax.set_ylim(bz0 + INSET_BLOCKS, bz0)
    ax.set_xticks([])
    ax.set_yticks([])
    for s in ax.spines.values():
        s.set_color(ERA[i])
        s.set_linewidth(3)

    sx, _, sz = era["spawn"]
    ax.plot(sx + 0.5, sz + 0.5, marker="+", ms=15, mew=2.6, color="white", zorder=5,
            path_effects=[patheffects.withStroke(linewidth=4.5, foreground=BG)])
    for st in era["stashes"]:
        px, _, pz = st["pos"]
        ax.plot(px + 0.5, pz + 0.5, "o", ms=17, mfc="none", mec=ERA[i], mew=2.6, zorder=5,
                path_effects=[patheffects.withStroke(linewidth=5, foreground="white")])
        away = np.array([px - sx, pz - sz], float)
        away = away / (np.hypot(*away) or 1)
        ax.annotate(st["player"], (px + 0.5, pz + 0.5), xytext=(away[0] * 16, -away[1] * 16),
                    textcoords="offset points", ha="left" if away[0] > 0.3 else "right" if away[0] < -0.3 else "center",
                    va="center", color="white", fontsize=10.5, fontweight="bold", path_effects=HALO, zorder=6)
    ax.text(0.03, 0.97, str(i), transform=ax.transAxes, color=ERA_TEXT[i], fontsize=13, fontweight="bold",
            ha="left", va="top", zorder=7,
            bbox=dict(boxstyle="circle,pad=0.35", fc=ERA[i], ec="white", lw=2))

    x, y = caption_at
    style = era["style"].capitalize() or "Base"
    is_now = era is current
    head = f"Era {i}  ·  {style} base" + ("  ·  current" if is_now else "")
    fig.text(x / W, 1 - (y + 22) / H, head, color=ERA[i], fontsize=14, fontweight="bold", va="center")
    sx_now, sz_now = sx - current["offsetX"], sz - current["offsetZ"]
    if is_now:
        line2 = f"Spawn reads 0, 0 (true {num(sx)}, {num(sz)})"
        line3 = "Everyone starts here with nothing"
    else:
        line2 = f"Now reads {num(sx_now)}, {num(sz_now)}  (true {num(sx)}, {num(sz)})"
        chests = sum(s["chests"] for s in era["stashes"])
        line3 = f"{len(era['stashes'])} stashes, {chests} chests: " + ", ".join(s["player"] for s in era["stashes"])
    fig.text(x / W, 1 - (y + 47) / H, line2, color=FG, fontsize=11.5, va="center")
    fig.text(x / W, 1 - (y + 69) / H, line3, color=MUTED, fontsize=11.5, va="center")


def draw_legend(fig, ax, cfg):
    ax.set_axis_off()
    ax.set_xlim(0, 1)
    ax.set_ylim(1, 0)
    lo, hi = num(cfg["minSpawnDistance"]), num(cfg["maxSpawnDistance"])
    items = [
        ("era", "Spawn of an era. Bases grow around it."),
        ("arrow", "Each /resetworld: distance to the next spawn"),
        ("ring", f"{lo} keep-out around every earlier spawn"),
        ("zone", f"Where the newest era could land: {lo}–{hi} from the last spawn, outside every keep-out"),
        ("pref", f"Spawns prefer to stay within {num(cfg['preferredMaxRadius'])} of true 0, 0 (strongholds)"),
        ("spawn", "Base views: + is the era's spawn point"),
        ("stash", "Stacked chests with a signed name and date, left where each player stood"),
    ]
    ax.text(0.0, 0.03, "Reading the map", color=FG, fontsize=15, fontweight="bold", va="top")
    y = 0.12
    for kind, text in items:
        wrapped = textwrap.fill(text, 44)
        lines = wrapped.count("\n") + 1
        y += 0.021 * lines
        gx = 0.05
        if kind == "era":
            ax.plot(gx, y, "o", ms=17, mfc=ERA[2], mec="white", mew=2)
        elif kind == "arrow":
            ax.add_patch(FancyArrowPatch((gx - 0.04, y), (gx + 0.04, y), arrowstyle="-|>", mutation_scale=14,
                                         lw=2, color="white"))
        elif kind == "ring":
            ax.plot([gx - 0.04, gx + 0.04], [y, y], color=ERA[1], lw=1.8, ls=(0, (4, 3)))
        elif kind == "zone":
            ax.add_patch(plt.Rectangle((gx - 0.04, y - 0.018), 0.08, 0.036, fc=(1, 1, 1, 0.18), ec="white", lw=1.2))
        elif kind == "pref":
            ax.plot([gx - 0.04, gx + 0.04], [y, y], color="white", lw=1, ls=(0, (2, 3)), alpha=0.7)
        elif kind == "spawn":
            ax.plot(gx, y, marker="+", ms=13, mew=2.4, color="white")
        elif kind == "stash":
            ax.plot(gx, y, "o", ms=13, mfc="none", mec=ERA[3], mew=2.4,
                    path_effects=[patheffects.withStroke(linewidth=4.5, foreground="white")])
        ax.text(0.14, y, wrapped, color=FG, fontsize=11.5, va="center", linespacing=1.3)
        y += 0.021 * lines + 0.05


if __name__ == "__main__":
    main()

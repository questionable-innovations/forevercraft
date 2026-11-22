"""Turns the exported height/biome grid and block scans into RGB images."""

import numpy as np
from PIL import Image

from palette import BiomeColours, Textures, rgb

# Open ground per biome (texture name); anything unlisted is tinted grass.
SURFACE = {
    "desert": "sand", "beach": "sand", "snowy_beach": "sand", "badlands": "red_sand", "wooded_badlands": "red_sand",
    "eroded_badlands": "orange_terracotta", "stony_peaks": "stone", "stony_shore": "stone",
    "windswept_gravelly_hills": "gravel", "mushroom_fields": "mycelium_top", "mangrove_swamp": "mud",
    "old_growth_pine_taiga": "podzol_top", "old_growth_spruce_taiga": "podzol_top",
    "snowy_plains": "snow", "snowy_taiga": "snow", "ice_spikes": "snow", "snowy_slopes": "snow",
    "frozen_peaks": "snow", "jagged_peaks": "snow", "grove": "snow", "frozen_river": "ice",
}
# (canopy cover, leaves) per biome.
TREES = {
    "forest": (0.6, "oak"), "flower_forest": (0.45, "oak"), "birch_forest": (0.65, "birch"),
    "old_growth_birch_forest": (0.75, "birch"), "dark_forest": (0.92, "dark_oak"), "taiga": (0.6, "spruce"),
    "old_growth_pine_taiga": (0.7, "spruce"), "old_growth_spruce_taiga": (0.75, "spruce"),
    "snowy_taiga": (0.45, "spruce"), "grove": (0.4, "spruce"), "jungle": (0.92, "jungle"),
    "sparse_jungle": (0.45, "jungle"), "bamboo_jungle": (0.8, "jungle"), "savanna": (0.12, "acacia"),
    "savanna_plateau": (0.16, "acacia"), "windswept_savanna": (0.1, "acacia"), "swamp": (0.35, "oak"),
    "mangrove_swamp": (0.75, "mangrove"), "wooded_badlands": (0.3, "oak"), "windswept_forest": (0.5, "spruce"),
    "windswept_hills": (0.08, "spruce"), "cherry_grove": (0.55, "cherry"), "plains": (0.02, "oak"),
    "sunflower_plains": (0.02, "oak"), "meadow": (0.03, "oak"),
}
FROZEN_WATER = {"frozen_ocean", "deep_frozen_ocean", "frozen_river"}
TEXTURE_OF = {"grass_block": "grass_block_top", "sand": "sand"}


def _noise(shape, cell, rng):
    """Blocky value noise: clumps of roughly cell x cell samples."""
    h, w = shape
    coarse = rng.random((h // cell + 2, w // cell + 2)).astype(np.float32)
    fine = np.repeat(np.repeat(coarse, cell, 0), cell, 1)[:h, :w]
    return 0.6 * fine + 0.4 * rng.random(shape).astype(np.float32)


def _smooth_noise(shape, cell, rng):
    """Bilinear value noise with features about cell samples across."""
    h, w = shape
    coarse = rng.random((h // cell + 2, w // cell + 2)).astype(np.float32)
    img = Image.fromarray(coarse).resize(((w // cell + 2) * cell, (h // cell + 2) * cell), Image.BILINEAR)
    return np.asarray(img)[:h, :w]


def fill_pits(height: np.ndarray, depth=6, passes=3) -> np.ndarray:
    """Noise caves open at the surface; at this scale they read as fake ponds, so level them with their surroundings."""
    h = height.astype(np.float32)
    for _ in range(passes):
        around = np.stack([np.roll(np.roll(h, dy, 0), dx, 1) for dy in (-1, 0, 1) for dx in (-1, 0, 1)])
        median = np.median(around, axis=0)
        h = np.where(h < median - depth, median, h)
    return h


def hillshade(height: np.ndarray, cell: float, z: float, azimuth=315.0, altitude=40.0) -> np.ndarray:
    """Lambertian shade normalised so flat ground is 1.0."""
    gy, gx = np.gradient(height.astype(np.float32) * z, cell)
    slope = np.arctan(np.hypot(gx, gy))
    aspect = np.arctan2(-gx, gy)
    az, alt = np.radians(360.0 - azimuth + 90.0), np.radians(altitude)
    shade = np.sin(alt) * np.cos(slope) + np.cos(alt) * np.sin(slope) * np.cos(az - aspect)
    return shade / np.sin(alt)


def overview(height: np.ndarray, biome: np.ndarray, step: int, sea: int, colours: BiomeColours,
             tex: Textures, seed=7) -> np.ndarray:
    rng = np.random.default_rng(seed)
    height = fill_pits(height)
    n = len(colours.ids)
    surface = np.zeros((n, 3), np.float32)
    leaves = np.zeros((n, 3), np.float32)
    cover = np.zeros(n, np.float32)
    leaf_tex = {"oak": "oak_leaves", "birch": "birch_leaves", "spruce": "spruce_leaves", "dark_oak": "dark_oak_leaves",
                "jungle": "jungle_leaves", "acacia": "acacia_leaves", "mangrove": "mangrove_leaves",
                "cherry": "cherry_leaves"}
    for i, name in enumerate(colours.ids):
        if name in SURFACE:
            surface[i] = tex.average(SURFACE[name])
        else:
            surface[i] = tex.average("grass_block_top") * colours.grass[i]
        if name == "badlands":
            surface[i] = 0.6 * surface[i] + 0.4 * tex.average("orange_terracotta")
        c, leaf = TREES.get(name, (0.0, "oak"))
        cover[i] = c
        t = tex.average(leaf_tex[leaf])
        tint = colours.tint(leaf_tex[leaf], i)
        leaves[i] = t * tint if tint is not None else t
        if name in ("snowy_taiga", "grove"):
            leaves[i] = 0.55 * leaves[i] + 0.45 * tex.average("snow")

    b = np.clip(biome, 0, n - 1)
    names = np.array(colours.ids)
    # Biome edges are hard at this sample spacing; the game blends tints, so blur them too.
    land = blur(surface[b], 2)
    canopy_rgb = blur(leaves[b], 2)
    clumps = _noise(height.shape, 3, rng)
    canopy = clumps < blur(cover[b][..., None], 2)[..., 0]
    shade_var = (0.78 + 0.3 * rng.random(height.shape)).astype(np.float32)[..., None]
    land = np.where(canopy[..., None], canopy_rgb * shade_var * 0.92, land * (0.96 + 0.08 * clumps[..., None]))

    # Bare rock on high ground that is not already snow or stone.
    bare = blur((~np.isin(names, list(SURFACE)))[b].astype(np.float32)[..., None], 2)
    rock = np.clip((height - 150) / 70.0, 0, 0.7)[..., None] * bare
    land = land * (1 - rock) + tex.average("stone") * rock

    shade = hillshade(height, step, 2.4)
    land = land * np.clip(0.55 + 0.45 * shade, 0.45, 1.35)[..., None]

    depth = np.clip(sea - height, 0, None).astype(np.float32)
    water = blur(colours.water[b], 6)
    shallow = water * 0.5 + rgb(0xB9D3C4) * 0.5
    deep = water * 0.3 + rgb(0x0B1E3A) * 0.7
    t = np.clip(blur(depth[..., None], 1) / 34.0, 0, 1) ** 0.6
    sea_rgb = shallow * (1 - t) + deep * t
    sea_rgb = sea_rgb * np.clip(0.88 + 0.12 * hillshade(height, step, 1.0), 0.78, 1.12)[..., None]
    # Frozen water: broken ice over dark water rather than a flat sheet.
    frozen = blur(np.isin(names, list(FROZEN_WATER))[b].astype(np.float32)[..., None], 3)[..., 0]
    floes = 0.7 * _smooth_noise(height.shape, 4, rng) + 0.3 * _smooth_noise(height.shape, 2, rng)
    ice = (floes < 0.7 * frozen)[..., None]
    sea_rgb = np.where(ice, rgb(0xD5E2EC) * (0.9 + 0.1 * clumps[..., None]), sea_rgb)

    img = np.where((height < sea)[..., None], sea_rgb, land)
    return np.clip(img, 0, 1)


def blur(a: np.ndarray, r: int) -> np.ndarray:
    """Box blur over the first two axes (edges clamped)."""
    if r <= 0:
        return a
    k = 2 * r + 1
    out = np.pad(a.astype(np.float32), ((r, r), (r, r), (0, 0)), mode="edge")
    c = np.cumsum(out, axis=0)
    out = (np.concatenate([c[k - 1:k], c[k:] - c[:-k]], axis=0)) / k
    c = np.cumsum(out, axis=1)
    return (np.concatenate([c[:, k - 1:k], c[:, k:] - c[:, :-k]], axis=1)) / k


def inset(data: np.ndarray, palette: list[dict], colours: BiomeColours, tex: Textures, crop: int,
          scale=4) -> np.ndarray:
    """Top-down block render: each block is its texture at scale x scale, shaded by height."""
    top, top_y, floor, floor_y, biome = data
    size = top.shape[0]
    o = (size - crop) // 2
    sl = (slice(o, o + crop), slice(o, o + crop))
    top, top_y, floor, floor_y, biome = top[sl], top_y[sl], floor[sl], floor_y[sl], biome[sl]

    ids = [p["id"].split(":")[1] for p in palette]
    tiles: dict[tuple[int, int], np.ndarray] = {}

    def tile(block: int, b: int) -> np.ndarray:
        key = (block, b)
        if key not in tiles:
            name = ids[block]
            texture = tex.resolve(name)
            if texture is None:
                t = np.ones((scale, scale, 4), np.float32)
                t[..., :3] = rgb(palette[block]["mapColor"])
            else:
                t = tex.rgba(texture, scale).copy()
                tint = colours.tint(name, b)
                if tint is not None:
                    t[..., :3] *= tint
            if t[..., 3].mean() < 0.999:
                # Cut-out blocks (plants, leaves, torches) sit on ground we did not export: use grass or sand.
                under = "sand" if name in ("dead_bush", "cactus") else "grass_block"
                ground = tex.rgba(TEXTURE_OF[under], scale)[..., :3] * (colours.grass[b] if under == "grass_block" else 1)
                if name.endswith("_leaves"):
                    ground = t[..., :3].mean(axis=(0, 1)) * 0.55
                a = t[..., 3:]
                t = np.concatenate([t[..., :3] * a + ground * (1 - a), np.ones_like(a)], axis=-1)
            tiles[key] = t[..., :3]
        return tiles[key]

    out = np.zeros((crop * scale, crop * scale, 3), np.float32)
    for z in range(crop):
        for x in range(crop):
            t = tile(int(top[z, x]), int(biome[z, x]))
            if ids[int(top[z, x])] == "water" or top_y[z, x] != floor_y[z, x]:
                depth = top_y[z, x] - floor_y[z, x]
                a = min(0.5 + depth * 0.07, 0.92)
                w = tile(ids.index("water"), int(biome[z, x])) if "water" in ids else t
                t = tile(int(floor[z, x]), int(biome[z, x])) * (1 - a) + w * a
            out[z * scale:(z + 1) * scale, x * scale:(x + 1) * scale] = t

    h = top_y.astype(np.float32)
    north = np.roll(h, 1, 0)
    west = np.roll(h, 1, 1)
    nw = np.roll(north, 1, 1)
    f = np.clip(1 + 0.06 * (h - north) + 0.04 * (h - west), 0.7, 1.25)
    f = np.where(nw - h >= 2, f * 0.78, f)
    f = np.repeat(np.repeat(f, scale, 0), scale, 1)[..., None]
    return np.clip(out * f, 0, 1)


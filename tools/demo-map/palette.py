"""Block and biome colours taken from the vanilla client jar's textures and colormaps."""

import io
import zipfile

import numpy as np
from PIL import Image

WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")
SUFFIXES = ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_wall_sign",
            "_wall_hanging_sign", "_hanging_sign", "_sign", "_carpet", "_pane", "_door", "_trapdoor", "_wood")
TEXTURE = {
    "grass_block": "grass_block_top", "tall_grass": "tall_grass_top", "large_fern": "large_fern_top",
    "water": "water_still", "lava": "lava_still", "snow": "snow", "snow_block": "snow",
    "dirt_path": "dirt_path_top", "farmland": "farmland_moist", "wheat": "wheat_stage7",
    "carrots": "carrots_stage3", "potatoes": "potatoes_stage3", "beetroots": "beetroots_stage3",
    "smooth_sandstone": "sandstone_top", "cut_sandstone": "sandstone_top", "chest": "oak_planks",
    "bell": "gold_block", "bookshelf": "oak_planks", "cauldron": "cauldron_top", "water_cauldron": "cauldron_top",
    "wall_torch": "torch", "campfire": "campfire_log_lit", "glass_pane": "glass", "hay_block": "hay_block_top",
    "melon": "melon_top", "pumpkin": "pumpkin_top", "carved_pumpkin": "pumpkin_top", "cactus": "cactus_top",
    "bamboo": "bamboo_stalk", "sweet_berry_bush": "sweet_berry_bush_stage3", "tall_seagrass": "tall_seagrass_top",
    "moss_carpet": "moss_block", "podzol": "podzol_top", "mycelium": "mycelium_top", "brewing_stand": "brewing_stand_base",
    "smooth_stone_slab": "smooth_stone", "petrified_oak_slab": "oak_planks", "sunflower": "sunflower_front",
    "lilac": "lilac_top", "rose_bush": "rose_bush_top", "peony": "peony_top", "pitcher_plant": "pitcher_plant_top",
}
GRASS_TINT = {"grass_block", "grass", "short_grass", "tall_grass", "fern", "large_fern", "sugar_cane"}
FOLIAGE_TINT = {"oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine"}
FIXED_TINT = {"birch_leaves": 0x80A755, "spruce_leaves": 0x619961, "lily_pad": 0x208030}


def rgb(c: int) -> np.ndarray:
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], dtype=np.float32) / 255.0


class Textures:
    def __init__(self, jar_path: str):
        self.jar = zipfile.ZipFile(jar_path)
        self.names = {n[len("assets/minecraft/textures/block/"):-4] for n in self.jar.namelist()
                      if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")}
        self.cache: dict[tuple[str, int], np.ndarray] = {}
        self.grass_map = self._image("assets/minecraft/textures/colormap/grass.png")
        self.foliage_map = self._image("assets/minecraft/textures/colormap/foliage.png")

    def _image(self, path: str) -> Image.Image:
        return Image.open(io.BytesIO(self.jar.read(path))).convert("RGBA")

    def resolve(self, block: str) -> str | None:
        """Texture that best shows the block from above, or None to fall back to its map colour."""
        for name in self._candidates(block):
            if name in self.names:
                return name
        return None

    @staticmethod
    def _candidates(name: str):
        if name in TEXTURE:
            yield TEXTURE[name]
        if name.endswith("_bed"):
            yield name[:-4] + "_wool"
        if name.startswith("potted_"):
            yield "flower_pot"
        yield name + "_top"
        yield name
        for suffix in SUFFIXES:
            if name.endswith(suffix):
                base = name[: -len(suffix)]
                if suffix == "_wood":
                    yield base + "_log"
                if base in WOODS:
                    yield base + "_planks"
                if suffix == "_carpet":
                    yield base + "_wool"
                yield base + "_top"
                yield base
                yield base + "s"
                yield base + "_block"

    def rgba(self, name: str, size: int) -> np.ndarray:
        """First animation frame, box-filtered to size x size, RGBA in 0..1."""
        key = (name, size)
        if key not in self.cache:
            img = self._image(f"assets/minecraft/textures/block/{name}.png")
            img = img.crop((0, 0, img.width, img.width))
            arr = np.asarray(img, dtype=np.float32) / 255.0
            # Premultiply so transparent pixels do not drag the average towards black.
            premult = np.concatenate([arr[..., :3] * arr[..., 3:], arr[..., 3:]], axis=-1)
            small = np.stack([np.asarray(Image.fromarray(premult[..., c]).resize((size, size), Image.BOX))
                              for c in range(4)], axis=-1)
            alpha = small[..., 3:]
            small[..., :3] = np.where(alpha > 0, small[..., :3] / np.maximum(alpha, 1e-6), 0)
            self.cache[key] = small
        return self.cache[key]

    def average(self, name: str) -> np.ndarray:
        return self.rgba(name, 1)[0, 0, :3]

    def colormap(self, image: Image.Image, temperature: float, downfall: float) -> np.ndarray:
        t = min(max(temperature, 0.0), 1.0)
        d = min(max(downfall, 0.0), 1.0) * t
        x, y = int((1.0 - t) * 255), int((1.0 - d) * 255)
        return np.asarray(image.getpixel((x, y))[:3], dtype=np.float32) / 255.0


class BiomeColours:
    """Grass, foliage and water tints per biome index, as the vanilla client computes them."""

    def __init__(self, biomes: list[dict], tex: Textures):
        self.ids = [b["id"].split(":")[1] for b in biomes]
        self.grass = np.zeros((len(biomes), 3), np.float32)
        self.foliage = np.zeros((len(biomes), 3), np.float32)
        self.water = np.zeros((len(biomes), 3), np.float32)
        for i, b in enumerate(biomes):
            grass = rgb(b["grass"]) if "grass" in b else tex.colormap(tex.grass_map, b["temperature"], b["downfall"])
            if b["grassModifier"] == "dark_forest":
                g = int(grass[0] * 255) << 16 | int(grass[1] * 255) << 8 | int(grass[2] * 255)
                grass = rgb(((g & 0xFEFEFE) + 0x28340A) >> 1)
            elif b["grassModifier"] == "swamp":
                grass = rgb(0x6A7039)
            self.grass[i] = grass
            self.foliage[i] = rgb(b["foliage"]) if "foliage" in b else tex.colormap(
                tex.foliage_map, b["temperature"], b["downfall"])
            self.water[i] = rgb(b["water"])

    def tint(self, block: str, biome: int) -> np.ndarray | None:
        if block in GRASS_TINT:
            return self.grass[biome]
        if block in FOLIAGE_TINT:
            return self.foliage[biome]
        if block in FIXED_TINT:
            return rgb(FIXED_TINT[block])
        if block == "water":
            return self.water[biome]
        return None

from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageOps, ImageFilter

ROOT = Path(__file__).parent
OUT = ROOT / 'manual-composites'
OUT.mkdir(exist_ok=True)
size = (1086, 1448)
original = ImageOps.exif_transpose(Image.open('C:/Users/Jake/Downloads/IMG_0023.jpg')).convert('RGB').resize(size, Image.Resampling.LANCZOS)
original.save(OUT / 'original-no-flood.png')
base = np.asarray(original)
w, h = size
settings = {
    1: [(0,535),(250,540),(430,550),(620,551),(660,551),(940,519),(1085,510)],
    2: [(0,507),(250,512),(430,521),(620,518),(660,518),(940,490),(1085,481)],
    3: [(0,480),(250,481),(430,492),(620,484),(660,484),(940,461),(1085,452)],
}
metadata = []
for level, points in settings.items():
    water_image = Image.open(ROOT / f'unverified-{level}ft-v2.png').convert('RGB').resize(size)
    water = np.asarray(water_image)
    edge = np.interp(np.arange(w), *np.array(points).T)
    result = base.copy()
    mask = np.zeros((h,w), dtype=np.uint8)
    for x in range(w):
        top = int(round(edge[x]))
        # Only sample below all generated objects and gauge markings.
        source_y = np.linspace(570, h-1, h-top)
        lo = np.floor(source_y).astype(int)
        hi = np.minimum(lo+1, h-1)
        fraction = (source_y-lo)[:,None]
        texture = water[lo,x]*(1-fraction)+water[hi,x]*fraction
        result[top:,x] = np.rint(texture).astype(np.uint8)
        mask[top:,x] = 255
    # All un-submerged pixels are copied from the real photograph, not regenerated.
    assert np.array_equal(result[mask == 0], base[mask == 0])
    name = f'synthetic-assumed-{level}ft.png'
    Image.fromarray(result).save(OUT / name)
    Image.fromarray(mask).save(OUT / f'water-mask-{level}ft.png')
    metadata.append({'file':name,'synthetic':True,'assumed_feet':level,'verified_depth':False,'post_waterline_y':dict(points)[620],'boundary_convention':'upper edge of corresponding color band; unverified','size':size})
(OUT / 'metadata.json').write_text(json.dumps(metadata, indent=2))
(OUT / 'README.txt').write_text('Synthetic flood illustrations, NOT measured ground truth. Original un-submerged photo pixels are preserved at the output resolution. Water is composited from AI-generated textures. Band upper edges are assumed to represent 1/2/3 feet, without physical verification. Shoreline and perspective are illustrative. Keep every derivative of IMG_0023 in one dataset split. Water-mask files describe the synthetic edit region, not validated real-world segmentation.\n')
print(OUT)

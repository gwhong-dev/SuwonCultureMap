"""favicon.ico = B2 아이콘 16px + 32px. render.js 다음에 실행"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
out = Path(__file__).resolve().parent / "out"
i16 = Image.open(out / "favicon-16.png").convert("RGBA")
i32 = Image.open(root / "src/main/resources/static/brand/favicon-32.png").convert("RGBA")
i32.save(root / "src/main/resources/static/favicon.ico", format="ICO", sizes=[(16, 16), (32, 32)], append_images=[i16])

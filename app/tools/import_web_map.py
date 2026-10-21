#!/usr/bin/env python3
"""Кладёт карту Троицка из веб-версии внутрь приложения: app/src/main/assets/map/.

Карта собирается в веб-версии (qwest65/malenkiy-karavan-web, tools/build_map.py, workflow «Map»)
в папку map/: оформление, кусочки карты, значки и шрифты. Здесь она копируется в assets,
адреса в оформлении меняются на asset://, а шрифты — на имена без пробелов
(«Noto Sans Regular» → «NotoSansRegular»), чтобы MapLibre точно нашла их среди файлов приложения.

    python3 app/tools/import_web_map.py ../malenkiy-karavan-web/map
"""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/assets/map"


def font_name(stack):
    return stack.replace(" ", "")


def main(src):
    src = Path(src)
    style = json.loads((src / "style.json").read_text(encoding="utf-8"))
    if OUT.exists():
        shutil.rmtree(OUT)
    shutil.copytree(src / "tiles", OUT / "tiles")
    shutil.copytree(src / "sprites", OUT / "sprites")
    for d in (src / "fonts").iterdir():
        shutil.copytree(d, OUT / "fonts" / font_name(d.name))

    for source in style["sources"].values():
        source["tiles"] = [f"asset://map/{t}" for t in source["tiles"]]
    style["sprite"] = f"asset://map/{style['sprite']}"
    style["glyphs"] = f"asset://map/{style['glyphs']}"
    for layer in style["layers"]:
        font = layer.get("layout", {}).get("text-font")
        if isinstance(font, list):
            layer["layout"]["text-font"] = [font_name(f) if isinstance(f, str) else f for f in font]
    (OUT / "style.json").write_text(json.dumps(style, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    size = sum(f.stat().st_size for f in OUT.rglob("*") if f.is_file())
    print(f"assets/map: {size / 1e6:.1f} МБ")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ROOT.parent / "malenkiy-karavan-web/map")

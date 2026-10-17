#!/usr/bin/env python3
"""Готовит данные веб-версии (docs/app) из содержимого Android-приложения.

Копирует маршрут, пешеходные линии и наклейки, добавляет к точкам координаты
из каталога мест и перекодирует озвучку в MP3: Safari на iPhone не везде
играет Ogg Vorbis. Запускать после любых правок текстов, линий или озвучки:

    pip install soundfile numpy lameenc
    python app/tools/build_web.py
"""
import json
import shutil
from pathlib import Path

import lameenc
import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
KIDS = ROOT / "app/src/main/assets/kids"
CATALOG = ROOT / "core/src/main/assets/catalog.json"
OUT = ROOT / "docs/app"

MP3_KBPS = 64


def build_route():
    route = json.loads((KIDS / "route.json").read_text(encoding="utf-8"))
    places = {p["id"]: p for p in json.loads(CATALOG.read_text(encoding="utf-8"))["places"]}
    for stop in route["stops"]:
        place = places[stop["place_id"]]
        stop["lat"], stop["lon"] = place["lat"], place["lon"]
    data = OUT / "data"
    data.mkdir(parents=True, exist_ok=True)
    (data / "route.json").write_text(json.dumps(route, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    shutil.copy(KIDS / "paths.json", data / "paths.json")
    shutil.copy(KIDS / "streets.json", data / "streets.json")


def build_stickers():
    out = OUT / "stickers"
    out.mkdir(parents=True, exist_ok=True)
    for f in sorted((KIDS / "stickers").glob("*.webp")):
        shutil.copy(f, out / f.name)


def to_mp3(src: Path, dst: Path):
    audio, rate = sf.read(src, dtype="int16", always_2d=True)
    mono = audio.mean(axis=1).astype(np.int16)
    enc = lameenc.Encoder()
    enc.set_bit_rate(MP3_KBPS)
    enc.set_in_sample_rate(rate)
    enc.set_channels(1)
    enc.set_quality(2)
    dst.write_bytes(enc.encode(mono.tobytes()) + enc.flush())


def build_audio():
    out = OUT / "audio"
    out.mkdir(parents=True, exist_ok=True)
    for f in sorted((KIDS / "audio").glob("*.ogg")):
        to_mp3(f, out / (f.stem + ".mp3"))


def build_file_list():
    """Список наклеек и озвучки, которые sw.js сохраняет в телефон для работы без интернета."""
    files = [f"stickers/{f.name}" for f in sorted((OUT / "stickers").glob("*.webp"))]
    files += [f"audio/{f.name}" for f in sorted((OUT / "audio").glob("*.mp3"))]
    (OUT / "data/files.json").write_text(json.dumps(files, indent=0), encoding="utf-8")


if __name__ == "__main__":
    build_route()
    build_stickers()
    build_audio()
    build_file_list()
    print("Готово:", OUT)

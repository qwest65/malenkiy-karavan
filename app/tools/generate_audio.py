#!/usr/bin/env python3
"""Озвучка «Маленького каравана» из assets/kids/route.json.

Рассказчик — голос Piper «dmitri», Троша — голос «denis» с поднятым тоном.
Оба голоса обучены на наборе NabuCasa voice-datasets с лицензией CC0.

    pip install sherpa-onnx soundfile numpy
    python3 app/tools/generate_audio.py

Голоса скачиваются в ~/.cache/karavan-voices при первом запуске.
Файлы пишутся в app/src/main/assets/kids/audio/*.ogg (Ogg Vorbis, моно).
"""
import json
import pathlib
import tarfile
import urllib.request

import numpy as np
import sherpa_onnx
import soundfile as sf

ROOT = pathlib.Path(__file__).resolve().parents[1]
ROUTE = ROOT / "src/main/assets/kids/route.json"
OUT = ROOT / "src/main/assets/kids/audio"
CACHE = pathlib.Path.home() / ".cache/karavan-voices"
VOICE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-{}-medium.tar.bz2"

NARRATOR = dict(voice="dmitri", speed=0.9, pitch=1.0)
TROSHA = dict(voice="denis", speed=1.0, pitch=1.35)

_engines = {}


def engine(voice):
    if voice not in _engines:
        folder = CACHE / f"vits-piper-ru_RU-{voice}-medium"
        if not folder.exists():
            CACHE.mkdir(parents=True, exist_ok=True)
            archive = CACHE / f"{voice}.tar.bz2"
            urllib.request.urlretrieve(VOICE_URL.format(voice), archive)
            with tarfile.open(archive) as tar:
                tar.extractall(CACHE)
            archive.unlink()
        vits = sherpa_onnx.OfflineTtsVitsModelConfig(
            model=str(folder / f"ru_RU-{voice}-medium.onnx"),
            tokens=str(folder / "tokens.txt"),
            data_dir=str(folder / "espeak-ng-data"),
        )
        config = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=4))
        _engines[voice] = sherpa_onnx.OfflineTts(config)
    return _engines[voice]


def synthesize(text, voice, speed, pitch):
    # Тон поднимается пересэмплированием; речь заранее замедляется, чтобы темп не изменился.
    audio = engine(voice).generate(text, sid=0, speed=speed / pitch)
    samples = np.asarray(audio.samples, dtype=np.float32)
    if pitch != 1.0:
        samples = np.interp(np.arange(0, len(samples) - 1, pitch), np.arange(len(samples)), samples)
    return samples, audio.sample_rate


def write(name, samples, rate):
    peak = max(float(np.abs(samples).max()), 1e-6)
    pad = np.zeros(int(rate * 0.15), dtype=np.float32)
    data = np.concatenate([pad, samples / peak * 0.9, pad])
    sf.write(OUT / f"{name}.ogg", data, rate, format="OGG", subtype="VORBIS")
    print(f"{name}.ogg  {len(data) / rate:.1f} с")


def say(name, text, voice):
    write(name, *synthesize(text, **voice))


def bell(rate=22050):
    """Звон караванного колокольчика: три удара с затухающими обертонами."""
    t = np.arange(int(rate * 1.6)) / rate
    strike = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * d)
                 for f, a, d in [(1320, 1.0, 3.0), (2640, 0.5, 5.0), (3960, 0.25, 7.0), (880, 0.3, 2.5)])
    out = np.zeros(int(rate * 2.4), dtype=np.float32)
    for start in (0.0, 0.35, 0.7):
        i = int(start * rate)
        out[i:i + len(strike)] += strike[: len(out) - i]
    write("bell", out, rate)


def main():
    route = json.loads(ROUTE.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    say("intro_trosha", route["intro"]["trosha"], TROSHA)
    say("finale_trosha", route["finale"]["trosha"], TROSHA)
    for key, text in route["phrases"].items():
        say(f"phrase_{key}", text, TROSHA)
    for n, stop in enumerate(route["stops"], start=1):
        say(f"stop{n}_narrator", stop["narrator"], NARRATOR)
        say(f"stop{n}_trosha", stop["trosha"], TROSHA)
        say(f"stop{n}_task", stop["question"]["voice"], NARRATOR)
    bell()


if __name__ == "__main__":
    main()

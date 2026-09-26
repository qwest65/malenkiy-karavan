#!/usr/bin/env python3
"""Озвучка «Маленького каравана» из assets/kids/route.json.

Два движка:

* **salute** — SaluteSpeech от Сбера: живые нейросетевые голоса. Нужен ключ авторизации
  (Studio → проект SaluteSpeech API → «Ключ авторизации») в переменной SALUTE_AUTH_KEY.
  Физлицам бесплатно до 200 000 символов в месяц; все тексты маршрута — около 4 000.
  Серверы Сбера подписаны сертификатом НУЦ Минцифры: путь к нему — в SALUTE_CA_FILE.
* **piper** — офлайн-голоса Piper «dmitri» и «denis» (лицензия CC0), ключ не нужен.

    pip install soundfile numpy            # и sherpa-onnx для piper
    python3 app/tools/generate_audio.py --engine salute --narrator Nec_24000 --trosha May_24000
    python3 app/tools/generate_audio.py --engine salute --samples samples/   # образцы всех голосов

Файлы пишутся в app/src/main/assets/kids/audio/*.ogg (Ogg Vorbis, моно).
В GitHub Actions это делает workflow «Voice» (.github/workflows/voice.yml).
"""
import argparse
import io
import json
import os
import pathlib
import ssl
import tarfile
import urllib.parse
import urllib.request
import uuid

import numpy as np
import soundfile as sf

ROOT = pathlib.Path(__file__).resolve().parents[1]
ROUTE = ROOT / "src/main/assets/kids/route.json"
AUDIO = ROOT / "src/main/assets/kids/audio"

# --- Piper -------------------------------------------------------------------------------

PIPER_CACHE = pathlib.Path.home() / ".cache/karavan-voices"
PIPER_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-{}-medium.tar.bz2"
_piper = {}


def piper_engine(voice):
    import sherpa_onnx

    if voice not in _piper:
        folder = PIPER_CACHE / f"vits-piper-ru_RU-{voice}-medium"
        if not folder.exists():
            PIPER_CACHE.mkdir(parents=True, exist_ok=True)
            archive = PIPER_CACHE / f"{voice}.tar.bz2"
            urllib.request.urlretrieve(PIPER_URL.format(voice), archive)
            with tarfile.open(archive) as tar:
                tar.extractall(PIPER_CACHE)
            archive.unlink()
        vits = sherpa_onnx.OfflineTtsVitsModelConfig(
            model=str(folder / f"ru_RU-{voice}-medium.onnx"),
            tokens=str(folder / "tokens.txt"),
            data_dir=str(folder / "espeak-ng-data"),
        )
        config = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(vits=vits, num_threads=4))
        _piper[voice] = sherpa_onnx.OfflineTts(config)
    return _piper[voice]


def piper_say(text, voice, speed):
    audio = piper_engine(voice).generate(text, sid=0, speed=speed)
    return np.asarray(audio.samples, dtype=np.float32), audio.sample_rate


# --- SaluteSpeech ------------------------------------------------------------------------

SALUTE_OAUTH = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
SALUTE_SYNTH = "https://smartspeech.sber.ru/rest/v1/text:synthesize"
# Голос → (имя, латиницей для имён файлов: GitHub портит кириллицу в именах вложений релиза).
SALUTE_VOICES = {
    "Nec_24000": ("Наталья", "Natalya"),
    "Bys_24000": ("Борис", "Boris"),
    "May_24000": ("Марфа", "Marfa"),
    "Tur_24000": ("Тарас", "Taras"),
    "Ost_24000": ("Александра", "Aleksandra"),
    "Pon_24000": ("Сергей", "Sergey"),
}
_salute = {}


def salute_context():
    context = ssl.create_default_context()
    ca = os.environ.get("SALUTE_CA_FILE")
    if ca:
        context.load_verify_locations(cafile=ca)
    return context


def salute_token():
    if "token" not in _salute:
        key = os.environ.get("SALUTE_AUTH_KEY", "").strip()
        if not key:
            raise SystemExit("Нет ключа SaluteSpeech: задайте переменную SALUTE_AUTH_KEY")
        request = urllib.request.Request(
            SALUTE_OAUTH,
            data=urllib.parse.urlencode({"scope": os.environ.get("SALUTE_SCOPE", "SALUTE_SPEECH_PERS")}).encode(),
            headers={
                "Authorization": f"Basic {key}",
                "RqUID": str(uuid.uuid4()),
                "Content-Type": "application/x-www-form-urlencoded",
                "Accept": "application/json",
            },
        )
        with urllib.request.urlopen(request, timeout=30, context=salute_context()) as response:
            _salute["token"] = json.load(response)["access_token"]
    return _salute["token"]


def salute_say(text, voice):
    query = urllib.parse.urlencode({"format": "wav16", "voice": voice})
    request = urllib.request.Request(
        f"{SALUTE_SYNTH}?{query}",
        data=text.encode("utf-8"),
        headers={"Authorization": f"Bearer {salute_token()}", "Content-Type": "application/text"},
    )
    with urllib.request.urlopen(request, timeout=60, context=salute_context()) as response:
        data = response.read()
    samples, rate = sf.read(io.BytesIO(data), dtype="float32")
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    return samples, rate


# --- Общее -------------------------------------------------------------------------------


def pitch_up(samples, factor):
    """Поднимает тон пересэмплированием (речь при этом немного ускоряется)."""
    if factor == 1.0:
        return samples
    return np.interp(np.arange(0, len(samples) - 1, factor), np.arange(len(samples)), samples).astype(np.float32)


def write(path, samples, rate):
    peak = max(float(np.abs(samples).max()), 1e-6)
    pad = np.zeros(int(rate * 0.15), dtype=np.float32)
    data = np.concatenate([pad, samples / peak * 0.9, pad])
    sf.write(path, data, rate, format="OGG", subtype="VORBIS")
    print(f"{path.name}  {len(data) / rate:.1f} с")


class Voices:
    def __init__(self, args):
        self.args = args

    def narrator(self, text):
        a = self.args
        if a.engine == "salute":
            return salute_say(text, a.narrator)
        return piper_say(text, a.narrator or "dmitri", speed=0.9)

    def trosha(self, text):
        a = self.args
        if a.engine == "salute":
            samples, rate = salute_say(text, a.trosha)
            return pitch_up(samples, a.trosha_pitch), rate
        # У Piper нет детского голоса: взрослый голос заранее замедляется и поднимается в тоне.
        pitch = a.trosha_pitch if a.trosha_pitch != 1.0 else 1.35
        samples, rate = piper_say(text, a.trosha or "denis", speed=1.0 / pitch)
        return pitch_up(samples, pitch), rate


def bell(rate=22050):
    """Звон караванного колокольчика: три удара с затухающими обертонами."""
    t = np.arange(int(rate * 1.6)) / rate
    strike = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t * d)
                 for f, a, d in [(1320, 1.0, 3.0), (2640, 0.5, 5.0), (3960, 0.25, 7.0), (880, 0.3, 2.5)])
    out = np.zeros(int(rate * 2.4), dtype=np.float32)
    for start in (0.0, 0.35, 0.7):
        i = int(start * rate)
        out[i:i + len(strike)] += strike[: len(out) - i]
    write(AUDIO / "bell.ogg", out, rate)


def generate_all(route, voices):
    AUDIO.mkdir(parents=True, exist_ok=True)

    def trosha(name, text):
        write(AUDIO / f"{name}.ogg", *voices.trosha(text))

    def narrator(name, text):
        write(AUDIO / f"{name}.ogg", *voices.narrator(text))

    trosha("intro_trosha", route["intro"]["trosha"])
    trosha("finale_trosha", route["finale"]["trosha"])
    for key, text in route["phrases"].items():
        trosha(f"phrase_{key}", text)
    for n, stop in enumerate(route["stops"], start=1):
        narrator(f"stop{n}_narrator", stop["narrator"])
        trosha(f"stop{n}_trosha", stop["trosha"])
        narrator(f"stop{n}_task", stop["question"]["voice"])
    bell()


def generate_samples(route, out):
    """Один и тот же фрагмент каждым голосом SaluteSpeech — чтобы выбрать на слух."""
    out.mkdir(parents=True, exist_ok=True)
    stop = route["stops"][1]
    for voice, (_, latin) in SALUTE_VOICES.items():
        write(out / f"{latin}-narrator.ogg", *salute_say(stop["narrator"], voice))
        samples, rate = salute_say(stop["trosha"], voice)
        write(out / f"{latin}-trosha.ogg", samples, rate)
        write(out / f"{latin}-trosha-higher.ogg", pitch_up(samples, 1.2), rate)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--engine", choices=["piper", "salute"], default="piper")
    parser.add_argument("--narrator", help="голос рассказчика (salute: Nec_24000; piper: dmitri)")
    parser.add_argument("--trosha", help="голос Троши (salute: May_24000; piper: denis)")
    parser.add_argument("--trosha-pitch", type=float, default=1.0, help="поднять тон Троши, например 1.2")
    parser.add_argument("--samples", type=pathlib.Path, help="вместо озвучки — образцы всех голосов SaluteSpeech в эту папку")
    args = parser.parse_args()
    if args.engine == "salute":
        args.narrator = args.narrator or "Nec_24000"
        args.trosha = args.trosha or "May_24000"
    route = json.loads(ROUTE.read_text(encoding="utf-8"))
    if args.samples:
        generate_samples(route, args.samples)
    else:
        generate_all(route, Voices(args))


if __name__ == "__main__":
    main()

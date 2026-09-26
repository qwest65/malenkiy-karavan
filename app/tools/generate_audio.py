#!/usr/bin/env python3
"""Озвучка «Маленького каравана» из assets/kids/route.json.

Два движка:

* **edge** — нейросетевые голоса Microsoft Edge («Прочесть вслух») через edge-tts:
  Дмитрий и Светлана. Бесплатно и без ключа, нужен интернет. Это неофициальный
  доступ к сервису Microsoft: для бесплатного приложения подходит, для платного — рискованно.
* **piper** — офлайн-голоса Piper «dmitri» и «denis» (лицензия CC0).

    pip install edge-tts soundfile numpy   # sherpa-onnx — для piper
    python3 app/tools/generate_audio.py --engine edge --narrator ru-RU-DmitryNeural \\
        --trosha ru-RU-SvetlanaNeural --trosha-pitch +25Hz
    python3 app/tools/generate_audio.py --engine edge --samples samples/   # образцы голосов

Файлы пишутся в app/src/main/assets/kids/audio/*.ogg (Ogg Vorbis, моно).
В GitHub Actions это делает workflow «Voice» (.github/workflows/voice.yml).
"""
import argparse
import json
import pathlib
import re
import tarfile
import urllib.request

import numpy as np
import soundfile as sf

ROOT = pathlib.Path(__file__).resolve().parents[1]
ROUTE = ROOT / "src/main/assets/kids/route.json"
CATALOG = ROOT.parent / "core/src/main/assets/catalog.json"
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


# --- Microsoft Edge ----------------------------------------------------------------------

# Голоса «Прочесть вслух» из Microsoft Edge через библиотеку edge-tts: ключ не нужен.
EDGE_VOICES = {
    "ru-RU-DmitryNeural": ("Дмитрий", "Dmitry"),
    "ru-RU-SvetlanaNeural": ("Светлана", "Svetlana"),
}


def edge_say(text, voice, rate="+0%", pitch="+0Hz", attempts=4):
    import asyncio
    import tempfile
    import time

    import edge_tts

    # Многоточие и длинные тире сервис иногда не озвучивает — заменяем на обычную пунктуацию.
    text = text.replace("…", ".").replace("—", ",")
    with tempfile.TemporaryDirectory() as tmp:
        mp3 = pathlib.Path(tmp) / "speech.mp3"
        for attempt in range(attempts):
            try:
                asyncio.run(edge_tts.Communicate(text, voice, rate=rate, pitch=pitch).save(str(mp3)))
                break
            except edge_tts.exceptions.EdgeTTSException as error:
                # Сервис Microsoft иногда отвечает пустым потоком — пробуем ещё раз с паузой.
                if attempt == attempts - 1:
                    raise
                print(f"  повтор после ошибки: {error}")
                time.sleep(3 * (attempt + 1))
        samples, rate_hz = sf.read(mp3, dtype="float32")
    if samples.ndim > 1:
        samples = samples.mean(axis=1)
    return samples, rate_hz


# --- Общее -------------------------------------------------------------------------------

# Ударения, которые синтезатор ставит неверно. Знак ударения (U+0301) добавляется только
# в озвучиваемый текст — на экране слова остаются без него.
# Римские века синтезатор читает как «восемнадцать веке» — заменяем словами.
SPOKEN = {
    "XVIII–XIX веках": "восемнадцатом и девятнадцатом веках",
    "XVIII веке": "восемнадцатом веке",
    "XIX веке": "девятнадцатом веке",
    "XIX века": "девятнадцатого века",
    "XX века": "двадцатого века",
}

STRESS = {
    # «Две реки́»: одного знака ударения синтезатору мало, поэтому пишем как слышится.
    r"реки": "рики́",
    r"города": "го́рода",
    r"двадцатого": "двадца́того",
    r"Троицкой": "Тро́йцкой",
    r"гербе": "ге́рбе",
    r"Троицк(\w*)": "Тро́ицк\\1",
    r"казаки": "казаки́",
    r"сыром": "сы́ром",
    r"ворону": "воро́ну",
}


def with_stress(text):
    for written, spoken in SPOKEN.items():
        text = text.replace(written, spoken)
    for word, spoken in STRESS.items():
        pattern = re.compile(rf"\b{word}\b", re.IGNORECASE)

        def keep_case(match, spoken=spoken, pattern=pattern):
            out = pattern.sub(spoken, match.group(0).lower())
            return out[0].upper() + out[1:] if match.group(0)[0].isupper() else out

        text = pattern.sub(keep_case, text)
    return text


def tighten(samples, rate, keep=0.25, frame=0.02, threshold_db=-40):
    """Обрезает тишину по краям и укорачивает длинные паузы внутри до [keep] секунд."""
    step = int(rate * frame)
    frames = len(samples) // step
    if frames == 0:
        return samples
    energy = np.sqrt(np.mean(samples[: frames * step].reshape(frames, step) ** 2, axis=1))
    loud = energy > max(float(energy.max()), 1e-6) * 10 ** (threshold_db / 20)
    if not loud.any():
        return samples
    first, last = int(np.argmax(loud)), frames - int(np.argmax(loud[::-1]))
    keep_frames = int(keep / frame)
    parts, quiet = [], 0
    for i in range(first, last):
        quiet = 0 if loud[i] else quiet + 1
        if quiet <= keep_frames:
            parts.append(samples[i * step:(i + 1) * step])
    return np.concatenate(parts)



def pitch_up(samples, factor):
    """Поднимает тон пересэмплированием (речь при этом немного ускоряется)."""
    if factor == 1.0:
        return samples
    return np.interp(np.arange(0, len(samples) - 1, factor), np.arange(len(samples)), samples).astype(np.float32)


def write(path, samples, rate, tail=0.05):
    samples = tighten(samples, rate)
    peak = max(float(np.abs(samples).max()), 1e-6)
    pad = np.zeros(int(rate * 0.05), dtype=np.float32)
    # Тишина в конце фразы — пауза перед следующим фрагментом (рассказчик → Троша → вопрос).
    data = np.concatenate([pad, samples / peak * 0.9, np.zeros(int(rate * tail), dtype=np.float32)])
    sf.write(path, data, rate, format="OGG", subtype="VORBIS")
    print(f"{path.name}  {len(data) / rate:.1f} с")


class Voices:
    def __init__(self, args):
        self.args = args

    def narrator(self, text):
        a = self.args
        if a.engine == "edge":
            return edge_say(with_stress(text), a.narrator, rate=a.narrator_rate)
        return piper_say(text, a.narrator or "dmitri", speed=0.9)

    def trosha(self, text):
        a = self.args
        if a.engine == "edge":
            return edge_say(with_stress(text), a.trosha, rate=a.trosha_rate, pitch=a.trosha_pitch)
        # У Piper нет детского голоса: взрослый голос заранее замедляется и поднимается в тоне.
        pitch = 1.35
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


PAUSE = 0.6


def generate_all(route, catalog, voices):
    AUDIO.mkdir(parents=True, exist_ok=True)

    def trosha(name, text):
        write(AUDIO / f"{name}.ogg", *voices.trosha(text), tail=PAUSE)

    def narrator(name, text):
        write(AUDIO / f"{name}.ogg", *voices.narrator(text), tail=PAUSE)

    trosha("intro_trosha", route["intro"]["trosha"])
    trosha("finale_trosha", route["finale"]["trosha"])
    for key, text in route["phrases"].items():
        trosha(f"phrase_{key}", text)
    for n, stop in enumerate(route["stops"], start=1):
        narrator(f"stop{n}_narrator", stop["narrator"])
        trosha(f"stop{n}_trosha", stop["trosha"])
        narrator(f"stop{n}_task", stop["question"]["voice"])
        # «Подробная история голосом» для взрослых: справка из общего каталога мест.
        place = catalog[stop["place_id"]]
        narrator(f"stop{n}_parent", f"{place['name']}. {place['description']}".replace("\n", " "))
    bell()


def generate_samples(route, out):
    """Один и тот же фрагмент разными голосами Edge — чтобы выбрать на слух."""
    out.mkdir(parents=True, exist_ok=True)
    stop = route["stops"][1]
    for voice, (_, latin) in EDGE_VOICES.items():
        write(out / f"{latin}-narrator.ogg", *edge_say(stop["narrator"], voice, rate="-5%"))
        # Троша — детский голос: тот же голос выше и чуть быстрее.
        for label, pitch in (("trosha", "+0Hz"), ("trosha-higher", "+25Hz"), ("trosha-highest", "+45Hz")):
            write(out / f"{latin}-{label}.ogg", *edge_say(stop["trosha"], voice, rate="+8%", pitch=pitch))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--engine", choices=["piper", "edge"], default="piper")
    parser.add_argument("--narrator", help="голос рассказчика (edge: ru-RU-DmitryNeural; piper: dmitri)")
    parser.add_argument("--narrator-rate", default="+0%", help="edge: скорость рассказчика, например -5%%")
    parser.add_argument("--trosha", help="голос Троши (edge: ru-RU-SvetlanaNeural; piper: denis)")
    parser.add_argument("--trosha-rate", default="+10%", help="edge: скорость Троши")
    parser.add_argument("--trosha-pitch", default="+25Hz", help="edge: тон Троши, например +25Hz")
    parser.add_argument("--samples", type=pathlib.Path, help="вместо озвучки — образцы голосов Edge в эту папку")
    args = parser.parse_args()
    if args.engine == "edge":
        args.narrator = args.narrator or "ru-RU-DmitryNeural"
        args.trosha = args.trosha or "ru-RU-SvetlanaNeural"
    route = json.loads(ROUTE.read_text(encoding="utf-8"))
    if args.samples:
        generate_samples(route, args.samples)
    else:
        catalog = {p["id"]: p for p in json.loads(CATALOG.read_text(encoding="utf-8"))["places"]}
        generate_all(route, catalog, Voices(args))


if __name__ == "__main__":
    main()

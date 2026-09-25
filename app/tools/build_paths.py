#!/usr/bin/env python3
"""Пешеходные линии маршрута «Маленького каравана» для assets/kids/paths.json.

Для каждой пары точек из assets/kids/route.json (i < j) запрашивает пешеходный
маршрут у OSRM (профиль foot, данные OpenStreetMap) и сохраняет геометрию.
Родитель может выбрать любые точки, а они проходятся в порядке маршрута, поэтому
нужны линии между любыми двумя из них. Приложение рисует их без интернета
и считает по ним оставшиеся шаги.

    python3 app/tools/build_paths.py

Запускается в GitHub Actions (.github/workflows/kids-paths.yml) при изменении
маршрута или каталога. Сервер можно заменить переменной окружения OSRM_URL.
"""
import json
import math
import os
import pathlib
import time
import urllib.request

REPO = pathlib.Path(__file__).resolve().parents[2]
ROUTE = REPO / "app/src/main/assets/kids/route.json"
CATALOG = REPO / "core/src/main/assets/catalog.json"
OUT = REPO / "app/src/main/assets/kids/paths.json"
OSRM_URL = os.environ.get("OSRM_URL", "https://routing.openstreetmap.de/routed-foot")
USER_AGENT = "MalenkiyKaravan-build/1.0 (+https://github.com/qwest65/malenkiy-karavan)"


def distance_m(a, b):
    """Расстояние между точками [lon, lat] по формуле гаверсинусов, м."""
    lon1, lat1, lon2, lat2 = map(math.radians, (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * 6_371_008.8 * math.asin(math.sqrt(min(1.0, h)))


def length_m(points):
    return sum(distance_m(points[i - 1], points[i]) for i in range(1, len(points)))


def fetch_leg(start, end, attempts=4):
    coords = f"{start[0]:.6f},{start[1]:.6f};{end[0]:.6f},{end[1]:.6f}"
    url = f"{OSRM_URL}/route/v1/driving/{coords}?overview=full&geometries=geojson"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                data = json.load(response)
            if data.get("code") != "Ok" or not data.get("routes"):
                raise RuntimeError(f"OSRM: {data.get('code')} {data.get('message', '')}")
            return data["routes"][0]["geometry"]["coordinates"]
        except (OSError, RuntimeError) as error:
            if attempt == attempts - 1:
                raise
            print(f"  повтор после ошибки: {error}")
            time.sleep(2 ** (attempt + 1))


def build_leg(start, end, geometry):
    """Линия от точки до точки: OSRM привязывает концы к ближайшей дорожке, поэтому
    сами точки дописываются в начало и конец, чтобы линия упиралась в метки на карте."""
    points = [[round(lon, 6), round(lat, 6)] for lon, lat in geometry]
    if distance_m(start, points[0]) > 1:
        points.insert(0, start)
    if distance_m(end, points[-1]) > 1:
        points.append(end)
    return points


def main():
    route = json.loads(ROUTE.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    places = {p["id"]: p for p in catalog["places"]}
    stops = [[places[s["place_id"]]["lon"], places[s["place_id"]]["lat"]] for s in route["stops"]]

    legs = []
    for i in range(len(stops)):
        for j in range(i + 1, len(stops)):
            start, end = stops[i], stops[j]
            points = build_leg(start, end, fetch_leg(start, end))
            meters = length_m(points)
            straight = distance_m(start, end)
            print(f"{i + 1} → {j + 1}: {len(points)} точек, {meters:.0f} м пешком, {straight:.0f} м по прямой")
            if meters > straight * 4:
                raise SystemExit(f"{i + 1} → {j + 1} подозрительно длинный: {meters:.0f} м при {straight:.0f} м по прямой")
            legs.append({"from": i, "to": j, "meters": round(meters), "points": points})

    OUT.write_text(
        json.dumps(
            {
                "route": route["id"],
                "source": "OSRM, профиль foot; данные © участники OpenStreetMap (ODbL)",
                "legs": legs,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        )
        + "\n",
        encoding="utf-8",
    )
    full = sum(l["meters"] for l in legs if l["to"] == l["from"] + 1)
    print(f"записано {OUT.relative_to(REPO)}: {len(legs)} участков, весь маршрут {full} м")


if __name__ == "__main__":
    main()

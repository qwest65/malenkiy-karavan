#!/usr/bin/env python3
"""Публикация APK в RuStore через RuStore Public API.

Ключ создаётся в консоли RuStore (Компания → API RuStore) и лежит в секретах GitHub:
  RUSTORE_KEY_ID       — идентификатор ключа (keyId);
  RUSTORE_PRIVATE_KEY  — приватный ключ в base64 (как его выдаёт консоль).

Режимы:
  check    — только входит по ключу и показывает версии приложения в RuStore;
  publish  — создаёт черновик версии с текстом «Что нового», загружает APK
             и отправляет на модерацию; после модерации версия выходит сама.
"""
import argparse
import base64
import datetime
import json
import os
import sys

import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

API = "https://public-api.rustore.ru"
TIMEOUT = 120


def fail(msg):
    print(f"::error::{msg}")
    sys.exit(1)


def load_private_key(text):
    text = text.strip()
    if "BEGIN" in text:
        return serialization.load_pem_private_key(text.encode(), password=None)
    return serialization.load_der_private_key(base64.b64decode(text), password=None)


def auth(key_id, private_key):
    now = datetime.datetime.now(datetime.timezone.utc).astimezone()
    timestamp = now.isoformat(timespec="milliseconds")
    signature = private_key.sign((key_id + timestamp).encode(), padding.PKCS1v15(), hashes.SHA512())
    r = requests.post(
        f"{API}/public/auth/",
        json={"keyId": key_id, "timestamp": timestamp, "signature": base64.b64encode(signature).decode()},
        timeout=TIMEOUT,
    )
    body = answer(r, "вход по ключу")
    token = (body or {}).get("jwe")
    if not token:
        fail(f"RuStore не выдал токен: {r.text[:500]}")
    return token


def answer(r, what):
    """Разбирает ответ RuStore: {code, message, body}."""
    try:
        data = r.json()
    except ValueError:
        fail(f"{what}: HTTP {r.status_code}, ответ не JSON: {r.text[:500]}")
    if r.status_code >= 400 or data.get("code") not in (None, "OK"):
        fail(f"{what}: HTTP {r.status_code}, {data.get('code')}: {data.get('message')}")
    return data.get("body")


def list_versions(s, pkg):
    r = s.get(f"{API}/public/v1/application/{pkg}/version", params={"page": 0, "size": 20}, timeout=TIMEOUT)
    body = answer(r, "список версий") or {}
    return body.get("content", body if isinstance(body, list) else [])


def show(versions):
    if not versions:
        print("Версий в RuStore пока нет.")
        return
    print("Версии в RuStore (новые сверху):")
    for v in versions:
        print(f"  id={v.get('versionId')}  {v.get('versionName')} ({v.get('versionCode')})  "
              f"статус: {v.get('versionStatus')}  публикация: {v.get('publishType')}")


def publish(s, pkg, apk, whats_new, version_code):
    versions = list_versions(s, pkg)
    show(versions)
    codes = [v.get("versionCode") for v in versions if isinstance(v.get("versionCode"), int)]
    if version_code and codes and version_code <= max(codes):
        fail(f"versionCode {version_code} не больше уже загруженного {max(codes)} — сначала поднимите версию.")
    busy = [v for v in versions if v.get("versionStatus") in ("DRAFT", "MODERATION", "PREPARING")]
    if busy:
        fail("В RuStore уже есть версия в черновике или на модерации "
             f"(id={busy[0].get('versionId')}, статус {busy[0].get('versionStatus')}). "
             "Дождитесь модерации или удалите черновик в консоли.")

    r = s.post(f"{API}/public/v1/application/{pkg}/version",
               json={"whatsNew": whats_new, "publishType": "INSTANTLY"}, timeout=TIMEOUT)
    version_id = answer(r, "создание черновика")
    print(f"Черновик создан: id={version_id}")

    with open(apk, "rb") as f:
        r = s.post(f"{API}/public/v1/application/{pkg}/version/{version_id}/apk",
                   params={"isMainApk": "true", "servicesType": "Unknown"},
                   files={"file": (os.path.basename(apk), f, "application/vnd.android.package-archive")},
                   timeout=600)
    answer(r, "загрузка APK")
    print("APK загружен.")

    r = s.post(f"{API}/public/v1/application/{pkg}/version/{version_id}/commit", timeout=TIMEOUT)
    answer(r, "отправка на модерацию")
    print("Версия отправлена на модерацию. После проверки RuStore опубликует её сам.")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("mode", choices=["check", "publish"])
    p.add_argument("--package", default="ru.malenkiykaravan")
    p.add_argument("--apk")
    p.add_argument("--whats-new")
    p.add_argument("--version-code", type=int)
    a = p.parse_args()

    key_id = os.environ.get("RUSTORE_KEY_ID", "").strip()
    key_text = os.environ.get("RUSTORE_PRIVATE_KEY", "")
    if not key_id or not key_text.strip():
        fail("Не заданы секреты RUSTORE_KEY_ID и RUSTORE_PRIVATE_KEY.")
    try:
        private_key = load_private_key(key_text)
    except Exception as e:  # noqa: BLE001 — показываем причину, но не сам ключ
        fail(f"Приватный ключ не читается ({type(e).__name__}). Вставьте его в секрет целиком, как выдала консоль.")

    s = requests.Session()
    s.headers["Public-Token"] = auth(key_id, private_key)
    print("Вход в RuStore по ключу: успешно.")

    if a.mode == "check":
        show(list_versions(s, a.package))
        return
    if not a.apk or not os.path.isfile(a.apk):
        fail("Нет файла APK для загрузки.")
    whats_new = open(a.whats_new, encoding="utf-8").read().strip() if a.whats_new else ""
    if not whats_new:
        fail("Пустой текст «Что нового» (rustore/whats_new.txt).")
    if len(whats_new) > 5000:
        fail("Текст «Что нового» длиннее 5000 символов.")
    publish(s, a.package, a.apk, whats_new, a.version_code)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Best-effort build-time channel-logo matcher using iptv-org public API.

Downloads channel/logo metadata, picks only strongly matching raster logos,
and writes a small local mapping consumed by Google TV. There are no TV streams
in this API file and no images are copied into the repo/APK.
"""
import json
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHANNELS = ROOT / "app/src/main/assets/sports_channels.tsv"
DEST = ROOT / "app/src/main/assets/sports_logos.json"

API = "https://iptv-org.github.io/api/"
SUPPORTED = {"PNG", "JPEG", "JPG", "WEBP", "GIF"}


def normalize(text):
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return re.sub(r"[^a-z0-9]", "", text.lower())


def country_for(name, slug):
    suffixes = {"_mx": "MX", "_usa": "US", "_pt": "PT", "_de": "DE", "_nl": "NL", "_be": "BE"}
    for suffix, code in suffixes.items():
        if slug.endswith(suffix):
            return code
    special = {
        "premiere": "BR", "sportv": "BR", "sporttv": "PT",
        "eleven": "PT", "dazn": "ES", "sky_bundesliga": "DE",
        "sky_sports_laliga": "ES", "liga1": "PE", "golperu": "PE",
        "movistar": "PE", "win": "CO", "ecdf": "EC",
        "vtv": "UY", "goltv": "UY", "tnt sports chile": "CL",
        "tudn": "MX", "azteca": "MX", "caliente": "MX",
        "telefe": "AR", "tv publica": "AR", "tyc": "AR",
        "dsports": "AR", "espn": "AR", "fox sports": "AR",
        "tnt sports": "AR", "espnpremium": "AR"
    }
    test = normalize(name)
    for prefix, country in special.items():
        if test.startswith(normalize(prefix)) or slug.startswith(prefix.replace(" ", "")):
            return country
    return ""


def fetch(filename):
    request = urllib.request.Request(API + filename, headers={
        "User-Agent": "EnovaIPTV-logo-builder/1.0",
        "Accept": "application/json"
    })
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def select_logo(channel, images):
    options = [
        image for image in images
        if image.get("in_use") is not False
        and image.get("format", "").upper() in SUPPORTED
        and isinstance(image.get("url"), str)
        and image["url"].startswith("https://")
    ]
    if not options:
        return None

    def score(item):
        tags = item.get("tags") or []
        return (
            40 if item.get("feed") is None else 0,
            25 if "horizontal" in tags else 0,
            10 if "white" in tags else 0,
            min(item.get("width") or 0, 1280)
        )

    options.sort(key=score, reverse=True)
    return options[0]["url"]


def generate(channels, logos, table):
    by_id = {channel.get("id"): channel for channel in channels if channel.get("id")}
    images_by_channel = {}
    for logo in logos:
        images_by_channel.setdefault(logo.get("channel"), []).append(logo)

    # Avoid guessing logos: exact normalized channel/alternate name only.
    by_name = {}
    for channel in channels:
        for name in [channel.get("name"), *(channel.get("alt_names") or [])]:
            if name:
                by_name.setdefault(normalize(name), []).append(channel)

    matched = {}
    for row in table.splitlines():
        try:
            name, slug = row.split("\t")
        except ValueError:
            continue
        key = normalize(name)
        candidates = by_name.get(key, [])
        if not candidates:
            continue
        country = country_for(name, slug)
        ranked = sorted(candidates, key=lambda ch: (
            ch.get("country") == country if country else False,
            ch.get("closed") in (None, ""),
            normalize(ch.get("name", "")) == key,
        ), reverse=True)
        # Do not borrow unrelated brands for similar names.
        for channel in ranked:
            logo = select_logo(channel, images_by_channel.get(channel.get("id"), []))
            if logo:
                matched[slug] = logo
                break
    return matched


def main():
    table = CHANNELS.read_text(encoding="utf-8")
    try:
        channels = fetch("channels.json")
        logos = fetch("logos.json")
        mapping = generate(channels, logos, table)
        print("iptv-org logo match: %d of %d catalog entries" % (
            len(mapping), len(table.strip().splitlines())))
        DEST.write_text(
            json.dumps(mapping, ensure_ascii=False, sort_keys=True, indent=2) + "\n",
            encoding="utf-8"
        )
    except Exception as error:
        print("WARNING: logo source unavailable; using built-in channel tiles:", error)
        # Do not fail APK builds because a third-party logo endpoint is down.
        if not DEST.exists():
            DEST.write_text("{}\n", encoding="utf-8")


if __name__ == "__main__":
    main()

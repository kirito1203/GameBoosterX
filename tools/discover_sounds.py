#!/usr/bin/env python3
"""
FASE 1 — Descoberta. Não baixa nada para o app: só lista candidatos com licença,
autor, link e duração, para curadoria humana. Saída: tools/candidates/*.json

Roda no GitHub Actions (o ambiente de desenvolvimento não tem acesso a esses sites).
"""
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
from sounds_common import commons_api, http_get, allowed_by_robots, map_license, strip_html  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "candidates")
os.makedirs(OUT, exist_ok=True)

# ------------------------------------------------------------------ Wikimedia Commons

COMMONS_QUERIES = {
    "risadas": ["laughing", "laughter", "laugh", "giggle", "baby laughing", "evil laugh", "maniacal laugh",
                "chuckle", "children laughing", "crowd laughing"],
    "reacoes": ["applause", "cheering crowd", "crowd boo", "gasp", "scream", "screaming", "surprise", "sigh",
                "yawn", "sneeze", "cough", "whistle", "wolf whistle", "crying baby", "hiccup", "burp", "snoring",
                "kiss sound", "yell", "shout"],
    "comicos": ["air horn", "slide whistle", "kazoo", "sad trombone", "rimshot", "drum roll", "cymbal crash",
                "record scratch", "boing", "squeaky toy", "rubber duck", "honk", "bike horn", "cartoon"],
    "terror": ["thunder", "creaking door", "door creak", "footsteps", "heartbeat", "wolf howl", "owl hoot",
               "church bell", "wind howling", "horror", "creepy", "scream horror", "chains", "zombie", "ghost"],
    "impactos": ["glass breaking", "explosion", "punch", "slap", "hammer", "metal impact", "crash", "thud",
                 "whoosh", "sword", "bell strike", "gong", "falling"],
    "animais": ["cat meow", "dog bark", "rooster", "cow moo", "duck quack", "goat", "sheep bleat", "horse neigh",
                "frog croak", "chicken", "pig oink", "crow caw", "seagull", "parrot", "monkey", "lion roar",
                "elephant", "donkey bray"],
    "aleatorios": ["telephone ring", "doorbell", "alarm clock", "clock ticking", "typewriter", "camera shutter",
                   "water drop", "bubbles", "car horn", "train whistle", "siren", "fireworks", "popcorn",
                   "cash register", "coin drop", "zipper", "door slam", "keyboard typing", "bottle opening",
                   "match strike", "tea kettle", "toilet flush", "vacuum cleaner", "bicycle bell", "ship horn",
                   "motorcycle", "helicopter", "jet airplane"],
    "musica": ["fanfare", "jingle", "trumpet fanfare", "organ chord", "theremin", "berimbau", "cuica",
               "pandeiro", "samba", "forro", "accordion", "harmonica", "banjo", "bagpipe", "vuvuzela",
               "music box", "xylophone", "steel drum", "drum fill", "bass drop", "sting"],
    "games": ["8-bit", "chiptune", "retro game sound", "arcade", "game over", "power up", "laser", "beep"],
}

# Lingua Libre: gravações reais de pessoas falando palavras (CC BY-SA). Interjeições viram "bordões"/reações.
LINGUA_LIBRE = {
    "LL-Q750553": [  # português brasileiro
        "eita", "vixe", "oxe", "oxente", "nossa", "caramba", "socorro", "opa", "uau", "rapaz", "égua", "bah",
        "tchê", "valeu", "beleza", "sério", "mentira", "cuidado", "corre", "parabéns", "obrigado", "tchau",
        "cadê", "hein", "putz", "eba", "oba", "ufa", "credo", "uai", "pô", "xi", "aff", "olha", "ai", "ui",
        "não", "sim", "fogo", "meu deus", "nossa senhora", "que isso", "para", "bora", "vamos", "silêncio",
        "acorda", "pronto", "calma", "socorro", "gol", "craque", "nordeste", "saudade", "cafuné", "danou",
    ],
    "LL-Q1860": [  # inglês
        "bruh", "wow", "oops", "nope", "yes", "no", "what", "why", "hello", "goodbye", "help", "run", "boom",
        "ouch", "yay", "hooray", "okay", "amazing", "epic", "nice", "awesome", "wait", "stop", "seriously",
        "really", "whatever", "surprise", "fail", "victory", "noob", "legendary", "oh", "uh-oh", "yikes",
        "bingo", "jackpot", "checkmate", "goal", "bravo", "cheers", "welcome", "danger", "warning",
    ],
    "LL-Q5287": [  # japonês (anime)
        "すごい", "なに", "ばか", "かわいい", "さようなら", "こんにちは", "ありがとう", "せんぱい", "がんばれ",
        "やった", "だめ", "うそ", "ほんと", "いくぞ", "まって", "たすけて", "おはよう", "おやすみ",
    ],
}


def commons_search(query, limit=40):
    r = commons_api({
        "action": "query", "list": "search", "srnamespace": 6, "srlimit": limit,
        "srsearch": query,
    })
    return [h["title"] for h in r.get("query", {}).get("search", [])]


def commons_info(titles):
    out = []
    for i in range(0, len(titles), 40):
        chunk = titles[i:i + 40]
        r = commons_api({
            "action": "query", "prop": "imageinfo", "titles": "|".join(chunk),
            "iiprop": "url|size|mime|mediatype|extmetadata|metadata",
            "iiextmetadatafilter": "LicenseShortName|Artist|ImageDescription|Credit|AttributionRequired",
        })
        for p in r.get("query", {}).get("pages", []):
            ii = (p.get("imageinfo") or [None])[0]
            if not ii or ii.get("mediatype") != "AUDIO":
                continue
            em = ii.get("extmetadata", {})
            lic_short = em.get("LicenseShortName", {}).get("value", "")
            dur = None
            for m in ii.get("metadata") or []:
                if m.get("name") in ("length", "playtime_seconds", "duration"):
                    try:
                        dur = float(m.get("value"))
                    except (TypeError, ValueError):
                        pass
            out.append({
                "title": p["title"],
                "url": ii.get("url"),
                "page": ii.get("descriptionurl"),
                "mime": ii.get("mime"),
                "bytes": ii.get("size"),
                "duration": round(dur, 2) if dur else None,
                "license_raw": lic_short,
                "license": map_license(lic_short),
                "author": strip_html(em.get("Artist", {}).get("value", ""))[:120],
                "desc": strip_html(em.get("ImageDescription", {}).get("value", ""))[:200],
            })
    return out


def discover_commons():
    results = {}
    for group, queries in COMMONS_QUERIES.items():
        seen, items = set(), []
        for q in queries:
            try:
                titles = commons_search(f"{q} filetype:audio", 30)
            except Exception as e:  # noqa: BLE001
                print("busca falhou", q, repr(e))
                continue
            new = [t for t in titles if t not in seen]
            seen.update(new)
            for it in commons_info(new):
                it["query"] = q
                if it["license"] and (it["duration"] is None or it["duration"] <= 30):
                    items.append(it)
        results[group] = items
        print(f"commons {group}: {len(items)} candidatos utilizáveis")
    for prefix, words in LINGUA_LIBRE.items():
        items, seen = [], set()
        for w in words:
            try:
                titles = commons_search(f'intitle:"{prefix}" intitle:"{w}"', 6)
            except Exception as e:  # noqa: BLE001
                print("busca LL falhou", w, e)
                continue
            # o título termina com "-<palavra>.wav"
            exact = [t for t in titles if t not in seen and re.search(r"-" + re.escape(w) + r"\.(wav|ogg|flac|mp3)$", t, re.I)]
            seen.update(exact)
            for it in commons_info(exact[:3]):
                it["query"] = w
                if it["license"]:
                    items.append(it)
        results[f"lingualibre_{prefix}"] = items
        print(f"lingua libre {prefix}: {len(items)}")
    with open(os.path.join(OUT, "commons.json"), "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=0)

# ------------------------------------------------------------------ pacotes CC0 (OpenGameArt / Kenney)

ZIP_SOURCES = [
    {
        "key": "oga_voiceover",
        "title": "Voiceover Pack (40+ lines) — Kenney",
        "page": "https://opengameart.org/content/voiceover-pack-40-lines",
        "zip": "https://opengameart.org/sites/default/files/Voiceover%20Pack.zip",
        "license": "CC0", "author": "Kenney (www.kenney.nl)",
    },
    {
        "key": "oga_512",
        "title": "512 Sound Effects (8-bit style) — SubspaceAudio (Juhani Junkala)",
        "page": "https://opengameart.org/content/512-sound-effects-8-bit-style",
        "zip": "https://opengameart.org/sites/default/files/The%20Essential%20Retro%20Video%20Game%20Sound%20Effects%20Collection%20%5B512%20sounds%5D.zip",
        "license": "CC0", "author": "SubspaceAudio (Juhani Junkala)",
    },
]

KENNEY_PAGES = ["voiceover-pack-fighter", "voiceover-pack", "impact-sounds", "interface-sounds", "music-jingles",
                "digital-audio", "sci-fi-sounds", "rpg-audio", "casino-audio", "ui-audio", "foley-sounds"]


def kenney_zip(slug):
    page = f"https://kenney.nl/assets/{slug}"
    html = http_get(page)
    m = re.search(r'href="([^"]+\.zip)"', html)
    if not m:
        return None
    url = m.group(1)
    if url.startswith("/"):
        url = "https://kenney.nl" + url
    lic = "CC0" if re.search(r"Creative Commons CC0|CC0", html) else None
    return {"key": "kenney_" + slug.replace("-", "_"), "title": f"Kenney — {slug}", "page": page, "zip": url,
            "license": lic, "author": "Kenney (www.kenney.nl)"}


def probe_duration(path):
    try:
        r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                           capture_output=True, text=True, timeout=30)
        return round(float(r.stdout.strip()), 2)
    except Exception:  # noqa: BLE001
        return None


def discover_zips():
    sources = list(ZIP_SOURCES)
    robots = {}
    for slug in KENNEY_PAGES:
        url = f"https://kenney.nl/assets/{slug}"
        robots[url] = allowed_by_robots(url)
        if not robots[url]:
            continue
        try:
            z = kenney_zip(slug)
            if z:
                sources.append(z)
        except Exception as e:  # noqa: BLE001
            print("kenney falhou", slug, e)
    report = {"robots": robots, "packs": []}
    for s in sources:
        entry = dict(s, files=[], error=None)
        robots[s["zip"]] = allowed_by_robots(s["zip"])
        try:
            data = http_get(s["zip"], binary=True, pause=2)
            zf = zipfile.ZipFile(io.BytesIO(data))
            with tempfile.TemporaryDirectory() as tmp:
                for n in zf.namelist():
                    if not re.search(r"\.(wav|ogg|mp3|flac)$", n, re.I) or "__MACOSX" in n:
                        continue
                    p = os.path.join(tmp, "x" + os.path.splitext(n)[1])
                    with open(p, "wb") as f:
                        f.write(zf.read(n))
                    entry["files"].append({"path": n, "bytes": zf.getinfo(n).file_size, "duration": probe_duration(p)})
            lic_files = [n for n in zf.namelist() if re.search(r"licen|readme", n, re.I)]
            entry["license_files"] = {n: zf.read(n).decode("utf-8", "replace")[:600] for n in lic_files[:3]}
        except Exception as e:  # noqa: BLE001
            entry["error"] = str(e)
        print(f"pacote {s['key']}: {len(entry['files'])} arquivos {entry['error'] or ''}")
        report["packs"].append(entry)
    with open(os.path.join(OUT, "packs.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=0)


if __name__ == "__main__":
    if not os.path.exists(os.path.join(OUT, "packs.json")):
        discover_zips()
    discover_commons()

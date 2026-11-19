#!/usr/bin/env python3
"""
FASE 2 — Download dos sons escolhidos à mão em tools/sound_sources.json.

Para cada item:
  1. Revalida a licença NA FONTE no momento do download (Commons: API extmetadata;
     pacotes: licença declarada no pacote). Licença não permitida → item recusado.
  2. Baixa (um arquivo por vez, User-Agent identificado, robots.txt respeitado).
  3. Converte para OGG Vorbis mono 44,1 kHz, corta silêncio nas pontas, limita a duração
     e normaliza o volume. (CC BY-SA: a versão convertida continua CC BY-SA, com crédito.)
  4. Registra no manifesto: nome, categoria, fonte, autor, licença, link e duração medida.

Saídas: app/src/main/assets/sounds/x_*.ogg, app/src/main/assets/sounds.json (mescla com os
sons originais existentes) e docs/RELATORIO_SONS.md.
"""
import io
import json
import os
import subprocess
import sys
import tempfile
import urllib.parse
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
from sounds_common import commons_api, http_get, map_license, strip_html  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SOUNDS_DIR = os.path.join(ASSETS, "sounds")
MANIFEST = os.path.join(ASSETS, "sounds.json")
SOURCES = os.path.join(ROOT, "tools", "sound_sources.json")
PACKS = os.path.join(ROOT, "tools", "candidates", "packs.json")
REPORT = os.path.join(ROOT, "docs", "RELATORIO_SONS.md")
MAX_SECONDS_DEFAULT = 12.0

_zip_cache = {}


def pack_zip(pack_key):
    if pack_key in _zip_cache:
        return _zip_cache[pack_key]
    packs = {p["key"]: p for p in json.load(open(PACKS, encoding="utf-8"))["packs"]}
    p = packs[pack_key]
    data = http_get(p["zip"], binary=True, pause=2)
    _zip_cache[pack_key] = (zipfile.ZipFile(io.BytesIO(data)), p)
    return _zip_cache[pack_key]


def from_commons(title):
    r = commons_api({
        "action": "query", "prop": "imageinfo", "titles": title,
        "iiprop": "url|mime|mediatype|extmetadata",
        "iiextmetadatafilter": "LicenseShortName|Artist|Credit",
    })
    page = r["query"]["pages"][0]
    ii = (page.get("imageinfo") or [None])[0]
    if not ii:
        raise ValueError("arquivo não encontrado no Commons")
    em = ii.get("extmetadata", {})
    lic_raw = em.get("LicenseShortName", {}).get("value", "")
    lic = map_license(lic_raw)
    if not lic:
        raise ValueError(f"licença não permitida: {lic_raw!r}")
    data = http_get(ii["url"], binary=True, pause=1.5)
    author = strip_html(em.get("Artist", {}).get("value", "")) or None
    ext = os.path.splitext(urllib.parse.urlparse(ii["url"]).path)[1] or ".bin"
    return data, ext, {
        "license": lic, "license_raw": lic_raw, "author": author[:120] if author else None,
        "sourceUrl": ii.get("descriptionurl"), "source": "Wikimedia Commons",
    }


def from_zip(pack_key, path):
    zf, p = pack_zip(pack_key)
    lic = p.get("license")
    if lic not in ("CC0", "PUBLIC_DOMAIN"):
        raise ValueError(f"pacote sem licença CC0 confirmada: {lic!r}")
    return zf.read(path), os.path.splitext(path)[1], {
        "license": lic, "license_raw": lic, "author": p.get("author"),
        "sourceUrl": p.get("page"), "source": "OpenGameArt" if "opengameart" in p["page"] else p["page"],
    }


def from_freesound(sound_id):
    key = os.environ.get("FREESOUND_API_KEY")
    if not key:
        raise ValueError("FREESOUND_API_KEY não configurada (segredo do GitHub)")
    info = json.loads(http_get(
        f"https://freesound.org/apiv2/sounds/{int(sound_id)}/?fields=id,name,username,license,url,previews&token={key}",
        pause=1.0, respect_robots=False))
    lic_url = info.get("license", "")
    lic = "CC0" if "zero" in lic_url else "CC_BY" if "/by/" in lic_url else None
    if not lic:
        raise ValueError(f"licença Freesound não permitida: {lic_url}")
    data = http_get(info["previews"]["preview-hq-ogg"] + f"?token={key}", binary=True, respect_robots=False)
    return data, ".ogg", {"license": lic, "license_raw": lic_url, "author": info.get("username"),
                          "sourceUrl": info.get("url"), "source": "Freesound"}


def convert(raw, ext, out_path, max_seconds, start=0.0):
    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "in" + ext)
        with open(src, "wb") as f:
            f.write(raw)
        trim = "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.02"
        flt = (
            f"{trim},areverse,{trim},areverse,"
            f"atrim=0:{max_seconds},afade=t=out:st={max(0.0, max_seconds - 0.15)}:d=0.15,"
            "loudnorm=I=-16:TP=-1.5:LRA=11"
        )
        cmd = ["ffmpeg", "-v", "error", "-y"]
        if start:
            cmd += ["-ss", str(start)]
        cmd += ["-i", src, "-af", flt, "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "4", out_path]
        subprocess.run(cmd, check=True, timeout=120)
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", out_path],
                       capture_output=True, text=True, check=True)
    return int(round(float(r.stdout.strip()) * 1000))


def main():
    spec = json.load(open(SOURCES, encoding="utf-8"))
    manifest = json.load(open(MANIFEST, encoding="utf-8"))
    original = [s for s in manifest["sounds"] if s.get("license") == "ORIGINAL"]
    for f in os.listdir(SOUNDS_DIR):
        if f.startswith("x_") and f.endswith(".ogg"):
            os.remove(os.path.join(SOUNDS_DIR, f))

    added, failed = [], []
    for item in spec["sounds"]:
        src = item["from"]
        try:
            if src["type"] == "commons":
                raw, ext, meta = from_commons(src["title"])
            elif src["type"] == "zip":
                raw, ext, meta = from_zip(src["pack"], src["path"])
            elif src["type"] == "freesound":
                raw, ext, meta = from_freesound(src["id"])
            else:
                raise ValueError("tipo de fonte desconhecido")
            fname = f"x_{item['id']}.ogg"
            dur = convert(raw, ext, os.path.join(SOUNDS_DIR, fname),
                          float(item.get("maxSeconds", MAX_SECONDS_DEFAULT)), float(item.get("start", 0)))
            if dur < 80:
                raise ValueError("áudio vazio depois de remover silêncio")
            added.append({
                "id": "x_" + item["id"], "file": fname, "name": item["name"], "categoryId": item["categoryId"],
                "durationMs": dur, "license": meta["license"], "author": meta["author"],
                "sourceUrl": meta["sourceUrl"], "tags": item.get("tags", []) + [meta["source"].lower()],
                "_source": meta["source"], "_license_raw": meta["license_raw"],
                "_origin": src.get("title") or src.get("path") or str(src.get("id")),
            })
            print("ok ", item["id"], meta["license"], dur, "ms")
        except Exception as e:  # noqa: BLE001
            failed.append((item, str(e)))
            print("ERRO", item["id"], e)

    clean = [{k: v for k, v in s.items() if not k.startswith("_")} for s in added]
    manifest["sounds"] = original + clean
    manifest["pending"] = spec.get("pending", [])
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    write_report(manifest, original, added, failed, spec.get("pending", []))
    print(f"\n{len(added)} baixados, {len(failed)} recusados, {len(spec.get('pending', []))} pendentes")


def write_report(manifest, original, added, failed, pending):
    cats = {c["id"]: c for c in manifest["categories"]}
    lines = ["# Relatório da biblioteca de sons", "",
             "Gerado automaticamente por `tools/fetch_sounds.py`. Não edite à mão.", "",
             "## Totais por categoria", "",
             "| Categoria | Meta | Originais (síntese) | Fontes livres | Total | Pendentes (importação manual) |",
             "|---|---|---|---|---|---|"]
    for cid, c in cats.items():
        o = sum(1 for s in original if s["categoryId"] == cid)
        a = sum(1 for s in added if s["categoryId"] == cid)
        p = sum(1 for s in pending if s["categoryId"] == cid)
        lines.append(f"| {c['name']} | {c.get('targetCount', '')} | {o} | {a} | {o + a} | {p} |")
    lines.append(f"| **Total** | **{sum(c.get('targetCount', 0) for c in cats.values())}** | **{len(original)}** | "
                 f"**{len(added)}** | **{len(original) + len(added)}** | **{len(pending)}** |")
    lines += ["", "## Sons de fontes livres incluídos", "",
              "| Nome | Categoria | Fonte | Autor | Licença | Duração | Link |", "|---|---|---|---|---|---|---|"]
    for s in sorted(added, key=lambda s: (s["categoryId"], s["name"])):
        lines.append(f"| {s['name']} | {cats[s['categoryId']]['name']} | {s['_source']} | {s['author'] or '—'} | "
                     f"{s['_license_raw']} | {s['durationMs'] / 1000:.1f} s | {s['sourceUrl']} |")
    if failed:
        lines += ["", "## Recusados no download", "", "| Item | Motivo |", "|---|---|"]
        lines += [f"| {it['name']} | {why} |" for it, why in failed]
    lines += ["", "## Pendentes (sem fonte autorizada → importação manual pelo app)", "",
              "| Nome | Categoria | Por quê | Identificação |", "|---|---|---|---|"]
    for p in pending:
        lines.append(f"| {p['name']} | {cats[p['categoryId']]['name']} | {p.get('reason', '')} | {p.get('hint', '') or '—'} |")
    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    with open(REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()

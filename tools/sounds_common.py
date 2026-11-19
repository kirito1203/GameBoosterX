"""Funções compartilhadas pelos scripts de descoberta e download de sons.

Políticas respeitadas:
- Wikimedia Commons: API oficial, User-Agent identificado, pausa entre requisições (API:Etiquette).
- OpenGameArt: só arquivos de pacotes com licença CC0/CC-BY, baixados um a um, respeitando robots.txt.
- Pixabay: termos proíbem coleta automatizada → NÃO é acessado por script (só importação manual).
- Freesound: exige chave de API pessoal → só usado se o segredo FREESOUND_API_KEY existir.
"""
import json
import re
import time
import urllib.parse
import urllib.request
import urllib.robotparser

UA = "MemeSoundboardX-LibraryBuilder/0.3 (https://github.com/kirito1203/GameBoosterX; personal Android soundboard) python-urllib"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"

_robots = {}


def allowed_by_robots(url):
    p = urllib.parse.urlparse(url)
    base = f"{p.scheme}://{p.netloc}"
    rp = _robots.get(base)
    if rp is None:
        rp = urllib.robotparser.RobotFileParser()
        try:
            req = urllib.request.Request(base + "/robots.txt", headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=30) as r:
                rp.parse(r.read().decode("utf-8", "replace").splitlines())
        except Exception:
            rp.parse([])  # sem robots.txt acessível = sem restrições declaradas
        _robots[base] = rp
    return rp.can_fetch(UA, url)


def http_get(url, binary=False, pause=1.0, retries=3):
    if not allowed_by_robots(url):
        raise PermissionError(f"robots.txt não permite: {url}")
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r:
                data = r.read()
            time.sleep(pause)
            return data if binary else data.decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(3 * (attempt + 1))
    raise last


def commons_api(params):
    params = dict(params, format="json", formatversion="2")
    return json.loads(http_get(COMMONS_API + "?" + urllib.parse.urlencode(params), pause=0.5))


def strip_html(s):
    s = re.sub(r"<[^>]+>", "", s or "")
    s = re.sub(r"\s+", " ", s)
    return s.strip()


ALLOWED_LICENSES = [
    # (padrão no LicenseShortName do Commons, código no app)
    (r"^cc0", "CC0"),
    (r"public domain|^pd\b|^pd-", "PUBLIC_DOMAIN"),
    (r"^cc by-sa", "CC_BY_SA"),
    (r"^cc by\b", "CC_BY"),
]
BLOCKED = re.compile(r"\bnc\b|noncommercial|non-commercial|\bnd\b|noderiv|fair use|gfdl only|all rights", re.I)


def map_license(short):
    s = (short or "").strip().lower()
    if not s or BLOCKED.search(s):
        return None
    for pat, code in ALLOWED_LICENSES:
        if re.search(pat, s):
            return code
    return None

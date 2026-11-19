#!/usr/bin/env python3
"""
Gera a biblioteca embutida do MEME SOUNDBOARD X por síntese sonora.

Todos os sons são ORIGINAIS: criados do zero por este script (osciladores, ruído,
filtros, envelopes, síntese FM e Karplus-Strong). Nenhuma gravação de terceiros é usada,
por isso a licença no manifesto é ORIGINAL.

Uso:  python3 tools/generate_sounds.py
Saída: app/src/main/assets/sounds/*.ogg  e  app/src/main/assets/sounds.json
Requer: numpy, scipy, ffmpeg com libvorbis.
O resultado é determinístico (sementes fixas por som).
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
import scipy.signal as sg

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
OUT = os.path.join(ASSETS, "sounds")

# ---------------------------------------------------------------- primitivas

def T(d):
    return np.arange(int(SR * d)) / SR


def n_of(d):
    return int(SR * d)


def midi(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def phase_of(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,)) if np.ndim(freq) == 0 else np.asarray(freq, float)[:n]
    return 2 * np.pi * np.cumsum(f) / SR


def osc(freq, d, kind="sine", duty=0.5):
    n = n_of(d)
    ph = phase_of(freq, n)
    if kind == "sine":
        return np.sin(ph)
    frac = (ph / (2 * np.pi)) % 1.0
    if kind == "square":
        return np.where(frac < duty, 1.0, -1.0)
    if kind == "saw":
        return 2 * frac - 1
    if kind == "tri":
        return 2 * np.abs(2 * frac - 1) - 1
    raise ValueError(kind)


def sweep(f0, f1, d, curve="exp"):
    x = np.linspace(0, 1, n_of(d))
    if curve == "exp":
        return f0 * (f1 / f0) ** x
    return f0 + (f1 - f0) * x


class Rng:
    def __init__(self, seed):
        self.g = np.random.default_rng(seed)

    def noise(self, d, color="white"):
        x = self.g.standard_normal(n_of(d))
        if color == "pink":
            b, a = [0.049922035, -0.095993537, 0.050612699, -0.004408786], [1, -2.494956002, 2.017265875, -0.522189400]
            x = sg.lfilter(b, a, x)
        elif color == "brown":
            x = np.cumsum(x)
            x = sg.lfilter([1, -1], [1, -0.995], x)
        return x / (np.max(np.abs(x)) + 1e-9)


def env_exp(d, rate):
    return np.exp(-T(d) * rate)


def adsr(d, a=0.01, dc=0.1, s=0.7, r=0.1):
    n = n_of(d)
    e = np.ones(n) * s
    na, nd, nr = n_of(a), n_of(dc), n_of(r)
    na = max(1, min(na, n))
    e[:na] = np.linspace(0, 1, na)
    if nd > 0 and na + nd < n:
        e[na:na + nd] = np.linspace(1, s, nd)
    if nr > 0:
        nr = min(nr, n)
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def lp(x, fc, order=4):
    fc = min(fc, SR / 2 * 0.95)
    return sg.sosfilt(sg.butter(order, fc, "low", fs=SR, output="sos"), x)


def hp(x, fc, order=4):
    return sg.sosfilt(sg.butter(order, fc, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, order=3):
    hi = min(hi, SR / 2 * 0.95)
    return sg.sosfilt(sg.butter(order, [lo, hi], "band", fs=SR, output="sos"), x)


def fm(fc, ratio, index, d, index_env=None):
    n = n_of(d)
    mod_ph = phase_of(np.asarray(fc) * ratio if np.ndim(fc) else fc * ratio, n)
    idx = index if index_env is None else index * index_env
    car = phase_of(fc, n)
    return np.sin(car + idx * np.sin(mod_ph))


def ks(freq, d, decay=0.996, rng=None, bright=0.5):
    """Karplus-Strong: corda dedilhada."""
    n = n_of(d)
    period = max(2, int(SR / freq))
    g = (rng or Rng(int(freq))).g
    buf = g.uniform(-1, 1, period)
    buf = lp(buf, 2000 + 8000 * bright, 2) if period > 16 else buf
    out = np.zeros(n)
    for i in range(n):
        v = buf[i % period]
        out[i] = v
        buf[i % period] = decay * 0.5 * (v + buf[(i + 1) % period])
    return out


def pad(x, n):
    return np.pad(x, (0, max(0, n - len(x))))[:n] if n else x


def mix(*parts):
    n = max(len(p) for p in parts)
    return sum(pad(p, n) for p in parts)


def place(events, total=None):
    """events: lista de (tempo_s, sinal, ganho)."""
    end = max(int(t * SR) + len(s) for t, s, *_ in events)
    n = max(end, n_of(total) if total else 0)
    out = np.zeros(n)
    for ev in events:
        t, s = ev[0], ev[1]
        g = ev[2] if len(ev) > 2 else 1.0
        i = int(t * SR)
        out[i:i + len(s)] += g * s[: n - i]
    return out


def reverb(x, rt=1.2, mix_=0.3, seed=7, tone=6000):
    ir = Rng(seed).noise(rt) * env_exp(rt, 6.9 / rt)
    ir = lp(ir, tone, 2)
    wet = sg.fftconvolve(x, ir)[: len(x) + n_of(rt)]
    wet /= np.max(np.abs(wet)) + 1e-9
    dry = pad(x, len(wet))
    return (1 - mix_) * dry / (np.max(np.abs(dry)) + 1e-9) + mix_ * wet


def echo(x, ms=180, fb=0.45, n=4):
    out = pad(x, len(x) + n_of(ms / 1000 * n))
    for k in range(1, n + 1):
        i = n_of(ms / 1000 * k)
        out[i:i + len(x)] += (fb ** k) * x[: len(out) - i]
    return out


def crush(x, bits=6, down=4):
    y = np.repeat(x[::down], down)[: len(x)]
    q = 2 ** (bits - 1)
    return np.round(y * q) / q


def dist(x, drive=3.0):
    return np.tanh(x * drive) / np.tanh(drive)


def fade(x, fi=0.003, fo=0.015):
    x = x.copy()
    a, b = min(len(x), n_of(fi)), min(len(x), n_of(fo))
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def finish(x, peak=0.94, target_rms=0.17):
    """Normaliza loudness (RMS alvo, limitado pelo pico) e corta silêncio no fim."""
    x = np.nan_to_num(np.asarray(x, float))
    x = x - np.mean(x)
    m = np.max(np.abs(x))
    if m < 1e-9:
        return x
    x = x / m
    # RMS calculado só na parte audível, para sons esparsos não ficarem altos demais
    loud = np.abs(x) > 0.01
    rms = np.sqrt(np.mean(x[loud] ** 2)) if loud.any() else 1.0
    x = x * min(target_rms / rms, peak)
    nz = np.where(np.abs(x) > 0.004)[0]
    if len(nz):
        x = x[: min(len(x), nz[-1] + int(0.08 * SR))]
    return fade(x, 0.003, 0.02)

# ---------------------------------------------------------- instrumentos

def kick(d=0.45, f0=150, f1=45):
    return mix(osc(sweep(f0, f1, d), d) * env_exp(d, 9), 0.3 * Rng(1).noise(0.01))


def snare(d=0.25, seed=2):
    r = Rng(seed)
    return 0.7 * bp(r.noise(d), 1200, 7000) * env_exp(d, 18) + 0.4 * osc(190, d) * env_exp(d, 30)


def hat(d=0.06, seed=3):
    return hp(Rng(seed).noise(d), 7000) * env_exp(d, 60)


def clap(seed=4):
    r = Rng(seed)
    burst = lambda: bp(r.noise(0.012), 900, 5000)
    tail = bp(r.noise(0.18), 900, 4000) * env_exp(0.18, 25)
    return place([(0, burst()), (0.011, burst(), 0.8), (0.022, burst(), 0.6), (0.03, tail)])


def bell(f, d=1.2):
    return fm(f, 3.5, 2.5, d, env_exp(d, 3)) * env_exp(d, 3.2)


def epiano(f, d=0.8):
    return (fm(f, 1.0, 1.8, d, env_exp(d, 6)) + 0.3 * osc(f * 2, d) * env_exp(d, 8)) * env_exp(d, 3.5)


def chip(f, d, duty=0.5, rate=0):
    e = adsr(d, 0.003, 0.05, 0.6, 0.02) if rate == 0 else env_exp(d, rate)
    return osc(f, d, "square", duty) * e * 0.5


def pluck(f, d=0.6, decay=0.995, bright=0.6):
    return ks(f, d, decay, Rng(int(f * 10)), bright)


def padsynth(f, d, a=0.4):
    s = osc(f, d, "saw") + osc(f * 1.006, d, "saw") + osc(f * 0.994, d, "saw")
    return lp(s, 1800) * adsr(d, a, 0.2, 0.8, min(0.4, d / 3))


def bass(f, d):
    return lp(osc(f, d, "saw"), 700) * adsr(d, 0.005, 0.1, 0.7, 0.05)


def strings(f, d):
    v = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * T(d))
    s = osc(f * v, d, "saw") + osc(f * 1.003 * v, d, "saw")
    return lp(s, 2600) * adsr(d, 0.12, 0.2, 0.85, 0.15)


def brass(f, d):
    s = osc(f, d, "saw")
    cutoff_env = adsr(d, 0.05, 0.15, 0.7, 0.1)
    out = np.zeros_like(s)
    # filtro "abrindo" aproximado por mistura de dois passa-baixas
    out = lp(s, 900) * (1 - cutoff_env) + lp(s, 3500) * cutoff_env
    return out * adsr(d, 0.04, 0.1, 0.8, 0.08)


def melody(notes, inst, bpm=120, gap=1.0):
    """notes: lista de (midi|None, batidas)."""
    beat = 60 / bpm
    ev, t = [], 0.0
    for m, b in notes:
        d = b * beat
        if m is not None:
            ms = m if isinstance(m, (list, tuple)) else [m]
            for mm in ms:
                ev.append((t, inst(midi(mm), d * gap), 1 / len(ms) ** 0.5))
        t += d
    return place(ev, total=t)


def drums(pattern, bpm=110, bars=1, swing=0.0):
    """pattern: dict com 'k','s','h','c' -> strings de 16 passos ('x' toca)."""
    step = 60 / bpm / 4
    ev = []
    for bar in range(bars):
        for i in range(16):
            t = (bar * 16 + i) * step + (swing * step if i % 2 else 0)
            for key, fn, g in (("k", kick, 1.0), ("s", snare, 0.8), ("h", hat, 0.35), ("c", clap, 0.7)):
                p = pattern.get(key, "")
                if i < len(p) and p[i] == "x":
                    ev.append((t, fn(), g))
    return place(ev, total=bars * 16 * step)

# ------------------------------------------------------------- registro

SOUNDS = []


def reg(cat, sid, name, tags, fn):
    SOUNDS.append(dict(cat=cat, id=sid, name=name, tags=tags, fn=fn))

# ============================================================ IMPACTOS E EFEITOS CÔMICOS
C = "impactos_comicos"

def punch(weight=1.0, seed=10):
    r = Rng(seed)
    d = 0.35 * weight
    body = osc(sweep(120 * weight ** -0.3, 50, d), d) * env_exp(d, 14 / weight)
    snap = lp(r.noise(0.04), 4000) * env_exp(0.04, 70)
    return dist(mix(body, 0.8 * snap), 2.5)

reg(C, "soco_seco", "Soco seco", ["soco", "luta", "porrada"], lambda: punch(0.8))
reg(C, "soco_pesado", "Soco pesado", ["soco", "luta", "impacto"], lambda: reverb(punch(1.6, 11), 0.5, 0.2))
reg(C, "tapa", "Tapa", ["tapa", "slap", "bofetada"], lambda: mix(hp(Rng(12).noise(0.12), 900) * env_exp(0.12, 45), 0.4 * bp(Rng(13).noise(0.05), 2000, 6000) * env_exp(0.05, 80)))
reg(C, "bonk", "Bonk", ["bonk", "panela", "metal", "cabeçada"], lambda: (fm(sweep(520, 470, 0.7), 2.76, 3.0, 0.7, env_exp(0.7, 5)) * env_exp(0.7, 6)))
reg(C, "bonk_agudo", "Bonk agudo", ["bonk", "metal"], lambda: fm(sweep(900, 820, 0.5), 2.76, 2.2, 0.5, env_exp(0.5, 7)) * env_exp(0.5, 8))
reg(C, "queda_seca", "Queda seca", ["queda", "tombo", "thud"], lambda: osc(sweep(90, 40, 0.4), 0.4) * env_exp(0.4, 12) + 0.3 * lp(Rng(14).noise(0.4), 600) * env_exp(0.4, 15))

def explosion(d=1.6, size=1.0, seed=15):
    r = Rng(seed)
    body = lp(r.noise(d, "brown"), 400 + 600 * size) * env_exp(d, 3.5 / size)
    crack = lp(r.noise(0.15), 5000) * env_exp(0.15, 25)
    sub = osc(sweep(60, 28, d), d) * env_exp(d, 4)
    return dist(mix(body, 0.6 * crack, 0.8 * sub), 2.0)

reg(C, "explosao_curta", "Explosão curta", ["explosão", "boom", "bomba"], lambda: explosion(0.9, 0.7))
reg(C, "explosao_grande", "Explosão grande", ["explosão", "boom", "bomba"], lambda: reverb(explosion(2.4, 1.4, 16), 1.5, 0.25))
reg(C, "explosao_distante", "Explosão distante", ["explosão", "longe"], lambda: lp(reverb(explosion(2.0, 1.0, 17), 2.0, 0.5), 900))

def boing(f0=180, d=0.8, depth=0.35, rate=22):
    f = f0 * (1 + depth * np.sin(2 * np.pi * rate * T(d)) * env_exp(d, 3)) * sweep(1.0, 1.6, d, "lin")
    return osc(f, d, "tri") * env_exp(d, 4)

reg(C, "boing", "Boing", ["boing", "mola", "pulo"], lambda: boing())
reg(C, "boing_duplo", "Boing duplo", ["boing", "mola"], lambda: place([(0, boing(200, 0.5)), (0.35, boing(260, 0.6))]))
reg(C, "mola_vibrando", "Mola vibrando", ["mola", "boing"], lambda: boing(120, 1.2, 0.5, 30))

def slide_whistle(f0, f1, d=0.9):
    vib = 1 + 0.01 * np.sin(2 * np.pi * 6 * T(d))
    return (osc(sweep(f0, f1, d) * vib, d) + 0.15 * Rng(18).noise(d) * 0.3) * adsr(d, 0.05, 0.1, 0.9, 0.08)

reg(C, "apito_subindo", "Apito subindo", ["apito", "slide", "desenho"], lambda: slide_whistle(500, 1800))
reg(C, "apito_descendo", "Apito descendo", ["apito", "slide", "queda", "desenho"], lambda: slide_whistle(1800, 400, 1.1))
reg(C, "pop_bolha", "Pop de bolha", ["pop", "bolha"], lambda: osc(sweep(400, 1400, 0.06), 0.06) * env_exp(0.06, 50))
reg(C, "pop_rolha", "Pop de rolha", ["pop", "rolha", "garrafa"], lambda: mix(osc(sweep(900, 300, 0.08), 0.08) * env_exp(0.08, 40), 0.5 * bp(Rng(19).noise(0.05), 1000, 4000) * env_exp(0.05, 70)))

def tick(f=2500, d=0.03, seed=20):
    return bp(Rng(seed).noise(d), f * 0.6, f * 1.6) * env_exp(d, 120)

reg(C, "passos_desenho", "Passinhos de desenho", ["passos", "andar", "desenho"], lambda: place([(i * 0.16, mix(tick(1200, 0.05, 20 + i), 0.6 * osc(300 + 80 * (i % 2), 0.05) * env_exp(0.05, 60))) for i in range(6)]))
reg(C, "corrida_desenho", "Corrida de desenho", ["correr", "fuga", "desenho"], lambda: place([(i * 0.055, mix(tick(1500, 0.03, 30 + i), 0.5 * osc(400 + 60 * (i % 3), 0.03) * env_exp(0.03, 90))) for i in range(22)]))

def whoosh(d=0.5, lo=300, hi=3000, seed=40, up=True):
    r = Rng(seed)
    x = r.noise(d, "pink")
    e = np.sin(np.pi * np.linspace(0, 1, n_of(d))) ** 2
    fc = sweep(lo, hi, d) if up else sweep(hi, lo, d)
    # filtro variável aproximado por blocos
    out = np.zeros(n_of(d))
    blk = 512
    for i in range(0, len(out), blk):
        c = fc[min(i, len(fc) - 1)]
        out[i:i + blk] = bp(x, max(80, c * 0.5), c * 1.5, 2)[i:i + blk]
    return out * e

reg(C, "zip_rapido", "Zip rápido", ["zip", "rápido", "sumir"], lambda: whoosh(0.22, 800, 6000, 41))
reg(C, "whoosh", "Whoosh", ["whoosh", "vento", "passar"], lambda: whoosh(0.55))
reg(C, "whoosh_pesado", "Whoosh pesado", ["whoosh", "pesado"], lambda: lp(whoosh(0.9, 120, 1200, 42), 2000))
reg(C, "vidro_quebrando", "Vidro quebrando", ["vidro", "quebrar", "crash"], lambda: reverb(mix(hp(Rng(43).noise(0.6), 2500) * env_exp(0.6, 9), *[0.25 * osc(f, 0.6) * env_exp(0.6, 12 + i) for i, f in enumerate([3100, 4270, 5530, 6890, 2440])]), 0.6, 0.2))
reg(C, "batida_porta", "Batida na porta", ["porta", "bater", "toc toc"], lambda: place([(i * 0.22, lp(osc(sweep(180, 90, 0.12), 0.12) * env_exp(0.12, 30) + 0.4 * lp(Rng(44 + i).noise(0.12), 1500) * env_exp(0.12, 40), 2500)) for i in range(3)]))
reg(C, "sino_luta", "Sino de luta", ["sino", "ringue", "round", "luta"], lambda: place([(i * 0.28, bell(1180, 1.6)) for i in range(3)]))
reg(C, "rimshot", "Ba-dum-tss", ["piada", "rimshot", "bateria"], lambda: place([(0, snare(0.15)), (0.2, mix(snare(0.12), 0.8 * kick(0.3))), (0.45, hp(Rng(45).noise(1.2), 5000) * env_exp(1.2, 3.5), 0.7)]))

def sad_trombone():
    notes = [(midi(58), 0.38), (midi(57), 0.38), (midi(56), 0.38), (midi(55), 1.3)]
    ev, t = [], 0
    for f, d in notes:
        vib = 1 + (0.03 * np.sin(2 * np.pi * 7 * T(d)) * np.linspace(0, 1, n_of(d)) if d > 1 else 0)
        ev.append((t, brass(f * vib, d)))
        t += d
    return place(ev)

reg(C, "trombone_triste", "Trombone triste", ["fail", "triste", "falhou", "wah wah"], sad_trombone)
reg(C, "buzina_palhaco", "Buzina de palhaço", ["buzina", "palhaço", "honk"], lambda: place([(0, dist(lp(osc(310, 0.18, "saw") + osc(318, 0.18, "saw"), 2200), 3) * adsr(0.18, 0.01, 0.05, 0.8, 0.04)), (0.25, dist(lp(osc(300, 0.3, "saw") + osc(308, 0.3, "saw"), 2200), 3) * adsr(0.3, 0.01, 0.05, 0.8, 0.06))]))
reg(C, "apito_juiz", "Apito de juiz", ["apito", "falta", "juiz"], lambda: (osc(2900 * (1 + 0.02 * np.sign(np.sin(2 * np.pi * 28 * T(0.8)))), 0.8) + 0.2 * bp(Rng(46).noise(0.8), 2500, 3500)) * adsr(0.8, 0.01, 0.05, 0.9, 0.1))
reg(C, "plim_ideia", "Plim de ideia", ["ideia", "plim", "lâmpada"], lambda: mix(bell(1568, 1.0), 0.6 * bell(2093, 1.0)))
reg(C, "tombo_desenho", "Tombo de desenho", ["tombo", "queda", "cair", "desenho"], lambda: place([(0, slide_whistle(1600, 300, 1.0)), (1.05, mix(osc(sweep(90, 40, 0.4), 0.4) * env_exp(0.4, 10), 0.5 * lp(Rng(47).noise(0.4), 800) * env_exp(0.4, 14)))]))
reg(C, "pancada_metal", "Pancada metálica", ["metal", "pancada", "cano"], lambda: reverb(fm(330, 1.41, 6, 1.2, env_exp(1.2, 4)) * env_exp(1.2, 3.5), 0.8, 0.25))
reg(C, "martelada", "Martelada", ["martelo", "bater", "obra"], lambda: place([(i * 0.35, mix(fm(1800, 1.73, 3, 0.25, env_exp(0.25, 20)) * env_exp(0.25, 25), 0.6 * punch(0.5, 48 + i))) for i in range(3)]))
reg(C, "estalo_dedos", "Estalo de dedos", ["estalo", "dedos", "snap"], lambda: mix(bp(Rng(49).noise(0.05), 1500, 6000) * env_exp(0.05, 90), 0.4 * osc(2000, 0.02) * env_exp(0.02, 200)))
reg(C, "chicote", "Chicote", ["chicote", "estalo"], lambda: place([(0, whoosh(0.25, 400, 5000, 50)), (0.24, hp(Rng(51).noise(0.08), 2000) * env_exp(0.08, 60), 1.4)]))

# ============================================================ RISADAS E REAÇÕES (sem vozes)
C = "risadas_reacoes"

def crickets(d=3.0, seed=60):
    r = Rng(seed)
    ev = []
    t = 0.0
    while t < d - 0.3:
        chirp = osc(4300 + r.g.uniform(-150, 150), 0.12) * (0.5 + 0.5 * np.sin(2 * np.pi * 30 * T(0.12) - np.pi / 2)) ** 2 * adsr(0.12, 0.01, 0.02, 0.8, 0.03)
        ev.append((t, chirp, r.g.uniform(0.5, 1.0)))
        t += r.g.uniform(0.35, 0.7)
    return place(ev, total=d)

reg(C, "grilos", "Grilos (silêncio constrangedor)", ["grilos", "silêncio", "vergonha", "awkward"], lambda: crickets())

def applause(d=3.0, density=90, seed=61):
    r = Rng(seed)
    ev = []
    for _ in range(int(d * density)):
        t = r.g.uniform(0, d - 0.2)
        c = bp(r.noise(0.035), r.g.uniform(700, 1500), r.g.uniform(2500, 5000)) * env_exp(0.035, 90)
        ev.append((t, c, r.g.uniform(0.3, 1.0)))
    x = place(ev, total=d)
    return x * adsr(len(x) / SR, 0.3, 0.1, 1, 0.8)

reg(C, "aplausos", "Aplausos", ["aplausos", "palmas", "parabéns"], lambda: applause())
reg(C, "aplausos_fortes", "Aplausos fortes", ["aplausos", "palmas", "plateia"], lambda: reverb(applause(4.0, 180, 62), 0.8, 0.2))

def drumroll(d=2.0, seed=63):
    r = Rng(seed)
    ev = [(i * 0.035, snare(0.08, 63 + i), 0.35 + 0.65 * (i * 0.035 / d)) for i in range(int(d / 0.035))]
    return place(ev, total=d + 0.1)

reg(C, "rufar_tambor", "Rufar de tambor", ["suspense", "tambor", "resultado"], lambda: drumroll())
reg(C, "rufar_prato", "Rufar + prato", ["suspense", "tambor", "revelação"], lambda: place([(0, drumroll(1.8)), (1.85, mix(kick(0.5), hp(Rng(64).noise(1.6), 4000) * env_exp(1.6, 2.5)))]))
reg(C, "ta_da", "Ta-da!", ["tada", "mágica", "apresentando"], lambda: melody([(72, 0.5), ([72, 76, 79, 84], 2)], brass, 140))
reg(C, "ta_da_triunfal", "Ta-da triunfal", ["tada", "vitória", "triunfo"], lambda: reverb(melody([(67, 0.33), (67, 0.33), (67, 0.33), ([72, 76, 79], 2.5)], brass, 150), 1.0, 0.2))
reg(C, "acerto", "Resposta certa", ["certo", "acerto", "correto"], lambda: melody([(84, 0.5), (91, 1.5)], lambda f, d: bell(f, d), 180))
reg(C, "erro_buzzer", "Resposta errada (buzzer)", ["errado", "erro", "buzzer"], lambda: dist(lp(osc(110, 0.9, "saw") + osc(116, 0.9, "square"), 1500), 2) * adsr(0.9, 0.01, 0.05, 0.9, 0.05))
reg(C, "erro_duplo", "Erro duplo", ["errado", "erro"], lambda: place([(0, lp(osc(200, 0.22, "square"), 2000) * adsr(0.22, 0.005, 0.02, 0.9, 0.03)), (0.28, lp(osc(150, 0.45, "square"), 2000) * adsr(0.45, 0.005, 0.02, 0.9, 0.05))]))
reg(C, "ding", "Ding", ["ding", "notificação", "certo"], lambda: bell(1760, 1.5))
reg(C, "ding_dong", "Ding-dong", ["campainha", "porta", "chegou"], lambda: place([(0, bell(659, 1.0)), (0.5, bell(523, 1.6))]))
reg(C, "gongo", "Gongo", ["gongo", "eliminado", "fim"], lambda: reverb(fm(sweep(98, 92, 4.0), 1.48, 4, 4.0, env_exp(4.0, 1.2)) * env_exp(4.0, 1.0), 2.5, 0.3))
reg(C, "campainha_resposta", "Campainha de resposta", ["quiz", "campainha", "responder"], lambda: place([(i * 0.07, bell(2200, 0.4), 0.8) for i in range(6)]))
reg(C, "contagem_bips", "Contagem 3-2-1", ["contagem", "largada", "bip"], lambda: place([(0, chip(880, 0.15)), (1, chip(880, 0.15)), (2, chip(880, 0.15)), (3, chip(1760, 0.6))]))
reg(C, "tic_tac", "Tic-tac", ["relógio", "tempo", "esperando"], lambda: place([(i * 0.5, tick(3500 if i % 2 == 0 else 2600, 0.04, 70 + i)) for i in range(8)]))
reg(C, "suspense_quiz", "Suspense de quiz", ["quiz", "suspense", "pensando"], lambda: melody([(45, 0.5), (46, 0.5)] * 4, lambda f, d: bass(f, d) + 0.4 * padsynth(f * 2, d, 0.02), 130))
reg(C, "fanfarra_curta", "Fanfarra curta", ["fanfarra", "vitória", "anúncio"], lambda: melody([(60, 0.33), (64, 0.33), (67, 0.33), (72, 1), (67, 0.5), (72, 2)], brass, 160))
reg(C, "vitoria", "Vitória", ["vitória", "ganhou", "campeão"], lambda: reverb(melody([([60, 64, 67], 0.5), ([62, 65, 69], 0.5), ([64, 67, 72], 2)], lambda f, d: brass(f, d) + 0.5 * strings(f, d), 120), 1.2, 0.2))
reg(C, "derrota", "Derrota", ["derrota", "perdeu", "triste"], lambda: melody([(67, 0.75), (63, 0.75), (60, 0.75), ([55, 58, 62], 2.5)], strings, 90))
reg(C, "aww_triste", "Aww triste (instrumental)", ["triste", "aww", "pena"], lambda: melody([(76, 1), (74, 1), (72, 2.5)], lambda f, d: osc(f * (1 + 0.006 * np.sin(2 * np.pi * 5 * T(d))), d) * adsr(d, 0.1, 0.2, 0.7, 0.3), 100))

# ============================================================ SUSPENSE E TERROR
C = "suspense_terror"

def drone(f=55, d=5.0, seed=80, dark=600):
    r = Rng(seed)
    s = sum(osc(f * k * (1 + 0.002 * i), d, "saw") / k for i, k in enumerate([1, 1.5, 2, 2.01, 3]))
    s = lp(s, dark) + 0.2 * lp(r.noise(d, "brown"), 300)
    return s * adsr(d, 1.2, 0.5, 0.9, 1.5)

reg(C, "drone_sombrio", "Drone sombrio", ["drone", "sombrio", "tensão"], lambda: drone())
reg(C, "drone_grave", "Drone grave", ["drone", "grave", "terror"], lambda: drone(41, 6.0, 81, 300))

def heartbeat(bpm=60, beats=6):
    per = 60 / bpm
    one = lambda g: lp(osc(sweep(70, 40, 0.18), 0.18) * env_exp(0.18, 18), 300) * g
    return place([e for i in range(beats) for e in ((i * per, one(1.0)), (i * per + 0.22, one(0.7)))])

reg(C, "batimento", "Batimento cardíaco", ["coração", "batimento", "medo"], lambda: heartbeat())
reg(C, "batimento_acelerado", "Batimento acelerado", ["coração", "pânico", "medo"], lambda: heartbeat(130, 10))

def stab(seed=82, notes=(48, 49, 54, 55, 61), d=1.8):
    x = mix(sum(strings(midi(m), d) for m in notes), 0.6 * explosion(0.5, 0.6, seed))
    return reverb(dist(x, 2.5) * env_exp(d, 2.2), 1.5, 0.3)

reg(C, "jumpscare", "Jumpscare", ["susto", "jumpscare", "terror"], lambda: stab())
reg(C, "jumpscare_agudo", "Jumpscare agudo", ["susto", "grito", "terror"], lambda: stab(83, (72, 73, 78, 79, 85), 1.5))
reg(C, "violino_agudo", "Violinos estridentes", ["violino", "psicose", "facada"], lambda: place([(i * 0.32, hp(strings(midi(88 + (i % 2)), 0.28), 1500)) for i in range(5)]))

def creak(d=1.6, seed=84):
    r = Rng(seed)
    rate = sweep(18, 45, d, "lin") * (1 + 0.3 * np.sin(2 * np.pi * 1.3 * T(d)))
    pulses = (np.sin(phase_of(rate, n_of(d))) > 0.97).astype(float)
    return bp(sg.lfilter([1], [1, -0.97], pulses) + 0.05 * r.noise(d), 300, 2500) * adsr(d, 0.1, 0.1, 0.9, 0.3)

reg(C, "porta_rangendo", "Porta rangendo", ["porta", "rangido", "casa assombrada"], creak)

def wind(d=5.0, seed=85, howl=True):
    r = Rng(seed)
    x = r.noise(d, "pink")
    out = np.zeros(n_of(d))
    fc = 650 + 300 * np.sin(2 * np.pi * 0.23 * T(d)) + 150 * np.sin(2 * np.pi * 0.61 * T(d))
    for i in range(0, len(out), 1024):
        c = fc[i]
        out[i:i + 1024] = bp(x, c * 0.85, c * 1.15, 2)[i:i + 1024]
    return out * adsr(d, 1.0, 0.3, 0.9, 1.2)

reg(C, "vento_uivante", "Vento uivante", ["vento", "uivo", "tempestade"], wind)

def thunder(d=4.0, seed=86, far=False):
    r = Rng(seed)
    crack = hp(r.noise(0.3), 1000) * env_exp(0.3, 10)
    rumble = lp(r.noise(d, "brown"), 250) * (env_exp(d, 1.0) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 1.7 * T(d)))))
    x = mix(0 if far else crack, rumble) if not far else rumble
    return reverb(lp(x, 500 if far else 8000), 2.0, 0.3)

reg(C, "trovao", "Trovão", ["trovão", "tempestade", "raio"], lambda: thunder())
reg(C, "trovao_distante", "Trovão distante", ["trovão", "longe", "chuva"], lambda: thunder(5.0, 87, True))
reg(C, "sino_igreja", "Sino de igreja distante", ["sino", "igreja", "meia-noite"], lambda: lp(reverb(place([(i * 1.6, fm(196, 2.0, 3.5, 3.0, env_exp(3.0, 1.5)) * env_exp(3.0, 1.2)) for i in range(3)]), 2.5, 0.45), 2500))

def music_box(seed=88):
    notes = [76, 74, 72, 74, 76, 79, 77, 76, 74, 72, 71, 72]
    ev = []
    for i, m in enumerate(notes):
        f = midi(m) * (1 + 0.012 * np.sin(i * 1.7))
        ev.append((i * 0.42 * (1 + 0.04 * np.sin(i)), bell(f, 1.0), 0.8))
    return reverb(place(ev), 1.5, 0.35)

reg(C, "caixinha_assombrada", "Caixinha de música assombrada", ["caixinha", "assombrado", "boneca"], music_box)

def riser(d=4.0, f0=100, f1=1200, seed=89):
    r = Rng(seed)
    tone = osc(sweep(f0, f1, d), d, "saw") + osc(sweep(f0 * 1.01, f1 * 1.01, d), d, "saw")
    nz = hp(r.noise(d), 2000)
    e = np.linspace(0, 1, n_of(d)) ** 2
    return (lp(tone, 4000) * 0.6 + 0.4 * nz) * e

reg(C, "riser_tensao", "Riser de tensão", ["tensão", "subindo", "suspense"], lambda: riser())
reg(C, "riser_rapido", "Riser rápido", ["tensão", "rápido"], lambda: riser(1.5, 200, 2400, 90))

def braam(d=3.0, seed=91):
    x = sum(brass(midi(m), d) for m in (31, 38, 43)) + 0.5 * drone(midi(31), d, seed, 900)
    return reverb(dist(x, 3.0) * adsr(d, 0.03, 0.4, 0.7, 1.2), 2.0, 0.3)

reg(C, "braam", "Braaam", ["braam", "trailer", "épico", "grave"], braam)
reg(C, "braam_duplo", "Braaam duplo", ["braam", "trailer"], lambda: place([(0, braam(1.6)), (1.8, braam(2.6, 92))]))
reg(C, "gotejamento", "Goteira em caverna", ["gota", "caverna", "eco"], lambda: reverb(place([(t, osc(sweep(1400 + 300 * k, 2600 + 200 * k, 0.05), 0.05) * env_exp(0.05, 60)) for k, t in enumerate([0, 0.9, 1.6, 2.7, 3.3])], total=4.0), 2.5, 0.5))
reg(C, "passos_corredor", "Passos no corredor", ["passos", "alguém", "corredor"], lambda: reverb(place([(i * 0.62, lp(mix(osc(sweep(110, 60, 0.12), 0.12) * env_exp(0.12, 25), 0.5 * Rng(93 + i).noise(0.12) * env_exp(0.12, 35)), 1200)) for i in range(7)]), 1.8, 0.4))
reg(C, "estatica_radio", "Estática de rádio", ["estática", "rádio", "chiado"], lambda: bp(Rng(94).noise(3.0), 400, 5000) * (0.6 + 0.4 * (np.sin(2 * np.pi * 3.3 * T(3.0)) > -0.2)) * adsr(3.0, 0.05, 0.1, 1, 0.3))
reg(C, "interferencia", "Interferência", ["glitch", "interferência", "sinal"], lambda: crush(mix(place([(i * 0.11, osc(Rng(95 + i).g.uniform(200, 2000), 0.08, "square") * 0.6) for i in range(18)]), 0.3 * Rng(96).noise(2.1)), 4, 6))
reg(C, "batidas_parede", "Batidas na parede", ["batida", "parede", "vizinho"], lambda: reverb(place([(t, lp(punch(0.9, 97 + i), 600)) for i, t in enumerate([0, 0.3, 1.4, 1.65, 1.9])], total=2.6), 1.2, 0.35))

def alien(d=3.0):
    f = 600 + 400 * np.sin(2 * np.pi * 3.1 * T(d)) + 250 * np.sin(2 * np.pi * 7.7 * T(d))
    return fm(f, 1.5, 2.0, d) * adsr(d, 0.2, 0.2, 0.8, 0.4)

reg(C, "ruido_alienigena", "Ruído alienígena", ["alien", "ovni", "estranho"], alien)

def siren(d=4.0, lo=600, hi=1300, rate=0.5, kind="saw"):
    f = lo + (hi - lo) * (0.5 + 0.5 * np.sin(2 * np.pi * rate * T(d) - np.pi / 2))
    return lp(osc(f, d, kind), 3500) * adsr(d, 0.3, 0.1, 1, 0.5)

reg(C, "sirene_ataque", "Sirene de alerta", ["sirene", "alerta", "perigo"], lambda: siren(5.0, 300, 900, 0.25))
reg(C, "alarme", "Alarme", ["alarme", "perigo", "invasão"], lambda: place([(i * 0.5, lp(osc(1000, 0.35, "square") * adsr(0.35, 0.01, 0.02, 0.9, 0.03), 4000)) for i in range(6)]))
reg(C, "tique_taque_sinistro", "Tique-taque sinistro", ["relógio", "contagem", "bomba"], lambda: reverb(place([(i * 0.6, tick(1800 if i % 2 else 1300, 0.06, 98 + i)) for i in range(8)]), 1.5, 0.45))
reg(C, "glitch", "Glitch", ["glitch", "bug", "erro"], lambda: crush(place([(i * 0.04, (osc(Rng(99 + i).g.choice([100, 400, 1600, 3200]), 0.035, "square")) * 0.5) for i in range(30)]), 3, 8))
reg(C, "eco_metalico", "Eco metálico", ["metal", "eco", "fábrica"], lambda: echo(reverb(fm(240, 1.41, 5, 1.0, env_exp(1.0, 4)) * env_exp(1.0, 4), 1.0, 0.3), 260, 0.5, 5))
reg(C, "piano_dissonante", "Piano dissonante", ["piano", "dissonante", "susto"], lambda: reverb(sum(epiano(midi(m), 3.0) for m in (36, 37, 42, 43, 48)) * env_exp(3.0, 0.9), 2.0, 0.3))
reg(C, "sussurro_vento", "Sopro assombrado", ["sopro", "fantasma", "assombrado"], lambda: wind(4.0, 100))

# ============================================================ ANIME E PODERES
C = "anime_poderes"

def sparkle(d=1.2, seed=110, base=2000):
    r = Rng(seed)
    ev = [(r.g.uniform(0, d * 0.7), bell(base * r.g.uniform(1, 2.5), 0.4), r.g.uniform(0.3, 0.8)) for _ in range(int(d * 18))]
    return place(ev, total=d)

def zap(f0, f1, d, kind="saw"):
    return lp(osc(sweep(f0, f1, d), d, kind), 6000) * env_exp(d, 4 / d)

reg(C, "teleporte", "Teletransporte", ["teleporte", "sumir", "aparecer"], lambda: mix(zap(300, 3000, 0.5, "sine") * 0.8, whoosh(0.5, 500, 5000, 111), 0.5 * sparkle(0.6, 111)))
reg(C, "teleporte_rapido", "Teletransporte rápido", ["teleporte", "instantâneo"], lambda: mix(zap(3000, 300, 0.2, "sine"), 0.6 * whoosh(0.2, 4000, 800, 112, False)))

def charge(d=2.0, seed=113):
    f = sweep(80, 900, d)
    body = osc(f, d, "saw") + osc(f * 1.5, d, "saw") * 0.5
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * sweep(6, 30, d, "lin").cumsum() / SR)
    return lp(body, 3000) * trem * np.linspace(0.2, 1, n_of(d)) + 0.3 * hp(Rng(seed).noise(d), 3000) * np.linspace(0, 1, n_of(d))

reg(C, "carga_energia", "Carregando energia", ["carregar", "energia", "ki", "poder"], charge)
reg(C, "disparo_energia", "Disparo de energia", ["disparo", "raio", "energia", "kame"], lambda: mix(explosion(1.0, 0.8, 114), dist(zap(1200, 150, 1.2), 2) * 0.8, whoosh(1.0, 200, 4000, 114)))
reg(C, "laser", "Raio laser", ["laser", "tiro", "pew"], lambda: zap(2400, 300, 0.25, "square") * 0.8)
reg(C, "laser_longo", "Laser contínuo", ["laser", "raio", "feixe"], lambda: (lp(osc(900 * (1 + 0.05 * np.sin(2 * np.pi * 40 * T(1.6))), 1.6, "saw"), 5000) + 0.3 * hp(Rng(115).noise(1.6), 3000)) * adsr(1.6, 0.02, 0.1, 0.9, 0.2))
reg(C, "escudo", "Escudo ativado", ["escudo", "proteção", "barreira"], lambda: reverb(mix(fm(sweep(200, 400, 0.8), 2.0, 3, 0.8, env_exp(0.8, 3)) * adsr(0.8, 0.05, 0.2, 0.6, 0.3), 0.4 * sparkle(0.8, 116)), 1.0, 0.25))
reg(C, "cura", "Cura", ["cura", "vida", "magia"], lambda: reverb(melody([(72, 0.25), (76, 0.25), (79, 0.25), (84, 0.25), (88, 1.2)], lambda f, d: bell(f, d * 2), 220), 1.5, 0.3))
reg(C, "brilho_magico", "Brilho mágico", ["brilho", "magia", "estrela"], lambda: reverb(sparkle(1.4, 117), 1.2, 0.3))
reg(C, "feitico", "Feitiço", ["feitiço", "magia", "lançar"], lambda: reverb(mix(whoosh(0.7, 300, 3000, 118), 0.7 * sparkle(0.9, 118, 1500), 0.5 * zap(400, 1600, 0.7, "tri")), 1.0, 0.3))
reg(C, "explosao_magica", "Explosão mágica", ["explosão", "magia"], lambda: reverb(mix(explosion(1.2, 0.8, 119), 0.8 * sparkle(1.4, 119, 1200)), 1.4, 0.3))
reg(C, "aura_poder", "Aura de poder", ["aura", "poder", "nível"], lambda: mix(lp(Rng(120).noise(4.0, "brown"), 300) * np.linspace(0.3, 1, n_of(4.0)), 0.6 * drone(55, 4.0, 120, 1200)) * adsr(4.0, 0.5, 0.3, 1, 0.6))
reg(C, "transformacao", "Transformação", ["transformação", "super", "poder máximo"], lambda: place([(0, riser(2.5, 100, 1600, 121)), (2.5, reverb(mix(explosion(1.4, 1.0, 121), sparkle(1.2, 121)), 1.5, 0.3))]))

def sword_slash(seed=122):
    return mix(whoosh(0.25, 1500, 7000, seed), 0.6 * hp(Rng(seed).noise(0.25), 5000) * env_exp(0.25, 18))

def metal_ring(f=2400, d=1.2):
    return sum(osc(f * k, d) * env_exp(d, 3 + 2 * i) / (i + 1) for i, k in enumerate([1, 2.32, 4.25, 6.63]))

reg(C, "golpe_espada", "Golpe de espada", ["espada", "golpe", "corte"], sword_slash)
reg(C, "desembainhar", "Desembainhar espada", ["espada", "katana", "sacar"], lambda: mix(lp(Rng(123).noise(0.6) * sweep(0.3, 1, 0.6, "lin"), 6000) * adsr(0.6, 0.05, 0.1, 0.8, 0.1) * 0.6, place([(0.55, metal_ring(3100, 1.4))])))
reg(C, "choque_espadas", "Choque de espadas", ["espada", "clang", "duelo"], lambda: reverb(mix(metal_ring(1900, 1.0), metal_ring(2650, 1.0), 0.7 * hp(Rng(124).noise(0.1), 2000) * env_exp(0.1, 40)), 0.8, 0.2))
reg(C, "corte_rapido", "Corte rápido", ["corte", "espada", "rápido"], lambda: place([(0, sword_slash(125)), (0.12, sword_slash(126), 0.8), (0.22, sword_slash(127), 0.7)]))
reg(C, "soco_anime", "Soco de anime", ["soco", "impacto", "anime"], lambda: mix(whoosh(0.15, 800, 4000, 128), place([(0.13, reverb(punch(1.3, 128), 0.7, 0.2))])))
reg(C, "combo_socos", "Combo de socos", ["combo", "soco", "rajada"], lambda: place([(i * 0.07, punch(0.6 + 0.05 * (i % 3), 129 + i), 0.5 + 0.03 * i) for i in range(14)] + [(1.05, reverb(punch(1.8, 150), 0.8, 0.2))]))
reg(C, "raio_eletrico", "Raio elétrico", ["raio", "elétrico", "choque", "trovão"], lambda: mix(crush(hp(Rng(131).noise(1.2), 1500) * (np.abs(np.sin(2 * np.pi * 37 * T(1.2))) > 0.6) * env_exp(1.2, 2.5), 8, 2), 0.5 * thunder(1.5, 131)))

def fire(d=3.0, seed=132):
    r = Rng(seed)
    roar = lp(r.noise(d, "brown"), 900) * 0.8
    crack = place([(r.g.uniform(0, d - 0.05), hp(r.noise(0.01), 3000), r.g.uniform(0.2, 1)) for _ in range(int(d * 25))], total=d)
    return (roar + 0.6 * crack) * adsr(d, 0.3, 0.2, 0.9, 0.5)

reg(C, "fogo", "Ataque de fogo", ["fogo", "chama", "queimar"], lambda: mix(fire(2.0), whoosh(1.0, 200, 2000, 133)))
reg(C, "gelo", "Congelamento", ["gelo", "congelar", "frio"], lambda: reverb(mix(place([(i * 0.05, metal_ring(3000 + 400 * (i % 5), 0.6), 0.4) for i in range(14)]), 0.5 * hp(Rng(134).noise(1.2), 4000) * env_exp(1.2, 3)), 1.2, 0.3))
reg(C, "vento_cortante", "Vento cortante", ["vento", "corte", "lâmina"], lambda: mix(whoosh(0.6, 600, 6000, 135), whoosh(0.6, 900, 7000, 136)))
reg(C, "terremoto", "Terremoto", ["terremoto", "tremor", "chão"], lambda: mix(lp(Rng(137).noise(3.5, "brown"), 150) * (0.6 + 0.4 * np.sin(2 * np.pi * 9 * T(3.5))) * adsr(3.5, 0.4, 0.3, 1, 1.0), 0.5 * explosion(0.8, 0.6, 137)))
reg(C, "dash", "Dash", ["dash", "avançar", "velocidade"], lambda: mix(whoosh(0.3, 300, 4000, 138), 0.5 * zap(200, 1200, 0.3, "tri")))
reg(C, "salto_alto", "Salto alto", ["pulo", "salto", "voar"], lambda: mix(whoosh(0.6, 200, 3000, 139), 0.4 * osc(sweep(200, 900, 0.6), 0.6, "tri") * env_exp(0.6, 4)))
reg(C, "pouso_impacto", "Pouso com impacto", ["pouso", "aterrissar", "impacto"], lambda: mix(explosion(0.9, 0.6, 140), 0.8 * punch(1.5, 140)))
reg(C, "aparicao_dramatica", "Aparição dramática", ["aparição", "chegada", "dramático"], lambda: place([(0, whoosh(0.8, 100, 3000, 141)), (0.75, reverb(mix(braam(1.5, 141), 0.5 * sparkle(0.8, 141)), 1.0, 0.25))]))
reg(C, "brilho_olhos", "Brilho no olhar", ["brilho", "olhar", "ting"], lambda: reverb(mix(metal_ring(4200, 0.8), 0.5 * bell(3500, 0.8)), 0.8, 0.3))
reg(C, "poder_subindo", "Poder subindo", ["poder", "nível", "aumentando"], lambda: mix(riser(3.0, 60, 900, 142), 0.6 * charge(3.0, 142)))
reg(C, "barreira_quebrando", "Barreira quebrando", ["barreira", "quebrar", "escudo"], lambda: mix(place([(0.0, fm(400, 2.0, 3, 0.4) * env_exp(0.4, 6))]), reverb(hp(Rng(143).noise(1.0), 2000) * env_exp(1.0, 5), 0.6, 0.2), place([(0.05, metal_ring(2700, 1.0))])))
reg(C, "portal_abrindo", "Portal abrindo", ["portal", "abrir", "dimensão"], lambda: reverb(mix(riser(2.0, 80, 600, 144), 0.6 * alien(2.0)) * np.linspace(0.3, 1, n_of(2.0)), 1.5, 0.3))
reg(C, "portal_fechando", "Portal fechando", ["portal", "fechar"], lambda: reverb(mix(zap(900, 60, 1.2, "saw") * 0.8, 0.6 * whoosh(1.2, 4000, 200, 145, False)), 1.0, 0.3))
reg(C, "energia_caindo", "Energia acabando", ["energia", "acabar", "desligar"], lambda: lp(osc(sweep(800, 40, 1.5), 1.5, "saw"), 3000) * env_exp(1.5, 1.5))
reg(C, "carga_maxima", "Carga máxima", ["carga", "máximo", "pronto"], lambda: place([(0, charge(1.2, 146)), (1.2, reverb(mix(bell(1760, 1.0), metal_ring(3520, 1.0)), 1.0, 0.3))]))
reg(C, "golpe_final", "Golpe final", ["golpe final", "especial", "finalizar"], lambda: place([(0, charge(1.4, 147)), (1.4, reverb(mix(explosion(1.6, 1.3, 147), dist(zap(1500, 100, 1.4), 2)), 1.8, 0.3))]))
reg(C, "clone_sombra", "Clones aparecendo", ["clone", "jutsu", "fumaça"], lambda: place([(i * 0.09, mix(lp(Rng(148 + i).noise(0.25, "pink"), 1500) * env_exp(0.25, 12), 0.4 * osc(sweep(300 + 50 * i, 150, 0.2), 0.2) * env_exp(0.2, 15)), 0.8) for i in range(5)]))

# ============================================================ GAMES (estilo 8-bit, sons originais)
C = "games_roblox"

def chipseq(notes, dur=0.08, duty=0.5, rate=0):
    return place([(i * dur, chip(midi(m), dur * 1.02, duty, rate)) for i, m in enumerate(notes) if m is not None])

reg(C, "moeda", "Moeda", ["moeda", "coin", "dinheiro"], lambda: crush(place([(0, chip(midi(83), 0.07, 0.5)), (0.07, chip(midi(88), 0.4, 0.5, 9))]), 8, 1))
reg(C, "moeda_dupla", "Duas moedas", ["moeda", "coin"], lambda: place([(0, crush(place([(0, chip(midi(83), 0.06)), (0.06, chip(midi(88), 0.25, 0.5, 12))]), 8, 1)), (0.15, crush(place([(0, chip(midi(85), 0.06)), (0.06, chip(midi(90), 0.3, 0.5, 10))]), 8, 1))]))
reg(C, "pulo", "Pulo", ["pulo", "jump", "pular"], lambda: osc(sweep(250, 800, 0.18), 0.18, "square", 0.25) * env_exp(0.18, 8) * 0.5)
reg(C, "pulo_duplo", "Pulo duplo", ["pulo", "duplo"], lambda: place([(0, osc(sweep(250, 700, 0.14), 0.14, "square", 0.25) * env_exp(0.14, 9) * 0.5), (0.14, osc(sweep(400, 1100, 0.16), 0.16, "square", 0.25) * env_exp(0.16, 9) * 0.5)]))
reg(C, "power_up", "Power-up", ["power up", "poder", "item"], lambda: chipseq([60, 64, 67, 72, 64, 67, 72, 76, 67, 72, 76, 79], 0.05, 0.5))
reg(C, "vida_extra", "Vida extra", ["vida", "1up", "extra"], lambda: chipseq([76, 79, 88, 84, 86, 91], 0.1, 0.5))
reg(C, "dano", "Dano", ["dano", "hit", "machucou"], lambda: crush(osc(sweep(600, 120, 0.25), 0.25, "square") * env_exp(0.25, 8) * 0.6 + 0.3 * Rng(160).noise(0.25) * env_exp(0.25, 12), 5, 3))
reg(C, "game_over", "Game over", ["game over", "perdeu", "fim de jogo"], lambda: chipseq([67, None, 66, None, 65, None, 64, 64, 64, 64], 0.18, 0.5) + 0.0)
reg(C, "level_up", "Level up", ["level up", "nível", "subiu"], lambda: chipseq([60, 62, 64, 65, 67, 69, 71, 72, 76, 79, 84, 84, 84], 0.06, 0.25))
reg(C, "fase_completa", "Fase completa", ["fase", "completou", "vitória"], lambda: place([(0, chipseq([67, 72, 76, 79, 84, 88], 0.1, 0.5)), (0.6, chip(midi(91), 0.8, 0.5, 3)), (0.6, chip(midi(84), 0.8, 0.25, 3))]))
reg(C, "menu_selecao", "Seleção de menu", ["menu", "selecionar", "cursor"], lambda: chip(midi(84), 0.05, 0.25, 30))
reg(C, "confirmar", "Confirmar", ["confirmar", "ok", "aceitar"], lambda: chipseq([79, 91], 0.06, 0.5))
reg(C, "cancelar", "Cancelar", ["cancelar", "voltar", "não"], lambda: chipseq([72, 60], 0.07, 0.5))
reg(C, "item_coletado", "Item coletado", ["item", "coletar", "pegar"], lambda: chipseq([72, 79, 84, 91], 0.045, 0.125))
reg(C, "bau_aberto", "Baú aberto", ["baú", "tesouro", "abrir"], lambda: place([(0, chipseq([55, 59, 62, 67, 71, 74, 79], 0.07)), (0.5, crush(sparkle(0.8, 161, 2000), 6, 2))]))
reg(C, "tiro_8bit", "Tiro 8-bit", ["tiro", "disparo", "laser"], lambda: crush(zap(1800, 200, 0.15, "square") * 0.6, 6, 2))
reg(C, "explosao_8bit", "Explosão 8-bit", ["explosão", "8bit"], lambda: crush(lp(Rng(162).noise(0.8), 3000) * env_exp(0.8, 5), 4, 6))
reg(C, "inimigo_derrotado", "Inimigo derrotado", ["inimigo", "derrotado", "eliminar"], lambda: place([(0, crush(osc(sweep(900, 300, 0.12), 0.12, "square") * 0.5 * env_exp(0.12, 10), 6, 2)), (0.1, crush(lp(Rng(163).noise(0.3), 4000) * env_exp(0.3, 10), 4, 4))]))
reg(C, "checkpoint", "Checkpoint", ["checkpoint", "salvo", "bandeira"], lambda: chipseq([72, 76, 79, 84, None, 84], 0.09, 0.5))
reg(C, "teleporte_8bit", "Teleporte 8-bit", ["teleporte", "8bit"], lambda: crush(place([(i * 0.03, chip(midi(60 + (i * 7) % 36), 0.035, 0.5)) for i in range(20)]), 6, 1))
reg(C, "tempo_acabando", "Tempo acabando", ["tempo", "pressa", "alarme"], lambda: place([(i * 0.25, chip(midi(81 if i % 2 == 0 else 76), 0.18, 0.5)) for i in range(8)]))
reg(C, "pausa", "Pausa", ["pausa", "pause", "parar"], lambda: chipseq([83, 79, 83, 79], 0.07, 0.5))
reg(C, "erro_8bit", "Erro 8-bit", ["erro", "bloqueado", "não pode"], lambda: chip(midi(40), 0.3, 0.5))
reg(C, "combo", "Combo", ["combo", "sequência", "multiplicador"], lambda: chipseq([72, 74, 76, 77, 79, 81, 83, 84], 0.04, 0.25))
reg(C, "recorde", "Novo recorde", ["recorde", "high score", "pontuação"], lambda: chipseq([72, 76, 79, 84, 79, 84, 88, 91, 88, 91, 96], 0.07, 0.5))
reg(C, "bloqueio", "Bloqueio", ["bloquear", "defesa", "escudo"], lambda: crush(mix(chip(midi(48), 0.12, 0.5, 20), 0.6 * lp(Rng(164).noise(0.1), 3000) * env_exp(0.1, 40)), 6, 2))
reg(C, "escudo_8bit", "Escudo 8-bit", ["escudo", "proteção", "8bit"], lambda: chipseq([60, 67, 60, 67, 72, 79], 0.05, 0.125))
reg(C, "mola_8bit", "Mola 8-bit", ["mola", "pulo", "trampolim"], lambda: osc(sweep(200, 1200, 0.3) * (1 + 0.15 * np.sin(2 * np.pi * 25 * T(0.3))), 0.3, "square", 0.25) * env_exp(0.3, 5) * 0.5)
reg(C, "queda_buraco", "Caindo no buraco", ["queda", "buraco", "cair"], lambda: osc(sweep(1400, 80, 1.0), 1.0, "square", 0.5) * adsr(1.0, 0.01, 0.1, 0.8, 0.1) * 0.5)
reg(C, "chefe_aparece", "Chefão aparecendo", ["chefão", "boss", "batalha"], lambda: mix(chipseq([36, 37, 36, 37, 36, 37, 36, 37], 0.15, 0.5), place([(1.2, crush(braam(1.5, 165), 6, 3), 0.6)])))
reg(C, "vitoria_8bit", "Vitória 8-bit", ["vitória", "ganhou", "8bit"], lambda: place([(0, chipseq([72, 72, 72, 72, 68, 70, 72, None, 70, 72], 0.12, 0.5)), (0, chipseq([60, None, 60, None, 56, 58, 60, None, 58, 60], 0.12, 0.25))]))
reg(C, "respawn", "Respawn", ["respawn", "renascer", "voltar"], lambda: crush(mix(osc(sweep(100, 1000, 0.6), 0.6, "square", 0.25) * np.linspace(0, 1, n_of(0.6)) * 0.4, place([(0.55, chip(midi(84), 0.3, 0.5, 10))])), 6, 1))
reg(C, "conquista", "Conquista desbloqueada", ["conquista", "troféu", "achievement"], lambda: mix(chipseq([79, 84, 88], 0.08, 0.5), place([(0.24, bell(1568, 1.2), 0.6)])))
reg(C, "contagem_largada", "3, 2, 1, Vai!", ["largada", "corrida", "contagem"], lambda: place([(0, chip(midi(69), 0.25)), (1, chip(midi(69), 0.25)), (2, chip(midi(69), 0.25)), (3, chip(midi(81), 0.7, 0.5))]))
reg(C, "desbloqueio", "Desbloqueio", ["desbloquear", "porta", "liberado"], lambda: place([(0, chipseq([48, 55, 60], 0.06)), (0.2, chipseq([72, 76, 79, 84], 0.06, 0.25))]))
reg(C, "dano_critico", "Dano crítico", ["crítico", "dano", "golpe"], lambda: crush(mix(punch(1.2, 166), 0.6 * osc(sweep(1200, 200, 0.3), 0.3, "square") * env_exp(0.3, 8)), 5, 3))

# ============================================================ SONS ALEATÓRIOS
C = "sons_aleatorios"

reg(C, "bip", "Bip", ["bip", "beep"], lambda: osc(1000, 0.18) * adsr(0.18, 0.005, 0.01, 1, 0.01))
reg(C, "bip_duplo", "Bip duplo", ["bip", "beep"], lambda: place([(0, osc(1200, 0.1) * adsr(0.1, 0.005, 0.01, 1, 0.01)), (0.16, osc(1200, 0.1) * adsr(0.1, 0.005, 0.01, 1, 0.01))]))
reg(C, "clique", "Clique", ["clique", "click"], lambda: mix(tick(3000, 0.06, 180), 0.5 * osc(1800, 0.05) * env_exp(0.05, 70)))
reg(C, "clique_mouse", "Clique de mouse", ["mouse", "clique"], lambda: place([(0, tick(4000, 0.015, 181)), (0.07, tick(3000, 0.015, 182), 0.6)]))

def typing(d=2.5, seed=183):
    r = Rng(seed)
    ev, t = [], 0.0
    while t < d - 0.1:
        ev.append((t, mix(tick(r.g.uniform(2000, 4500), 0.03, int(t * 1000) + seed), 0.3 * osc(r.g.uniform(150, 250), 0.02) * env_exp(0.02, 150)), r.g.uniform(0.5, 1)))
        t += r.g.uniform(0.06, 0.18)
    return place(ev, total=d)

reg(C, "teclado", "Teclado digitando", ["teclado", "digitar", "hacker"], typing)
reg(C, "sino_bicicleta", "Campainha de bicicleta", ["bicicleta", "campainha", "trim"], lambda: place([(0, metal_ring(3300, 0.9)), (0.12, metal_ring(3300, 1.2))]))
reg(C, "campainha_porta", "Campainha de porta", ["campainha", "porta", "visita"], lambda: place([(0, bell(988, 1.2)), (0.45, bell(784, 1.8))]))

def phone_ring(cycles=2):
    one = (osc(440, 1.0) + osc(480, 1.0)) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 20 * T(1.0)))) * 0.5
    return place([(i * 2.0, one) for i in range(cycles)])

reg(C, "telefone", "Telefone tocando", ["telefone", "ligação", "atende"], lambda: phone_ring())
reg(C, "despertador", "Despertador", ["despertador", "acordar", "alarme"], lambda: place([(i * 0.14, osc(2600, 0.08, "square") * adsr(0.08, 0.002, 0.01, 1, 0.01) * 0.4) for i in range(20)]))
reg(C, "notificacao", "Notificação suave", ["notificação", "mensagem"], lambda: place([(0, epiano(midi(84), 0.4)), (0.12, epiano(midi(91), 0.8))]))
reg(C, "gota", "Gota d'água", ["gota", "água", "pingo"], lambda: osc(sweep(900, 2400, 0.07), 0.07) * env_exp(0.07, 40))
reg(C, "bolhas", "Bolhas", ["bolhas", "água", "aquário"], lambda: place([(t, osc(sweep(f, f * 2.2, 0.05), 0.05) * env_exp(0.05, 50), 0.7) for t, f in zip(np.sort(Rng(184).g.uniform(0, 1.5, 14)), Rng(185).g.uniform(300, 900, 14))], total=1.7))
reg(C, "splash", "Splash", ["água", "mergulho", "splash"], lambda: mix(lp(Rng(186).noise(0.9), 3000) * env_exp(0.9, 5), 0.6 * osc(sweep(250, 90, 0.2), 0.2) * env_exp(0.2, 15)))
reg(C, "moeda_chao", "Moeda caindo no chão", ["moeda", "chão", "cair"], lambda: place([(t, metal_ring(4200, 0.25), g) for t, g in [(0, 1), (0.18, 0.7), (0.3, 0.5), (0.38, 0.35), (0.43, 0.25), (0.46, 0.2), (0.48, 0.15)]]))
reg(C, "papel_amassando", "Papel amassando", ["papel", "amassar", "lixo"], lambda: place([(t, hp(Rng(188 + i).noise(0.04), 1500) * env_exp(0.04, 50), 0.6) for i, t in enumerate(Rng(187).g.uniform(0, 1.2, 40))], total=1.4))
reg(C, "relogio", "Relógio", ["relógio", "tic tac"], lambda: place([(i * 1.0, tick(2200 + 300 * (i % 2), 0.03, 189 + i)) for i in range(5)]))
reg(C, "cuco", "Cuco", ["cuco", "relógio", "hora"], lambda: place([(i * 0.9, mix(place([(0, osc(659, 0.18) * adsr(0.18, 0.01, 0.05, 0.7, 0.05))]), place([(0.22, osc(523, 0.28) * adsr(0.28, 0.01, 0.05, 0.7, 0.08))])), 1) for i in range(3)]))
reg(C, "buzina_carro", "Buzina de carro", ["buzina", "carro", "trânsito"], lambda: place([(0, lp(osc(415, 0.25, "square") + osc(523, 0.25, "square"), 2500) * adsr(0.25, 0.01, 0.02, 0.9, 0.03) * 0.5), (0.35, lp(osc(415, 0.6, "square") + osc(523, 0.6, "square"), 2500) * adsr(0.6, 0.01, 0.02, 0.9, 0.05) * 0.5)]))
reg(C, "sirene_policia", "Sirene", ["sirene", "polícia", "fuga"], lambda: siren(4.0, 650, 1500, 0.9, "tri"))
reg(C, "apito_trem", "Apito de trem", ["trem", "apito", "estação"], lambda: reverb(sum(osc(f, 2.0, "saw") for f in (311, 370, 466)) * 0.3 * adsr(2.0, 0.15, 0.2, 0.9, 0.4), 1.5, 0.3))
reg(C, "motor", "Motor ligando", ["motor", "carro", "ligar"], lambda: lp(dist(osc(sweep(25, 55, 2.5), 2.5, "saw") * (0.6 + 0.4 * np.sin(2 * np.pi * sweep(8, 30, 2.5, "lin").cumsum() / SR)), 3), 900) * adsr(2.5, 0.2, 0.2, 1, 0.3))
reg(C, "vento_suave", "Vento suave", ["vento", "brisa"], lambda: wind(4.0, 190) * 0.8)

def rain(d=5.0, seed=191):
    r = Rng(seed)
    drops = place([(r.g.uniform(0, d - 0.05), osc(r.g.uniform(2000, 5000), 0.01) * env_exp(0.01, 300), r.g.uniform(0.1, 0.5)) for _ in range(int(d * 150))], total=d)
    return (0.6 * lp(r.noise(d, "pink"), 4000) + drops) * adsr(d, 0.8, 0.2, 1, 1.0)

reg(C, "chuva", "Chuva", ["chuva", "tempestade"], rain)
reg(C, "fogueira", "Fogueira", ["fogo", "fogueira", "acampamento"], lambda: fire(5.0, 192) * 0.9)
reg(C, "ondas_mar", "Ondas do mar", ["mar", "praia", "ondas"], lambda: lp(Rng(193).noise(6.0, "pink"), 2500) * (0.25 + 0.75 * np.sin(np.pi * T(6.0) / 3.0) ** 4) * adsr(6.0, 0.5, 0.1, 1, 1.0))
reg(C, "passarinho", "Passarinho", ["pássaro", "passarinho", "manhã"], lambda: place([(t, osc(sweep(3000, 4500, 0.08) * (1 + 0.08 * np.sin(2 * np.pi * 60 * T(0.08))), 0.08) * adsr(0.08, 0.01, 0.02, 0.8, 0.03), 0.8) for t in [0, 0.12, 0.24, 0.7, 0.8, 1.4, 1.48, 1.56, 1.64]], total=2.0))
reg(C, "assobio_lobo", "Fiu-fiu", ["assobio", "fiu fiu", "elogio"], lambda: place([(0, osc(sweep(1200, 2600, 0.25), 0.25) * adsr(0.25, 0.02, 0.05, 0.9, 0.05)), (0.35, osc(sweep(2600, 1000, 0.55) * sweep(1, 1.3, 0.55, "lin") ** 0, 0.55) * adsr(0.55, 0.02, 0.05, 0.9, 0.1))]))
reg(C, "ping_pong", "Ping-pong", ["ping pong", "bola", "mesa"], lambda: place([(t, bp(osc(1700, 0.04) + 0.3 * Rng(194 + i).noise(0.04), 900, 4000) * env_exp(0.04, 80)) for i, t in enumerate([0, 0.45, 0.9, 1.35, 1.8])]))
reg(C, "bola_quicando", "Bola quicando", ["bola", "quicar", "basquete"], lambda: place([(t, lp(osc(sweep(160, 90, 0.08), 0.08) * env_exp(0.08, 30), 1200), g) for t, g in [(0, 1), (0.5, 0.8), (0.88, 0.6), (1.16, 0.45), (1.36, 0.35), (1.5, 0.25), (1.6, 0.2), (1.67, 0.15)]]))

def robot(d=2.0, seed=195):
    r = Rng(seed)
    return place([(i * 0.12, lp(osc(r.g.choice([220, 330, 440, 550, 660]), 0.1, "square"), 2500) * adsr(0.1, 0.005, 0.02, 0.8, 0.02) * 0.5) for i in range(int(d / 0.12))])

reg(C, "robo_falando", "Robô bipando", ["robô", "bip", "máquina"], robot)
reg(C, "modem", "Modem discando", ["modem", "internet", "antigo"], lambda: place([(0, robot(0.8, 196)), (0.9, crush(bp(Rng(197).noise(2.0), 800, 3000) * (0.5 + 0.5 * np.sign(np.sin(2 * np.pi * 13 * T(2.0)))), 6, 3))]))
reg(C, "radio_sintonizando", "Rádio sintonizando", ["rádio", "sintonizar", "estação"], lambda: mix(bp(Rng(198).noise(2.5), 500, 4000) * 0.5, osc(sweep(300, 2500, 2.5) * (1 + 0.1 * np.sin(2 * np.pi * 7 * T(2.5))), 2.5) * 0.3) * adsr(2.5, 0.1, 0.1, 1, 0.3))
reg(C, "scanner", "Scanner", ["scanner", "leitura", "escanear"], lambda: (osc(sweep(400, 1600, 1.5, "lin"), 1.5, "tri") * 0.5 + 0.2 * osc(60, 1.5, "saw")) * adsr(1.5, 0.05, 0.1, 0.9, 0.1))
reg(C, "estalo", "Estalo", ["estalo", "quebrar", "galho"], lambda: hp(Rng(199).noise(0.1), 800) * env_exp(0.1, 50))
reg(C, "pipoca", "Pipoca estourando", ["pipoca", "estourar", "cinema"], lambda: place([(t, mix(osc(sweep(800, 300, 0.03), 0.03) * env_exp(0.03, 80), 0.6 * hp(Rng(201 + i).noise(0.03), 1500) * env_exp(0.03, 90)), 0.8) for i, t in enumerate(np.sort(Rng(200).g.uniform(0, 2.5, 25)))], total=2.8))
reg(C, "garrafa_abrindo", "Refrigerante abrindo", ["refrigerante", "lata", "abrir", "gás"], lambda: place([(0, bp(Rng(202).noise(0.06), 1000, 5000) * env_exp(0.06, 60)), (0.04, hp(Rng(203).noise(1.6), 3500) * env_exp(1.6, 2.5) * 0.6)]))
reg(C, "fosforo", "Fósforo acendendo", ["fósforo", "acender", "fogo"], lambda: mix(hp(Rng(204).noise(0.3), 2000) * env_exp(0.3, 10), place([(0.15, fire(1.0, 205) * 0.6)])))
reg(C, "cronometro", "Cronômetro", ["cronômetro", "tempo", "fim"], lambda: place([(i * 0.18, osc(2000, 0.09) * adsr(0.09, 0.003, 0.01, 1, 0.01)) for i in range(4)] + [(0.8, osc(2000, 0.6) * adsr(0.6, 0.003, 0.01, 1, 0.05))]))
reg(C, "maquina_registradora", "Caixa registradora", ["dinheiro", "caixa", "venda"], lambda: place([(0, tick(2000, 0.04, 206)), (0.08, tick(2500, 0.04, 207)), (0.25, metal_ring(3800, 1.0))]))
reg(C, "pombo_eletronico", "Ronronar eletrônico", ["fofo", "eletrônico", "pet"], lambda: lp(osc(80 * (1 + 0.1 * np.sin(2 * np.pi * 25 * T(2.0))), 2.0, "saw"), 500) * (0.5 + 0.5 * np.sin(np.pi * T(2.0) / 1.0) ** 2))
reg(C, "zipper", "Zíper", ["zíper", "fechar", "roupa"], lambda: place([(i * 0.012, tick(3500, 0.01, 208 + i), 0.6) for i in range(50)]))
reg(C, "sonar", "Sonar", ["sonar", "submarino", "radar"], lambda: reverb(osc(1400, 0.4) * env_exp(0.4, 6), 2.5, 0.5))

# ============================================================ VINHETAS E MÚSICAS CURTAS
C = "vinhetas_musicas"

def chord_prog(chords, inst, bpm=100, beats=4):
    return melody([(c, beats) for c in chords], inst, bpm, 0.98)

def song(*layers):
    return mix(*layers)

reg(C, "abertura_epica", "Abertura épica", ["abertura", "épico", "intro"], lambda: reverb(song(chord_prog([[48, 55, 60, 64], [44, 51, 56, 60], [46, 53, 58, 62], [43, 50, 55, 59, 62]], strings, 80, 2), melody([(None, 4), (67, 1), (72, 1), (74, 2), (79, 4)], brass, 80), drums({"k": "x.......x.......", "s": "........x......."}, 80, 2)), 1.8, 0.25))
reg(C, "abertura_gamer", "Abertura gamer", ["abertura", "gamer", "intro", "live"], lambda: song(drums({"k": "x...x...x...x...", "s": "....x.......x...", "h": "x.x.x.x.x.x.x.x."}, 128, 2), melody([(64, 0.5), (67, 0.5), (71, 0.5), (76, 0.5), (74, 1), (71, 1)] * 2, lambda f, d: chip(f, d, 0.25), 128), melody([(40, 2), (43, 2), (45, 2), (47, 2)], bass, 128)))
reg(C, "encerramento", "Encerramento", ["fim", "encerramento", "tchau"], lambda: reverb(melody([([60, 64, 67], 1), ([57, 60, 64], 1), ([53, 57, 60], 1), ([55, 59, 62], 1), ([48, 52, 55, 60], 4)], epiano, 90), 2.0, 0.25))
reg(C, "transicao_rapida", "Transição rápida", ["transição", "corte"], lambda: mix(whoosh(0.6, 200, 5000, 220), place([(0.5, mix(kick(0.4), 0.5 * hp(Rng(221).noise(0.8), 5000) * env_exp(0.8, 4)))])))
reg(C, "transicao_suave", "Transição suave", ["transição", "suave"], lambda: reverb(mix(padsynth(midi(60), 2.0, 0.6), padsynth(midi(67), 2.0, 0.6), 0.5 * sparkle(2.0, 222, 1500)), 1.5, 0.3))
reg(C, "intro_chiptune", "Intro chiptune", ["chiptune", "8bit", "intro"], lambda: song(melody([(72, 0.5), (76, 0.5), (79, 0.5), (84, 0.5), (83, 0.5), (79, 0.5), (76, 1), (74, 0.5), (77, 0.5), (81, 0.5), (86, 0.5), (84, 2)], lambda f, d: chip(f, d, 0.25), 150), melody([(48, 1), (55, 1), (48, 1), (55, 1), (50, 1), (57, 1), (48, 2)], lambda f, d: chip(f, d, 0.5) * 0.7, 150), drums({"k": "x.......x.......", "h": "..x...x...x...x."}, 150, 2)))
reg(C, "jingle_feliz", "Jingle feliz", ["feliz", "alegre", "jingle"], lambda: song(melody([(72, 0.5), (74, 0.5), (76, 0.5), (79, 0.5), (76, 0.5), (79, 0.5), (84, 2)], pluck, 140), melody([([48, 55, 64], 2), ([53, 60, 69], 2)], epiano, 140)))
reg(C, "jingle_triste", "Jingle triste", ["triste", "melancolia"], lambda: reverb(song(melody([(69, 1), (67, 1), (64, 1), (62, 1), (60, 3)], lambda f, d: osc(f, d) * adsr(d, 0.05, 0.2, 0.6, 0.3), 70), chord_prog([[45, 52, 57], [41, 48, 53], [43, 50, 55]], padsynth, 70, 2.33)), 2.0, 0.3))
reg(C, "jingle_misterioso", "Jingle misterioso", ["mistério", "investigação", "detetive"], lambda: reverb(song(melody([(64, 0.5), (67, 0.5), (66, 0.5), (63, 1.5), (64, 0.5), (60, 2)], lambda f, d: osc(f, d, "tri") * adsr(d, 0.02, 0.1, 0.6, 0.2), 100), melody([(40, 1), (40, 1), (39, 1), (39, 1), (40, 1)], lambda f, d: pluck(f, d, 0.996, 0.3), 100)), 1.8, 0.35))
reg(C, "vinheta_noticia", "Vinheta de notícia", ["notícia", "jornal", "plantão"], lambda: song(melody([([60, 64, 67, 72], 0.5), (None, 0.5), ([60, 64, 67, 72], 0.5), ([62, 65, 69, 74], 0.5), ([64, 67, 71, 76], 2)], brass, 120), drums({"k": "x.....x.x.......", "s": "....x.......x..."}, 120, 1), place([(0, metal_ring(2000, 2.0), 0.3)])))
reg(C, "batida_eletronica", "Batida eletrônica", ["eletrônica", "batida", "beat"], lambda: song(drums({"k": "x...x...x...x...", "c": "....x.......x...", "h": "..x...x...x...x."}, 124, 2), melody([(36, 0.5), (36, 0.5), (48, 0.5), (36, 0.5)] * 4, bass, 124)))
reg(C, "loop_lofi", "Loop lo-fi", ["lofi", "relaxar", "loop", "estudar"], lambda: lp(song(drums({"k": "x......x..x.....", "s": "....x.......x...", "h": "x.x.x.x.x.x.x.x."}, 80, 2, 0.15) * 0.8, chord_prog([[57, 60, 64, 67], [53, 57, 60, 64], [50, 53, 57, 60], [52, 55, 59, 62]], epiano, 80, 2)), 3500))
reg(C, "loop_chiptune", "Loop chiptune", ["chiptune", "loop", "fase"], lambda: song(melody([(76, 0.5), (79, 0.5), (81, 0.5), (79, 0.5), (76, 0.5), (74, 0.5), (72, 1)] * 2, lambda f, d: chip(f, d, 0.25), 140), melody([(45, 0.5), (57, 0.5)] * 8, lambda f, d: chip(f, d, 0.5) * 0.6, 140), drums({"k": "x...x...x...x...", "h": "..x...x...x...x."}, 140, 2)))
reg(C, "loop_suspense", "Loop de suspense", ["suspense", "loop", "tensão"], lambda: song(melody([(40, 0.25)] * 32, lambda f, d: pluck(f, d * 0.9, 0.99, 0.4), 110), chord_prog([[52, 55, 59], [53, 56, 60]], padsynth, 110, 4) * 0.6, drums({"k": "x.......x.......", "h": "....x.......x..."}, 110, 2)))
reg(C, "loop_acao", "Loop de ação", ["ação", "loop", "perseguição"], lambda: song(drums({"k": "x.x...x.x.x...x.", "s": "....x.......x...", "h": "xxxxxxxxxxxxxxxx"}, 150, 2), melody([(40, 0.25), (40, 0.25), (52, 0.25), (40, 0.25), (43, 0.25), (40, 0.25), (50, 0.25), (40, 0.25)] * 4, lambda f, d: dist(bass(f, d), 2), 150), melody([(None, 4), (64, 1), (67, 1), (71, 1), (69, 1)], strings, 150)))
reg(C, "fanfarra_real", "Fanfarra real", ["fanfarra", "rei", "anúncio", "realeza"], lambda: reverb(song(melody([(60, 0.33), (60, 0.33), (60, 0.33), (67, 1), (60, 0.5), (67, 0.5), (72, 2.5)], brass, 110), melody([(None, 1), ([48, 55], 1), ([48, 55], 1), ([48, 52, 55, 60], 2.5)], brass, 110) * 0.7, drums({"s": "x.x.x.....x.x..."}, 110, 1)), 1.6, 0.25))
reg(C, "fanfarra_vitoria", "Fanfarra de vitória", ["vitória", "fanfarra", "campeão"], lambda: reverb(song(melody([(67, 0.5), (72, 0.5), (76, 0.5), (79, 1.5), (76, 0.5), (79, 2)], brass, 130), chord_prog([[48, 55, 64], [53, 60, 69], [55, 62, 71], [48, 55, 60, 64]], strings, 130, 1.5)), 1.5, 0.25))
reg(C, "tema_vilao", "Tema de vilão", ["vilão", "mal", "malvado"], lambda: reverb(song(melody([(36, 1), (36, 0.5), (37, 0.5), (36, 1), (42, 1), (41, 2)], lambda f, d: brass(f, d) + bass(f, d), 90), melody([(None, 2), (60, 0.5), (61, 0.5), (66, 2)], strings, 90), drums({"k": "x.......x.......", "s": "............x..."}, 90, 2)), 1.8, 0.3))
reg(C, "tema_heroi", "Tema de herói", ["herói", "heroico", "salvar"], lambda: reverb(song(melody([(67, 1), (72, 1.5), (74, 0.5), (76, 1), (79, 2), (77, 0.5), (76, 0.5), (74, 1), (72, 2)], brass, 120), chord_prog([[48, 55, 64], [53, 60, 69], [55, 62, 71], [48, 55, 64]], strings, 120, 2.5), drums({"k": "x.......x.......", "s": "....x.......x..."}, 120, 5)), 1.5, 0.25))
reg(C, "tema_comedia", "Tema de comédia", ["comédia", "engraçado", "palhaçada"], lambda: song(melody([(72, 0.5), (None, 0.5), (71, 0.5), (None, 0.5), (69, 0.5), (67, 0.5), (65, 0.5), (64, 0.5), (65, 0.5), (None, 0.5), (67, 1)], lambda f, d: pluck(f, 0.25, 0.98, 0.7), 150), melody([(48, 1), (55, 1), (48, 1), (55, 1), (53, 1), (48, 1)], lambda f, d: pluck(f, 0.3, 0.98, 0.4), 150)))
reg(C, "valsa_curta", "Valsa curta", ["valsa", "dança", "elegante"], lambda: song(melody([(72, 3), (76, 1.5), (74, 1.5), (72, 3), (67, 3)], lambda f, d: strings(f, d) * 0.8, 160), melody([(48, 1), ([64, 67], 1), ([64, 67], 1), (43, 1), ([62, 65], 1), ([62, 65], 1)] * 2, lambda f, d: pluck(f, 0.4, 0.99), 160)))
reg(C, "musica_elevador", "Música de elevador", ["elevador", "espera", "tédio"], lambda: song(chord_prog([[60, 64, 67, 71], [57, 60, 64, 67], [62, 65, 69, 72], [55, 59, 62, 65]], epiano, 95, 2), melody([(76, 1), (74, 0.5), (72, 0.5), (71, 1), (72, 1), (74, 2), (None, 2)], lambda f, d: osc(f, d) * adsr(d, 0.02, 0.1, 0.6, 0.1) * 0.7, 95), melody([(48, 2), (45, 2), (50, 2), (43, 2)], bass, 95)))
reg(C, "musica_espera", "Música de espera", ["espera", "aguardando", "telefone"], lambda: song(melody([(67, 0.5), (69, 0.5), (71, 0.5), (74, 0.5), (72, 1), (71, 1), (69, 0.5), (67, 0.5), (69, 2)], lambda f, d: bell(f, d * 1.5) * 0.6, 110), chord_prog([[55, 59, 62], [52, 55, 59], [48, 52, 55], [50, 54, 57]], padsynth, 110, 2) * 0.5))
reg(C, "caixinha_musica", "Caixinha de música", ["caixinha", "ninar", "doce"], lambda: reverb(melody([(79, 1), (76, 0.5), (79, 0.5), (84, 1), (83, 1), (81, 1), (79, 0.5), (76, 0.5), (74, 2)], lambda f, d: bell(f, 1.2), 100), 1.5, 0.3))
reg(C, "virada_bateria", "Virada de bateria", ["bateria", "virada", "fill"], lambda: song(drums({"s": "x.x.xxxx..xx.xxx", "k": "x...x...x.x.x..."}, 120, 1), place([(2.0, mix(kick(0.5), hp(Rng(223).noise(1.5), 4500) * env_exp(1.5, 2.5)))])))
reg(C, "batida_suspense", "Batida de suspense", ["suspense", "batida", "investigação"], lambda: reverb(song(drums({"k": "x..x......x.....", "s": "........x......."}, 95, 2), melody([(None, 4), (52, 2), (51, 2)], strings, 95) * 0.6), 1.5, 0.25))
reg(C, "beat_rap", "Beat de rap", ["rap", "beat", "hip hop"], lambda: song(drums({"k": "x......x..x.....", "s": "....x.......x...", "h": "x.xxx.x.x.xxx.x."}, 90, 2, 0.1), melody([(36, 1.5), (36, 0.5), (39, 1), (41, 1)] * 2, lambda f, d: osc(f, d) * adsr(d, 0.005, 0.2, 0.7, 0.1), 90), chord_prog([[60, 63, 67], [58, 62, 65]], epiano, 90, 4) * 0.5))
reg(C, "batida_funk", "Batida funk (tamborzão)", ["funk", "batida", "baile"], lambda: song(drums({"k": "x..x..x...x..x..", "s": "...x..x....x..x.", "c": "......x.......x."}, 130, 2), melody([(36, 0.75), (36, 0.75), (36, 0.5), (43, 2)] * 2, lambda f, d: osc(sweep(f * 2, f, d), d) * env_exp(d, 4), 130)))
reg(C, "batida_trap", "Batida trap", ["trap", "beat", "808"], lambda: song(drums({"k": "x......x.x......", "c": "........x.......", "h": "xxxxxxxxxxxxxxxx"}, 140, 2), melody([(36, 3), (34, 1)] * 2, lambda f, d: dist(osc(sweep(f * 1.5, f, 0.05) if d < 0 else f, d) * env_exp(d, 1.2), 1.5), 140)))
reg(C, "bossa_curta", "Bossa curta", ["bossa", "violão", "brasil", "relax"], lambda: song(melody([([52, 59, 62, 67], 1.5), ([52, 59, 62, 67], 1), ([50, 57, 60, 65], 1.5), ([50, 57, 60, 65], 1), ([55, 62, 65, 69], 1.5), ([55, 62, 65, 69], 1), ([48, 55, 59, 64], 2.5)], lambda f, d: pluck(f, 0.8, 0.997, 0.5), 130), drums({"h": "x.xx.x.xx.xx.x.x", "k": "x..x...x..x....."}, 130, 3) * 0.5))
reg(C, "samba_curto", "Batucada curta", ["samba", "batucada", "carnaval"], lambda: song(drums({"k": "x...x...x...x...", "s": "..x..x.x..x..xx.", "h": "xxxxxxxxxxxxxxxx", "c": "....x.......x..."}, 140, 3), place([(i * 60 / 140 / 2, tick(1800, 0.05, 224 + i), 0.5) for i in range(0, 24, 3)])))
reg(C, "rock_curto", "Riff de rock", ["rock", "guitarra", "riff"], lambda: song(melody([(40, 0.5), (40, 0.5), (43, 0.5), (40, 0.5), (45, 0.5), (43, 0.5), (40, 1)] * 2, lambda f, d: dist(sum(pluck(f * k, d, 0.995, 0.8) for k in (1, 1.5, 2)), 5) * 0.6, 140), drums({"k": "x...x.x.x...x.x.", "s": "....x.......x...", "h": "x.x.x.x.x.x.x.x."}, 140, 2)))
reg(C, "metal_curto", "Riff pesado", ["metal", "pesado", "guitarra"], lambda: song(melody([(38, 0.25)] * 6 + [(41, 0.5), (38, 0.25), (38, 0.25), (44, 0.5), (43, 0.5)], lambda f, d: dist(sum(pluck(f * k, d, 0.99, 0.9) for k in (1, 1.5)), 8) * 0.6, 160), drums({"k": "xxxxxxxxxxxxxxxx", "s": "....x.......x..."}, 160, 1)))
reg(C, "reggae_curto", "Reggae curto", ["reggae", "relax", "praia"], lambda: song(melody([(None, 0.5), ([57, 60, 64], 0.5)] * 4 + [(None, 0.5), ([55, 59, 62], 0.5)] * 4, lambda f, d: epiano(f, 0.2), 80), melody([(45, 1.5), (52, 0.5), (45, 2), (43, 1.5), (50, 0.5), (43, 2)], bass, 80), drums({"k": "........x.......", "s": "........x......."}, 80, 2)))
reg(C, "piano_dramatico", "Piano dramático", ["piano", "drama", "novela"], lambda: reverb(melody([([45, 52, 57, 60, 64], 2), ([41, 48, 53, 57, 60], 2), ([43, 50, 55, 59, 62], 2), ([40, 47, 52, 56, 59], 3)], lambda f, d: epiano(f, d) * 1.0, 70), 2.2, 0.3))
reg(C, "orquestra_tensa", "Orquestra tensa", ["orquestra", "tensão", "batalha"], lambda: reverb(song(melody([(48, 0.5), (48, 0.5), (49, 0.5), (48, 0.5)] * 4, lambda f, d: strings(f, d * 0.8), 140), melody([(None, 4), (72, 2), (73, 2)], brass, 140), drums({"k": "x.....x.x.......", "s": "..............xx"}, 140, 2)), 1.6, 0.25))
reg(C, "festa", "Festa", ["festa", "dança", "aniversário"], lambda: song(drums({"k": "x...x...x...x...", "c": "....x.......x...", "h": "..x...x...x...x."}, 124, 2), melody([(72, 0.5), (72, 0.5), (74, 1), (72, 1), (77, 1), (76, 2), (None, 2)], lambda f, d: chip(f, d, 0.5) + 0.5 * pluck(f, d), 124), melody([(41, 2), (48, 2), (43, 2), (36, 2)], bass, 124)))
reg(C, "parabens_instrumental", "Ritmo de festa (sinos)", ["festa", "sinos", "comemoração"], lambda: reverb(song(melody([(67, 0.5), (69, 0.5), (71, 0.5), (72, 0.5), (74, 1), (76, 1), (79, 2)], lambda f, d: bell(f, 1.0), 120), place([(i * 0.5, tick(5000, 0.05, 230 + i), 0.4) for i in range(12)])), 1.2, 0.2))
reg(C, "intro_podcast", "Intro de podcast", ["podcast", "intro", "programa"], lambda: song(chord_prog([[57, 60, 64], [53, 57, 60], [55, 59, 62], [57, 60, 64]], lambda f, d: pluck(f, d, 0.997, 0.5), 110, 1), drums({"k": "x.......x.......", "s": "....x.......x...", "h": "x.x.x.x.x.x.x.x."}, 110, 1), melody([(45, 1), (41, 1), (43, 1), (45, 1)], bass, 110)))
reg(C, "cha_cha_curto", "Ritmo latino", ["latino", "dança", "salsa"], lambda: song(melody([(None, 0.5), ([60, 64, 67], 0.5), (None, 0.25), ([60, 64, 67], 0.75)] * 2 + [(None, 0.5), ([62, 65, 69], 0.5), (None, 0.25), ([62, 65, 69], 0.75)] * 2, lambda f, d: epiano(f, 0.25), 120), drums({"k": "x...x...x...x...", "h": "x.xxx.xxx.xxx.xx", "s": "...x..x....x..x."}, 120, 2) * 0.7, melody([(48, 1), (55, 1)] * 2 + [(50, 1), (57, 1)] * 2, bass, 120)))
reg(C, "sintetizador_retro", "Sintetizador retrô", ["retrô", "anos 80", "synthwave"], lambda: reverb(song(chord_prog([[57, 64, 69], [53, 60, 65], [48, 55, 60], [55, 62, 67]], padsynth, 100, 2), melody([(45, 0.5)] * 4 + [(41, 0.5)] * 4 + [(36, 0.5)] * 4 + [(43, 0.5)] * 4, bass, 100), drums({"k": "x...x...x...x...", "s": "....x.......x..."}, 100, 4)), 1.5, 0.25))

# ------------------------------------------------------------- execução

CATEGORIES = [
    ("memes_classicos", "Memes clássicos", 50),
    ("memes_brasileiros", "Memes brasileiros", 50),
    ("risadas_reacoes", "Risadas e reações", 35),
    ("impactos_comicos", "Impactos e efeitos cômicos", 35),
    ("suspense_terror", "Suspense e terror", 30),
    ("anime_poderes", "Anime e poderes", 35),
    ("games_roblox", "Games e Roblox", 35),
    ("sons_aleatorios", "Sons aleatórios", 40),
    ("vinhetas_musicas", "Vinhetas e músicas curtas", 40),
]


def write_wav(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".ogg"):
            os.remove(os.path.join(OUT, f))
    ids = [s["id"] for s in SOUNDS]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        sys.exit(f"ids repetidos: {dup}")

    entries, problems = [], []
    with tempfile.TemporaryDirectory() as tmp:
        for s in SOUNDS:
            np.random.seed(int(hashlib.md5(s["id"].encode()).hexdigest()[:8], 16))
            x = finish(s["fn"]())
            dur = len(x) / SR
            rms = float(np.sqrt(np.mean(x ** 2)))
            if dur < 0.03 or rms < 0.01:
                problems.append(f"{s['id']}: duração {dur:.2f}s rms {rms:.3f}")
                continue
            wav = os.path.join(tmp, s["id"] + ".wav")
            ogg = os.path.join(OUT, s["id"] + ".ogg")
            write_wav(wav, x)
            subprocess.run(
                ["ffmpeg", "-v", "error", "-y", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", "-ac", "1", ogg],
                check=True,
            )
            entries.append(
                {
                    "id": s["id"],
                    "file": s["id"] + ".ogg",
                    "name": s["name"],
                    "categoryId": s["cat"],
                    "durationMs": int(round(dur * 1000)),
                    "license": "ORIGINAL",
                    "author": "MEME SOUNDBOARD X (síntese sonora original)",
                    "tags": s["tags"],
                }
            )
    manifest = {
        "schemaVersion": 1,
        "categories": [{"id": c, "name": n, "targetCount": t, "order": i + 1} for i, (c, n, t) in enumerate(CATEGORIES)],
        "sounds": entries,
    }
    with open(os.path.join(ASSETS, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    by_cat = {}
    for e in entries:
        by_cat[e["categoryId"]] = by_cat.get(e["categoryId"], 0) + 1
    print(f"{len(entries)} sons gerados")
    for c, n, t in CATEGORIES:
        print(f"  {n}: {by_cat.get(c, 0)}/{t}")
    if problems:
        print("REJEITADOS:", *problems, sep="\n  ")
        sys.exit(1)


if __name__ == "__main__":
    main()

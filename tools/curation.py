#!/usr/bin/env python3
"""
Curadoria manual → tools/sound_sources.json

Cada som foi escolhido a dedo a partir dos candidatos de tools/candidates/*.json
(nome, categoria e tags decididos por pessoa, não por busca automática).
Rode: python3 tools/curation.py   (depois faça commit com "[fetch]")
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKS = json.load(open(os.path.join(ROOT, "tools", "candidates", "packs.json"), encoding="utf-8"))
_files = {p["key"]: [f["path"] for f in p["files"]] for p in PACKS["packs"]}


def zipf(pack, name):
    """Acha o caminho completo de um arquivo no pacote pelo nome (termina com /name)."""
    hits = [p for p in _files[pack] if p.endswith("/" + name) or p == name]
    if len(hits) != 1:
        raise SystemExit(f"{pack}: '{name}' encontrou {len(hits)} arquivos")
    return {"type": "zip", "pack": pack, "path": hits[0]}


def commons(title):
    return {"type": "commons", "title": title}


S = []


def add(sid, name, cat, src, tags=(), **kw):
    S.append(dict(id=sid, name=name, categoryId=cat, **{"from": src}, tags=list(tags), **kw))


# ---------------------------------------------------------------- Kenney — Voiceover Pack (CC0)
V = "oga_voiceover"
for f, name, cat, tags in [
    ("Male/game_over.ogg", "Game over (voz)", "games_roblox", ["game over", "perdeu"]),
    ("Female/game_over.ogg", "Game over (voz feminina)", "games_roblox", ["game over"]),
    ("Male/you_win.ogg", "You win! (voz)", "games_roblox", ["vitória", "ganhou"]),
    ("Female/you_win.ogg", "You win! (voz feminina)", "games_roblox", ["vitória"]),
    ("Male/you_lose.ogg", "You lose (voz)", "games_roblox", ["derrota", "perdeu"]),
    ("Female/you_lose.ogg", "You lose (voz feminina)", "games_roblox", ["derrota"]),
    ("Male/level_up.ogg", "Level up! (voz)", "games_roblox", ["level up", "nível"]),
    ("Male/power_up.ogg", "Power up! (voz)", "games_roblox", ["power up"]),
    ("Male/new_highscore.ogg", "New highscore! (voz)", "games_roblox", ["recorde"]),
    ("Male/mission_completed.ogg", "Mission completed (voz)", "games_roblox", ["missão", "completa"]),
    ("Male/mission_failed.ogg", "Mission failed (voz)", "games_roblox", ["missão", "falhou", "fail"]),
    ("Male/objective_achieved.ogg", "Objective achieved (voz)", "games_roblox", ["objetivo"]),
    ("Male/ready.ogg", "Ready? (voz)", "games_roblox", ["pronto", "largada"]),
    ("Male/go.ogg", "Go! (voz)", "games_roblox", ["vai", "largada"]),
    ("Male/hurry_up.ogg", "Hurry up! (voz)", "games_roblox", ["pressa", "rápido"]),
    ("Male/time_over.ogg", "Time over (voz)", "games_roblox", ["tempo", "acabou"]),
    ("Male/final_round.ogg", "Final round (voz)", "games_roblox", ["round final", "luta"]),
    ("Male/its_a_tie.ogg", "It's a tie (voz)", "games_roblox", ["empate"]),
    ("Male/war_fire_in_the_hole.ogg", "Fire in the hole! (voz)", "games_roblox", ["fire in the hole", "granada"]),
    ("Female/war_fire_in_the_hole.ogg", "Fire in the hole! (voz feminina)", "games_roblox", ["fire in the hole"]),
    ("Male/war_go_go_go.ogg", "Go, go, go! (voz)", "games_roblox", ["vai", "ataque"]),
    ("Male/war_medic.ogg", "Medic! (voz)", "games_roblox", ["médico", "socorro"]),
    ("Male/war_look_out.ogg", "Look out! (voz)", "games_roblox", ["cuidado"]),
    ("Male/war_get_down.ogg", "Get down! (voz)", "games_roblox", ["abaixa"]),
    ("Male/war_target_destroyed.ogg", "Target destroyed (voz)", "games_roblox", ["alvo", "destruído"]),
    ("Male/war_reloading.ogg", "Reloading! (voz)", "games_roblox", ["recarregando"]),
    ("Male/congratulations.ogg", "Congratulations! (voz)", "risadas_reacoes", ["parabéns"]),
    ("Female/congratulations.ogg", "Congratulations! (voz feminina)", "risadas_reacoes", ["parabéns"]),
    ("Male/correct.ogg", "Correct! (voz)", "risadas_reacoes", ["certo", "correto"]),
    ("Female/correct.ogg", "Correct! (voz feminina)", "risadas_reacoes", ["certo"]),
    ("Male/wrong.ogg", "Wrong! (voz)", "risadas_reacoes", ["errado"]),
    ("Female/wrong.ogg", "Wrong! (voz feminina)", "risadas_reacoes", ["errado"]),
]:
    add("kv_" + f.lower().replace("/", "_").replace(".ogg", ""), name, cat, zipf(V, f), tags)

# ---------------------------------------------------------------- SubspaceAudio — 512 retro SFX (CC0)
R = "oga_512"
for f, name, cat, tags in [
    ("sfx_coin_cluster1.wav", "Chuva de moedas (retrô)", "games_roblox", ["moeda", "coin"]),
    ("sfx_coin_double2.wav", "Moeda dupla (retrô)", "games_roblox", ["moeda"]),
    ("sfx_sounds_powerup1.wav", "Power-up (retrô)", "games_roblox", ["power up"]),
    ("sfx_sounds_powerup4.wav", "Power-up longo (retrô)", "games_roblox", ["power up"]),
    ("sfx_sounds_fanfare1.wav", "Fanfarra (retrô)", "games_roblox", ["vitória", "fanfarra"]),
    ("sfx_sounds_error12.wav", "Erro longo (retrô)", "games_roblox", ["erro"]),
    ("sfx_sounds_error1.wav", "Erro (retrô)", "games_roblox", ["erro"]),
    ("sfx_sounds_damage1.wav", "Dano (retrô)", "games_roblox", ["dano", "hit"]),
    ("sfx_movement_jump11.wav", "Pulo (retrô)", "games_roblox", ["pulo"]),
    ("sfx_sounds_pause1_in.wav", "Pausa (retrô)", "games_roblox", ["pausa"]),
    ("sfx_menu_select4.wav", "Seleção de menu (retrô)", "games_roblox", ["menu"]),
    ("sfx_movement_portal2.wav", "Portal (retrô)", "anime_poderes", ["portal", "teleporte"]),
    ("sfx_movement_portal4.wav", "Teleporte (retrô)", "anime_poderes", ["teleporte"]),
    ("sfx_wpn_laser11.wav", "Raio laser (retrô)", "anime_poderes", ["laser", "raio"]),
    ("sfx_wpn_laser5.wav", "Laser carregado (retrô)", "anime_poderes", ["laser"]),
    ("sfx_exp_medium1.wav", "Explosão (retrô)", "impactos_comicos", ["explosão"]),
    ("sfx_exp_long5.wav", "Explosão longa (retrô)", "impactos_comicos", ["explosão", "boom"]),
    ("sfx_sounds_impact10.wav", "Impacto (retrô)", "impactos_comicos", ["impacto"]),
    ("sfx_sounds_falling10.wav", "Queda longa (retrô)", "impactos_comicos", ["queda", "caindo"]),
    ("sfx_deathscream_human3.wav", "Grito de derrota (retrô)", "suspense_terror", ["grito", "morte"]),
    ("sfx_deathscream_alien1.wav", "Grito alienígena (retrô)", "suspense_terror", ["alien", "grito"]),
    ("sfx_deathscream_robot3.wav", "Robô desligando (retrô)", "games_roblox", ["robô", "desligar"]),
    ("sfx_lowhealth_alarmloop1.wav", "Vida baixa (retrô)", "games_roblox", ["vida", "alarme"]),
    ("sfx_sound_bling.wav", "Bling (retrô)", "sons_aleatorios", ["bling", "brilho"]),
    ("sfx_sound_refereewhistle.wav", "Apito de juiz (retrô)", "sons_aleatorios", ["apito", "juiz"]),
    ("sfx_sound_poweron.wav", "Ligando (retrô)", "sons_aleatorios", ["ligar"]),
    ("sfx_sound_shutdown1.wav", "Desligando (retrô)", "sons_aleatorios", ["desligar"]),
]:
    add("ss_" + f.replace("sfx_", "").replace(".wav", ""), name, cat, zipf(R, f), tags)

# ---------------------------------------------------------------- Wikimedia Commons (escolhidos à mão)
COMMONS_PICKS = json.load(open(os.path.join(ROOT, "tools", "commons_picks.json"), encoding="utf-8")) \
    if os.path.exists(os.path.join(ROOT, "tools", "commons_picks.json")) else []
for p in COMMONS_PICKS:
    add(p["id"], p["name"], p["categoryId"], commons(p["title"]), p.get("tags", []),
        **({"maxSeconds": p["maxSeconds"]} if "maxSeconds" in p else {}))

# ---------------------------------------------------------------- pendentes (sem fonte autorizada)
COPY = "gravação protegida por direitos autorais"
PENDING = [
    # memes clássicos
    ("xcubidu", "XCubidu", "memes_classicos", COPY,
     "provável meme \"Escubidubidu\" (homem cantando o tema do Scooby-Doo); confirme ouvindo"),
    ("vine_boom", "Vine Boom", "memes_classicos", COPY, "batida grave dos vídeos do Vine"),
    ("bruh", "Bruh (original)", "memes_classicos", COPY, "voz dizendo \"bruh\""),
    ("metal_pipe", "Metal Pipe", "memes_classicos", COPY, "cano de metal caindo"),
    ("discord_join", "Discord Join", "memes_classicos", "som de marca registrada do Discord", "entrar em chamada"),
    ("discord_leave", "Discord Leave", "memes_classicos", "som de marca registrada do Discord", "sair de chamada"),
    ("windows_error", "Windows Error", "memes_classicos", "som de sistema da Microsoft", "erro do Windows XP"),
    ("windows_startup", "Windows XP Startup", "memes_classicos", "som de sistema da Microsoft", "inicialização do XP"),
    ("among_us", "Among Us (sus)", "memes_classicos", "som do jogo Among Us", "revelação de impostor"),
    ("mlg_airhorn", "MLG Airhorn", "memes_classicos", COPY, "buzina dos vídeos MLG"),
    ("sad_violin", "Sad Violin", "memes_classicos", COPY, "violino triste"),
    ("emotional_damage", "Emotional Damage", "memes_classicos", COPY, "Steven He"),
    ("oh_no_laugh", "Oh no no no (risada)", "memes_classicos", COPY, "\"oh no, no, no\" + risada"),
    ("robert_weide", "Directed by Robert B. Weide", "memes_classicos", COPY, "tema de encerramento"),
    ("to_be_continued", "To Be Continued", "memes_classicos", COPY, "JoJo / Roundabout"),
    ("taco_bell", "Taco Bell Bong", "memes_classicos", "som de marca registrada", "\"bong\" da Taco Bell"),
    ("fbi_open_up", "FBI Open Up", "memes_classicos", COPY, "batida na porta + \"FBI\""),
    ("wasted", "Wasted (GTA)", "memes_classicos", "som do jogo GTA", "tela de morte"),
    ("dramatic_chipmunk", "Dun dun dunnn", "memes_classicos", COPY, "Dramatic Chipmunk"),
    ("coffin_dance", "Coffin Dance", "memes_classicos", "música protegida", "Astronomia"),
    ("roblox_oof", "Oof (Roblox antigo)", "memes_classicos", "som licenciado, retirado do Roblox em 2022", "som de morte antigo"),
    ("troll_song", "Trollface song", "memes_classicos", "música protegida", "Problem? / Trololo"),
    ("rickroll", "Rickroll", "memes_classicos", "música protegida", "Never Gonna Give You Up"),
    # memes brasileiros
    ("eu_boto_ou_nao_boto", "Eu boto ou não boto? Tô com medo", "memes_brasileiros", "música protegida",
     "\"História da Corrinha\", Caviar com Rapadura (forró)"),
    ("faustao_errou", "Errou! (Faustão)", "memes_brasileiros", COPY, "Domingão do Faustão"),
    ("faustao_oloco", "Ô loco, meu! (Faustão)", "memes_brasileiros", COPY, "Domingão do Faustão"),
    ("silvio_ma_oe", "Ma oe! (Silvio Santos)", "memes_brasileiros", COPY, "SBT"),
    ("receba", "Receba!", "memes_brasileiros", COPY, "bordão viral"),
    ("acabou_jessica", "Já acabou, Jéssica?", "memes_brasileiros", COPY, "vídeo viral"),
    ("taca_pau_marcos", "Taca-lhe pau, Marcos!", "memes_brasileiros", COPY, "vídeo viral"),
    ("ele_gosta", "Ele gosta!", "memes_brasileiros", COPY, "vídeo viral"),
    ("irra", "Irraaa!", "memes_brasileiros", COPY, "bordão viral"),
    ("para_nossa_alegria", "Para nossa alegria", "memes_brasileiros", COPY, "vídeo viral"),
    ("que_deselegante", "Que deselegante!", "memes_brasileiros", COPY, "Sandra Annenberg"),
    ("olha_a_faca", "Olha a faca!", "memes_brasileiros", COPY, "vídeo viral"),
    ("sabe_de_nada", "Sabe de nada, inocente!", "memes_brasileiros", COPY, "Compadre Washington"),
    ("e_tetra", "É tetra! (Galvão Bueno)", "memes_brasileiros", COPY, "narração da Copa de 1994"),
    ("senta_la_claudia", "Senta lá, Cláudia", "memes_brasileiros", COPY, "Faustão"),
    ("eita_giovana", "Eita, Giovana!", "memes_brasileiros", COPY, "vídeo viral"),
    ("o_gas", "Ó o gás!", "memes_brasileiros", COPY, "jingle do caminhão de gás"),
]


def main():
    spec = {
        "sounds": S,
        "pending": [dict(id=i, name=n, categoryId=c, reason=r, hint=h) for i, n, c, r, h in PENDING],
    }
    ids = [s["id"] for s in S]
    assert len(ids) == len(set(ids)), "ids repetidos"
    with open(os.path.join(ROOT, "tools", "sound_sources.json"), "w", encoding="utf-8") as f:
        json.dump(spec, f, ensure_ascii=False, indent=1)
    print(f"{len(S)} sons escolhidos, {len(PENDING)} pendentes")


if __name__ == "__main__":
    main()

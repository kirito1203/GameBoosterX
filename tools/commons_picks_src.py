#!/usr/bin/env python3
"""Escolhas manuais do Wikimedia Commons (gera tools/commons_picks.json).
Cada linha: (trecho único do título OU da descrição, id, nome, categoria, tags, maxSeconds)."""
import json, os, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
C = json.load(open(os.path.join(ROOT, "tools", "candidates", "commons.json"), encoding="utf-8"))
ALL = {}
for items in C.values():
    for it in items:
        ALL[it["title"]] = it

M, R, I, T, A, G, X, V = ("memes_classicos", "risadas_reacoes", "impactos_comicos", "suspense_terror",
                          "anime_poderes", "games_roblox", "sons_aleatorios", "vinhetas_musicas")
PICKS = [
    # --- memes clássicos: gravações reais de bordões/reações em inglês (não são os memes originais)
    ("File:Wilhelm Scream.ogg", "wilhelm", "Grito Wilhelm", M, ["grito", "wilhelm", "filme"], None),
    ("File:Sad Trombone-Joe Lamb", "sad_trombone_1", "Trombone triste (gravação)", M, ["fail", "triste", "wah wah"], None),
    ("File:Sad Trombone.wav", "sad_trombone_2", "Trombone triste 2 (gravação)", M, ["fail", "triste"], None),
    ("File:Apollo13-wehaveaproblem", "houston_problem", "Houston, we've had a problem (NASA)", M, ["houston", "problema", "nasa"], 9),
    ("File:Man saying lol.oga", "lol", "LOL (voz)", M, ["lol", "risada"], None),
    ("Wodencafe-nope.wav", "nope", "Nope (voz)", M, ["nope", "não"], None),
    ("Rachlel-what.wav", "what", "What? (voz)", M, ["what", "quê"], None),
    ("She animates-why.wav", "why", "Why? (voz)", M, ["why", "por quê"], None),
    ("Grendelkhan-uh-oh.wav", "uh_oh", "Uh-oh (voz)", M, ["uh oh", "ops"], None),
    ("Wodencafe-seriously.wav", "seriously", "Seriously? (voz)", M, ["sério"], None),
    ("Wodencafe-epic.wav", "epic", "Epic! (voz)", M, ["épico", "epic"], None),
    ("Vealhurl-noob.wav", "noob", "Noob (voz)", M, ["noob", "novato"], None),
    ("Nattes à chat-fail.wav", "fail", "Fail (voz)", M, ["fail", "falhou"], None),
    ("Back ache-whatever.wav", "whatever", "Whatever (voz)", M, ["tanto faz"], None),
    ("Back ache-wow.wav", "wow", "Wow (voz)", M, ["wow", "uau"], None),
    ("Vealhurl-yippie ki-yay.wav", "yippee", "Yippee ki-yay (voz)", M, ["yippee"], None),
    ("Wodencafe-really.wav", "really", "Really? (voz)", M, ["sério"], None),
    ("Simplificationalizer-no-no.wav", "no_no", "No, no! (voz)", M, ["não"], None),
    ("Wodencafe-awesome.wav", "awesome", "Awesome! (voz)", M, ["incrível"], None),
    ("Wodencafe-nice.wav", "nice", "Nice (voz)", M, ["nice", "boa"], None),
    ("Arlo Barnes-oh-oh.wav", "oh_oh", "Oh-oh (voz)", M, ["ops"], None),
    ("Simplificationalizer-stop.wav", "stop", "Stop! (voz)", M, ["pare"], None),
    ("Vealhurl-help.wav", "help", "Help! (voz)", M, ["socorro"], None),
    ("StuckInLagToad-ouch.wav", "ouch", "Ouch! (voz)", M, ["ai", "dor"], None),
    ("Wodencafe-amazing.wav", "amazing", "Amazing! (voz)", M, ["incrível"], None),
    ("Back ache-wait.wav", "wait", "Wait... (voz)", M, ["espera"], None),
    ("Vealhurl-jackpot.wav", "jackpot", "Jackpot! (voz)", M, ["prêmio"], None),
    ("Vealhurl-bingo.wav", "bingo", "Bingo! (voz)", M, ["bingo"], None),
    ("Back ache-warning.wav", "warning", "Warning! (voz)", M, ["alerta"], None),
    # --- risadas e reações
    ("approx-800-laughter-and-clapter", "plateia_ri_aplaude", "Plateia rindo e aplaudindo", R, ["risada", "plateia", "aplausos"], 9),
    ("approx-800-laugh-1", "plateia_gargalhada", "Plateia gargalhando", R, ["risada", "plateia"], None),
    ("approx-800-laughter-only", "plateia_rindo", "Plateia rindo", R, ["risada", "plateia"], 8),
    ("File:Menschen-Lachen.ogg", "pessoas_rindo", "Pessoas rindo", R, ["risada"], None),
    ("Exemplar of joyful laughter", "risada_alegre", "Risada alegre", R, ["risada", "feliz"], 9),
    ("Exemplar of tickling laughter", "risada_cocegas", "Risada de cócegas", R, ["risada", "cócegas"], None),
    ("Exemplar of taunting laughter", "risada_deboche", "Risada debochada", R, ["risada", "deboche", "zoeira"], None),
    ("File:Baby Laugh.ogg", "risada_bebe", "Risada de bebê", R, ["risada", "bebê", "fofo"], None),
    ("File:Evil laugh 2.oga", "risada_maligna", "Risada maligna", R, ["risada", "maligna", "vilão"], None),
    ("File:Evil laughter.ogg", "risada_maligna_longa", "Risada maligna longa", R, ["risada", "maligna", "vilão"], 10),
    ("File:Evil laugh.ogg", "risada_maligna_grupo", "Risada maligna em grupo", R, ["risada", "maligna"], 8),
    ("File:Cartoon Laugh.ogg", "risada_desenho", "Risada de desenho", R, ["risada", "desenho"], 6),
    ("File:Laugh.wav", "risada_robo", "Risada de robô (SAM)", R, ["risada", "robô", "retrô"], None),
    ("File:Ηχογράφηση (2).ogg", "criancas_rindo", "Crianças rindo", R, ["risada", "crianças"], 8),
    ("File:Applause ii.ogg", "aplausos_real", "Aplausos (gravação)", R, ["aplausos", "palmas"], 8),
    ("File:Clapping hurray.ogg", "aplausos_vivas", "Aplausos e vivas", R, ["aplausos", "vivas"], 8),
    ("File:Ohhh ahhh.ogg", "ohhh_ahhh", "Ohhh! Ahhh! (plateia)", R, ["plateia", "uau"], None),
    ("File:Surprise! - A party! Awesome!.ogg", "surprise_party", "Surprise! A party!", R, ["surpresa", "festa"], None),
    ("Nattes à chat-surprise.wav", "surprise", "Surprise! (voz)", R, ["surpresa"], None),
    ("File:Wolf whistle.ogg", "fiu_fiu_real", "Fiu-fiu (assobio real)", R, ["assobio"], None),
    ("UncleSigmund - ahhh", "ahhh", "Ahhh! (grito)", R, ["grito", "susto"], None),
    ("File:Drum Roll Intro.ogg", "rufar_real", "Rufar de tambor (gravação)", R, ["suspense", "tambor"], None),
    ("File:Sneeze.ogg", "espirro", "Espirro", R, ["espirro", "atchim"], None),
    ("File:Burp.ogg", "arroto", "Arroto", R, ["arroto"], None),
    ("File:Hiccupsound.ogg", "soluco", "Soluço", R, ["soluço"], None),
    ("File:Yawning 2.ogg", "bocejo", "Bocejo", R, ["bocejo", "sono", "tédio"], None),
    ("File:Hm and sigh.ogg", "hmm_suspiro", "Hmm... suspiro", R, ["suspiro", "tédio"], None),
    ("File:Zzz.ogg", "ronco", "Ronco", R, ["ronco", "dormindo"], 6),
    ("File:Goal Shout.ogg", "gol_grito", "GOOOOL! (grito)", R, ["gol", "futebol"], 6),
    ("Wodencafe-hooray.wav", "hooray", "Hooray! (voz)", R, ["viva"], None),
    ("Grendelkhan-bravo.wav", "bravo", "Bravo! (voz)", R, ["bravo", "parabéns"], None),
    ("Simplificationalizer-victory.wav", "victory", "Victory! (voz)", R, ["vitória"], None),
    ("She animates-yes.wav", "yes", "Yes! (voz)", R, ["sim"], None),
    # --- impactos e efeitos cômicos
    ("File:Glass breaking 2 (Gravity Sound).wav", "vidro_real", "Vidro quebrando (gravação)", I, ["vidro", "quebrar"], None),
    ("File:Hard Punch Flesh (Gravity Sound).mp3", "soco_forte", "Soco forte (gravação)", I, ["soco"], None),
    ("File:Punch Gut Damage (Gravity Sound).mp3", "soco_barriga", "Soco na barriga", I, ["soco"], None),
    ("File:Smack (Gravity Sound).wav", "tapa_real", "Tapa (gravação)", I, ["tapa"], None),
    ("File:Slam closet door (Gravity Sound).wav", "porta_batendo", "Porta batendo", I, ["porta"], None),
    ("File:Explosion 10.ogg", "explosao_real", "Explosão (gravação)", I, ["explosão"], None),
    ("File:Explosions-LS100150.ogg", "explosoes", "Explosões", I, ["explosão"], None),
    ("File:Crash cymbal.ogg", "prato_bateria", "Prato de bateria", I, ["prato", "bateria"], None),
    ("File:Boing raw.ogg", "boing_real", "Boing (gravação)", I, ["boing"], None),
    ("File:Rubber Duck Squeaker.ogg", "patinho", "Patinho de borracha", I, ["patinho", "apito"], None),
    ("File:NoiseCollector KazooA.wav", "kazoo", "Kazoo", I, ["kazoo"], None),
    ("File:Sting (2).ogg", "sting_comedia", "Sting de comédia", I, ["piada", "sting"], None),
    ("File:Dull thud.ogg", "pancada_surda", "Pancada surda", I, ["pancada"], None),
    ("File:Clonk debris falling off table.ogg", "coisas_caindo", "Coisas caindo da mesa", I, ["queda"], None),
    ("File:Dropping tool from desk.ogg", "ferramenta_caindo", "Ferramenta caindo", I, ["queda"], None),
    ("File:Metal Clanging Noises.ogg", "metal_chao", "Metal caindo no chão", I, ["metal", "queda"], 5),
    ("File:Gong or bell vibrant (short).ogg", "gongo_real", "Gongo (gravação)", I, ["gongo"], None),
    # --- suspense e terror
    ("File:Thunder 01.ogg", "trovao_real", "Trovão (gravação)", T, ["trovão"], None),
    ("File:Rain and thunder (01).ogg", "chuva_trovao", "Chuva e trovão", T, ["chuva", "trovão"], None),
    ("File:Horror laugh.ogg", "risada_terror", "Risada de terror", T, ["risada", "terror"], None),
    ("File:Zombie moan.ogg", "zumbi_gemido", "Gemido de zumbi", T, ["zumbi"], None),
    ("File:Zombie-Gutteral-Sounds.ogg", "zumbi_rosnando", "Zumbi rosnando", T, ["zumbi"], 6),
    ("File:Strix aluco male.oga", "coruja", "Coruja na noite", T, ["coruja", "noite"], None),
    ("File:Church Bells of Murbach.flac", "sinos_abadia", "Sinos da abadia", T, ["sino", "igreja"], None),
    ("File:Heartbeat.ogg", "coracao_real", "Coração batendo (gravação)", T, ["coração"], 8),
    ("Nick121087 - Demonic Woman Scream", "grito_demoniaco", "Grito demoníaco", T, ["grito", "demônio"], None),
    ("File:Theremin puccini high.ogg", "theremin", "Theremin assustador", T, ["theremin", "estranho"], None),
    ("File:Music box notes 1.ogg", "caixinha_notas", "Caixinha de música (notas)", T, ["caixinha"], None),
    ("File:Metal cabinet door jarring.ogg", "porta_metal", "Porta de metal rangendo", T, ["porta", "rangido"], 6),
    ("File:Siren.ogg", "sirene_real", "Sirene (gravação)", T, ["sirene"], 6),
    # --- anime e poderes
    ("File:Sword 1 (Gravity Sound).mp3", "espada_1", "Espada cortando", A, ["espada"], None),
    ("File:Sword 2 (Gravity Sound).mp3", "espada_2", "Espada 2", A, ["espada"], None),
    ("File:Sword 3 (Gravity Sound).mp3", "espada_3", "Golpe rápido de espada", A, ["espada"], None),
    ("Fukujinzuke-すごい.wav", "sugoi", "Sugoi! (voz japonesa)", A, ["sugoi", "anime", "incrível"], None),
    ("Fukujinzuke-なに.wav", "nani", "Nani? (voz japonesa)", A, ["nani", "anime", "quê"], None),
    ("Fukujinzuke-かわいい.wav", "kawaii", "Kawaii (voz japonesa)", A, ["kawaii", "anime", "fofo"], None),
    ("Fukujinzuke-うそ.wav", "uso", "Uso! — Mentira! (voz japonesa)", A, ["anime", "mentira"], None),
    ("Fukujinzuke-さようなら.wav", "sayonara", "Sayonara (voz japonesa)", A, ["anime", "tchau"], None),
    ("Fukujinzuke-ありがとう.wav", "arigatou", "Arigatou (voz japonesa)", A, ["anime", "obrigado"], None),
    ("Fukujinzuke-おはよう.wav", "ohayou", "Ohayou (voz japonesa)", A, ["anime", "bom dia"], None),
    ("Fukujinzuke-おやすみ.wav", "oyasumi", "Oyasumi (voz japonesa)", A, ["anime", "boa noite"], None),
    ("Fukujinzuke-こんにちは.wav", "konnichiwa", "Konnichiwa (voz japonesa)", A, ["anime", "olá"], None),
    ("File:Alien technology scanning device.ogg", "scanner_alien", "Scanner alienígena", A, ["scanner", "alien"], None),
    # --- games
    ("File:Fim efeito sonoro retrô.oga", "game_over_br", "Game over retrô", G, ["game over", "retrô"], None),
    ("File:Level Up 2 (Gravity Sound).wav", "level_up_g2", "Level up (gravação)", G, ["level up"], None),
    ("File:Level Up 4 (Gravity Sound).wav", "level_up_g4", "Level up 2 (gravação)", G, ["level up"], None),
    ("File:Jump Arcade (Gravity Sound).mp3", "pulo_arcade", "Pulo de arcade", G, ["pulo"], None),
    # --- sons aleatórios
    ("File:Felis silvestris catus.ogg", "gato", "Gato miando", X, ["gato", "miau"], None),
    ("File:Barking of a dog.ogg", "cachorro", "Cachorro latindo", X, ["cachorro", "au au"], None),
    ("File:Rooster crowing.oga", "galo", "Galo cantando", X, ["galo", "manhã"], None),
    ("File:Mudchute cow 1.ogg", "vaca", "Vaca mugindo", X, ["vaca", "muu"], None),
    ("File:Sheep bleat.ogg", "ovelha", "Ovelha", X, ["ovelha", "mé"], None),
    ("File:Wiehern.ogg", "cavalo", "Cavalo relinchando", X, ["cavalo"], None),
    ("File:Mudchute pig 1.ogg", "porco", "Porco", X, ["porco", "oinc"], None),
    ("File:Elephant voice - trumpeting.ogg", "elefante", "Elefante", X, ["elefante"], None),
    ("a-donkey-is-braying", "burro", "Burro zurrando", X, ["burro", "jumento"], 6),
    ("File:Chickens demanding food.ogg", "galinhas", "Galinhas", X, ["galinha"], 6),
    ("File:Screaming Hawk.wav", "falcao", "Falcão gritando", X, ["falcão", "águia"], None),
    ("File:Talking Parrot (Psittacula krameri).ogg", "papagaio", "Papagaio", X, ["papagaio"], 6),
    ("File:Doorbell-cheap-dingdong.ogg", "campainha_real", "Campainha (gravação)", X, ["campainha"], None),
    ("File:Alarmclock-mechanical.ogg", "despertador_real", "Despertador (gravação)", X, ["despertador"], 5),
    ("stubb typewriter-ding", "maquina_escrever", "Ding da máquina de escrever", X, ["máquina de escrever"], None),
    ("File:Holga shuttersound.ogg", "foto", "Foto (obturador)", X, ["foto", "câmera"], None),
    ("File:Bubble 01 nevit.ogg", "bolha_real", "Bolha", X, ["bolha"], None),
    ("File:Parovoz sound.ogg", "trem_vapor", "Apito de trem a vapor", X, ["trem"], None),
    ("File:Cash register.ogg", "caixa_real", "Caixa registradora (gravação)", X, ["dinheiro", "caixa"], None),
    ("File:Coins dropped in metallic moneybox.ogg", "cofrinho", "Moedas no cofrinho", X, ["moedas", "dinheiro"], 4),
    ("File:Fireworks in Rome 01.ogg", "fogos", "Fogos de artifício", X, ["fogos", "festa"], None),
    ("File:Kettle whistle.ogg", "chaleira", "Chaleira apitando", X, ["chaleira"], 5),
    ("File:Toilettenspülung 01.ogg", "descarga", "Descarga", X, ["descarga", "banheiro"], 6),
    ("File:Motorbike 4.ogg", "moto", "Moto", X, ["moto"], None),
    ("File:Rotating-bicycle-bell.wav", "bike_real", "Campainha de bicicleta (gravação)", X, ["bicicleta"], None),
    ("File:Sputnik beep.ogg", "sputnik", "Bip do Sputnik", X, ["sputnik", "espaço", "bip"], None),
    ("File:Vuvuzela single note.ogg", "vuvuzela", "Vuvuzela", X, ["vuvuzela", "copa"], None),
    ("File:Leslie A200-156.ogg", "air_horn_trem", "Buzina de locomotiva (air horn)", X, ["buzina", "air horn"], 4),
    ("File:Model 500 Telephone British ring.ogg", "telefone_antigo", "Telefone antigo", X, ["telefone"], 6),
    # --- vinhetas e músicas curtas
    ("File:Loping Sting", "sting_kevin_1", "Sting (Kevin MacLeod)", V, ["sting", "vinheta"], None),
    ("File:Der Kleber Sting", "sting_kevin_2", "Sting 2 (Kevin MacLeod)", V, ["sting", "vinheta"], None),
    ("File:Kevin MacLeod - Running Fanfare.ogg", "fanfarra_kevin", "Fanfarra (Kevin MacLeod)", V, ["fanfarra"], 10),
    ("File:Home Base Groove", "groove_kevin", "Groove (Kevin MacLeod)", V, ["groove", "loop"], 12),
    ("File:Xylophone jingle.wav", "jingle_xilofone", "Jingle de xilofone", V, ["jingle", "xilofone"], None),
    ("File:Cuica01.ogg", "cuica", "Cuíca", V, ["cuíca", "samba", "brasil"], None),
    ("File:Toque-de-angola.ogg", "berimbau", "Berimbau (capoeira Angola)", V, ["berimbau", "capoeira", "brasil"], 10),
    ("File:Caxixi.ogg", "caxixi", "Caxixi", V, ["caxixi", "capoeira"], 5),
    ("File:Drawbar C Chord.ogg", "orgao", "Acorde de órgão", V, ["órgão"], None),
    ("File:Deslizando o dedo no piano.ogg", "glissando", "Glissando no piano", V, ["piano"], None),
    ("File:Harmonica playing.ogg", "gaita", "Gaita", V, ["gaita", "harmônica"], None),
    ("File:Accordion chords-01.ogg", "sanfona", "Sanfona (acordes)", V, ["sanfona", "forró"], 8),
]


def resolve(key):
    hits = [t for t, it in ALL.items() if (key == t) or (not key.startswith("File:") and (key in t or key in (it.get("desc") or "")))
            or (key.startswith("File:") and t.startswith(key))]
    exact = [t for t in hits if t == key]
    return exact if exact else hits


out, problems = [], []
for key, sid, name, cat, tags, mx in PICKS:
    h = resolve(key)
    if len(h) != 1:
        problems.append(f"{key!r}: {len(h)} → {h[:3]}")
        continue
    p = {"id": "c_" + sid, "name": name, "categoryId": cat, "title": h[0], "tags": tags}
    if mx:
        p["maxSeconds"] = mx
    out.append(p)
if problems:
    print("AMBÍGUOS/NÃO ENCONTRADOS:", *problems, sep="\n  ")
    sys.exit(1)
json.dump(out, open(os.path.join(ROOT, "tools", "commons_picks.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(out), "escolhas do Commons")

# Fontes de sons — pesquisa, licenças e como a biblioteca é montada

## Resumo

| Fonte | Usada? | Como | Por quê |
|---|---|---|---|
| **Wikimedia Commons** | ✅ automática | API oficial, licença lida de cada arquivo | Licenças explícitas por arquivo (CC0, domínio público, CC BY, CC BY-SA); API permite uso por scripts identificados |
| **Lingua Libre** (no Commons) | ✅ automática | Mesma API | Gravações reais de pessoas falando palavras (inglês, português, japonês) |
| **OpenGameArt** | ✅ pacotes CC0 | Download direto do arquivo do pacote | Pacotes com licença CC0 declarada; robots.txt permite os arquivos |
| **Kenney.nl** | ❌ | — | robots.txt do site bloqueia acesso automatizado. Os pacotes da Kenney usados vieram da cópia oficial no OpenGameArt |
| **Pixabay** | ❌ automática / ✅ manual | Você baixa no site e importa no app | Os termos do Pixabay proíbem coleta automatizada ("scraping", robôs). A licença permite uso pessoal; o download tem que ser feito por você |
| **Freesound** | ⏸ opcional | API com chave pessoal | Exige conta e chave de API. O código está pronto (`from_freesound`): basta criar a chave e salvar como segredo `FREESOUND_API_KEY` no GitHub |
| Sites de "soundboard" (Voicy, MyInstants, Voicemod Tuna etc.) | ❌ | — | Hospedam trechos de TV, filmes, músicas e jogos sem licença; não são fonte autorizada |

## Como funciona (2 fases, tudo no GitHub Actions)

1. **Descoberta** (`tools/discover_sounds.py`, commit com `[discover]`):
   consulta a API do Commons por termos (risada, aplausos, trovão…) e lista o conteúdo dos pacotes CC0.
   Guarda só candidatos com licença permitida e até 30 s em `tools/candidates/`.
   Nada é colocado no app nessa fase.
2. **Curadoria manual** (`tools/commons_picks_src.py` e `tools/curation.py`):
   cada som foi escolhido por título exato, com nome em português, categoria e tags definidos à mão.
   Pronúncias de nomes de lugares, gravações científicas, MIDI e coisas que não são efeito sonoro foram descartadas.
3. **Download** (`tools/fetch_sounds.py`, commit com `[fetch]`):
   para cada item, **a licença é conferida de novo na fonte** no momento do download. Depois o áudio é convertido
   para OGG mono, com silêncio cortado, duração limitada e volume normalizado, e o manifesto recebe
   fonte, autor, licença e link.
   Resultado em [RELATORIO_SONS.md](RELATORIO_SONS.md).

Regras seguidas: User-Agent identificado, uma requisição por vez com pausa, robots.txt respeitado para páginas
e arquivos; a API da Wikimedia segue a política própria de acesso (que permite scripts identificados).

## Licenças aceitas

| Licença | Pode usar? | Obrigação |
|---|---|---|
| CC0 / domínio público | ✅ | Nenhuma (o app credita mesmo assim) |
| CC BY | ✅ | Creditar autor, licença e fonte → tela **Créditos** e toque longo em cada som |
| CC BY-SA | ✅ | Igual ao CC BY; a versão convertida do áudio continua CC BY-SA |
| CC BY-NC, CC BY-ND, "uso justo", sem licença | ❌ | Recusadas automaticamente |

A licença registrada é a que o Commons declara para o arquivo. O caso mais delicado é o
**Grito Wilhelm**, marcado como CC0 no Commons; ele foi mantido conforme essa declaração.

## Os dois memes pedidos especificamente

### "XCubidu" — "I can't name is for you. I cannot destroy."
- A busca por esse nome leva ao meme **"Escubidubidu"**: um homem cantando de forma engraçada o tema do
  *Scooby-Doo* ("Scooby-Dooby-Doo, where are you?…"). A frase que você lembra parece ser a letra ouvida
  "errada" desse vídeo.
- **Identificação não confirmada:** não consigo ouvir áudio. Confirme ouvindo antes de usar.
- **Situação:** não há versão com licença livre (o vídeo é de terceiros e a música do Scooby-Doo é protegida).
  Fica como **item pendente "XCubidu"** no app → Meus áudios → "Memes aguardando arquivo".

### "Eu boto ou não boto? Ai, eu tô com medo"
- É da música **"História da Corrinha"** (também chamada "Eu boto ou não boto"), da banda de forró **Caviar com Rapadura**.
- **Situação:** música protegida por direitos autorais, sem fonte autorizada.
  Fica como **item pendente** em Memes brasileiros.

## Memes populares pedidos

| Pedido | Resultado |
|---|---|
| Vine Boom, Bruh (original), Metal Pipe, Discord Join, Windows Error, Troll | ❌ Sem fonte livre → **pendentes** (importação manual) |
| Risada maligna | ✅ 3 gravações reais (Commons: CC BY-SA, domínio público, CC0) |
| Risada de bebê | ✅ gravação real (Commons, CC BY) |
| Dramatic Fail | ✅ "Trombone triste" — 2 gravações reais (Commons, CC BY) + versão sintetizada |
| Suspense | ✅ rufar de tambor real + vários sintetizados |
| Sons de surpresa | ✅ "Surprise! A party!" (domínio público), "Surprise!" (voz), "Ohhh! Ahhh!" |
| Bordões brasileiros | ⚠️ Os bordões de TV/internet são protegidos → pendentes. Incluídas **gravações reais de interjeições em português** ("Nossa!", "Caramba!", "Égua!", "Socorro!"…) do Lingua Libre — **não são os memes**, são palavras faladas por voluntários |
| Reações engraçadas | ✅ espirro, arroto, soluço, bocejo, ronco, "GOOOOL!", plateia, vozes em inglês ("Nope", "What?", "Bruh" não existe) |
| Trechos curtos de memes virais | ❌ Sem fonte livre → pendentes |

Nos "memes clássicos" incluídos, as falas em inglês ("Nope", "What?", "Epic!", "Noob"…) são **gravações reais
dessas palavras, feitas por voluntários do Lingua Libre**, e não os áudios virais. O nome de cada uma termina
com "(voz)" para não confundir.

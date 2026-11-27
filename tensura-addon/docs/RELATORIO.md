# Relatório de desenvolvimento — Tensura RPG

## Diagnóstico inicial (Fase 1)

- **O repositório não continha nenhum addon de Minecraft.** `GameBoosterX` é um app Android
  (Kotlin + Jetpack Compose) de otimização de jogos. Também não havia addon nas outras 9 branches
  remotas: não existem `manifest.json` de pacote, `behavior_pack` nem scripts de Minecraft. A única
  menção a Minecraft é o pacote `com.mojang.minecraftpe` na lista de jogos conhecidos do app.
- Logo, não havia versão do Bedrock, versão da Script API nem sistemas de raças/magias/interface a
  preservar ou corrigir. O addon foi criado do zero em `tensura-addon/`, **sem tocar no app Android**.
  O workflow do APK só roda na `main` e não é afetado; o addon tem um workflow próprio.
- Versões escolhidas e verificadas:
  - `@minecraft/server` 2.0.0 + `@minecraft/server-ui` 2.0.0 (estáveis, Minecraft 1.21.90+).
    Cada método usado foi checado contra as definições oficiais (`npm run typecheck`).
  - Interface baseada nos arquivos oficiais da Mojang (`bedrock-samples`, tag `v1.21.90.3` e `main`):
    `server_form.json` (factory `long_form`/`custom_form`, mesma estrutura nas duas versões),
    `collection_index` (padrão de `pdp_screenshots_section.json`), concatenação de textura em binding
    (`game_tip_screen.json`), semântica de `%y` (`ui_purchase_common.json`).
  - Partículas e sons conferidos nas listas oficiais. Os clientes das invocações derivam das
    definições vanilla da 1.21.90.

## Fases 2–10: o que foi implementado

| Fase | Implementado | Arquivos principais |
|---|---|---|
| 2 Estrutura de dados | DSL de habilidades com todos os campos pedidos (ID, nomes, categoria, raças, pré-requisitos, custos, nível, raridade, efeito, evoluções, origem) | `data/dsl.js`, `data/registry.js`, `data/races.js`, `data/elements.js` |
| 3 Raciais | 19 linhagens, 50 raças (as 26 pedidas incluídas), 165 habilidades raciais (incluindo 19 raízes e 31 evoluções), evoluções com requisitos, escolhas exclusivas, mecânicas por raça | `data/trees/racial.js`, `systems/passives.js`, `systems/combat.js` |
| 4 Universais | 9 árvores: Elemental (29), Aspectual (22 + 6 compartilhados), Sagrada, Invocação, Ilusão, Espiritual, Necromancia, Trevas, Avançada | `data/trees/*.js`, `systems/effects.js`, `systems/bolts.js`, `systems/fields.js`, `systems/summons.js`, `systems/tempBlocks.js`, `BP/entities/` |
| 5 Pré-requisitos/evoluções | Avaliação com estados (bloqueada, falta requisito, disponível, desbloqueada) e lista exata do que falta; exclusividade; conquistas; nº de épicas; árvore de outra linhagem e magia sagrada bloqueadas por raça | `core/progression.js` |
| 6 Árvore visual | Tela cheia, fundo escuro, nós circulares com ícone, linhas pelos pré-requisitos reais, nós maiores para habilidades principais, molduras por estado e raridade, selo de nó compartilhado, painel de detalhes lateral | `core/layout.js`, `core/view.js`, `core/uiSpec.js`, `tools/build-ui.mjs`, `RP/ui/*` |
| 7 Navegação | Setas nas 4 direções, 3 níveis de zoom preservando o centro, centralizar, toque no nó, 10 abas, ir para a árvore de origem | `core/view.js`, `ui/treeUI.js` |
| 8 Magicules | Capacidade por raça + nível + passivas + Absorção; regeneração; custo com afinidades; recarga; HUD na action bar | `systems/magicules.js`, `systems/casting.js`, `core/stats.js` |
| 9 Salvamento | Propriedades dinâmicas por jogador; salva na hora ao desbloquear/evoluir/subir de nível; descarta IDs removidos; tolera dados corrompidos; blocos temporários limpos ao reabrir o mundo | `core/save.js`, `systems/storage.js`, `systems/tempBlocks.js` |
| 10 Integração | Itens de acesso (sem comandos), receitas, entrega no primeiro login, build `.mcaddon`, workflow do Actions | `main.js`, `BP/items`, `BP/recipes`, `tools/build.mjs`, `.github/workflows/build-tensura-addon.yml` |

## O que foi testado (de verdade)

Automatizado: `npm test`, 35 testes, todos passando. Mais `npm run typecheck` sem erros contra a API 2.0.0.

- Dados: as 26 raças e as 141 habilidades/magias nomeadas no pedido existem pelo nome original;
  IDs únicos; pré-requisitos existentes e sem ciclos; só a raiz de cada árvore não tem pré-requisito;
  custo dentro da faixa da raridade; todo efeito é de um tipo implementado; magias avançadas exigem mais
  que nível; toda raça é alcançável; a raça não recebe a árvore inteira; invocações têm entidade no
  BP e no RP.
- Regras: bloqueio por nó anterior (com a mensagem exata), pontos, nível, evolução que muda a raça,
  exclusividade Mystic × Wicked Oni, evolução sem a anterior bloqueada, compatibilidade racial,
  conquistas, Aprendizado Mágico, afinidades alterando custo e dano, Magicules por nível e Absorção.
- Salvamento: ida e volta completa (simula sair e entrar), IDs removidos, dados corrompidos, limite de
  32 767 caracteres com todas as habilidades.
- Árvore: sem nós sobrepostos; cada linha corresponde a um pré-requisito real, começa e termina nos
  nós e não atravessa outros nós; tocos simétricos (sem pontas soltas); todas as árvores ramificam em
  2D; índice de cada botão = posição do nó; todas as texturas existem; navegação e zoom; nada clicável
  sob o painel de detalhes; botão de desbloqueio só quando permitido; JSON UI gerado com cada índice
  na posição certa cobrindo a grade inteira; desempenho < 20 ms por formulário.
- Simulação do JSON UI (`tools/preview.mjs`): interpreta o `tensura_tree.json` gerado e confirma que
  nós e linhas caem nas mesmas células e que os nós são circulares. **Este teste encontrou 2 erros
  reais, já corrigidos:** tamanhos `%y` circulares e nós ovais.

## O que NÃO foi testado

**Nada foi executado dentro do Minecraft.** Este ambiente não tem o cliente Bedrock (não há versão
para Linux nem tela). O simulador é uma interpretação da semântica do JSON UI, não o jogo. Precisa ser
verificado num aparelho (itens 1–17 do pedido):

1. O item abre a árvore e a troca do formulário pelo `server_form.json` funciona (item 1–3).
2. Nós e linhas aparecem alinhados e circulares no jogo (item 4).
3. Toques, setas, zoom e centralizar (itens 5–8). Tamanho dos alvos de toque no celular.
4. Desbloqueio, evolução, Magicules, salvar/sair/entrar (itens 9–16): a lógica está testada; a
   integração com o jogo, não.
5. Desempenho em Android de entrada (item 17): tempo de reabrir o formulário a cada toque.
6. Cada efeito de magia no mundo (dano, partículas, blocos temporários, invocações seguindo o dono).

## Pendências e riscos conhecidos

- **Arrastar** não é possível em formulários do Bedrock; foi substituído por setas e zoom.
- Ao abrir a árvore, o jogo também cria os botões do formulário padrão (invisíveis). Pode ficar
  lento em aparelhos fracos e só um teste real dirá.
- Se uma atualização do Minecraft mudar `server_form.json`, a sobreposição pode precisar de ajuste.
- Voo, Dominação e Possessão são aproximações dentro do que a API permite (ver README).
- Classificação de origem (obra/adaptado/original): revisar em `docs/CATALOGO.md`.

## Versão 0.2 — menu e árvore no estilo das referências

- **Menu principal** (aba Status como a referência): boneco do jogador (`live_player_renderer`, o mesmo
  do inventário), EP total, Aura, Magículas, nível, progresso de evolução com barra, habilidades em
  destaque e Barra de Habilidades com 5 espaços; abas Raça, Habilidades, Evolução e Registros.
- **Aura** passou a ser um recurso real: habilidades raciais gastam Aura; magias gastam Magículas.
- **Árvore em mapa único**: a raça no centro e as categorias em volta, com posições e rotas
  calculadas no build (`tools/gen-map.mjs`). O jogo só lê o resultado. As linhas são retas e diagonais,
  e cada direção tem o próprio estado (acesa/apagada): 1.276 peças geradas a partir das combinações
  que existem no mapa. Os nós são quadrados em pixel art e o fundo é uma paisagem noturna gerada por código.
- **Habilidades do anime**: categorias novas Únicas (17), Supremas (14), Extras (22) e Resistências (13),
  num total de 333 habilidades.
- Testes: 37 automatizados passando (mapa, linhas, menu, JSON UI gerado, simulação e desempenho).
- **Ainda não testado no jogo:** o menu e o mapa novos (inclusive o boneco do jogador dentro de um
  formulário) precisam ser conferidos no aparelho.

## Versão 0.2.1 — correção após o primeiro teste no jogo

O print do jogo mostrou o que funcionava e o que não:
- **Funcionou:** a troca do formulário padrão pela tela do addon (`server_form.json`) e o
  boneco do jogador (`live_player_renderer`).
- **Falhou:** todos os elementos mostravam os dados do botão 0 (o "X" de fechar) e nenhum texto.
  O `collection_index` fixo é ignorado nos formulários do servidor.
- **Correção:** cada elemento agora é ligado ao botão pela **posição numa grade** (`grid_position`;
  índice = linha × colunas + coluna), o mesmo mecanismo da grade de crafting e do inventário do jogo.
  As células da árvore usam grades 2D, e as camadas começam no início de uma linha (botões vazios
  de preenchimento).
- O boneco era desenhado com ~1,9× a altura do quadro e saía dele: o quadro foi reduzido para caber.
- O simulador e os testes passaram a usar a regra da grade e a verificar que cada valor (EP, nomes
  das habilidades, textos das abas) aparece no lugar certo.

## Versão 0.3.0 — Grande Sábio, reencarnação e tela de raças

**Arquivos criados:** `BP/scripts/systems/intro.js`, `BP/scripts/ui/raceUI.js`, `BP/scripts/core/raceView.js`,
`BP/scripts/data/raceCards.js`, `BP/entities/great_sage.json`, `RP/entity/great_sage.entity.json`,
`RP/models/entity/great_sage.geo.json`, `RP/animations/great_sage.animation.json`, `tools/gen_race_textures.py`,
`test/race.test.mjs`, texturas em `RP/textures/tensura/race/` e `RP/textures/entity/tensura/`.
**Arquivos modificados:** `main.js` (primeira entrada), `core/save.js` (`introDone`), `ui/menus.js` (escolha de raça
usa a tela nova), `core/uiSpec.js`, `tools/build-ui.mjs` (tela + animações), `tools/build.mjs`, `tools/preview.mjs`,
`data/trees/racial.js` (Autorregeneração passa a ser inicial do Slime).

**Apresentação (na primeira entrada):**
1. A tela ganha um tom azul/roxo (fade colorido da câmera) e soa o beacon.
2. O Grande Sábio surge à frente do jogador. É uma entidade própria com modelo 3D, anel mágico girando,
   flutuação e animação de entrada (cresce do zero), com partículas leves (3 a cada 6 ticks).
3. A câmera se aproxima e enquadra o Grande Sábio enquanto o movimento fica bloqueado.
4. As 6 falas aparecem letra a letra no título/subtítulo. Agachar completa a fala ou avança;
   sem interação, avança sozinho após ~5 s.
5. Abre a tela "Escolha sua Raça", que é obrigatória: fechar reabre.
6. Ao confirmar, a tela clareia, surgem partículas e o título "Reencarnação concluída".
   A câmera e os controles voltam, e o jogador recebe o Grimório e o Cristal.

Ao sair no meio, a apresentação recomeça na próxima entrada, e os itens não são duplicados (`gifted`).
Quem já tinha raça em versões anteriores não vê a apresentação.

**Tela de raças:**
- O Grande Sábio à esquerda, com flutuação, brilho pulsante e cubos mágicos animados (animações do
  JSON UI encadeadas, como as do próprio jogo), e uma caixa de diálogo cuja fala muda conforme a seleção.
- Título e subtítulo; 26 cards em 2 páginas (6×3), com retrato em pixel art de cada raça.
- O card selecionado ganha borda azul e brilho pulsante. As 7 evoluções aparecem bloqueadas, com cadeado e explicação.
- Painel com descrição, habilidades iniciais, afinidade, resistências, fraquezas, evoluções,
  especializações e dificuldade (estrelas).
- Botão verde "CONFIRMAR RAÇA" e as setas « ».

**Limitações honestas:**
- O efeito "letra a letra" é real, mas acontece no título/subtítulo do jogo, no mundo, e não dentro da tela de raças:
  um formulário não pode ser atualizado enquanto está aberto.
- Cada toque num card reabre a tela, com uma breve transição. É a mesma limitação dos outros menus.
- Testado: 45 testes automáticos (dados, regras, salvamento, telas simuladas) e o type-check.
  **Não testado no jogo:** a apresentação (câmera, fade, bloqueio de movimento, entidade) e a tela nova.
  Além disso, a correção 0.2.1 (ligação por `grid_position`), que essas telas usam, ainda não foi confirmada no jogo.

## Versão 0.3.1 — Sistema definitivo do Grande Sábio: etapas 1 e 2 (modelo 3D)

**Etapa 1 — análise:** o Grande Sábio já existia como uma figura encapuzada simples usada na
apresentação (`systems/intro.js`). O pedido completo, com 11 painéis, análise, síntese, memória,
notificações e evolução para Raphael e Ciel, será feito em etapas. Estrutura planejada:

```
BP/items/sage_core.json                          etapa 3 — Núcleo do Grande Sábio
BP/scripts/sage/assistente.js, analise.js,       etapas 3–7
  sintese.js, memoria.js, notificacoes.js, evolucao.js
RP/sounds/ + RP/sounds/sound_definitions.json    etapa 6 — sons próprios
```

**Etapa 2 — modelo 3D feminino (feito):**
- `tools/gen_sage_model.py` gera a geometria e a textura juntas, então a UV sempre bate:
  13 ossos e 17 cubos (cabeça, camada de cabelo com franja, mechas laterais, cabelo longo, tronco,
  casaco aberto com barra, braços finos com mangas, pernas com meias) e 3 anéis mágicos separados
  (auréola, cintura inclinada e base). A textura é 128×128 em pixel art.
- `RP/animations/great_sage.animation.json`: flutuação (cabelo e casaco balançam, anéis giram, base pulsa),
  fala (cabeça e gesto do braço), aparição (cresce de baixo para cima) e desaparecimento (sobe e encolhe).
- `RP/animation_controllers/...`: aparição → parada ⇄ falando → desaparecendo, guiado pela propriedade
  `tensura:state` (enum sincronizado com o cliente), que o script muda com `setProperty`.
- `RP/render_controllers/...`: holograma. Ignora a iluminação (brilha no escuro), tem um tom ciano pulsante
  (`overlay_color`) e anéis translúcidos (`entity_alphablend`).
- `BP/entities/great_sage.json`: sem colisão (caixa 0,01), sem física, imune a dano e fogo, some sozinho
  em 5 min se perder o controle.
- `systems/sageEntity.js`: aparecer, falar, desaparecer e partículas leves (3 a cada 6 ticks).
  A apresentação usa a fala durante o texto e o desaparecimento no fim.
- Testes: consistência entre geometria, animações, controlador, propriedade e script; UVs dentro da
  textura; visões ortográficas geradas por `tools/sage_preview.py` (`docs/img/grande_sabio_modelo.png`).
- **Não testado no jogo.** Este ambiente não tem o Minecraft. Falta confirmar o modelo, a transparência
  dos anéis, o brilho e a troca de animação pela propriedade.

## Versão 0.4.0 — árvore sem recarregar ao tocar e rolagem com o dedo

**Problema relatado no jogo:** tocar num nó fechava a tela, reabria e só então mostrava a habilidade (lento).
Também não dava para arrastar a árvore com o dedo.

**O que mudou:**
- **Seleção instantânea:** cada nó virou um *toggle* de um grupo de rádio, o mesmo mecanismo das abas das
  configurações do jogo. O painel de detalhes de cada nó já vai no formulário e aparece ao observar o estado do
  toggle (`source_control_name` + `#toggle_state`). Tocar num nó acontece só no celular, sem ir ao script e sem
  reabrir a tela. "Fechar detalhes" (barra superior) é outro toggle do mesmo grupo.
- **Rolagem vertical com o dedo:** a área da árvore virou um painel rolável (`common.scrolling_panel`) com
  13×12 células.
- **Só voltam ao script** as ações que mudam o progresso (Desbloquear, Colocar/remover da barra, Ir para o
  original), as setas laterais, as páginas ▲▼ e os saltos de categoria.
- **Menos carga a cada abertura:** a lista oculta do formulário padrão foi limitada a 64 botões
  (`max_children_size`, como o chat do jogo faz). Antes, o jogo criava centenas de botões invisíveis a cada
  abertura. Os 3 níveis de zoom simultâneos também foram retirados.

**Limitações:**
- Arrastar para os lados não existe no Bedrock: o `scroll_view` do jogo só rola na vertical. Os lados
  continuam nas setas ◀ ▶.
- O zoom foi retirado: com rolagem, cada nível de zoom triplicaria os elementos da tela.
- Depois de desbloquear, a tela reabre (é o script salvando o progresso) e o painel fica fechado. O nó
  desbloqueado aparece aceso.
- **Não testado no jogo:** os toggles de rádio e a rolagem dentro de um formulário do servidor.

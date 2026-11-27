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

## Versão 0.5.0 — Painel individual do Grande Sábio

**Acesso (sem comandos):**
- Botão **GRANDE SÁBIO** na coluna esquerda do menu principal, em todas as abas.
- Item **Núcleo do Grande Sábio** (`tensura:sage_core`). Ele é entregue uma vez a quem já tem raça e também
  pode ser fabricado: ametista + diamante + lápis-lazúli.
- As duas entradas chamam a mesma função (`openSage`) e usam os mesmos dados (`d.sage`). A entidade
  espiritual também usa esses dados: o estágio define a aparência dela.

**Tela própria** (marcador `§t§n§g`), em azul-escuro com bordas luminosas:
- à esquerda, o retrato do estágio, o nome, o nível e a barra de XP;
- no meio, as 8 categorias;
- à direita, o conteúdo com rolagem e até 6 botões de ação.

**As 8 categorias:**
1. **Perguntar:** campo de texto com botão ENVIAR (`ModalFormData.textField`), lista de exemplos, histórico e
   "Limpar histórico" com confirmação. As respostas são programadas: palavras-chave levam a uma intenção, e
   a resposta é montada com os dados reais do jogador (raça, nível, pontos, habilidades e magias
   disponíveis, fraquezas, resistências, evolução racial, ameaça do alvo, capacidades e nível do Sábio).
   - Quando não há dados, a resposta é "Dados insuficientes".
   - Síntese responde que ainda não existe.
   - O painel avisa que não é IA generativa.
   - `registerAnswerer()` permite plugar um provedor real no futuro.
2. **Análises:**
   - criatura na mira: vida, hostilidade, chefe, morto-vivo, ameaça, EP e efeitos;
   - item na mão: categoria arma/armadura/ferramenta, durabilidade e encantamentos;
   - habilidade ou magia pelo nome (dados do catálogo do addon);
   - lista paginada das análises salvas.
   As habilidades Análise e Analista também gravam na mesma memória.
3. **Nível e evolução:**
   - estágio, nível e limite, XP e XP necessário, próximo nível;
   - capacidades desbloqueadas e bloqueadas, próxima melhoria e requisitos;
   - o botão **Evoluir** só aparece quando todos os requisitos são cumpridos;
   - botão "Manifestar o Sábio".
4. **Habilidades do Sábio:** capacidades do estágio atual e as herdadas.
5. **Inteligência e capacidades:** as 11 categorias pedidas. Cada capacidade mostra nome, descrição,
   estágio, nível, estado, requisitos e próxima melhoria. "Síntese" aparece como não implementada.
6. **Próximas evoluções:** Grande Sábio → Raphael → Ciel, com requisitos, capacidades novas e melhorias.
7. **Histórico:** evoluções, descobertas, análises e conversas.
8. **Configurações:** sons, notificações, frequência (todas / só importantes / desligadas) e salvar conversas.

**Progressão própria** (separada do nível do jogador; configurável em `CONFIG.sage`):
- **Limites de nível:** Grande Sábio 30, Raphael 60, Ciel 100. São balanceamento do addon, não níveis da
  obra. No limite, o XP para de contar.
- **XP ganha com:**
  - primeira análise de uma espécie (30) e de um item (6);
  - primeira vitória contra uma espécie (10) e contra um chefe (150);
  - cada habilidade desbloqueada (8) e cada nível do jogador (15).
- **Anti-farm:**
  - análises repetidas valem 2 XP, no máximo 20 por dia do mundo;
  - perguntas valem 1 XP, no máximo 15 por dia;
  - repetir a mesma análise não dá XP de "nova espécie".
- **Requisitos de evolução** (do addon):
  - **Raphael:** Sábio nível 30, jogador nível 35, 20 espécies analisadas e um chefe derrotado.
  - **Ciel:** Raphael nível 60, jogador nível 60, 60 espécies, Wither e Ender Dragon derrotados, e a raça
    no 3º estágio.

**Capacidades com efeito real (19):**
- Análise, perguntas, relatório de status, análise de itens, memória e consulta de habilidades.
- Avaliação de ameaça, com alerta de chefe a até 64 blocos.
- Assistência de combate: vida do alvo ao golpear; no Raphael, também a fraqueza ao Sagrado.
- Bônus de atributos:
  - Pensamento Acelerado: +10% regeneração;
  - Processamento paralelo: −10% recarga;
  - Otimização de Magicules: −8% custo de magia;
  - Senhor da Sabedoria: +15% regeneração;
  - Manas: +10% dano mágico.
- Recomendações e avaliação de evolução.
- **Ciel:** aviso de vida abaixo de 30% e cura automática abaixo de 25%, com a melhor cura do jogador e
  recarga de 60 s.

**Notificações** (no chat, com som; respeitam as configurações):
- subiu de nível;
- nova capacidade;
- limite do estágio;
- requisito cumprido (uma vez cada);
- perto de evoluir (≥75%);
- evolução disponível;
- nova espécie;
- vitória contra um chefe.

**Aparência por estágio:**
- Nova propriedade `tensura:stage` na entidade. O render controller escolhe a textura (Grande Sábio azul,
  Raphael dourado, Ciel prata/violeta) e a cor do brilho.
- Retratos e ícones de cada estágio são gerados a partir do próprio modelo 3D (`docs/img/sabio_estagios.png`).

**Salvamento:**
- Propriedade dinâmica separada `tensura:sage`, por jogador, gravada só quando algo do Sábio muda.
- Se crescer demais, as conversas mais antigas são descartadas primeiro (limite < 32 767 caracteres).
- Histórico: últimas 30 conversas e até 200 espécies.

**Testes executados (57 passando; `test/sage.test.mjs` tem 11 novos):**
- XP, curva e limite do estágio;
- limite diário;
- capacidades por nível e estágio;
- bônus aplicados nos atributos reais;
- requisitos de Raphael e de Ciel (só evolui com tudo cumprido);
- perguntas com dados reais e "dados insuficientes";
- ameaça e cura;
- salvamento, ida e volta, e separação entre jogadores;
- tamanho máximo;
- 8 categorias ligadas aos botões;
- botão no menu;
- arquivos do resource pack e simulação da tela.

`tsc` está sem erros. Capturas do simulador: `docs/img/painel_sabio_*.png`.

**Não testado no jogo** (este ambiente não tem Minecraft):
- abertura pelo menu e pelo item;
- campo de texto e ENVIAR;
- sons;
- troca de textura da entidade;
- funcionamento no celular.

## Versão 0.5.1 — nova aparência do Grande Sábio (núcleo mágico)

Seguindo a imagem de referência enviada, o Grande Sábio deixou de ser uma figura humana: agora é
um **núcleo mágico** flutuante, sem corpo.

**Modelo 3D** (`tools/gen_sage_model.py`, 13 ossos, 29 cubos):
- núcleo em cruz brilhante com centro branco e uma casca de luz translúcida;
- 3 anéis tracejados que giram em sentidos opostos (o do meio inclinado);
- círculo mágico com hexagrama no chão;
- 3 painéis holográficos orbitando e 8 fragmentos flutuantes.

**Animações:**
- **flutuar:** o núcleo gira e pulsa, os anéis e painéis giram;
- **falar:** pulso forte e anéis acelerados;
- **aparecer** e **desaparecer**.

O controlador de estados e a propriedade `tensura:state` continuam os mesmos.

**Aparência por estágio:** Grande Sábio azul, Raphael dourado, Ciel violeta. Isso vale para a entidade, os
retratos, os ícones e a figura da tela de escolha de raça (`tools/sage_art.py`, a mesma arte para tudo).

**Painel no estilo "Menu do Grande Sábio" da referência:**
- **Tela inicial:**
  - grade 3x3: Conversar, Analisar, Status, Habilidades, Magias, Capacidades, Evolução, Memória e Manifestar;
  - barra **Configurações**;
  - caixa de diálogo com a fala do Sábio, que muda por estágio.
- **Páginas:** cada bloco abre a sua página, com **Voltar** para a tela inicial.
- **Nova página Magias:** suas magias com custo e recarga reais (já com os bônus do Sábio), qual espaço da
  barra ocupam e as que você pode aprender agora.
- **Sem botões falsos:** "Síntese" e "Missões" da imagem não viraram botões, porque esses sistemas ainda
  não existem.

**Testes:** 57 passando. O teste do modelo agora exige as partes do núcleo, proíbe partes de corpo humano e
confere as UVs dentro da textura. O teste da tela inicial confere 9 blocos, Configurações, Voltar e a troca de
estágio. Prévia 3D: `docs/img/sabio_nucleo_3d.png`.

**Não testado no jogo:** a transparência dos anéis e painéis, a rotação no jogo e o visual no celular.

## Versão 0.6.0 — Árvore completa do Rimuru Tempest (raça Slime)

Decisões aplicadas (as recomendadas no diagnóstico):
- As habilidades universais iguais às do Rimuru continuam para as outras raças, mas ficam **proibidas para o
  Slime**. Quem já as tinha recebe a versão do Slime automaticamente, sem perder pontos e sem efeito em dobro.
- **Raphael e Ciel do painel** exigem, para o Slime, os nós correspondentes da árvore.
- God Slime continua, marcado como **Expansão — Mod**.
- A pesquisa de origens na internet **não foi possível**: os sites da wiki estão bloqueados na rede desta sessão.
  O catálogo usa o conhecimento da obra; o que não tenho certeza ficou **PENDENTE DE VERIFICAÇÃO**
  (33 nós, ex.: Shub-Niggurath, Forma Ultimate Slime, nomes internos de Azathoth).

**Catálogo:** 116 nós em 9 setores, em `data/skill_trees/slime/` (um arquivo por setor).
- Cada nó tem rank (F→EX, configurável em `CONFIG.ranks`), origem (Anime / Mangá / LN / Mod / Pendente),
  setor, custo, nível, requisitos, método de aquisição e efeito real.
- Tabela completa: `docs/CATALOGO_SLIME.md`, gerada dos dados por `tools/catalog-race.mjs`.
- Junções para não duplicar:
  - Passo das Sombras = Movimento das Sombras;
  - Comunicação Telepática = Comunicação Mental;
  - Controle de Leis = Manipulação de Leis;
  - Relâmpago Negro e Manipulação das Chamas ficam só no setor de Absorção;
  - Consumo de Almas fica no setor do Predador;
  - Raphael e Beelzebuth ficam nos seus ramos, com moldura dourada de Suprema.

**Requisitos novos (nó vermelho = requisito especial pendente):**
- Magicules máximas;
- estágio racial;
- descoberta por absorção e absorção de espécie específica;
- missão concluída;
- nível de domínio de outra habilidade;
- chefes derrotados.

Supremas e finais exigem cadeias longas, missões, domínio, Magicules e evolução: não dá para pular só com pontos (testado).

**Absorção:**
- Banco com criatura da obra, mob do addon (adaptação), habilidades, método, condições, evoluções e origem.
- Absorver **descobre** as habilidades. Algumas exigem Predador ou Glutonaria para serem assimiladas.
- O Grande Sábio avisa "Uma nova habilidade foi identificada".

**Missões:**
- 8 missões: Primeiras Refeições, Herança de Shizu, Desastre Orc, Festival da Colheita, Despertar da Tempestade,
  Manas, Deus do Vazio e Senhor da Abundância.
- O progresso é calculado com dados reais; a recompensa sai uma única vez.

**Domínio:**
- Cada habilidade ativa sobe com o uso real, até o máximo do rank (3 a 5).
- Cada nível reduz custo e recarga em 5%.
- Predação nível 4 e 5 liberam Glutonaria e Beelzebuth.

**Efeitos novos:**
- anulação total por grupo de dano;
- imunidade a efeitos;
- redução de recarga;
- +3 espaços na barra;
- Magicules por absorção;
- formas da Imitação mais fortes e sem gasto.

**Interface:**
- Nós **circulares** com cores pelo estado: cinza bloqueado, azul disponível, verde desbloqueado, vermelho requisito
  especial. Anel interno roxo = única; dourado = suprema.
- Os 9 setores ficam lado a lado no mapa. O botão da árvore racial abre a lista de setores (com progresso) e a
  **Visão geral** (zoom afastado 3x).
- O painel do nó mostra rank, origem, setor, aquisição, domínio, requisitos e evoluções.
- **Grande Sábio:**
  - novo bloco **Missões** (progresso e absorções conhecidas);
  - responde sobre absorções possíveis, missões, combinações reais e domínio.

**Reutilizável:** outra raça ganha uma árvore completa criando `data/skill_trees/<linhagem>/` no mesmo formato
(setores, banco de absorção, missões e equivalências).

**Testes:** 69 passando, 12 novos em `test/slime.test.mjs`. Cobrem:
- catálogo, setores, ranks e origens;
- exclusividade entre raças e migração;
- progressão difícil e requisitos especiais;
- absorção, missões e domínio;
- efeitos nos atributos;
- salvamento;
- respostas do Sábio;
- setores e visão geral.

**Não testado no jogo:**
- abertura e navegação no celular;
- cores dos nós no jogo;
- notificações de descoberta;
- conclusão de missões em partida real;
- desempenho com o pacote maior (6,9 MB, por causa das peças de linha do mapa maior).

## Versão 0.7.0 — Árvore de Habilidades Supremas do Slime + Ciel (MANAS)

Decisões aprovadas ("pode começar" com as propostas do diagnóstico):
- Origens no padrão ANIME / MANGA / LIGHT_NOVEL / MOD_EXPANSION, com o campo **verificada** separado
  (aviso "não verificada" na tela).
- Nenhuma "primeira aparição" foi preenchida: nada pôde ser confirmado, porque a wiki está bloqueada na rede
  desta sessão.
- "Grande Sábio" foi mantido na interface.

**Supremas** (tier "Habilidade Suprema", ranks SS / EX / EX+; EX e EX+ são classificações do mod):

| Suprema | Requisitos principais | Sub-habilidades |
|---|---|---|
| **Raphael** — Lorde da Sabedoria | Grande Sábio + Decompositor + Toda a Criação + Demon Slime + missão Festival da Colheita | Thought Acceleration, Analytical Appraisal, Parallel Processing, Chant Annulment, All of Creation, Alteration (nova), Synthesis, Separation |
| **Beelzebuth** — Lorde da Gula | Glutonaria + Demon Slime + domínio 5 de Predação + 900 Magicules + missão "O Lorde da Gula" (nova) | Predation, Imaginary Space (movida de Azathoth), Mimicry, Corrosion, Soul Consumption, Food Chain |
| **Uriel** — Lorde dos Votos | Prisão Infinita + Beelzebuth + domínio 3 em habilidade **defensiva** e em **espacial** + missão "O Voto de Proteção" (nova) | Infinity Prison, Law Manipulation, Spatial Domination, Universal Barrier (nova), Absolute Defense |
| **Veldora** — Lorde da Tempestade | Vínculo com Veldora + Raphael + missão com **análise da essência** do Ender Dragon | Storm Dragon Release, Storm Dragon Restoration (nova), manifestação do Dragão da Tempestade, Corredor de Almas |
| **Velgrynd** — Lorde da Chama (nova, separada de Veldora) | Raphael + Anulação de Fogo + missão "A Chama Abrasadora" (análise de Blaze e Ghast + Wither) | Scorch Dragon Release, Scorch Dragon Restoration, Calor Abrasador, manifestação do Dragão Abrasador |
| **Azathoth** — Deus do Vazio (EX+) | Raphael, Beelzebuth, Ciel, Veldora, Velgrynd + missão "Síntese de Azathoth" | Soul Gluttony (nova), Nihility Collapse, Imaginary Space, Multiple Parallel Existence, True Dragon Release, True Dragon Nucleation, Space-Time Domination, Multi-Dimensional Barrier |
| **Shub-Niggurath** — Lorde da Colheita (EX+) | Cadeia Alimentar + Análise e Avaliação + 4 Supremas + missão | Skill Creation, Skill Duplication, Skill Gifting, Skill Storage |

Observações:
- **Uriel:** a relação com Raguel e Shub-Niggurath ficou registrada como "a verificar". Não existe evolução direta
  Uriel → Shub-Niggurath.
- **Azathoth:** a síntese só pode ser executada **por Ciel**. Não sai comprando pontos.
- **Sub-habilidades compartilhadas:** um único nó, sem ID duplicada. Ao ganhar a Suprema, o efeito real
  **evolui** (ex.: Parallel Processing passa de -10% para -20% de recarga; Predation ganha alcance 12).
- **Absorção:** agora recusa aliados (invocações, animais domados, jogadores). Os limites anteriores continuam
  (sem chefes, alvo enfraquecido, teto de Magicules).

**Ciel (categoria MANAS, não é Suprema):** página "Ciel — Manas" no painel do Grande Sábio, no lugar do bloco
Habilidades quando você tem Ciel. Operações reais, programadas (não é IA):
- requisitos mais próximos e recomendações;
- sínteses: combinações reais da árvore e a Síntese de Azathoth;
- Biblioteca;
- compatibilidade;
- operações de Shub-Niggurath;
- abrir a Árvore Suprema.

**Shub-Niggurath:**
- **Biblioteca:** suas habilidades + as registradas por análise de habilidade ou de jogadores.
- **Criar:** só habilidades universais até Épica, compatíveis e com nível suficiente; 1,5x os pontos + 300
  Magicules, uma vez por dia.
- **Duplicar:** só universais; até 3 cópias; 200 Magicules, uma vez por dia.
- **Conceder:** a jogador a até 8 blocos, que precisa aceitar; raça compatível, nível suficiente, sem repetir.
- **Armazenar:** +3 espaços e 3 conjuntos de barra.

**Árvore Suprema (tela própria):**
- Mapa só com as 7 Supremas + Ciel, em colunas, com as sub-habilidades abaixo de cada uma (nós vinculados
  quando a sub-habilidade é compartilhada).
- Botões de salto por Suprema e barra de progresso no topo.
- Detalhes: nomes, tier, rank, origem (verificada ou não), aquisição, requisitos, sub-habilidades, evolução,
  efeito atual e limitações.
- Acesso: botão da árvore racial → "Árvore Suprema", ou a página de Ciel.

**Dragões:** 2 entidades novas (Tempestade e Abrasador), com modelo voxel leve próprio e asas animadas.
- Usam as regras seguras das invocações: dono, só atacam monstros, somem por timer.
- Duração de 60 s.

**Estado:**
- **IMPLEMENTADO:** tudo acima.
- **TESTADO** (79 testes automáticos, 10 novos em `test/supreme.test.mjs`, checagem de tipos sem erros e simulador
  de tela):
  - dados e sub-habilidades;
  - requisitos de Uriel e de Azathoth;
  - evolução de efeitos;
  - Ciel e Shub (regras);
  - mapa supremo;
  - salvamento;
  - arquivos dos dragões.
- **PENDENTE (não testado no jogo):**
  - abertura e navegação no celular;
  - operações de Ciel numa partida (formulários);
  - concessão entre dois jogadores;
  - comportamento real dos dragões;
  - desempenho com o pacote maior (8,2 MB, por causa das peças de linha dos dois mapas).
- **PENDENTE (dados):** primeira aparição e confirmação das origens marcadas como não verificadas.
- **NÃO SUPORTADO:** arrastar para os lados (Bedrock só rola na vertical); criar habilidades novas fora do
  catálogo; IA real.

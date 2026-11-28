# Códice Umbral — grimório digital de fichas de RPG

App Android (Kotlin + Jetpack Compose) para criar, organizar e jogar com fichas de personagens de
RPG sobrenatural, paranormal, fantasia, dark fantasy, medieval, horror e ocultismo.
É um módulo independente deste repositório (`:grimorio`): APK, nome, ícone e pacote
(`com.umbral.codex`) próprios, sem afetar o Game Booster X (`:app`).

Funciona offline, sem conta e sem serviços externos. Nenhuma permissão é pedida.

## Gerar o APK

- **Pelo celular (GitHub Actions):** a cada push na `main`, o workflow *Build APK* publica em
  *Releases* o arquivo `CodiceUmbral-vN.apk` junto com o do Game Booster X.
- **No computador:** `./gradlew :grimorio:testDebugUnitTest :grimorio:assembleRelease`
  → `grimorio/build/outputs/apk/release/grimorio-release.apk`.

## Funcionalidades

| Área | O que faz |
|---|---|
| Início | Identidade do app, “Nova ficha”, “Catálogos”, continuar editando, acessados recentemente, favoritas, categorias. |
| Catálogos | 8 gêneros iniciais com ambientação, seções de lore, características, raças, classes, poderes, modelos e personagens. Tudo editável; crie categorias próprias com cor, ícone e imagem de capa. |
| Biblioteca | Pesquisa, filtro por categoria/favoritas, ordenação (edição, nome, nível, criação), favoritar, editar, duplicar, exportar e excluir com confirmação. |
| Criar ficha | Do zero com um sistema inicial (Clássico d20, Sobrenatural d20, Horror investigativo d100, Sistema livre) ou a partir de um modelo de catálogo. |
| Ficha | Retrato, dados básicos, progressão (XP com barra, adicionar XP, subir de nível, proficiência, iniciativa rolável, deslocamento, CA) e recursos configuráveis (atual/mín./máx., +/−, passo, cor, barra/número/ambos). |
| Atributos | Selos circulares com valor, modificador, bônus, penalidade e descrição; regra de modificador por ficha (clássica, direta, dividida ou manual); atributos personalizados e reordenáveis. |
| Perícias | Atributo ligado, valor base, treino, proficiência (metade/proficiente/especialista), bônus, penalidade, total; tocar no nome rola o teste (com vantagem/desvantagem). Sistemas d100 rolam abaixo do valor com grau de sucesso. |
| Magia | Grimório com ícone/imagem, nível, escola, tipo, custos de mana/energia, tempo, alcance, duração, dano rolável, efeito, requisitos; filtros (favoritas, nível, tipo, escola, disponíveis/indisponíveis) e pesquisa; “Conjurar” desconta o custo dos recursos. |
| Poderes | Habilidades ativas/passivas, técnicas, poderes raciais, transformações, sobrenaturais, demoníacos, divinos, de combate e talentos — agrupados por categoria (crie as suas), favoritos, dados roláveis. Características naturais e sobrenaturais com tipo, efeito, requisitos, limitações. |
| Inventário | Itens com imagem, quantidade, peso, valor, raridade, categoria, efeitos, bônus e dados; equipar/desequipar; moedas (ganhar, gastar, definir saldo, motivo, histórico); carga atual × capacidade (manual ou por Força) com aviso de sobrecarga. |
| Dados | Mesa 3D: d2, d4, d6, d8, d10, d12, d20 e d100 como poliedros reais (faces iluminadas, números em perspectiva, opostos somando N+1) que são arremessados, quicam e param com o resultado de frente. Toque num dado para pô-lo na mesa, segure para tirar; Limpar, MOD −/+, Rolar; ADV/DIS (dobra os d20 e mantém o maior/menor), % (d100), luz da mesa, rolagens salvas, expressões livres (`1d20+5`, `4d6kh3`) e histórico por ficha. Os dados seguem a cor do app. |
| História | Origem, infância, família, personalidade, aparência, objetivos, motivações, medos, segredos, relações, eventos e texto livre. |
| Outras informações | Notas, idiomas, campos personalizados, abas visíveis, regras (dado de teste, rolar abaixo, proficiência, bônus de treino, curva de XP, nível máximo), renomear campos, salvar como modelo, duplicar, exportar. |
| Ajustes | Cor arcana (5 temas ou qualquer cor: matiz, saturação, brilho e código #RRGGBB), brilhos, ornamentos, tamanho do texto, animação, vibração, biblioteca compacta, categoria padrão; backup/restauração; exemplos fictícios; restaurar catálogos; limpar imagens; apagar tudo (com confirmação digitada). |

## Dados e backup

- Cada ficha é um JSON em `files/codex/characters/` (pasta privada do app); catálogos em
  `catalogs.json`, ajustes em `settings.json`, imagens (JPEG reduzido a 1024 px) em `images/`.
- **Salvamento automático:** toda alteração vai para a memória na hora e para o disco ~0,4 s depois,
  com gravação atômica (arquivo temporário + rename). Ao sair do app, o pendente é gravado.
  O cabeçalho da ficha mostra “Salvando…/Salvo/Erro ao salvar”.
- Ficha corrompida não é apagada: vai para `quarentena/` e o app avisa.
- **Backup completo** (`.json` com fichas, catálogos, ajustes e imagens em base64) e **ficha única**
  pelo seletor de arquivos do sistema. Restaurar permite *mesclar* ou *substituir tudo*. Importar
  uma ficha que já existe cria uma cópia — nunca sobrescreve.

## Conteúdo de exemplo

Os catálogos iniciais trazem ambientações originais e genéricas criadas para o app (marcadas como
“Catálogo do app · editável”). Personagens de exemplo só são criados se você pedir, ficam com a
etiqueta **Exemplo** e podem ser removidos de uma vez em Ajustes. São fictícios.

## Estrutura

```
grimorio/src/main/java/com/umbral/codex/
├── model/     Modelos serializáveis (Character, Catalog, RuleSet, AppSettings)
├── core/      Lógica pura: Dice (rolagens), SheetMath (modificadores, perícias, XP, carga, moedas), Presets
├── data/      CodexRepository (persistência/autosave), BackupCodec, SeedCatalogs, DemoContent
├── ui/
│   ├── theme/       Paleta, tipografia serifada, temas de cor
│   ├── components/  Painéis ornamentados, selos arcanos, campos, diálogos, dados desenhados, imagens
│   ├── home/ catalog/ library/ create/ settings/
│   └── sheet/       Ficha: uma aba por arquivo + SheetController (rolagens e histórico)
├── CodexApplication.kt
└── MainActivity.kt
grimorio/src/test/  Testes JVM: dados, cálculos, persistência, backup e catálogos
                    + testes de interface com Robolectric (telas reais renderizadas)
```

## Testes

| Teste | O que verifica |
|---|---|
| `DiceTest`, `SheetMathTest` | Expressões de dados, vantagem/desvantagem, críticos, modificadores, perícias, XP/nível, carga, moedas. |
| `RepositoryTest` | Salvamento em disco, releitura, exclusão, backup completo com imagens, importação sem sobrescrever, catálogos completos. |
| `FlowsTest` | Pela interface: cria ficha, edita raça/classe/idade, altera Vida, ganha XP e sobe de nível, cria atributo, rola testes, cria e conjura magia (desconta mana), cria poder e característica, ganha ouro, recusa gasto acima do saldo, cria e equipa item, escreve história, relê tudo do disco e exclui com confirmação. |
| `ScreensTest` | Percorre todas as telas e abas e salva capturas em `grimorio/build/screenshots/`. |
| `Dice3DTest` | Geometria dos dados: número de faces, faces planas, rótulos completos, opostos somando N+1 e, para todo valor de todo dado, a face do resultado termina de frente. |
| `ResponsiveTest` | Celular de 320 dp e tablet de 840 dp: nenhum elemento ultrapassa a largura em nenhuma tela ou aba. |

`./gradlew :grimorio:testDebugUnitTest` roda tudo (no GitHub Actions também). Em ambientes onde o
Maven Central limita downloads grandes, há duas opções de linha de comando que apontam para cópias
locais (conferidas por checksum) dos arquivos do Robolectric: `-PcodexLocalRepo=<pasta maven>` e
`-ProbolectricJars=<pasta com android-all-instrumented>`. Sem elas, tudo é baixado normalmente.

Dependências além das já usadas pelo projeto: `kotlinx-serialization-json` (JSON) e
`material-icons-extended` (ícones; o R8 remove os não usados no APK de release).

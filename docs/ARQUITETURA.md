# Arquitetura — MEME SOUNDBOARD X

## Visão geral

MVVM + Clean Architecture em três camadas, módulo único `:app`.

```
UI (Compose)  →  ViewModel  →  UseCase  →  Repository (interface)
                                              ↑
                         data: Room · DataStore · assets · SAF (implementações)
```

- **domain/** — modelos, interfaces de repositório e casos de uso. Kotlin puro: nenhuma classe
  do Android. Por isso é testado na JVM, sem emulador.
- **data/** — Room (sons e categorias), DataStore (configurações), leitor do manifesto embutido.
- **ui/** — Compose + Material 3, uma pasta por tela, cada tela com seu ViewModel.
- **di/** — Hilt.
- **core/** — utilitários puros (normalização de texto, formatação, formatos aceitos).
  `core/audio/` recebe o motor na ETAPA 3.
- **service/** — serviço de reprodução (ETAPA 3) e painel flutuante (ETAPA 6).

Módulo único foi escolha deliberada: multi-módulo aumenta o tempo de build no GitHub Actions
e complica a edição pelo celular. A separação por pacote mantém as fronteiras; se um dia for
preciso, cada pasta vira um módulo sem reescrever código.

## Estrutura

```
app/src/main/
├── assets/sounds.json          manifesto da biblioteca (9 categorias, meta 350)
├── assets/sounds/              arquivos de áudio embutidos (vazio na ETAPA 1)
└── java/com/memesoundboardx/
    ├── MemeSoundboardApp.kt    Hilt + sincronização do manifesto em segundo plano
    ├── MainActivity.kt
    ├── core/util/              TextNormalizer, Formatters, SupportedFormats
    ├── domain/model/           Sound, Category, AppSettings, LibraryStats…
    ├── domain/repository/      SoundRepository, CategoryRepository, SettingsRepository, LibrarySyncer
    ├── domain/usecase/         favoritar, pesquisar, criar categoria, renomear
    ├── data/local/db/          AppDatabase, entidades, DAOs
    ├── data/local/datastore/   SettingsDataStore
    ├── data/manifest/          SoundManifestParser (validação do sounds.json)
    ├── data/repository/        implementações + AssetLibrarySyncer
    ├── di/                     módulos Hilt
    └── ui/                     theme, components, navigation, screens/(7 telas + detalhe de categoria)
```

## Banco de dados (Room, versão 1)

**categories** — `id` (PK texto), `name`, `isCustom`, `sortOrder`, `targetCount`.

**sounds** — `id` (PK), `key` (único: `bundled:<id>` ou `imported:<uuid>`), `name`, `searchName`,
`categoryId` (FK → categories, `SET NULL` ao excluir), `source`, `uri`, `durationMs`, `sizeBytes`,
`isFavorite`, `license`, `author`, `sourceUrl`, `tags`, `addedAt`, `isAvailable`.

Índices em `key`, `categoryId`, `isFavorite`, `source`. A pesquisa usa `searchName`
(nome + tags sem acento, minúsculo), então "acao" encontra "Ação". Resultado limitado a 200 linhas.

Sem migração destrutiva: atualizar o app nunca apaga favoritos ou importações.
Cada mudança de schema futura terá uma `Migration` explícita; o schema é exportado em `app/schemas/`.

## Biblioteca embutida

`assets/sounds.json` define as 9 categorias padrão (meta total de 350) e a lista de sons.
Na abertura do app, `AssetLibrarySyncer`:

1. valida o manifesto (id, arquivo, formato, categoria, licença, caminho seguro);
2. confere se cada arquivo existe de verdade no APK;
3. insere/atualiza os sons preservando favoritos e remove os que saíram do manifesto;
4. publica um relatório (válidos/rejeitados), visível em Configurações.

Sons importados e categorias do usuário nunca são alterados por esse processo.

## Motor de áudio (planejado — ETAPA 3)

| Tipo de som | Player | Motivo |
|---|---|---|
| Clipes ≤ 6 s (maioria) | `SoundPool` | Latência mínima; áudio decodificado em memória |
| Vinhetas e músicas | Media3 / ExoPlayer | Streaming; não ocupa RAM com PCM |

- Até 8 sons simultâneos (configurável de 1 a 8).
- Cache LRU: só os ~40 sons mais recentes ficam carregados; o resto é liberado.
- **Não pede foco de áudio por padrão** → o Roblox continua tocando sem ser abaixado.
- `AudioAttributes`: `USAGE_GAME` + `CONTENT_TYPE_SONIFICATION`.
- Serviço em primeiro plano do tipo `mediaPlayback` para continuar com o app em segundo plano.

## Configurações (DataStore)

Volume geral, máximo de sons simultâneos, pedir foco de áudio (padrão: não), onboarding concluído.
Já são persistidas na ETAPA 1; os controles aparecem na ETAPA 3, junto com o motor que os aplica.

## Versões

Kotlin 2.1.0 · AGP 8.7.3 · Gradle 8.11.1 · Compose BOM 2024.12.01 · Room 2.6.1 · Hilt 2.53.1.
`minSdk 26`, `targetSdk 35`. O app roda no Android 16 do Realme C71; o `targetSdk` será
atualizado para 36 quando a etapa de otimização validar o comportamento.

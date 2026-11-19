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
- **core/** — utilitários puros (normalização de texto, formatação, formatos aceitos) e o motor de áudio.
- **service/** — painel flutuante (ETAPA 6).

Módulo único foi escolha deliberada: multi-módulo aumenta o tempo de build no GitHub Actions
e complica a edição pelo celular. A separação por pacote mantém as fronteiras; se um dia for
preciso, cada pasta vira um módulo sem reescrever código.

## Estrutura

```
app/src/main/
├── assets/sounds.json          manifesto da biblioteca (9 categorias, meta 350)
├── assets/sounds/              241 efeitos originais (.ogg), gerados por tools/generate_sounds.py
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

## Motor de áudio — `core/audio/SoundEngine.kt`

| Tipo de som | Player | Motivo |
|---|---|---|
| Até 5 s e até 1 MB (216 dos 241 embutidos) | `SoundPool` | Amostra decodificada na RAM → disparo quase instantâneo |
| Vinhetas e músicas (> 5 s) | `MediaPlayer` | Streaming; não ocupa RAM com áudio decodificado |

- Tocar o mesmo som de novo **não duplica**: tocando → pausa; pausado → continua. Botão quadrado para.
- Até 8 sons simultâneos (configurável 1–8). Ao passar do limite, o mais antigo é interrompido.
  No máximo 2 sons longos ao mesmo tempo.
- **Cache LRU de 40 amostras**: com centenas de sons na biblioteca, só os 40 usados por último ficam
  na memória. Sons tocando nunca são descarregados. O Android pedindo memória (`onTrimMemory`)
  descarrega tudo que está parado. Os favoritos (até 16) são pré-carregados na tela Início.
- Todo o estado do motor muda numa fila única (dispatcher com paralelismo 1): não há condição de corrida
  entre toques rápidos, término de sons e mudança de volume.
- `AudioAttributes`: `USAGE_GAME` + `CONTENT_TYPE_SONIFICATION`.
- **Não pede foco de áudio por padrão** → o som do Roblox não é abaixado. Opção em Configurações.
- Fone desconectado (`ACTION_AUDIO_BECOMING_NOISY`) → para tudo.
- Arquivo inválido ou apagado → mensagem na tela; o motor continua funcionando (testado).
- O SoundPool não avisa quando um som termina; o motor usa a duração medida (manifesto ou importação)
  descontando pausas para liberar o estado "tocando".

## Importação — `data/importer/SafAudioImporter.kt`

1. Seletor do sistema (Storage Access Framework, `OpenMultipleDocuments`): não pede permissão de armazenamento.
2. Aceita MP3, WAV, OGG, M4A e AAC (por extensão ou tipo MIME). Limite: 30 MB e 10 minutos por arquivo.
3. **Copia** o arquivo para a pasta privada do app (`files/imported/`), calculando SHA-256 durante a cópia.
   Assim o som sobrevive a fechar o app, reiniciar o celular, mover ou apagar o original, ou o app de origem
   revogar o acesso. O arquivo original nunca é alterado nem apagado.
4. Conteúdo idêntico já importado → marcado como duplicado, sem nova cópia.
5. Validação real: `MediaExtractor` precisa encontrar uma faixa de áudio e ler uma amostra. Arquivo de texto
   renomeado para `.mp3`, vazio ou corrompido é recusado com o motivo.
6. Duração por `MediaExtractor`/`MediaMetadataRetriever`; tamanho pelos bytes copiados.
7. Categoria automática pelo nome do arquivo (`CategoryAutoMatcher`). Sem correspondência → "Sons aleatórios".
   O usuário pode mover depois (⋮ → Mover para categoria).
8. Excluir remove a linha do banco e apaga a cópia interna.

Na abertura do app, importados cuja cópia sumiu são marcados como indisponíveis (aparecem acinzentados).

## Configurações (DataStore)

Volume geral, máximo de sons simultâneos, pedir foco de áudio (padrão: não). O motor observa essas
configurações e aplica na hora, inclusive em sons já tocando.

## Versões

Kotlin 2.1.0 · AGP 8.7.3 · Gradle 8.11.1 · Compose BOM 2024.12.01 · Room 2.6.1 · Hilt 2.53.1.
`minSdk 26`, `targetSdk 35`. O app roda no Android 16 do Realme C71; o `targetSdk` será
atualizado para 36 quando a etapa de otimização validar o comportamento.

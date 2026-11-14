# GAME BOOSTER X

App Android (Kotlin + Jetpack Compose, MVVM) para monitorar e otimizar o desempenho em jogos, sem root.
Aparelho prioritário: **Realme C71**, com detecção automática e perfil genérico para outros aparelhos.

**Versão atual:** 0.1.0 — Fase 1 (projeto base + Dashboard funcional).

## Como gerar o APK só com o celular (GitHub Actions)

O projeto já vem com um workflow que compila na nuvem do GitHub, de graça.

1. Crie um repositório novo no GitHub (pelo app ou navegador do celular).
2. Envie todos os arquivos deste projeto para a branch `main`, mantendo as pastas
   (inclusive `.github/workflows/build-apk.yml` e `gradle/wrapper/gradle-wrapper.jar`).
3. Abra a aba **Actions** → o workflow **Build APK** roda sozinho a cada envio
   (ou toque em *Run workflow*).
4. Ao terminar (~5–8 min), abra **Releases** → `build-N` → baixe `GameBoosterX-vN.apk` e instale.
   O Android vai pedir para permitir instalação de fontes desconhecidas.

Se um build falhar, a aba Actions mostra o erro e o artefato **relatorios** traz os relatórios de testes e lint.

## Como compilar num computador

Requisitos: JDK 17, Android SDK (API 35). Depois:

```bash
./gradlew testDebugUnitTest   # testes unitários
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/
```

## Estrutura

```
app/src/main/java/com/gamebooster/x/
├── core/          Lógica pura em Kotlin (sem Android) — 100% testável na JVM
│   ├── PerformanceMode.kt    Modos Extreme/Balanced/Cool e suas políticas
│   ├── ThermalAnalyzer.kt    Níveis térmicos e detecção de limitação térmica
│   ├── MemoryAnalyzer.kt     Pressão de memória
│   ├── DeviceProfile.kt      Identificação do aparelho e perfil Realme C71
│   ├── DashboardAdvisor.kt   Recomendações por regras
│   ├── SampleHistory.kt      Histórico para gráficos em tempo real
│   ├── KnownGames.kt         Jogos suportados (pacotes oficiais)
│   └── BoostReport.kt        Resultado medido da otimização
├── hardware/      Leitura de RAM, temperatura, CPU, tela, bateria (APIs públicas)
├── performance/   Limpeza de processos em segundo plano
├── games/         Detecção de jogos instalados e abertura do jogo
├── data/          Preferências (DataStore)
├── ui/            Tema, componentes e Dashboard (Compose)
├── GameBoosterApp.kt   Container de dependências
└── MainActivity.kt
app/src/test/      Testes unitários JUnit do core
```

As pastas pedidas (`fps/`, `graphics/`, `ai/`, `benchmark/`, `database/`) entram como pacotes nas fases
em que forem implementadas, para não existir código vazio. O projeto é um único módulo Gradle (`app`)
organizado por pacotes; separar em módulos Gradle pode ser feito depois sem mudar a lógica.

## Dependências

| Biblioteca | Versão | Uso |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Build |
| Kotlin + Compose Compiler | 2.0.21 | Linguagem e UI |
| Compose BOM | 2024.12.01 | Material 3, UI |
| Lifecycle | 2.8.7 | ViewModel, coleta segura de estado |
| Activity Compose | 1.9.3 | Entrada da UI |
| DataStore Preferences | 1.1.1 | Modo de desempenho salvo |
| Coroutines | 1.9.0 | Monitor em tempo real |
| JUnit | 4.13.2 | Testes |

Room entra na Fase 4 (perfis e histórico de jogos). Nenhuma dependência paga ou serviço online.

## Permissões

- `KILL_BACKGROUND_PROCESSES` — permissão normal, concedida na instalação; usada pelo botão “Otimizar agora”.
- `<queries>` para apps com ícone no launcher — detecta jogos sem pedir acesso a todos os pacotes.

Nada de root, comandos privilegiados ou alteração de arquivos de jogos.

## Assinatura

Nenhuma chave ou senha fica no repositório. O GitHub Actions assina os APKs com a chave de debug
do Android e a guarda no cache privado do Actions, para que builds seguidos instalem um por cima do outro.
Se o cache expirar (7 dias sem builds), desinstale a versão antiga antes de instalar a nova.

Para uma chave própria, crie os Secrets `GBX_KEYSTORE_B64` (keystore em base64), `GBX_KEYSTORE_PASSWORD`,
`GBX_KEY_ALIAS` e `GBX_KEY_PASSWORD` em Settings → Secrets and variables → Actions.

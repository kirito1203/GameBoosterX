# ☀ PULSE SOLAR

App Android nativo em **Java + XML**, feito para o **realme C71 (Android 16, 8 GB RAM, 256 GB)**.
Sem root e sem bibliotecas externas: usa só o Android SDK. minSdk 26 (Android 8) · targetSdk 35 · roda normalmente no Android 16.

Visual: fundo preto, detalhes em azul e núcleo solar dourado/laranja animado na tela inicial.

---

## Módulos

| # | Módulo | O que faz de verdade |
|---|---|---|
| 1 | **Solar Boost** | Recursos ao vivo (RAM, armazenamento, temperatura, economia de bateria, tempo ligado); lista dos apps usados nas últimas 3 h com atalho para "Forçar parada"; limpeza do cache do próprio app; atalhos para otimização de bateria, serviços em execução e lista de apps |
| 2 | **Solar Guard** | Nível e temperatura da bateria com gráfico, saúde, tensão, corrente, mAh, tempo até carga completa, estado e margem térmica; **monitor contínuo** em notificação com alertas de temperatura (limite ajustável de 35 a 48 °C), bateria baixa e limitação térmica |
| 3 | **Solar RAM** | Total, disponível, em uso, limite de memória baixa, gráfico dos últimos 60 s, memória do próprio app, limite de memória por app |
| 4 | **Solar Storage** | Espaço livre e ocupado; divisão real entre apps, fotos, vídeos, áudio e sistema; ranking dos 15 apps que mais ocupam espaço (toque abre a tela do sistema para limpar cache/dados) |
| 5 | **Solar Network** | Tipo de conexão, internet validada, rede tarifada, sinal Wi-Fi (dBm), velocidade do link, banda 2,4/5/6 GHz, sinal celular, DNS e DNS privado; tráfego em tempo real; teste de latência TCP (média, jitter, perda); teste de download real |
| 6 | **Solar Gaming** | Checklist antes de jogar (RAM, espaço, economia, temperatura, bateria, latência); Modo Jogos com Não Perturbe, brilho fixo e tempo de tela de 10 min, tudo restaurado ao desativar; lista de jogos instalados para abrir com o modo ativo; atalho para o Espaço de Jogos da realme |
| 8 | **Solar FPS Engine** | Pulso do sistema ao vivo (quadros reais que o Pulse Solar recebe do Android), diagnóstico de gargalos de CPU e memória, sessão de jogo monitorada com painel sobre o jogo e alertas térmicos, perfis por jogo e relatório antes/durante a sessão |
| 9 | **Solar Game Optimizer** | Três perfis funcionais — **SOLAR ROBLOX**, **SOLAR RHYTHM** (Friday Night Funkin') e **SOLAR CRAFT** (Minecraft) — com diagnóstico, configurações gráficas recomendadas calculadas pelo estado real do aparelho, temperatura e RAM ao vivo, relatório de estabilidade a partir das sessões gravadas, dicas contra travamentos, espaço do jogo e ferramenta própria (teste de conexão, calibração de sincronia musical, teste térmico) |
| 7 | **Solar Intelligence** | Diagnóstico de possíveis causas de travamentos com botões para a tela certa do sistema; teste de CPU e teste de estabilidade térmica; frequências dos núcleos ao vivo; dados da tela e do aparelho |

### Solar FPS Engine — o que realmente funciona no realme C71 / Android 16

| Função | Situação | Como funciona |
|---|---|---|
| FPS real do jogo | ✖ Não disponível | O Android não oferece essa leitura a apps sem root/ADB. O app **não mostra nem estima** FPS de jogos. Se o Espaço de Jogos da realme tiver medidor de FPS, ele é o caminho oficial. |
| Pulso do sistema | ✔ Funciona | `Choreographer` mede os quadros que o próprio Pulse Solar recebe. Mostra a taxa real da tela e detecta engasgos do sistema (quadros atrasados e travadas > 100 ms). |
| Estabilidade e quedas | ✔ Funciona | Calculadas sobre o pulso do sistema, segundo a segundo, e gravadas na sessão. |
| Temperatura e estado térmico | ✔ Funciona | `BatteryManager` + `PowerManager.getCurrentThermalStatus` (APIs oficiais). |
| Previsão térmica | ◐ Depende do fabricante | `PowerManager.getThermalHeadroom`; se a realme não fornecer, aparece "não informada". |
| Frequência e teto da CPU | ◐ Depende do aparelho | Leitura do cpufreq do kernel; se o SELinux bloquear, o app avisa. Só leitura, nunca altera. |
| Uso total da CPU (%) e GPU | ✖ Não disponível | `/proc/stat` é bloqueado para apps desde o Android 8; não existe API oficial para GPU. |
| Gargalo de memória | ✔ Funciona | RAM livre e sinal de memória baixa do `ActivityManager`. |
| Painel sobre o jogo | ✔ Com permissão | Permissão "Exibir sobre outros apps". |
| Identificar o jogo da sessão | ✔ Com permissão | Acesso ao uso, ou iniciando a sessão pelo perfil do jogo. |
| Perfis de jogos | ✔ Funciona | Não Perturbe, brilho, tempo de tela, monitor e limite de temperatura. |

A tela do módulo tem o card **"O que funciona neste aparelho"**, que faz esse teste no próprio celular e mostra ✔ / ◐ / ✖ para cada item.

### Solar Game Optimizer — como funciona

- **Detecção do jogo:** Roblox (`com.roblox.client`) e Minecraft (`com.mojang.minecraftpe`) são detectados pelo pacote; o FNF, que tem versões e ports diferentes no Android, é procurado pelo nome do app ("Funkin"/"FNF"). Em todos dá para escolher o app manualmente.
- **Classe do aparelho:** estimada a partir da RAM total e da frequência máxima dos núcleos rápidos (quando legível). Se o aparelho estiver quente ou em economia, as recomendações descem um nível. A regra aparece na tela.
- **Recomendações gráficas:** Roblox (modo manual, barras de qualidade, taxa de quadros), FNF (limite de FPS que prioriza estabilidade, qualidade baixa, antialiasing, offset de áudio) e Minecraft (distância de renderização calculada pela classe e pela RAM livre, simulação, gráficos sofisticados, iluminação suave, céu, nuvens, folhas, visuais vibrantes). **O usuário aplica no menu do jogo; o Pulse Solar não altera arquivos nem configurações dos jogos.**
- **Calibração de sincronia (SOLAR RHYTHM):** toca 24 batidas geradas pelo app e usa `AudioTrack.getTimestamp()` (relógio de apresentação do áudio) para comparar com seus toques. Mostra o atraso médio e a variação, detecta fone Bluetooth e salva o offset no perfil.
- **Jogar com o perfil Solar:** aplica Não Perturbe, brilho e tempo de tela, inicia o monitor de sessão (temperatura, RAM e alertas) e abre o jogo. A sessão gravada alimenta o relatório de estabilidade do perfil.

### O que o app não faz (porque o Android não permite sem root)
Aumentar FPS, mudar a frequência da CPU/GPU, forçar a taxa de atualização, encerrar processos de outros apps ou apagar o cache de outros apps. Nesses casos o Pulse Solar mostra o problema e abre a tela oficial do sistema.

---

## Permissões

| Permissão | Tipo | Para quê |
|---|---|---|
| Internet, estado da rede e do Wi-Fi | automática | Solar Network e checklist de jogos |
| Notificações | pedida ao ligar o Solar Guard | alertas de temperatura e bateria |
| Serviço em primeiro plano | automática | monitor do Solar Guard |
| Exibir sobre outros apps | você concede em Configurações | painel do Solar FPS Engine durante o jogo |
| Acesso ao uso | você concede em Configurações | apps recentes, ranking de espaço, apps mais usados |
| Acesso ao Não Perturbe | você concede em Configurações | Modo Jogos |
| Alterar configurações do sistema | você concede em Configurações | brilho e tempo de tela no Modo Jogos |

Cada tela mostra o botão que leva direto à permissão necessária.

---

## Como gerar o APK pelo celular (GitHub Actions)

### 1. Criar o repositório
1. No navegador do celular, entre em **github.com/new** (logado na sua conta).
2. **Repository name:** `PulseSolar`.
3. Deixe **Public** (ou Private, se preferir).
4. **Não** marque "Add a README", ".gitignore" nem "license": o repositório precisa nascer vazio.
5. Toque em **Create repository**.

### 2. Enviar os arquivos
Envie todos os arquivos mantendo as pastas exatamente como estão, inclusive:
`.github/workflows/build.yml`, `gradlew`, `gradle/wrapper/gradle-wrapper.jar` e `app/debug.keystore`.
(O GitHub pelo navegador do celular não envia pastas inteiras com facilidade; por isso o mais simples é pedir para o Claude enviar, ou usar *Add file → Create new file* digitando o caminho completo no nome.)

### 3. Acompanhar a compilação
1. Abra o repositório → aba **Actions**.
2. O workflow **Build Pulse Solar APK** começa sozinho a cada envio (ou toque nele → **Run workflow**).
3. Bolinha amarela = compilando (≈ 4–6 min na primeira vez). ✔ verde = APK gerado. ✖ vermelho = erro: abra a execução, toque no passo que falhou e copie o erro.

### 4. Baixar e instalar o APK
- **Pelo Releases (mais fácil no celular):** página do repositório → **Releases** → *Pulse Solar build N* → toque em `PulseSolar-N.apk`.
- **Pelos Artifacts:** Actions → execução com ✔ → seção **Artifacts** → `PulseSolar-apk-N` (vem dentro de um .zip; é preciso estar logado; fica disponível por 30 dias).
- Abra o APK baixado e permita **"Instalar apps desconhecidos"** para o navegador quando o Android pedir.
- Builds novos instalam por cima do anterior (mesma chave de depuração e versão crescente), sem perder dados.

### Sobre o APK
É um **APK de depuração** (`assembleDebug`), assinado com a chave de depuração incluída no projeto (`app/debug.keystore`, senha `android`). Serve para instalar no seu aparelho; para a Play Store seria preciso uma chave de release própria.

## Como gerar no computador (opcional)

- **Android Studio:** *File > Open* → pasta `PulseSolar` → *Build > Build APK(s)*.
- **Linha de comando:** `./gradlew assembleDebug` (Linux/macOS) ou `gradlew.bat assembleDebug` (Windows).
  O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Dependências de compilação

| Item | Versão |
|---|---|
| Android Gradle Plugin | 8.7.3 (repositório google()) |
| Gradle (wrapper) | 8.10.2 |
| JDK | 17 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| Bibliotecas externas | nenhuma (só Android SDK; `org.json` já faz parte do Android) |

## Estrutura

```
PulseSolar/
├── .github/workflows/build.yml        → compila e publica o APK
├── build.gradle · settings.gradle · gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/pulsesolar/app/
        │   ├── MainActivity.java       → tela inicial
        │   ├── BaseActivity.java
        │   ├── core/                   → leitura do sistema e lógica
        │   │   ├── SystemInfo.java     (RAM, armazenamento, bateria, CPU)
        │   │   ├── Analyzer.java       (causas de travamento)
        │   │   ├── NetTests.java       (estado da rede, latência, download)
        │   │   ├── GameModeController.java
        │   │   ├── UsageHelper.java    (estatísticas de uso)
        │   │   ├── Benchmark.java      (teste de CPU)
        │   │   └── Files, Format, Nav, Palette, Perms, Ui
        │   ├── ui/                     → RingGauge, LineGraph, SolarCoreView
        │   └── modules/
        │       ├── boost/SolarBoostActivity.java
        │       ├── guard/SolarGuardActivity.java + GuardService.java
        │       ├── ram/SolarRamActivity.java
        │       ├── storage/SolarStorageActivity.java
        │       ├── network/SolarNetworkActivity.java
        │       ├── gaming/SolarGamingActivity.java
        │       ├── intelligence/SolarIntelligenceActivity.java
        │       ├── optimizer/          (Solar Game Optimizer)
        │       │   ├── GameOptimizerActivity.java, OptimizerProfileActivity.java
        │       │   ├── RhythmCalibrationActivity.java (sincronia musical)
        │       │   ├── GameSpec.java + RobloxSpec, RhythmSpec, CraftSpec
        │       │   └── DeviceState.java, OptimizerStore.java
        │       └── fps/                (Solar FPS Engine)
        │           ├── SolarFpsActivity.java, SessionReportActivity.java, GameProfileActivity.java
        │           ├── SessionService.java   (sessão, painel sobre o jogo, alertas)
        │           ├── FrameMonitor.java     (pulso do sistema via Choreographer)
        │           ├── CpuProbe.java, Bottleneck.java, CompatCheck.java
        │           └── Session.java, SessionStore.java, GameProfiles.java
        └── res/
            ├── layout/   (8 telas + 3 itens de lista)
            ├── drawable/ (fundos, botões, ícone do app e da notificação)
            ├── values/ · values-v35/ (cores, textos, tema)
            └── mipmap-anydpi-v26/ic_launcher.xml
```

## Observações

- A corrente da bateria vem do aparelho; cada fabricante usa mA ou µA, e o app converte automaticamente.
- Se o sistema bloquear a leitura das frequências da CPU, o app mostra "bloqueado" em vez de inventar valores.
- O brilho do Modo Jogos usa a escala padrão do Android (0–255). Se a realme usar outra escala, o brilho fixo pode ficar diferente do esperado; ao desativar, o valor original volta.
- O teste de velocidade usa até 25 MB de internet.
- O Solar Guard não liga sozinho depois de reiniciar o aparelho; ligue de novo na tela do módulo.

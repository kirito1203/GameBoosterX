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

1. Crie um repositório novo no GitHub, por exemplo `PulseSolar`.
2. Envie **todos** os arquivos mantendo as pastas exatamente como estão, inclusive `.github/workflows/build.yml`.
3. Abra a aba **Actions**: o workflow **Build Pulse Solar APK** roda sozinho a cada envio (ou toque em *Run workflow*).
4. Em cerca de 4 minutos, abra **Releases**: o `PulseSolar.apk` fica com link direto para baixar.
   (Também fica em Actions → execução → *Artifacts*.)
5. Abra o APK no celular e permita "instalar apps desconhecidos" para o navegador quando o Android pedir.

## Como gerar no computador (opcional)

- **Android Studio:** *File > Open* → pasta `PulseSolar` → *Build > Build App Bundle(s) / APK(s) > Build APK(s)*.
- **Linha de comando:** com Android SDK e Gradle 8.10+ instalados: `gradle assembleDebug`
  O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

---

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

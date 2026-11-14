# GAME BOOSTER X — ROG ULTIMATE

Central de jogos para Android (Java + XML, sem root), feita para o **realme C71 / Android 16**.
Sem bibliotecas externas: só o framework Android.

---

## 1. Compilar pelo celular (GitHub Actions)

1. Crie um repositório novo no GitHub (ex.: `GameBoosterX-ROG`) — ou apague o conteúdo antigo do atual.
2. Envie **todo o conteúdo** desta pasta mantendo a estrutura (inclusive a pasta oculta `.github`).
   - Dica no celular: o app **MGit** ou o site github.com ▸ *Add file ▸ Upload files* (arraste as pastas).
3. Abra a aba **Actions** ▸ *Build APK*. O build roda sozinho a cada envio (ou toque em *Run workflow*).
4. Quando ficar verde (~4–6 min), abra a execução ▸ **Artifacts ▸ GameBoosterX-apk** e baixe o ZIP.
5. Extraia o `app-debug.apk` e instale (permita "instalar apps desconhecidos" para o navegador/gerenciador de arquivos).

> O APK é assinado com a chave fixa `app/gbx-release.jks`, então cada versão nova **instala por cima** da anterior
> sem precisar desinstalar. (A senha está no `app/build.gradle`; serve apenas para uso pessoal.)

### Compilar num PC (opcional)
```
gradle assembleDebug        # Gradle 8.11+, JDK 17, Android SDK 35
```

---

## 2. Primeira execução — permissões

Abra **Configurações (PERMISSÕES)** no app e conceda:

| Permissão | Para quê | Obrigatória? |
|---|---|---|
| Sobrepor a outros apps | Game Sidebar | Para a barra |
| Acesso de Uso | Detectar o jogo aberto, aplicar perfil, gravar sessão, listar apps ativos, tamanho dos jogos | Recomendada |
| Notificações | Monitor ativo e alertas de temperatura | Recomendada |
| Modificar configurações do sistema | Brilho na barra e nos perfis | Opcional |
| Acesso ao Não Perturbe | Silenciar notificações nos jogos | Opcional |
| Acesso a todos os arquivos | Achar arquivos grandes/temporários | Opcional |
| DUMP (via ADB/LADB) | **Medição real de FPS** | Opcional |
| WRITE_SECURE_SETTINGS (via ADB/LADB) | Turbo avançado: tela no máximo + animações rápidas | Opcional |
| Sem restrição de bateria | Evitar que o realme UI feche a barra | Recomendada |

### Habilitar o FPS real (uma vez só)
O Android não deixa apps lerem o FPS de outros apps. A única fonte confiável sem root é o compositor do sistema,
que exige a permissão `DUMP`. Ela pode ser concedida pelo próprio celular:

1. *Sobre o telefone* ▸ toque 7× em *Versão* (ativa Opções do desenvolvedor).
2. Ative **Depuração por Wi-Fi**.
3. Instale o app **LADB** (ou use ADB num PC) e rode:
   ```
   adb shell pm grant com.gameboosterx.rog android.permission.DUMP
   ```
Sem isso, o FPS aparece como **N/D** — o app nunca inventa valores.

---

## 3. O que cada módulo faz (e os limites reais do Android 16 sem root)

| Módulo | Funciona de verdade | Limitação e alternativa adotada |
|---|---|---|
| **Dashboard** | RAM, bateria, temperatura da bateria, FPS (com fonte), status, lista de jogos | — |
| **Turbo X** | Checklist "Pronto para jogar" com verificação real; **Fechamento assistido** de apps em segundo plano (mede a RAM liberada); Não Perturbe; monitor; com WRITE_SECURE_SETTINGS via LADB: tela fixa na taxa máxima e animações 0,5x (restaurado ao desligar) | Não altera CPU/GPU (exige root/sistema) |
| **Otimização** | Lista apps que mais usaram recursos (24 h), limpa cache do próprio app, mede RAM antes/depois, leva ao "Forçar parada/Restringir" de cada app | Desde o Android 14 apps não podem encerrar processos de outros apps; processos do sistema são ocultados |
| **Game Sidebar** | Aba arrastável sobre o jogo: FPS, temperatura, RAM, bateria, estado térmico, brilho, atalhos de jogos, Turbo, Otimizar, Ajustes, Não Perturbe, recolher/ocultar/desligar | Usa sobreposição autorizada + serviço com notificação visível |
| **FPS Stabilizer** | FPS real via SurfaceFlinger, gráfico de 2 min, desvio, quedas bruscas, relação com temperatura, comparação de sessões | Requer permissão DUMP |
| **Touch Boost** | Teste que mede a taxa real de eventos de toque, atalhos de Tela/Acessibilidade/Desenvolvedor | Não altera sensibilidade física nem amostragem do touch |
| **Thermal Control** | Temperatura da bateria, estado térmico do sistema, margem até throttling, alertas e pausa recomendada | Temperatura de CPU/GPU é bloqueada pelo SELinux (lida só se o aparelho permitir) |
| **Network Gaming** | Ping (ICMP ou TCP), jitter, perda, nota de qualidade, monitor contínuo, teste de estabilidade, registro de oscilações | Apenas mede; não reduz ping |
| **Refresh Rate** | Lista modos suportados, prévia de cada taxa na tela do app, impacto na bateria | Taxa global só pelas Configurações de Tela |
| **Auto Game Detect** | Detecta jogos (categoria + catálogo), seleção manual, aplica perfil ao abrir | Requer Acesso de Uso |
| **Perfis** | Roblox, Minecraft, FNF, Genshin Impact, CoD Mobile: recomendações de estabilidade, temperatura, gráficos e bateria + Não Perturbe/brilho/barra/gravação por jogo | Recomendações referem-se às opções dentro de cada jogo |
| **Session Analyzer** | Duração, temperatura inicial/final/máx, bateria usada e %/h, FPS médio/mín, quedas, estado térmico máx, gráficos, comparação | Grava sessões ≥ 1 min |
| **Smart Battery** | Corrente, tensão, saúde, temperatura, autonomia instantânea (carga ÷ corrente) e autonomia jogando pelas suas sessões | Depende do que o aparelho informa |
| **Storage Manager** | Espaço livre, tamanho de cada jogo, cache do app, arquivos grandes e temporários com exclusão só após confirmação | Não acessa `Android/data`, não limpa cache de outros apps, nunca toca em pastas de jogos |

---

## 4. Estrutura

```
app/src/main/
├── AndroidManifest.xml
├── java/com/gameboosterx/rog/
│   ├── core/      SystemStats, Perms, Prefs, SystemControl, Diagnostics
│   ├── data/      GameCatalog, GameRepository, Session, SessionDb, SessionRecorder
│   ├── fps/       FpsMonitor (SurfaceFlinger)
│   ├── net/       NetworkTester
│   ├── service/   BoostService (Sidebar + monitor + sessões)
│   ├── ui/        15 telas (Main, Turbo, Optimize, Fps, Touch, Thermal, Network,
│   │              RefreshRate, Games, Profile, Sessions, SessionDetail, Battery,
│   │              Storage, Settings)
│   └── views/     GaugeView, LineChartView, TouchTestView
└── res/           layouts, drawables neon, ícones vetoriais, tema ROG, animações
```

## 5. Segurança
- Sem root, sem serviços ocultos (o serviço sempre mostra notificação), sem coleta de dados: nada sai do aparelho.
- Nunca desativa proteções térmicas, não modifica arquivos de outros apps e não apaga nada sem confirmação.

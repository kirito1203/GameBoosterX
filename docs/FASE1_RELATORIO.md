# Relatório — Fase 1

Data: 13/11/2026 · Versão 0.1.0-fase1

## 1. Ambiente inspecionado

| Item | Resultado |
|---|---|
| Projeto existente | Nenhum — projeto criado do zero |
| JDK | OpenJDK 21.0.12 (o projeto compila para Java 17) |
| Gradle | 8.14.3 instalado localmente; wrapper do projeto fixado em 8.11.1 |
| Kotlin | Compilador 2.0.21 disponível (embutido no Gradle) |
| Android SDK | **Ausente** |
| Acesso à internet (Maven/Google) | **Bloqueado** neste ambiente |

Consequência: não foi possível baixar o Android Gradle Plugin nem as bibliotecas AndroidX aqui,
então **o APK não foi compilado neste ambiente**. A compilação está configurada para rodar no
GitHub Actions (`.github/workflows/build-apk.yml`).

## 2. O que foi implementado

**Dashboard funcional**, com dados reais lidos do aparelho:

- **Estado do dispositivo:** fabricante, modelo, nome comercial (se exposto), Android/API, chip (Android 12+), núcleos, arquitetura, bateria.
- **Perfil Realme C71:** detectado por marca `realme` + “C71” no modelo/nome comercial/produto. Demais aparelhos recebem perfil genérico. A faixa (entrada/intermediário/avançado) usa RAM e núcleos **medidos**.
- **Modos Extreme / Balanced / Cool:** escolha salva no DataStore. Comportamentos reais de cada modo:
  intervalo de leitura do monitor (1 s / 2 s / 3 s), limites de alerta térmico, limite de alerta de RAM e tela ligada com o app aberto (desligado no Cool).
- **Memória:** uso, disponível, total, indicador de memória baixa do sistema e gráfico ao vivo das últimas 60 leituras.
- **Temperatura:** bateria (sempre), status térmico do sistema (Android 10+), margem térmica (Android 11+), sensores `/sys/class/thermal` quando o sistema permite; detecção de limitação térmica; gráfico ao vivo.
- **CPU:** frequência atual por núcleo quando legível; caso bloqueado, a tela informa.
- **FPS e tela:** taxa de atualização atual e suportadas, resolução. FPS de jogos aparece como “sem fonte” — nenhum número é inventado.
- **Jogos detectados:** Minecraft Bedrock, Roblox, Free Fire, Free Fire MAX e qualquer app que se declare jogo.
- **Otimizar agora:** encerra processos em segundo plano de apps do usuário e mostra a memória livre **medida antes e depois**.
- **Otimizar e jogar:** mesma otimização preservando o jogo escolhido, depois abre o jogo.
- **Recomendações:** baseadas em temperatura, memória, modo, perfil e taxa de atualização.
- Monitor pausa 5 s depois que o app sai da tela (economia de bateria).

## 3. Testes executados e evidências

### 3.1 Testes unitários do núcleo — EXECUTADOS

Os 10 arquivos de `core/` e `CoreLogicTest.kt` foram compilados com o Kotlin 2.0.21 local e executados com JUnit 4.13.2:

```
JUnit version 4.13.2
...........................
Time: 0.093

OK (27 tests)
```

Cobertura: modos e políticas, análise térmica (sem dados, por modo, status do sistema, margem, sensor),
memória (faixas, limite por modo), detecção do Realme C71 (inclusive sem confundir C61/C715 ou outras marcas),
faixa do aparelho, formatação pt-BR, histórico, média de CPU, relatório de otimização e recomendações.

### 3.2 Código Android e Compose — VERIFICAÇÃO DE TIPOS COM STUBS

Todo o código do app (hardware, limpeza, jogos, DataStore, ViewModel, telas Compose) foi compilado pelo
Kotlin 2.0.21 contra *stubs* escritos à mão das APIs Android/AndroidX usadas. Resultado: **0 erros**.
Isso encontrou e corrigiu 2 erros reais (precedência de `and` em `BackgroundCleaner` e uma chamada a valor anulável).

**Limite desta evidência:** os stubs reproduzem as assinaturas conhecidas, não as bibliotecas reais.
A confirmação definitiva é a compilação no GitHub Actions.

### 3.3 Ainda não executado

- Compilação com Android Gradle Plugin e geração do APK → roda no GitHub Actions.
- Lint Android → roda no GitHub Actions (relatório no artefato `relatorios`).
- Execução no aparelho: inicialização, navegação, permissões, consumo de memória → precisa do APK instalado no Realme C71.

## 4. Limitações conhecidas (Android sem root)

| Pedido | Situação | Alternativa adotada |
|---|---|---|
| FPS de outros apps | Android não expõe para apps comuns | Sem número falso; FPS Engine (próxima fase) avaliará fontes reais: depuração sem fio (ADB/Shizuku) ou medição pelo próprio jogo |
| Controlar CPU/GPU | Exige root | Modos ajustam o comportamento do app e as recomendações |
| Sensores térmicos internos | Muitos aparelhos bloqueiam por SELinux | Bateria + status térmico + margem térmica oficiais |
| Frequência da CPU | Pode ser bloqueada | Exibida quando legível; aviso claro quando não |
| Limpeza de memória | Só processos em segundo plano/cache | Resultado medido e exibido, inclusive quando é zero |
| Mudar gráficos dos jogos | Só o próprio jogo controla | Graphics Engine vai recomendar ajustes para o usuário aplicar |

## 5. Próxima fase

Fase 2 — Hardware Monitor dedicado (tela própria, registro de histórico, navegação entre telas).

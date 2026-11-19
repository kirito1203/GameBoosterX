# Limitações conhecidas — MEME SOUNDBOARD X

Este documento é mantido em todas as etapas. Nada listado aqui como "não suportado"
pode aparecer no app como se funcionasse.

## L1 — Transmissão pelo microfone do Roblox: NÃO SUPORTADA

**Situação:** o app não transmite efeitos sonoros pelo microfone do Roblox.
Nenhuma tela, texto ou botão do app pode afirmar o contrário sem teste real que comprove.

### Por que não é possível (sem root)

| API investigada | O que faz | Resolve? |
|---|---|---|
| `AudioRecord` | Lê áudio de fontes físicas (MIC, VOICE_COMMUNICATION…) | Não. É leitura; não existe escrita numa fonte de entrada. |
| `AudioPlaybackCapture` (Android 10+) | Captura o áudio que **sai** de outros apps | Não. Direção oposta: saída → app, nunca app → entrada de outro app. |
| `AudioManager` | Volume, roteamento, modos | Não cria dispositivos de entrada. |
| `AudioFocus` | Prioridade de reprodução entre apps | Não tem relação com captura. |
| `AudioDeviceInfo` | Lista entradas reais (mic interno, fone com fio, USB, Bluetooth) | Não permite registrar entrada virtual. |
| Microfone virtual | — | Não existe API pública. Ferramentas que fazem isso exigem root (LSPosed/Magisk) ou modificar o APK do Roblox, ambos fora do escopo. |

Restrição adicional (Android 10+): quando dois apps gravam ao mesmo tempo, o sistema
silencia um deles. O app não pode "ouvir junto" com o Roblox para mixar áudio.

### O que acontece na prática

Os sons tocam no alto-falante (ou no fone). Restam dois caminhos, **ambos sem comprovação**:

1. **Acústico (alto-falante → microfone do próprio celular).**
   O chat de voz usa a fonte `VOICE_COMMUNICATION`, que liga o cancelamento de eco (AEC).
   O AEC existe justamente para apagar do microfone o som que o próprio aparelho toca.
   Resultado esperado: som cortado, abafado ou ausente. Depende do aparelho e do Roblox.
2. **Cabo de loopback (fone de ouvido com fio).**
   Um cabo TRRS que leva a saída de áudio de volta ao pino de microfone do mesmo conector.
   Não precisa de computador nem de segundo celular, mas exige hardware extra e tem riscos:
   o AEC pode cancelar mesmo assim (ele usa como referência o que o aparelho toca, não o caminho
   físico), os sons do jogo também entram no "microfone" e você para de ouvir pelo alto-falante.
   Verificar se o Realme C71 tem conector P2 antes de considerar.

Exigências do próprio Roblox: chat de voz requer verificação de idade e não está disponível
para todas as contas. Tocar sons no chat de voz pode gerar denúncias e moderação.

### Plano para a ETAPA 7 (sem promessa de resultado)

- **Teste de cancelamento de eco no aparelho:** o app toca um tom conhecido e grava ao mesmo tempo
  com `VOICE_COMMUNICATION` + AEC, medindo quanto do tom sobrevive. Isso aproxima o que o Roblox
  recebe, mas **não é** um teste do Roblox. Precisa rodar com o Roblox fechado (limite de captura simultânea).
- **Teste real com o Roblox:** feito por você, com outra pessoa ouvindo no jogo. Só esse teste pode
  mudar o status desta limitação, e o resultado será registrado em `TESTES.md`.

## L2 — Biblioteca começa vazia

Não há arquivos de áudio embutidos na ETAPA 1. Memes de TV, músicas e falas de filmes têm direitos
autorais e não serão embutidos. O app aceita, no manifesto, apenas sons com licença declarada
(CC0, domínio público, CC-BY, original ou licenciado). Seus próprios arquivos entram pela importação (ETAPA 5).

## L3 — Painel flutuante pode ser ocultado pelo jogo

Desde o Android 12, um app pode pedir ao sistema que esconda sobreposições de outros apps
enquanto ele está aberto. Se o Roblox fizer isso, o painel não aparece por cima do jogo.
Alternativa planejada: controles na notificação de mídia. A confirmar na ETAPA 6.

## L4 — Economia de bateria da realme

O sistema da realme encerra apps em segundo plano de forma agressiva. Pode ser necessário
liberar o app em Configurações → Bateria. O app vai orientar, mas não pode mudar isso sozinho.

## L5 — Compilação e testes

O projeto é compilado pelo GitHub Actions. O desenvolvedor (Claude) não tem um Realme C71:
toda marcação "testado no dispositivo real" depende de você.

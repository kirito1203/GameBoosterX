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

## L2 — Biblioteca: 241 sons originais, sem memes gravados

A biblioteca embutida tem **241 efeitos originais**, criados por síntese sonora pelo script
`tools/generate_sounds.py` (osciladores, ruído, filtros, síntese FM e Karplus-Strong). Não há
gravações de terceiros, por isso a licença é ORIGINAL.

| Categoria | Embutidos | Meta |
|---|---|---|
| Memes clássicos | **0** | 50 |
| Memes brasileiros | **0** | 50 |
| Risadas e reações | 21 (sem risadas: só reações instrumentais) | 35 |
| Impactos e efeitos cômicos | 34 | 35 |
| Suspense e terror | 30 | 30 |
| Anime e poderes | 37 | 35 |
| Games e Roblox (estilo 8-bit, sem sons do Roblox) | 36 | 35 |
| Sons aleatórios | 42 | 40 |
| Vinhetas e músicas curtas | 41 | 40 |

Por que memes e risadas ficam de fora: são gravações (falas, vozes, trechos de TV e internet)
com direitos autorais. Síntese não reproduz voz humana de forma aceitável. Esses entram pela
importação ("Meus áudios"), com arquivos que você tenha. A categorização automática coloca
arquivos com nomes como "bruh", "vine boom", "faustão", "kkkk" nas categorias certas.

Os sons foram verificados por medidas objetivas (duração, volume, silêncio, decodificação,
espectrograma), mas **ninguém os ouviu antes da entrega** — o desenvolvedor não tem como ouvir áudio.
Sua avaliação no celular é o teste de qualidade real.

## L3 — Painel flutuante pode ser ocultado pelo jogo

Desde o Android 12, um app pode pedir ao sistema que esconda sobreposições de outros apps
enquanto ele está aberto. Se o Roblox fizer isso, o painel não aparece por cima do jogo.
Alternativa planejada: controles na notificação de mídia. A confirmar na ETAPA 6.

## L4 — Economia de bateria da realme

O sistema da realme encerra apps em segundo plano de forma agressiva. Pode ser necessário
liberar o app em Configurações → Bateria. O app vai orientar, mas não pode mudar isso sozinho.

## L5 — Reprodução em segundo plano

Os sons continuam tocando se você sair do app, enquanto o Android mantiver o processo vivo.
Não há serviço em primeiro plano nem notificação nesta etapa; isso entra junto com o painel
flutuante (ETAPA 6), que é o jeito de disparar sons por cima do Roblox.

## L6 — Volume

O volume do app é relativo ao volume de mídia do celular. Se o celular estiver no mudo de
mídia, o app não toca nada, mesmo em 100%.

## L7 — Compilação e testes

O projeto é compilado pelo GitHub Actions. O desenvolvedor (Claude) não tem um Realme C71:
toda marcação "testado no dispositivo real" depende de você.

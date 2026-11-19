# Relatório de testes e status

## Build 30 (biblioteca 0.3.0) — commit ce322da

- Compilação: ✅ · Testes de JVM: ✅ **54/54** · Emulador Android (API 34): ✅ **23/23**
- Novos testes:
  - manifesto real com 475 sons: todo arquivo existe; todo som externo tem link e licença válida; todo CC BY/BY-SA tem autor (JVM)
  - itens pendentes e licença CC BY-SA no manifesto (JVM)
  - sincronização da biblioteca real com 350+ sons, remover som embutido, não voltar, restaurar (emulador)
  - sons do Commons e do OpenGameArt tocam no motor (emulador)
  - importar arquivo para um meme pendente: nome e categoria certos, sai da lista de pendentes, aparece na pesquisa (emulador)
- Durante o trabalho, o teste de JVM encontrou 1 som CC BY-SA sem autor ("Cuíca"); corrigido creditando quem enviou o arquivo ao Commons.

Legenda: ✅ sim · ❌ não · ⏳ aguardando · — não se aplica

**Implementado** = código escrito · **Compilado** = passou no GitHub Actions ·
**Testado** = testes automáticos passaram · **No C71** = verificado por você no aparelho

## Build 24 (ETAPA 2) — commit bc3475e

- Compilação: ✅
- Testes de JVM: ✅ **52/52**
- Testes instrumentados em emulador Android (API 34, x86_64): ✅ **20/20**

| Requisito | Implementado | Compilado | Testado | No C71 |
|---|---|---|---|---|
| Tocar som curto (SoundPool) | ✅ | ✅ | ✅ emulador | ⏳ |
| Tocar som longo (MediaPlayer) | ✅ | ✅ | ✅ emulador | ⏳ |
| Pausar / continuar / parar | ✅ | ✅ | ✅ emulador | ⏳ |
| Parar todos | ✅ | ✅ | ✅ emulador | ⏳ |
| Sem duplicação ao tocar de novo | ✅ | ✅ | ✅ emulador | ⏳ |
| Volume geral (inclusive em som tocando) | ✅ | ✅ | ✅ emulador | ⏳ |
| Repetição | ✅ | ✅ | ✅ emulador | ⏳ |
| Limite de sons simultâneos | ✅ | ✅ | ✅ emulador + JVM | ⏳ |
| Recuperação após arquivo quebrado | ✅ | ✅ | ✅ emulador | ⏳ |
| Memória: cache de no máx. 40 amostras | ✅ | ✅ | ✅ emulador (80 sons) + JVM (350) | ⏳ |
| Importar via seletor do Android (SAF) | ✅ | ✅ | ⏳ parcial¹ | ⏳ |
| Validar arquivo (falso .mp3, vazio, .txt) | ✅ | ✅ | ✅ emulador | ⏳ |
| Duração e tamanho dos importados | ✅ | ✅ | ✅ emulador | ⏳ |
| Duplicados | ✅ | ✅ | ✅ emulador | ⏳ |
| Categoria automática pelo nome | ✅ | ✅ | ✅ JVM + emulador | ⏳ |
| Persistência após fechar (banco + cópia) | ✅ | ✅ | ✅ emulador² | ⏳ |
| Importado toca pelo motor | ✅ | ✅ | ✅ emulador | ⏳ |
| Excluir apaga a cópia, não o original | ✅ | ✅ | ✅ emulador | ⏳ |
| Favoritos | ✅ | ✅ | ✅ JVM + emulador | ⏳ |
| Pesquisa sem acento | ✅ | ✅ | ✅ JVM + emulador | ⏳ |
| 241 sons embutidos válidos | ✅ | ✅ | ✅ JVM³ | ⏳ ouvir |
| Microfone do Roblox | ❌ **não suportado** | — | — | — |

1. O teste importa por `file://` com o mesmo código. A tela do seletor do sistema (toque do usuário) não é automatizada.
2. Fecha o banco, apaga o arquivo original, reabre: os sons continuam lá e tocam.
3. Manifesto válido, cada arquivo existe, tem mais de 1 KB, duração e licença ORIGINAL. Todos decodificam no ffmpeg.
   **Ninguém ouviu os sons ainda.**

## O que os testes NÃO cobrem

- Latência real no Realme C71 (emulador não mede isso de forma confiável).
- Tocar com o Roblox aberto ao mesmo tempo.
- Qualidade sonora dos efeitos sintetizados.
- Bateria.

## Checklist para você no Realme C71 (ETAPA 2)

1. Biblioteca mostra 241 sons? Configurações → "Sons válidos no manifesto: 241", "rejeitadas: 0"?
2. Toque em "Bonk", "Moeda", "Boing": o som sai na hora ou tem atraso perceptível?
3. Toque num som e toque de novo: pausa? De novo: continua? O botão quadrado para?
4. Toque 5 sons rápido: tocam juntos? Em Configurações, ponha "Sons ao mesmo tempo: 2" e repita.
5. Arraste a barra de volume com um som longo tocando ("Abertura épica"): o volume muda?
6. Repetição (ícone de setas) + um som curto: fica repetindo? Desligue: para no fim?
7. Meus áudios → Importar: escolha 2–3 MP3/WAV seus. Aparecem com duração e tamanho? Na categoria certa?
8. Feche o app (tire dos recentes), abra de novo: os importados continuam lá e tocam?
9. Abra o Roblox, volte e toque sons: o som do jogo continua normal?
10. Quais sons ficaram ruins ou estranhos? (lista por nome)

# Relatório de testes e status

Legenda: ✅ sim · ❌ não · ⏳ aguardando · — não se aplica ainda

**Implementado** = código escrito · **Compilado** = passou no GitHub Actions ·
**Testado** = testes automáticos passaram · **No C71** = verificado por você no aparelho

## ETAPA 1 — Estrutura e arquitetura

Build 21 do GitHub Actions: compilação ✅, 35 testes unitários ✅ (com `org.json` real), testes instrumentados compilados ✅.

| Item | Implementado | Compilado | Testado | No C71 |
|---|---|---|---|---|
| Projeto Gradle, Hilt, Compose, tema | ✅ | ✅ | — | ⏳ |
| Banco Room (sons, categorias) | ✅ | ✅ | ⏳ instrumentado¹ | ⏳ |
| DataStore (configurações) | ✅ | ✅ | ✅ regras de limite² | — |
| Manifesto + validação | ✅ | ✅ | ✅ parser³ | ⏳ |
| Navegação com 7 telas + detalhe | ✅ | ✅ | — | ⏳ |
| Favoritos | ✅ | ✅ | ✅ regra² · ⏳ banco¹ | ⏳ |
| Pesquisa sem acento | ✅ | ✅ | ✅ normalização² · ⏳ banco¹ | ⏳ |
| Criar/excluir categoria | ✅ | ✅ | ✅ regra² · ⏳ banco¹ | ⏳ |
| Renomear/excluir som importado | ✅ | ✅ | ✅ regra² | — (sem importação ainda) |

1. `DatabaseTest` (8 testes) é compilado no CI, mas só roda com `connectedDebugAndroidTest` em aparelho ou emulador.
2. 24 testes de JVM **executados e aprovados** durante o desenvolvimento (compilador Kotlin 2.0.21 + JUnit 4.13.2):
   TextNormalizer, Formatters, SupportedFormats, AppSettings, NameRules e os casos de uso
   de favorito, pesquisa, criar categoria e renomear.
3. `SoundManifestParserTest` (11 testes) **executado e aprovado** com um substituto temporário do `org.json`
   (a biblioteca real não estava disponível no ambiente de desenvolvimento). No GitHub Actions roda com o `org.json` real.
   Inclui a verificação do `sounds.json` real: 9 categorias, meta total 350.

## Pendente (etapas futuras)

| Requisito do projeto | Etapa | Status |
|---|---|---|
| Reprodução, pausa, parar, repetição, fila | 3 | ❌ não implementado |
| Controle de volume e sons simultâneos | 3 | ❌ (configuração salva, sem efeito ainda) |
| Recuperação após interrupção (ligação, outro app) | 3 | ❌ |
| Importação MP3/WAV/OGG/M4A/AAC | 5 | ❌ |
| Detecção de arquivo inválido, duração, tamanho | 5 | ❌ |
| Painel flutuante | 6 | ❌ |
| Transmissão pelo microfone do Roblox | 7 | ❌ **não suportado** — ver LIMITACOES.md |
| Consumo de memória | 8 | ❌ |

## Checklist para você no Realme C71 (ETAPA 1)

1. Instalar o APK de Releases e abrir. O app abre em até ~2 s?
2. Início mostra 0 sons e 9 categorias?
3. Categorias: as 9 padrão aparecem com "0 / N sons"? Criar "Teste", aparece em "Minhas categorias"?
4. Criar "teste" de novo → deve recusar (nome repetido).
5. Excluir "Teste" → some da lista.
6. Configurações → "Sons válidos no manifesto: 0", "Entradas rejeitadas: 0".
7. Girar a tela e trocar de aba várias vezes: nada trava ou fecha?

Responda com o resultado de cada item; a coluna "No C71" só é marcada a partir disso.

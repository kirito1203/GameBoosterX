# MEME SOUNDBOARD X

Soundboard Android para sessões de jogo, otimizado para o Realme C71.
Kotlin · Jetpack Compose · Material 3 · Room · DataStore · Hilt. Sem root.

> **Limitação importante:** o app **não** transmite sons pelo microfone do Roblox.
> O Android não permite isso sem root. Detalhes em [docs/LIMITACOES.md](docs/LIMITACOES.md).

## Status

**ETAPA 1 — Estrutura e arquitetura** (versão 0.1.0). Ainda não toca sons.
Veja o que está implementado, compilado e testado em [docs/TESTES.md](docs/TESTES.md).

## Como compilar só pelo celular (GitHub Actions)

### Primeira vez

1. No site do GitHub, crie um repositório novo chamado `MemeSoundboardX` (pode ser privado),
   marcando **Add a README**.
2. Crie os dois workflows: **Add file → Create new file**.
   - Nome: `.github/workflows/unpack.yml` → cole o conteúdo de `unpack.yml` → **Commit**.
   - Nome: `.github/workflows/build.yml` → cole o conteúdo de `build.yml` → **Commit**.
3. **Add file → Upload files** → envie `MemeSoundboardX-etapa1.zip` → **Commit**.
4. Aba **Actions**: o "Desempacotar ZIP" roda primeiro, depois o "Build APK" (~6–10 min).
5. Aba **Releases** (página inicial do repositório, lado direito) → baixe `MemeSoundboardX-buildN.apk`.
6. Instale no celular (permita "instalar apps desconhecidos" para o navegador, se pedir).

### Próximas etapas

Cada etapa vem num zip novo: envie pelo **Upload files** e espere o APK novo em Releases.
O APK é assinado sempre com a mesma chave (`debug-ci.keystore`), então instala por cima
da versão anterior sem perder favoritos.

### Se o build falhar

Abra **Actions → Build APK → (execução com X vermelho)**, toque no passo que falhou,
copie as últimas ~40 linhas do log e me envie.

## Documentação

- [docs/ARQUITETURA.md](docs/ARQUITETURA.md) — camadas, banco, motor de áudio planejado
- [docs/LIMITACOES.md](docs/LIMITACOES.md) — o que não é possível e por quê
- [docs/TESTES.md](docs/TESTES.md) — status honesto de cada item
- [app/src/main/assets/sounds/LEIA-ME.txt](app/src/main/assets/sounds/LEIA-ME.txt) — como adicionar sons embutidos

## Etapas

1. ✅ Estrutura e arquitetura
2. Interface completa
3. Motor de áudio
4. Biblioteca de efeitos
5. Importação de áudios
6. Botão flutuante
7. Investigação da integração com Roblox
8. Otimização
9. Testes e correções
10. APK final

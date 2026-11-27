# RPG Dice — APK

Rolador de dados de RPG em estética dark fantasy, empacotado como app Android.
O site inteiro vai dentro do APK: **funciona offline**.

Feito por **Link creator**.

---

## Como gerar o APK

Este app vive no branch `rpg-dice`. Qualquer push nele dispara o build,
e o APK sai em **Releases** com a tag `rpg-dice-<número>`.

Para gerar sem alterar nada: aba **Actions** → *Build APK — RPG Dice* → **Run workflow**.

Na hora de instalar, o Android vai pedir para permitir "instalar apps desconhecidos"
para o navegador ou gerenciador de arquivos que você usou. É normal para APK fora da Play Store.

---

## Detalhes

| | |
|---|---|
| Nome do pacote | `com.linkcreator.rpgdice` |
| Android mínimo | 7.0 (API 24) |
| Compilado para | API 35 |
| Tamanho | ~1,5 MB |
| Permissões | só `INTERNET`, e apenas para baixar as fontes decorativas |

**Assinatura.** Não há chave no repositório. O workflow guarda a chave de debug no
cache do Actions, então toda build sai com a mesma assinatura e a versão nova instala
**por cima** da antiga sem desinstalar — mesmo esquema do branch `main`.

**Offline.** As fontes Cinzel vêm do Google Fonts quando há rede; sem internet o app
cai para a serifada do sistema e continua funcionando igual.

**Onde ficam os dados.** Histórico, skin e ajustes ficam no armazenamento local do
WebView — sobrevivem a fechar o app e a atualizar de versão.

---

## Mexer no app depois

Todo o app é **um arquivo só**: `app/src/main/assets/index.html`.
Edite ele, envie a alteração, e o GitHub gera um APK novo automaticamente.

Para mudar o número da versão, altere `versionCode` e `versionName` em `app/build.gradle`.

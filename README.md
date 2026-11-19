# Pulso · Capture agora. Organize depois.

> **Projeto inicial Android (v0.1.0).** Nome e identificador `dev.pulso.app` são provisórios.
> O código-fonte foi gerado, mas **não houve compilação Android neste ambiente** (sem Android SDK/Gradle instalado). A primeira build deve ser validada em Android Studio/CI antes de instalar o APK.

Aplicativo Android nativo em **Kotlin + Jetpack Compose + Material 3**. Prioriza captura em uma **Caixa de Entrada separada das tarefas**, armazenamento local, acesso rápido por widget e evolução segura por agentes de IA. Primeiro dispositivo-alvo: Moto G84 5G.

## O que existe no código

- Caixa de entrada com captura rápida e arquivamento; `CaptureActivity` abre o campo direto.
- Organização de uma anotação em tarefa (transação Room preserva o histórico da anotação).
- Criação, edição, conclusão e exclusão lógica de tarefas.
- Categorias livres, prioridades, descrição, prazo opcional (09h na data selecionada).
- Telas Hoje, Caixa de Entrada, Tarefas (ativas, concluídas, atrasadas + busca) e Ajustes.
- Room/SQLite offline-first e IDs UUID; timestamps UTC em epoch milliseconds.
- Widget Glance: até três tarefas, concluir pelo widget, acesso à captura por `+`.
- Lembretes locais aproximados via WorkManager; requer permissão de notificações no Android 13+.
- Tema claro/escuro armazenado em DataStore; esquema dinâmico Material You.
- Exportação/importação JSON sem servidor: importação **aditiva**, UUIDs repetidos são ignorados.
- Testes unitários de validação e vencimento; workflow de CI e check do repositório.

## Ainda NÃO está implementado

Sincronização em nuvem, conta/login, app web, IA, compromissos, hábitos, recorrências, anexos, integração com calendário, links como campo próprio, widget em diferentes tamanhos, captura por voz, lembretes com horário configurável/exato, revisão semanal e lembretes de itens restaurados por importação. Veja [roadmap](docs/ROADMAP.md).

## Preparação (Windows / Android Studio)

1. Instale **JDK 17 ou 21** (ou use o JBR 21 do Android Studio; aponte `JAVA_HOME` para ele se `java` não estiver no PATH), Android SDK Platform **36** e Android SDK Build Tools compatíveis no **Android Studio**.
2. Extraia o ZIP e, em um PowerShell aberto na raiz, execute `.\gradlew.bat --version` para baixar o jar do wrapper Gradle com checksum validado.
3. Abra a pasta no Android Studio e aguarde **Sync Project with Gradle Files**. O Gradle 9.6.0 e dependências são baixados na primeira execução.
4. Em terminal PowerShell na raiz:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

5. Conecte o Moto G84 em depuração USB, ou use um emulador. Instale:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Depois, na tela inicial Android: pressione por alguns segundos -> **Widgets** -> **Pulso** -> adicione o widget. No app, em **Ajustes**, conceda permissão de notificações para receber lembretes.

Para macOS/Linux: `./scripts/check.sh` (ou `./gradlew ...`).

### Segurança do wrapper

A distribuição Gradle é obtida de `services.gradle.org`; o jar wrapper vem da tag `v9.6.0` do repositório oficial `gradle/gradle` no GitHub (o `services.gradle.org` publica só o checksum dele); os hashes ficam em `gradlew`/`gradlew.bat` e `gradle/wrapper/gradle-wrapper.properties`. O jar é baixado no primeiro uso em vez de ser incluído no ZIP. Não substitua URLs/checksums sem verificar as [referências oficiais](https://gradle.org/release-checksums/).

## Mapa do repo

```text
app/src/main/java/dev/pulso/app/
  domain/       # modelos e regras sem dependências de Android
  data/         # Room, repositório, backup, preferências
  ui/           # Compose, ViewModel, organização da entrada
  widget/       # Glance e callbacks de interação
  reminders/    # WorkManager e notificações
app/src/test/    # testes unitários
AGENTS.md        # instruções canônicas para agentes
MEMORY.md        # estado do projeto, não memória pessoal
CLAUDE.md        # ponteiro para AGENTS.md
.github/         # CI e templates
 docs/           # arquitetura, design, specs, decisões, testes e roadmap
```

## Guia para agentes de IA

**Comece por `AGENTS.md`, depois `MEMORY.md` e pela spec pertinente em `docs/specs/`.** Não implemente itens futuros só porque aparecem em um documento. Ao terminar uma mudança, atualize os documentos de estado quando necessário e registre os comandos realmente executados. Detalhes em [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Privacidade

Dados locais; não há API remota, analytics nem SDK de IA. O export JSON contém os dados em texto puro e deve ser armazenado em local seguro. O Android Auto Backup está desativado nesta versão para evitar prometer sincronização automática que não foi implementada.

**Captura por voz (opcional):** o botão "Ditar anotação" usa o reconhecedor de fala do Android (`RecognizerIntent`); o Pulso não pede `RECORD_AUDIO`, não declara `INTERNET`, não grava áudio e recebe só o texto, que vai ao campo para revisão. Dependendo do provedor do aparelho, o áudio pode ser processado online. O aviso aparece uma vez e fica em Ajustes → Privacidade. Sem reconhecedor no aparelho, o botão não aparece.

## Referências

- [Arquitetura Android offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Jetpack Compose](https://developer.android.com/develop/ui/compose)
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance)
- [Room](https://developer.android.com/training/data-storage/room)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)

# Plano de testes

## Gates automatizados

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

**Status (2026-11-19):** executados localmente no Windows com JBR 21: 15 testes unitários, lint e APK debug passando. CI executado no GitHub Actions em 2026-11-19 (push de `main` 0f4dc0f e tag `v0.3.0`): "Android checks" (`verify` e `instrumented`) e "Release" passaram.

## Testes instrumentados (emulador)

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.pulso.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use `am instrument` num aparelho/emulador com dados que importam: `connectedDebugAndroidTest` desinstala o app ao final e apaga os dados. No CI (emulador limpo) o `connectedDebugAndroidTest` é usado.

**Status (2026-11-19):** 12 testes passando no emulador API 37 e no Moto G84 5G (Android 15, via `am instrument`) (`LocalTaskRepositoryTest`, `MigrationTest`, `BackupServiceTest`). Também verificado: instalar a v2 sobre um banco v1 real migra sem perda e preserva o tema escolhido.

### Correção de check-ins (2026-11-19, emulador API 37, `am instrument`)

- Unitários: 80 passando; `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` OK (JBR da instalação do Android Studio, versão 25; `ANDROID_HOME` apontando para o SDK).
- Instrumentados: **27 passando** (`BackupServiceTest` 10, `HabitCheckinDaoTest` 5, `LocalTaskRepositoryTest` 7, `MigrationTest` 5). `HabitCheckinDaoTest`: tombstone + restauração reaproveitam a mesma linha e UUID; insert de outro UUID na mesma (habitId, date) lança `SQLiteConstraintException` e preserva a linha (inclusive tombstone); `observeBetween` com limites inclusivos e sem tombstones; `findByHabitAndDate` encontra tombstone.
- Schema não mudou (v3); migração e backup rodados como regressão.
- **NÃO EXECUTADO:** Moto G84 (não usado nesta etapa).

### WP6 / subtarefas, schema v4 (2026-11-20) — NÃO EXECUTADO

Executado pelo dono no emulador API 37 em 2026-11-20: **76/78**. `MigrationTest` (incl. 3→4) e `SubtaskRepositoryTest` passaram. As 2 falhas de `BackupServiceTest` eram testes desatualizados (versão 3 esperada no export; versão 4 usada como "mais nova"); corrigidas para 4 e 5, teste renomeado para `v4RoundTrip...`. Reexecução desses 2 testes e do caso de série viva (`liveSubtaskThatStillRepeatsIsRejectedButEndedRepetitionIsKept`) pendente. Moto G84 ainda não testado com v4.

### WP0 / schema v3 (2026-11-19, emulador API 37, `am instrument`)

- Unitários: 22 passando (inclui `DaysOfWeekMaskTest`). Lint e `assembleDebug` OK (JBR 21).
- Instrumentados: 22 passando (`BackupServiceTest` 10, `LocalTaskRepositoryTest` 7, `MigrationTest` 5): migração 2→3 com dados de borda, 1→3 encadeada, todas as versões exportadas até a atual com `ALL_MIGRATIONS`, índice único de check-in; backup v3 ida e volta/idempotente, fixtures v1/v2/v3 (`app/src/androidTest/assets/backups/`), versão 4 e referência ausente rejeitadas sem escrever, check-in duplicado ignorado.
- Migração real: APK v2 (HEAD) instalado, banco populado, APK v3 por cima; `user_version=3`, linhas/tombstone preservados.
- **NÃO EXECUTADO:** WP0 no Moto G84; exportar backup de um APK v2 real e importar na v3.

## Manual no Moto G84 5G

1. Instalar APK debug e abrir sem internet. Capturar "Comprar ração"; fechar e reabrir; a anotação deve persistir.
2. Organizar em tarefa, selecionar categoria "Compras", prioridade alta e data; confirmar que a anotação sai da Inbox sem criar duas tarefas.
3. Editar tarefa; concluir; reabrir. Confirmar que filtros e Hoje refletem estados.
4. Adicionar widget ao launcher, testar `+` e concluir tarefa tocando uma linha. Reiniciar app e verificar dados.
5. Em Android 13+, permitir notificações via Ajustes; programar tarefa próxima e verificar lembrete *aproximado*. Repetir com permissão negada.
6. Exportar JSON; instalar em ambiente separado/emulador; importar e confirmar dados. Importar novamente sem duplicar.
7. Usar telefone offline, modo escuro, texto grande, rotação de tela, TalkBack, teclado de terceiros e economia de bateria.
8. Validar alterações de fuso horário, prazo vencido e DST histórico em testes de regra.

### Resultado no Moto G84 5G (2026-11-19, Android 15, APK debug instalado limpo)

- [x] Telas Hoje, Tarefas, Ajustes e editor (bottom sheet) renderizam em 1080x2400, densidade 340, tema escuro.
- [x] Permissão: estado "ainda não permitidas" → diálogo do sistema → negar mostra "bloqueadas" + "Abrir ajustes" (abre as notificações do Pulso) → ativar lá e voltar mostra "permitidas".
- [x] Lembrete chegou no horário do prazo (3 casos, atraso de 2–10 s), com ações Concluir / Adiar 1h / Amanhã.
- [x] Ações conferidas no banco: Concluir grava `completedAt`; Adiar 1h e Amanhã gravam `snoozedUntil` (+1h; amanhã 09:00) sem mudar o prazo; a notificação some.
- [x] Job forçado antes do horário não notifica (proteção contra trabalho obsoleto).
- [x] Editar o prazo registra histórico (2 casos) e limpa o adiamento; o editor mostra o aviso de lembrete adiado.
- [x] Corrigido após o teste: a notificação dizia "Atrasada" mesmo no horário exato; agora mostra "Vence agora · HH:mm" até 15 min após o prazo (`TaskRulesTest`).
- Observação: com 2+ lembretes o Android agrupa as notificações; tocar no grupo abre o app e dispensa todas (comportamento do sistema).
- [x] Widget no launcher Motorola, tamanho grande: cabeçalho, contagem, até 6 tarefas, "Tudo em dia" quando vazio; título abre o app; círculo conclui e o widget atualiza em até 2 s, inclusive com o processo do app encerrado (`am kill`).
- [x] Corrigido após o teste: concluir pelo widget não atualizava a lista (o Glance reaproveitava a primeira leitura enquanto a sessão estava aberta); agora o widget coleta o Flow do repositório dentro do `provideContent`. Também corrigido o plural "1 pendentes".
- [x] Captura rápida pelo `+` do widget salva na Entrada e volta para a tela inicial. Corrigido após o teste: o teclado não abria sozinho (o foco era pedido antes do campo existir dentro do `AnimatedVisibility`).
- Observação: logo após reinstalar o APK, o primeiro toque no widget pode abrir o app em vez da ação, até o widget ser redesenhado.
- [x] Widget mais baixo (3x2 no launcher Motorola, ~368x155 dp): a versão com número fixo de linhas por tamanho cortava a segunda tarefa. O launcher informa ao app uma altura menor que a real (113 dp), então calcular linhas pela altura não funciona; a lista virou `LazyColumn` rolável, que ocupa o espaço que sobrar sem cortar linhas fixas. Verificado: rolar, concluir dentro da lista e tocar no título para abrir o app.
- [x] Instalar o build com banco v3 (M1.5 em andamento) sobre o v2 do aparelho migrou sem perda (7 tarefas, 3 reagendamentos).
- [x] Widget estreito (2 colunas, ~245 dp): contagem + `+`; `+` abre a captura com teclado; tocar na contagem abre o app. Ajuste após o teste: o card inteiro abre o app (antes só o número, alvo pequeno); conferido no aparelho (toque no canto do card abre o app, `+` segue abrindo a captura).
- [x] Atalho "Capturar" do ícone (confirmado pelo usuário).
- [~] TalkBack (auditoria da árvore de acessibilidade via `uiautomator` com TalkBack ativo; navegação por gestos e anúncios falados não verificados): Hoje, Entrada, Tarefas, Ajustes, busca, editor, captura e widget. Corrigido: botão flutuante "Capturar"/"Nova tarefa" sem rótulo (o texto interno do `ExtendedFloatingActionButton` não chega à acessibilidade); interruptor "Cores do papel de parede" sem rótulo (linha inteira agora é `toggleable`); botão de horário anunciado só como "09:00"; chip "Nova" → "Nova categoria"; captura tinha dois "botões" do tamanho da tela (fundo e painel) — trocados por `pointerInput`; cabeçalho do widget com alvo de 34 dp → linha inteira de 48 dp; título da tarefa no widget com alvo de 16 dp → linha inteira de 48 dp abre o app (círculo segue concluindo). Reauditado no aparelho: nenhum elemento sem rótulo nem alvo menor que 48 dp nas telas auditadas.
- [x] Fonte grande (200%, máximo do Android 15): Hoje, Entrada, Tarefas, Ajustes, editor, captura e widget sem cortes nem sobreposição; títulos quebram linha, filtros rolam na horizontal, lista tem espaço livre sob o botão flutuante, widget usa reticências no título. Observação: os três botões de tema em Ajustes ficam no limite da largura.

## Captura por voz (SPEC 011 / WP4) — 2026-11-19

- [x] Unitários: `VoiceCaptureTest` (anexar ditado ao campo, limite de 500); 80 testes no total passando com build e lint.
- [x] Merged manifest do debug: sem `RECORD_AUDIO` e sem `INTERNET` (só `POST_NOTIFICATIONS` do app; `ACCESS_NETWORK_STATE`/`WAKE_LOCK`/`RECEIVE_BOOT_COMPLETED`/`FOREGROUND_SERVICE` vêm do WorkManager); `<queries>` de `RECOGNIZE_SPEECH` presente.
- [x] Emulador API 37 com um handler de `RECOGNIZE_SPEECH` (o app de TTS do Google): microfone aparece na captura rápida; 1º toque mostra o aviso (Continuar/Cancelar); Continuar abre o reconhecedor; voltar (cancelar) não mostra mensagem nem altera o campo.
- [x] Mesmo emulador com esse pacote desabilitado: o microfone some da captura.
- NÃO EXECUTADO: fluxo "fala → texto no campo → Salvar" (o emulador não tem reconhecedor real; só a lógica de anexar é coberta por teste unitário); atalho "Capturar por voz" (a `CaptureActivity` não é exportada, então `am start` é negado e o atalho do launcher não foi acionado); 2ª abertura sem aviso após Continuar (flag no DataStore não conferida na tela); campo da Entrada, Ajustes → Privacidade e TalkBack por tela/leitor.
- NÃO TESTADO: Moto G84 (pt-BR, modo avião) — documentar o comportamento offline do reconhecedor do aparelho.

## Lacunas conhecidas

- Widget sem teste automatizado; estreito, grande e baixo (lista rolável) verificados no Moto G84.
- Ações da notificação (Concluir/Adiar) sem teste automatizado; verificadas manualmente no Moto G84.
- Sem testes de UI Compose; TalkBack auditado pela árvore de acessibilidade (sem navegação por gestos); fonte grande (200%) revisada no Moto G84.
- Moto G84: falta navegar com gestos do TalkBack (ouvindo os anúncios).

## Checklist de regressão para features de sync futuras

Duplicados por retries, conflitos simultâneos, tombstones, merges de relógios divergentes, dados offline por semanas, quotas e autenticação expirada.

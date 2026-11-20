# MEMORY.md — contexto durável do projeto (não memória pessoal)

> Atualizado em 2026-11-20. Use para retomar sessões de IA. **Não é evidência de teste.** Atualize após mudanças concretas.

## Visão confirmada

- Nome provisório: Pulso; aplicativo Android nativo Kotlin/Compose.
- Dispositivo-alvo inicial: Moto G84 5G.
- Fluxo prioritário: capturar anotação sem metadados -> Inbox -> organizar depois.
- MVP offline-first, sem login/serviço remoto; widget Android e notificações locais.
- Futuro: sincronização em nuvem para acesso pelo PC via API, possível ASP.NET Core; organização por IA com prévia e confirmação.

## Implementação presente no código

- Room **v3** (`Migration1To2`, `Migration2To3` aditivas): `InboxEntity`, `TaskEntity` (+`links`, `snoozedUntil`, `seriesId`), `CategoryEntity`, `RescheduleEntity`, e (WP0, só persistência, sem regras/UI) `SeriesEntity`, `HabitEntity`, `HabitCheckinEntity`, `ChecklistItemEntity` com DAOs básicos. Schemas v1–v3 em `app/schemas`; v3 congelado após merge (mudança = v4).
- Separação `domain/data/ui/widget/reminders`; repository e DI manual (`AppContainer`).
- Compose: Hoje, Entrada, Tarefas, Ajustes + sobreposições Buscar e Revisão semanal; captura rápida translúcida (`CaptureActivity`).
- M1 implementado (SPEC 007): nova tarefa direta, links, categorias gerenciáveis, prazo com hora, adiar lembrete, estados de permissão, busca global, revisão semanal com histórico de reagendamento, widget responsivo.
- Backup JSON v3 (lê v1 e v2), aditivo, valida enums/limites/referências antes de escrever; rejeita versão mais nova com mensagem própria.
- Identidade visual "Brasa & Tinta" aplicada (ver `docs/DESIGN.md`): tema/motion em `ui/theme/Theme.kt`, componentes em `ui/components/Components.kt`, editor em bottom sheet, widget ordenado por `TaskRules.agenda`. Regra: mudanças visuais em `ui/` não alteram domain/data/reminders nem a API do ViewModel.

## Estado de validação

- **Build local: EXECUTADO** em 2026-11-19 (Windows, Gradle 9.6.0, JBR 21 do Android Studio; `java` do PATH é 8 e não serve): `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, 15 testes unitários.
- **Instrumentados: EXECUTADOS** no emulador API 37 via `am instrument`: 12 testes (repositório, migração 1→2, backup) passando.
- **Migração real:** APK v2 instalado sobre banco v1 do emulador sem perda.
- **Após o redesenho (2026-11-19):** build/lint/15 unitários e 12 instrumentados passando de novo; tela Hoje conferida por screenshot.
- **CI: EXECUTADO e passou** (2026-11-19, GitHub `github.com/carrijoga/pulso`, após push de `main` 0f4dc0f e da tag `v0.3.0`): "Android checks" (`verify` e `instrumented`) passou; "Release" passou e publicou o prerelease "Pulso 0.3.0" com `pulso-v0.3.0.apk` e `.sha256`.
- **Moto G84 (2026-11-19, Android 15): PARCIAL.** 12 instrumentados OK; telas, permissão (negada → concedida) e lembretes com ações Concluir/Adiar 1h/Amanhã validados (ver `docs/TESTING.md`). Widget (estreito, baixo, grande) e captura pelo widget validados. Build com banco v3 do M1.5 instalado no G84 em 13:27: migração v2→v3 real sem perda (7 tarefas, 3 reagendamentos). Widget usa lista rolável (`LazyColumn`): o launcher Motorola informa altura menor que a real, então não calcular linhas por altura. Atalho do ícone OK. TalkBack: árvore auditada e corrigida (rótulo no próprio FAB — o texto do ExtendedFAB não aparece para acessibilidade; nada de `clickable` decorativo em fundos/painéis). Fonte 200% OK em todas as telas e no widget. Não testado: navegação TalkBack por gestos. ATENÇÃO: o Pulso no G84 já tem dados reais do usuário — antes de instalar, comparar o identityHash do schema com `room_master_table` do aparelho e fazer backup via exportação JSON do app (Ajustes); nunca tocar em tarefas reais nos testes. Glance: dados do widget vêm de `collectAsState` dentro do `provideContent` (ler só antes deixava o widget desatualizado após ações). Para inspecionar o banco no aparelho: `run-as dev.pulso.app cat databases/pulso.db*` (não há `sqlite3` no aparelho); vale só para builds debug. Com o release instalado, `run-as` não funciona: use a exportação JSON do app.
- **Moto G84 (2026-11-20):** v0.3.0 de release (versionCode 300, assinada, não debuggable) instalada desde 2026-11-19 20:29; sem crashes do Pulso no log de crash. Telas de recorrência e hábitos ainda NÃO testadas manualmente no aparelho.

- **WP1a do M1.5 (2026-11-19):** `domain/RecurrenceRules.kt` (validate/nextDueAt/occurrencesBetween/describe) + `RecurrenceRulesTest`; `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, **48 testes unitários** (29 novos; contagem anterior neste arquivo, 15/22, estava defasada). Sem instrumentados rodados nesta etapa; sem commit. WP1 (repositório/UI da SPEC 008) não iniciado.
- **WP1 do M1.5 (2026-11-19, branch `m1.5/wp1-recorrencia`):** repositório (série, próxima ocorrência idempotente, "Desfazer", "Excluir só esta" e "Encerrar repetição"), editor "Repetir" com prévia, rótulo no `TaskCard`, `RecurrencePresets` (+6 unitários). `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, **95 testes unitários**, 0 falhas. Instrumentados: **EXECUTADOS** no emulador API 37 em 2026-11-19: 39/39 OK (inclui `RecurrenceRepositoryTest`, 12 testes da primeira rodada). Correções da revisão (2026-11-19, commit seguinte): `RecurrenceRepositoryTest` tem 15 testes; os 3 novos (desfazer de encerrar, encerrar recusado em concluída, repetição recusada em concluída sem série) **EXECUTADOS** no emulador API 37 (42/42 na branch; 56/56 após o merge em `main`). Não feito: ícone no widget, "Pular esta vez", validação manual em tela e no Moto G84. Sem migração (schema v3 intacto); `setCompleted` agora devolve a ocorrência criada.
- **WP2a do M1.5 (2026-11-19):** `domain/HabitRules.kt` (regras puras da SPEC 009; `dueToday` recebe `zone` extra, helpers `createdOn`/`weekStart`) + `HabitRulesTest` (28 testes); `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, **76 testes unitários** no total. Sem instrumentados; sem commit. WP2 (repositório/UI) não iniciado.
- **Correção de perda de dados em check-ins (2026-11-19, pré-WP2):** `HabitCheckinDao.upsert` (REPLACE) trocado por `insert` (ABORT) + `update`, com `findByHabitAndDate` e `observeBetween` (ativos, intervalo ISO inclusivo). REPLACE em índice único apagaria tombstones. Sem mudança de schema (continua v3). Unitários 80 passando; lint e assemble OK; instrumentados 27 passando no emulador API 37 (`am instrument`), incluindo `HabitCheckinDaoTest` (5 novos), `MigrationTest` e `BackupServiceTest`. Build feito com JBR do Android Studio (versão 25, não 21: é a única instalada). Commit na branch `m1.5/fix-habit-checkin-dao`; sem push/merge.
- **WP2 do M1.5 (2026-11-19, branch `m1.5/wp2-habitos`):** `data/HabitRepository.kt` (`LocalHabitRepository`, ligado no `AppContainer`), `ui/HabitViewModel.kt`, `ui/HabitScreens.kt` (seção "Hábitos" em Hoje, sobreposição "Hábitos" com criar/editar/reordenar/arquivar/excluir, entrada em Ajustes); `HabitRules.validateDraft`/`positionsAfterMove`; `HabitDao.activeInOrder()` (query nova, sem mudança de schema). **EXECUTADO (2026-11-19, JBR do Android Studio):** `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin` passando; **100 testes unitários** (`HabitDraftRulesTest` 11). **Instrumentados no emulador API 37 (reportado pelo coordenador): 39/39 OK**, incluindo `LocalHabitRepositoryTest` (12). 2 testes novos da revisão (`setCheckedTodayReadsTheClockOnEveryCall`, `setCheckedReportsWhetherAnythingChanged`) **EXECUTADOS** no emulador API 37 (41/41 na branch; 56/56 após o merge em `main`). **NÃO EXECUTADOS:** fluxo de UI no emulador, TalkBack e fonte grande. Lembretes de hábito (P2) não implementados.
- **Correções da revisão do WP2 (mesma branch, commit novo):** Hoje relê o dia no `ON_RESUME` e a marcação usa `setCheckedToday` (relógio no clique); `setChecked` devolve Boolean e a VM só mostra mensagem/Desfazer quando gravou; snackbar nova substitui a atual; texto de sequência em `HabitRules.streakText` ("dias seguidos" / "semanas seguidas").
- **Merge WP1 + WP2 em `main` (2026-11-19, v0.3.0):** revisados por agente revisor; achados corrigidos. Na árvore integrada: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, **106 testes unitários**; instrumentados **56/56** no emulador API 37 (`am instrument`). Telas de recorrência/hábitos não conferidas manualmente; Moto G84 NÃO testado.
- **WP0 do M1.5 (2026-11-19):** unitários 22 e instrumentados 22 passando no emulador API 37; migração real v2→v3 conferida (APK v2 → v3 sobre o mesmo banco). NÃO EXECUTADO no Moto G84; sem commit.

- **Ajuste de meta na semana de criação de hábitos (2026-11-19):** `HabitRules.weekProgress` recebe `createdOn` e aplica meta proporcional só na semana que contém a criação (SPEC 009, "Semana de criação"); sequência e visibilidade de `TIMES_PER_WEEK` usam a mesma regra. `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, **89 testes unitários** (`HabitRulesTest` 37). Sem instrumentados; sem push.
- **WP4 do M1.5 (2026-11-19):** captura por voz (SPEC 011) implementada: `ui/VoiceCapture.kt`, `<queries>` no manifesto (sem `RECORD_AUDIO`/`INTERNET`, conferido no merged manifest), botão "Ditar anotação" na `CaptureActivity` e no campo da Entrada (só com reconhecedor), atalho "Por voz" (`EXTRA_START_VOICE`), flag `voiceNoticeShown` no DataStore, seção Privacidade em Ajustes. Build/lint/80 unitários passando; emulador conferido parcialmente (ver `docs/TESTING.md`); fala real, atalho e Moto G84 NÃO testados; botão no widget pendente (P2). Sem commit.

## Pendências com maior prioridade

1. Moto G84: só falta navegação TalkBack por gestos (ouvir os anúncios).
2. ~~Publicar o repositório e rodar o CI.~~ Feito em 2026-11-19 (ver estado de validação).
3. TalkBack/fonte grande nas telas redesenhadas.
4. Decidir nome final.
5. M1.5: WP0, WP1, WP2 e WP4 implementados (WP1/WP2 em `main` e na v0.3.0; telas novas no G84 pendentes). Próximo: validar recorrência e hábitos no G84; WP3 (SPEC 010) e WP5 (SPEC 013) em paralelo. Decisões em `docs/PLANO-M1.5.md` §2.

## Não decidir sem solicitar

- Provedor de autenticação, hospedagem, custo de API, política de retenção, envio de dados para IA, compartilhamento entre usuários.

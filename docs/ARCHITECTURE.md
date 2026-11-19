# Arquitetura — offline-first

## Limites

- **UI** (`ui/`): Compose renderiza `PulsoUiState`, ViewModel coordena ações e converte erros para callbacks.
- **Domínio** (`domain/`): contratos puros `InboxItem`, `TaskItem`, `TaskDraft`, `TaskRules`.
- **Persistência** (`data/`): Room v3 (migrações explícitas 1→2 e 2→3) e `TaskRepository`, que publica `Flow<List<...>>`. Room é a única fonte de verdade.
- **Widget** (`widget/`): consulta `repository.observeTasks().first()` e mostra até três tarefas; usa callback no repositório para concluir.
- **Lembretes** (`reminders/`): `TaskChangeListener.taskChanged(taskId, reminderAt)` agenda/cancela WorkManager (`reminderAt` = adiamento ou prazo; `null` cancela) e atualiza o widget. O worker reconfere o horário antes de notificar. `ReminderActionReceiver` trata Concluir/Adiar da notificação.
- **Preferências**: DataStore para tema (Sistema/Claro/Escuro + cores do sistema), horário padrão do prazo, data da última revisão e se o pedido de notificação já foi exibido.
- **Backup**: `BackupService` (formato v3, lê v1 e v2) valida tudo antes de escrever e importa aditivamente numa única transação Room; sem sobrescrita por UUID.

## Fluxos principais

```mermaid
flowchart TD
    C[Captura rápida ou Inbox] --> V[ViewModel]
    V --> R[TaskRepository]
    R --> DB[(Room / SQLite)]
    DB --> F[Flow observado]
    F --> V
    V --> UI[Compose]
    C2[Organizar entrada] --> TX[Transação Room: Task + Inbox CONVERTED]
    TX --> DB
    R --> L[TaskChangeListener]
    L --> W[WorkManager + Widget Glance]
```

## Modelo de dados local v3

**InboxEntity:** `id` UUID, `text`, `status=INBOX|CONVERTED|ARCHIVED`, `createdAt`, `updatedAt`, `convertedTaskId`, `deletedAt`. Converter preserva entrada original e referência da tarefa.

**TaskEntity:** `id` UUID, `title`, `notes`, `category` (texto), `priority=LOW|NORMAL|HIGH`, `dueAt?`, `completedAt?`, `createdAt`, `updatedAt`, `deletedAt?`. Exclusão é lógica; reativação de tarefa concluída limpa `completedAt`. v2: `links` (texto, um por linha) e `snoozedUntil` (adia só o lembrete).

**CategoryEntity (v2):** `id`, `name`, timestamps, `deletedAt`. Tarefas guardam o *nome* da categoria; renomear atualiza tarefas na mesma transação.

**RescheduleEntity (v2):** histórico append-only `taskId`, `fromDueAt`, `toDueAt`, `changedAt`, indexado por `taskId`.

Datas persistidas em epoch milliseconds UTC; UI converte para fuso local. Prazo = data + hora escolhidas no fuso local (hora padrão configurável, 09:00 por omissão). `dueAt` é instante; **não** equivale a data sem hora.

## Schema v3 (WP0 do M1.5)

**Implementado (persistência apenas; sem regras, UI nem repositório das features):** `Migration2To3` aditiva, `schemas/3.json`, entidades/DAOs básicos e mapeamentos, tipos de domínio (`domain/Recurrence.kt`, `DaysOfWeekMask`) e backup formato v3.

- `task_items.seriesId` (TEXT NULL, índice `index_task_items_seriesId`).
- `task_series`: `frequency`, `repeatInterval`, `daysOfWeek` (bitmask seg=bit0), `basis`, `anchorDueAt`, timestamps, `deletedAt`.
- `habits`: `name`, `notes`, `scheduleType`, `daysOfWeek`, `timesPerWeek`, `reminderMinutes`, `position`, `archivedAt`, timestamps, `deletedAt`.
- `habit_checkins`: `habitId`, `date` (texto ISO, dia local), índice único (`habitId`, `date`); desmarcar = tombstone.
- `checklist_items`: `taskId`, `text`, `position`, `checkedAt`, timestamps, `deletedAt`.
- Sem `FOREIGN KEY` (ADR 0008); referências validadas na importação. Schema v3 congelado após o merge: mudanças viram v4.

Os campos `TaskItem.recurrence`/`TaskDraft.recurrence` existem só com valor padrão `null`; o repositório ainda não os preenche (WP1). Ver [SPEC 012](specs/012-schema-v3-backup-e-migracoes.md), [ADR 0008](adr/0008-schema-v3-consolidado.md) e [`PLANO-M1.5.md`](PLANO-M1.5.md).

## Extensibilidade para nuvem

Room continua SSOT, nunca leitura direta da rede para tela. Em V2 adicionar `RemoteDataSource` + `SyncCoordinator` + outbox persistente, paginação, idempotência, versionamento servidor e tombstones. Não usar somente `updatedAt` como resolução de conflitos. Autenticação e migração de registros anônimos precisarão de design específico. A API ASP.NET Core e UI desktop são projetos futuros separados. Ver [ADR 0003](adr/0003-sync-future.md).

## Fronteiras intencionais

- Uma única feature não justifica multi-modularização/DDD/CQRS/MediatR no cliente.
- `TaskChangeListener` é uma porta; o listener Android implementa integração de widgets/notificações.
- Não adicionar `allowMainThreadQueries`, `fallbackToDestructiveMigration` ou `GlobalScope`.
- Mudança de banco exige migração e preservação de dados existentes.

## Pontos a reforçar

Schemas exportados em `app/schemas` (v1, v2 e v3) são versionados e alimentam `MigrationTestHelper`. Importação reagenda lembretes das tarefas novas. Widget ainda sem teste automatizado. Specs e MEMORY especificam status real.

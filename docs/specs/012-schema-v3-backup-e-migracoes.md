# SPEC 012 — Schema v3, backup v3 e migrações (M1.5, WP0)

**Status:** Implementado (WP0) e validado no emulador API 37 em 2026-11-19; falta conferir no Moto G84. Decisões de escopo confirmadas (`docs/PLANO-M1.5.md` §2). ADR: [0008](../adr/0008-schema-v3-consolidado.md).

## Objetivo

Criar de uma vez a base de dados das features do M1.5 (recorrência, hábitos, checklist), com migração aditiva 2→3, backup formato v3 que continua lendo v1/v2, e uma bateria de testes de migração mais completa. Este pacote **não** implementa regras de negócio nem UI das features — só persistência, mapeamento, backup e testes.

## Estado atual (v2, verificado no código em 2026-11-19)

- `PulsoDatabase` versão 2, `ALL_MIGRATIONS = [Migration1To2]`, `exportSchema = true`, schemas em `app/schemas/dev.pulso.app.data.PulsoDatabase/{1,2}.json`.
- Tabelas: `inbox_items`, `task_items`, `categories`, `task_reschedules`.
- `BackupService`: `format = "pulso-backup"`, `CURRENT_VERSION = 2`, aceita `version in 1..CURRENT_VERSION`, valida tudo antes de escrever, `INSERT IGNORE` por UUID numa transação, reagenda lembretes das tarefas novas.

## Schema v3 (aditivo)

### `task_items` — coluna nova

| Coluna | Tipo | Regra |
|---|---|---|
| `seriesId` | `TEXT NULL` | UUID de `task_series`; `NULL` = tarefa avulsa. Índice `index_task_items_seriesId`. Sem FOREIGN KEY (tombstones e import aditivo; ver ADR 0008). |

### `task_series` (SPEC 008)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `TEXT NOT NULL` PK | UUID |
| `frequency` | `TEXT NOT NULL` | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `repeatInterval` | `INTEGER NOT NULL` | 1..99 (nome evita palavra genérica `interval` em SQL) |
| `daysOfWeek` | `INTEGER NOT NULL` | bitmask seg=bit0 … dom=bit6 (`DayOfWeek.value - 1`); obrigatório ≠0 em `WEEKLY`, `0` nas demais |
| `basis` | `TEXT NOT NULL` | `SCHEDULE` (calendário fixo) ou `COMPLETION` (a partir da conclusão) |
| `anchorDueAt` | `INTEGER NOT NULL` | instante (epoch ms UTC) da primeira ocorrência; define hora local e dia do mês/ano |
| `createdAt`, `updatedAt` | `INTEGER NOT NULL` | |
| `deletedAt` | `INTEGER NULL` | série encerrada |

### `habits` (SPEC 009)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `TEXT NOT NULL` PK | UUID |
| `name` | `TEXT NOT NULL` | 1..60 caracteres |
| `notes` | `TEXT NOT NULL DEFAULT ''` | |
| `scheduleType` | `TEXT NOT NULL` | `DAILY`, `DAYS_OF_WEEK`, `TIMES_PER_WEEK` |
| `daysOfWeek` | `INTEGER NOT NULL DEFAULT 0` | bitmask como acima; ≠0 só em `DAYS_OF_WEEK` |
| `timesPerWeek` | `INTEGER NOT NULL DEFAULT 0` | 1..7 só em `TIMES_PER_WEEK` |
| `reminderMinutes` | `INTEGER NULL` | minutos desde 00:00 local (P2; pode ficar sem uso) |
| `position` | `INTEGER NOT NULL DEFAULT 0` | ordem manual |
| `archivedAt` | `INTEGER NULL` | pausado/arquivado, some de Hoje mas mantém histórico |
| `createdAt`, `updatedAt` | `INTEGER NOT NULL` | |
| `deletedAt` | `INTEGER NULL` | |

### `habit_checkins` (SPEC 009)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `TEXT NOT NULL` PK | UUID |
| `habitId` | `TEXT NOT NULL` | índice |
| `date` | `TEXT NOT NULL` | data local ISO `yyyy-MM-dd` (dia do calendário do usuário, **não** instante; ver ADR 0006) |
| `createdAt`, `updatedAt` | `INTEGER NOT NULL` | |
| `deletedAt` | `INTEGER NULL` | desmarcar = tombstone; marcar de novo restaura a mesma linha |

Índice **único** `index_habit_checkins_habitId_date` em (`habitId`, `date`).

### `checklist_items` (SPEC 013)

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | `TEXT NOT NULL` PK | UUID |
| `taskId` | `TEXT NOT NULL` | índice |
| `text` | `TEXT NOT NULL` | 1..200 |
| `position` | `INTEGER NOT NULL` | |
| `checkedAt` | `INTEGER NULL` | |
| `createdAt`, `updatedAt` | `INTEGER NOT NULL` | |
| `deletedAt` | `INTEGER NULL` | |

## Migração `Migration2To3`

- Somente `ALTER TABLE ... ADD COLUMN`, `CREATE TABLE IF NOT EXISTS` e `CREATE INDEX IF NOT EXISTS`. Nenhuma linha existente alterada; nenhum dado semeado.
- O SQL precisa bater exatamente com o `3.json` exportado pelo Room (tipos, `NOT NULL`, defaults, nomes de índice). Gere a entidade, rode o build para exportar o schema e escreva/ajuste o SQL a partir dele; o `MigrationTest` valida.
- `PulsoDatabase.version = 3`, `ALL_MIGRATIONS = [Migration1To2, Migration2To3]`. Nunca `fallbackToDestructiveMigration`.
- Depois do merge em `main`, o schema v3 está congelado: qualquer mudança vira v4 com nova migração.

## Tipos de domínio e mapeamento (criar no WP0)

Em `domain/` (sem Android):

```kotlin
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class RecurrenceBasis { SCHEDULE, COMPLETION }
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<java.time.DayOfWeek> = emptySet(),
    val basis: RecurrenceBasis = RecurrenceBasis.SCHEDULE,
)
data class TaskSeries(val id: String, val rule: RecurrenceRule, val anchorDueAt: Long)

enum class HabitScheduleType { DAILY, DAYS_OF_WEEK, TIMES_PER_WEEK }
data class Habit(
    val id: String, val name: String, val notes: String,
    val scheduleType: HabitScheduleType,
    val daysOfWeek: Set<java.time.DayOfWeek>, val timesPerWeek: Int,
    val reminderMinutes: Int?, val position: Int, val archived: Boolean,
    val createdAt: Long, // sequência não conta dias anteriores à data local de criação
)
data class HabitCheckin(val id: String, val habitId: String, val date: java.time.LocalDate)

data class ChecklistItem(val id: String, val taskId: String, val text: String, val position: Int, val checked: Boolean)
```

`TaskItem` ganha `seriesId: String? = null` e `recurrence: RecurrenceRule? = null` (preenchido por JOIN/consulta no repositório). `TaskDraft` ganha `recurrence: RecurrenceRule? = null`. Valores padrão mantêm todo o código existente compilando.

Conversão do bitmask num helper puro (`DaysOfWeekMask.toMask(Set<DayOfWeek>)` / `fromMask(Int)`) testado em `app/src/test/`.

DAOs mínimos: `observe*`/`findById`/`allForBackup`/`upsert`/`importUnseen` por tabela, no mesmo padrão dos DAOs atuais. Queries específicas das features ficam para os WPs delas.

## Backup formato v3

- `CURRENT_VERSION = 3`; aceita `version in 1..3`. Versão maior: rejeitar com "Este backup foi criado por uma versão mais nova do Pulso. Atualize o app para importar." (mensagem distinta do formato desconhecido).
- Campos novos no topo: `exportedAt` (epoch ms), `appVersionName` (string, `BuildConfig.VERSION_NAME`), `schemaVersion` (3). São informativos; a importação não depende deles.
- Arrays novos: `series`, `habits`, `habitCheckins`, `checklistItems` (ausentes = vazio, para ler v1/v2). Tarefas ganham `seriesId` (`optString`/nulo em v1/v2).
- Nome sugerido do arquivo exportado: `pulso-backup-v3-AAAA-MM-DD.json`.

### Validação antes de escrever (tudo ou nada)

- Enums válidos; `repeatInterval` 1..99; bitmask 0..127 e coerente com o tipo; `timesPerWeek` 1..7 quando aplicável; `date` parseável como `LocalDate`; textos dentro dos limites.
- Referências: `task.seriesId`, `checkin.habitId` e `checklist.taskId` precisam existir **no arquivo ou no banco local**; senão rejeitar com "Backup inconsistente: referência ausente."
- Limites: mesmo `MAX_ROWS` por array; `habitCheckins` e `checklistItems` até `MAX_ROWS * 4`.

### Escrita

- Mesma transação para tudo; `INSERT IGNORE` por UUID. Para `habit_checkins`, o índice único (`habitId`,`date`) também faz ignorar o duplicado com outro UUID — o registro local prevalece.
- Resumo (`ImportSummary`) passa a contar hábitos também; mensagem em pt-BR.
- Reagendamento de lembretes das tarefas novas: inalterado.

## Testes obrigatórios

Instrumentados (`app/src/androidTest/`):

- [x] `MigrationTest`: 2→3 preserva todas as linhas v2 e valida o schema; 1→3 encadeado (1→2→3) preserva dados; um teste que percorre **todas** as versões exportadas até a atual usando `ALL_MIGRATIONS` (protege migrações futuras).
- [x] Migração com dados de borda: tarefa com `dueAt`/`snoozedUntil` nulos, links vazios, categoria com acento, tombstones, entrada `CONVERTED`.
- [x] `BackupServiceTest`: ida e volta v3 com séries, hábitos, check-ins e checklist; reimportação idempotente; importa arquivos v1 e v2 num banco v3; rejeita versão 4 com a mensagem específica sem escrever; rejeita referência ausente sem escrever; check-in duplicado (mesmo hábito/data, UUID diferente) não duplica.
- [x] Fixtures JSON v1/v2/v3 versionadas em `app/src/androidTest/assets/backups/` (sem dados pessoais reais).

Unitários (`app/src/test/`):

- [x] `DaysOfWeekMask` ida e volta e limites.

## Aceites

- [x] Banco v2 real (emulador) atualizado para v3 sem perda: APK v2 (HEAD) criou o banco, linhas inseridas, APK v3 instalado por cima; `user_version=3`, linhas e tombstone preservados, tabelas novas vazias. Tema/preferências (DataStore) não foram conferidos; não são tocados pela migração.
- [ ] Backup exportado na v2 (APK atual) importa na v3. Coberto só por fixture v2 escrita à mão e testes; falta exportar de um APK v2 real.
- [x] `ARCHITECTURE.md` (modelo de dados) e `006-backup-e-privacidade.md` atualizados para v3 com status real.
- [x] Gates de build/lint/unitários e instrumentados executados e registrados em `MEMORY.md`/`TESTING.md`.

## Fora de escopo

Regras de recorrência, cálculo de sequência de hábitos, UI. Criptografia do backup e backup automático (seguem pendentes na SPEC 006).

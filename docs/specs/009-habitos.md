# SPEC 009 — Hábitos (M1.5, WP2a + WP2)

**Status:** Planejado. Depende do WP0 (SPEC 012). ADR: [0006](../adr/0006-habitos-separados.md).

## Objetivo

Registrar rotinas que não são "trabalho a concluir" (beber água, ler 20 min, academia 3x/semana) com um toque por dia, ver o progresso da semana e uma sequência gentil — sem culpa, sem gamificação agressiva e sem misturar com a lista de tarefas.

## Conceitos

- **Hábito** (`habits`): nome, observações, agenda, ordem, arquivado.
- **Agenda** (`HabitScheduleType`):
  - `DAILY` — todo dia.
  - `DAYS_OF_WEEK` — dias marcados (ex.: seg, qua, sex).
  - `TIMES_PER_WEEK` — N vezes por semana (1..7), em qualquer dia.
- **Check-in** (`habit_checkins`): um por hábito por **data local** (`LocalDate`). Desmarcar = tombstone; marcar de novo restaura a mesma linha.
- Semana começa na **segunda-feira** (pt-BR), igual ao calendário (SPEC 010).

## Regras puras (`domain/HabitRules.kt`, WP2a)

```kotlin
object HabitRules {
    const val MAX_NAME = 60
    fun validateName(name: String): String
    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean
    /** Hábitos para a seção de Hoje, na ordem de `position`. */
    fun dueToday(habits: List<Habit>, checkins: List<HabitCheckin>, today: LocalDate): List<HabitToday>
    fun weekProgress(habit: Habit, checkins: List<HabitCheckin>, weekStart: LocalDate): WeekProgress
    fun currentStreak(habit: Habit, checkins: List<HabitCheckin>, today: LocalDate, createdOn: LocalDate): Int
    fun canCheck(date: LocalDate, today: LocalDate): Boolean
    fun describe(habit: Habit): String // "Todo dia", "Seg, qua e sex", "3x por semana"
}
data class HabitToday(val habit: Habit, val doneToday: Boolean, val progress: WeekProgress, val streak: Int)
data class WeekProgress(val done: Int, val target: Int)
```

- `canCheck`: só hoje e até **7 dias para trás**; nunca datas futuras.
- `isScheduledOn`: `DAILY` sempre; `DAYS_OF_WEEK` se o dia está marcado; `TIMES_PER_WEEK` sempre (qualquer dia vale).
- `weekProgress.target`: `DAILY` = 7; `DAYS_OF_WEEK` = nº de dias marcados; `TIMES_PER_WEEK` = N. `done` conta check-ins na semana (em `DAYS_OF_WEEK`, só nos dias marcados).
- **Sequência:**
  - `DAILY` / `DAYS_OF_WEEK`: nº de dias agendados consecutivos com check-in, contando para trás a partir de hoje. Se hoje é agendado e ainda não foi feito, começa a contar de ontem (o dia não terminou). Dias não agendados são pulados. Não conta antes de `createdOn`.
  - `TIMES_PER_WEEK`: nº de semanas consecutivas que atingiram a meta; a semana atual entra se já atingiu, e não quebra a sequência enquanto não terminou.
  - Check-in num dia não agendado é aceito e não quebra nada (conta como extra, não aumenta a meta).
- `dueToday`: inclui hábitos não arquivados agendados hoje; `TIMES_PER_WEEK` aparece enquanto a meta da semana não foi atingida **ou** se já foi feito hoje.
- Datas: `LocalDate.now(zone)` no momento da ação, calculado fora das regras e passado como `today`.

## Repositório (WP2)

Criar `data/HabitRepository.kt` (interface + `LocalHabitRepository`) em vez de crescer `TaskRepository`; ligar no `AppContainer`.

```kotlin
interface HabitRepository {
    fun observeHabits(includeArchived: Boolean = false): Flow<List<Habit>>
    fun observeCheckins(from: LocalDate, to: LocalDate): Flow<List<HabitCheckin>>
    suspend fun createHabit(draft: HabitDraft)
    suspend fun editHabit(id: String, draft: HabitDraft)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun deleteHabit(id: String)          // tombstone; check-ins ficam
    suspend fun restoreHabit(id: String)
    suspend fun move(id: String, newPosition: Int)
    suspend fun setChecked(habitId: String, date: LocalDate, checked: Boolean)
}
```

- `setChecked` valida `canCheck`; usa o índice único (`habitId`,`date`): se existe linha com tombstone, restaura; se não existe, insere. Desmarcar = `deletedAt`. Idempotente.
- Excluir hábito: tombstone + "Desfazer" (padrão atual de tarefas). Check-ins não são apagados.
- Hábitos não passam pelo `TaskChangeListener`; se o widget passar a mostrar hábitos (fora de escopo agora), criar porta própria.

## UI (WP2)

- **Hoje:** seção "Hábitos" acima das tarefas, só se houver hábitos de hoje. Cada linha: círculo de 48dp para marcar, nome, "2/3 nesta semana" e, se a sequência for ≥ 2, o texto "5 dias seguidos" (sem emoji; seguir `docs/DESIGN.md`). Feedback com "Desfazer". Link "Gerenciar".
- **Sobreposição "Hábitos"** (mesmo padrão de Buscar/Revisão em `PulsoScreens.kt`): lista com reordenar (botões subir/descer acessíveis, arrastar opcional), criar, editar (bottom sheet: nome, observações, agenda), arquivar/reativar, excluir.
- Acesso também por Ajustes → "Hábitos".
- Linguagem gentil: nunca "você quebrou a sequência"; sequência zerada simplesmente não aparece.
- Acessibilidade: `contentDescription` "Marcar <nome> como feito hoje" / "Desmarcar …"; estado anunciado.

## Lembretes de hábito (P2, opcional no M1.5 — confirmado)

Se implementado: canal próprio "Lembretes de hábitos"; `reminderMinutes` por hábito; `OneTimeWork` diário reagendado após rodar (inexato, como tarefas); não notifica se já feito hoje ou se não é dia agendado; ação "Feito" na notificação chama `setChecked(today)`. Permissão segue os estados da SPEC 007 §6. Se não implementado, o campo não aparece na UI.

## Aceites

- [x] `HabitRulesTest` (28 testes, passando em 2026-11-19): as três agendas; sequência com dia de hoje pendente; dias não agendados; extra fora da agenda; semana com meta atingida/não; `createdOn`; limite de 7 dias; virada de semana; troca de fuso não muda datas já gravadas.
- [ ] Repositório (instrumentado): marcar/desmarcar/remarcar reaproveita a linha; marcar duas vezes não duplica; data futura rejeitada; excluir/restaurar hábito preserva check-ins.
- [ ] Backup v3 com hábitos e check-ins (SPEC 012).
- [ ] Manual: TalkBack anuncia estado; fonte grande sem corte na seção de Hoje.

## Fora de escopo

Hábitos com quantidade (copos, minutos), metas mensais, estatísticas/gráficos, hábitos no widget, gamificação (pontos, medalhas).

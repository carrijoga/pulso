# SPEC 010 — Calendário semanal (M1.5, WP3)

**Status:** Planejado. Grade de tarefas pode começar após o WP0; projeções de recorrência dependem do WP1 e a linha de hábitos do WP2.

## Objetivo

Ver a semana de relance — o que vence em cada dia, o que está atrasado, quais hábitos foram feitos — e redistribuir tarefas entre dias sem abrir o editor. É uma visão do mesmo banco local; **não** é integração com o calendário do Android/Google.

## Navegação

- Nova aba **"Semana"** (`Icons.Rounded.CalendarViewWeek` / `Outlined`), entre Tarefas e Ajustes (decisão confirmada, `PLANO-M1.5.md` §2). Estado da semana exibida salvo com `rememberSaveable`.
- Cabeçalho: "17 – 23 nov." com botões "Semana anterior", "Próxima semana" e "Hoje" (volta à semana atual). Gesto de deslizar horizontal é opcional; os botões são obrigatórios (acessibilidade).

## Conteúdo

Lista vertical de 7 dias (seg → dom), não uma grade de horas — prazos do Pulso são pontuais e a tela do Moto G84 é estreita.

- **Atrasadas** (só na semana atual, no topo): tarefas ativas com `dueAt` antes de hoje, com ações rápidas "Hoje" e "Amanhã" (usam `TaskRules.rescheduleFromToday`, registrando histórico).
- **Cada dia:** cabeçalho "seg. 17" (hoje destacado), tarefas ativas com prazo naquele dia local ordenadas por hora, depois concluídas naquele dia (esmaecidas, ocultáveis por um filtro "Mostrar concluídas"), depois **projeções** de recorrência.
- **Projeções (após WP1):** para cada série ativa, `RecurrenceRules.occurrencesBetween` dentro da semana, excluindo a data da ocorrência ativa já existente. Aparência fantasma ("Previsto"), sem checkbox; tocar abre a ocorrência ativa da série. Somente `SCHEDULE`; `COMPLETION` não projeta (depende da conclusão).
- **Hábitos (após WP2):** faixa compacta no cabeçalho de cada dia com um marcador por hábito agendado (feito/pendente). Tocar alterna o check-in respeitando `HabitRules.canCheck` (desabilitado para futuro e > 7 dias).
- **Sem prazo:** não aparecem; rodapé "N tarefas sem prazo" leva à aba Tarefas.
- Dia vazio: "Livre" discreto, sem ilustração grande.

## Interações

- Tocar tarefa → editor (mesmo `Editor.Edit` de `PulsoScreens.kt`).
- Checkbox conclui (com "Desfazer"; recorrência segue a SPEC 008).
- **Mover para outro dia:** menu da tarefa "Mover para…" com os 7 dias da semana exibida + "Escolher data". Mantém a hora local (`TaskRules.shiftDays` / `dueAtFor`) e chama `repository.reschedule` (registra histórico, limpa adiamento, reagenda lembrete). Arrastar e soltar é P2.
- Botão "+" na aba cria "Nova tarefa" com prazo pré-preenchido no dia em foco (último dia tocado) no horário padrão.

## Regras puras (`domain/WeekRules.kt`)

```kotlin
object WeekRules {
    fun weekStart(date: LocalDate): LocalDate          // segunda-feira
    fun daysOf(weekStart: LocalDate): List<LocalDate>   // 7 dias
    fun build(tasks: List<TaskItem>, series: List<TaskSeries>, habits: List<Habit>,
              checkins: List<HabitCheckin>, weekStart: LocalDate, nowMillis: Long, zone: ZoneId): WeekView
}
data class WeekView(val overdue: List<TaskItem>, val days: List<DayView>, val withoutDueCount: Int)
data class DayView(val date: LocalDate, val isToday: Boolean, val active: List<TaskItem>,
                   val completed: List<TaskItem>, val projected: List<ProjectedOccurrence>,
                   val habits: List<HabitDayMark>)
data class ProjectedOccurrence(val seriesId: String, val activeTaskId: String, val title: String, val dueAt: Long)
data class HabitDayMark(val habitId: String, val name: String, val done: Boolean, val editable: Boolean)
```

Antes do WP1/WP2, `series`/`habits` podem ser listas vazias — a assinatura já prevê os dois.

## Dados

Nenhuma tabela nova. O ViewModel precisa expor as séries ativas (WP1) e hábitos/check-ins do intervalo da semana (WP2). Para tarefas concluídas de semanas passadas, `observeTasks()` já inclui concluídas não excluídas.

## Aceites

- [ ] `WeekRulesTest`: início da semana em segunda; tarefa às 23:30 e 00:30 no dia local certo; DST; atrasadas só na semana atual; projeções sem duplicar a ocorrência ativa; `COMPLETION` não projeta; hábitos editáveis só até 7 dias.
- [ ] "Mover para…" registra histórico de reagendamento e mantém a hora.
- [ ] Navegar semanas não dispara escrita.
- [ ] Manual: TalkBack lê "segunda-feira, 17 de novembro, 3 tarefas"; fonte grande; tema escuro.

## Fora de escopo

Visão mensal, grade por horas, eventos com duração, leitura/escrita do `CalendarContract` do Android, compartilhamento de agenda.

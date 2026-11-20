# SPEC 010 — Calendário semanal (M1.5, WP3)

**Status:** Parcial (WP3 implementado em 2026-11-20: regras puras, aba, navegação, projeções, hábitos e "Mover para…". Unitários **EXECUTADOS**; manual (TalkBack, fonte grande, tema escuro) e Moto G84 pendentes). Depende de WP1 (SPEC 008) e WP2 (SPEC 009).

## Implementação (WP3)

- Regras puras: `domain/WeekRules.kt` (`weekStart`, `daysOf`, `build`, `seriesOf`, `moveToDay`). Séries vêm das próprias tarefas (`TaskItem.recurrence` e `recurrenceAnchorDueAt`, preenchidos pelo repositório), então não há query nova, nem mudança no `TaskRepository`, nem no schema.
- Projeções: `RecurrenceRules.occurrencesBetween` a partir da âncora da série, só para datas **depois** da ocorrência aberta (sem duplicar o dia dela e sem projetar o passado). Só para séries com um item aberto; `COMPLETION` não projeta.
- UI: `ui/WeekScreen.kt` (aba "Semana", 5ª aba, entre Tarefas e Ajustes). `PulsoScreens.kt` ganhou a aba, o dia em foco para o "+" (`weekFocus`, epoch day, salvo com `rememberSaveable`) e `Editor.NewTask(day)` com prazo no horário padrão. Sem mudança em `PulsoViewModel.kt` nem em `TaskComponents.kt`.
- Hábitos: `HabitViewModel.setChecked(habit, date, checked)` (dia da semana, com "Desfazer"); o repositório continua recusando datas fora de `HabitRules.canCheck`.
- Escritas: concluir (`toggleTask`), "Hoje"/"Amanhã" em atrasadas e "Mover para…" usam `vm.reschedule` (histórico e lembrete existentes); check-in usa `habitVm.setChecked`. Navegar só troca o dia em foco.
- Interpretações: "concluídas naquele dia" = concluídas cujo **prazo** é o dia (o prazo é o eixo da visão). "Atrasadas" = abertas com prazo antes de hoje, listadas só na semana atual (inclusive as de semanas anteriores); fora da semana atual, elas aparecem no próprio dia.

## Evidência (2026-11-20)

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**; **120 testes unitários**, 0 falhas (inclui `WeekRulesTest`, **14** testes, com `America/Sao_Paulo`, `Europe/Lisbon` e `America/New_York`).
- Instrumentados: não escritos nem executados nesta etapa (sem emulador/adb). "Mover para…" reusa `TaskRepository.reschedule`, coberto por `LocalTaskRepositoryTest` (instrumentado, executado em rodadas anteriores, não reexecutado agora).
- Não executado: navegação e telas no emulador ou no Moto G84, TalkBack, fonte 200%, tema escuro.

## Limitações conhecidas

- "Hoje" em Atrasadas mantém a hora da tarefa (`TaskRules.rescheduleFromToday`). Se essa hora já passou, a tarefa fica listada em Hoje com a hora passada e o cartão mostra "Atrasada"; ela não volta para a seção de atrasadas, que é por dia.
- Projeções seguem o mesmo limiar de `RecurrenceRules.nextDueAt`: nada com `due <= max(ocorrência aberta, agora)` e nada antes de hoje. O "agora" da grade atualiza quando o dia muda (ON_RESUME) ou a tela recompõe; não há relógio contínuo.
- O foco da semana nunca fica antes de hoje: ao entrar na aba ou retomar o app, ele volta para hoje; o "+" usa o maior entre foco e hoje.
- Projeção exige ocorrência aberta; série sem ocorrência aberta não aparece como previsto.
- "Pular esta vez", arrastar e soltar, e lembretes de hábito ficam fora. Hábitos `TIMES_PER_WEEK` aparecem em todos os dias (qualquer dia conta).
- Widget não mostra a semana.

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

- [x] `WeekRulesTest` (14 testes, executados em 2026-11-20): início da semana em segunda; 23:30 e 00:30 no dia local certo (SP, em que UTC cairia no dia seguinte); DST de NY (projeção mantém 09:00 local) e lacuna de Lisboa; atrasadas só na semana atual e sem repetir na coluna do dia; projeções sem duplicar a ocorrência ativa e só para dias seguintes; `COMPLETION` não projeta; série encerrada/concluída não projeta; hábitos editáveis só até 7 dias e não no futuro; criação do hábito; arquivados fora.
- [~] "Mover para…" registra histórico de reagendamento e mantém a hora. Hora: `WeekRulesTest.moveToDayKeepsTheLocalHour` (inclusive através do DST). Histórico: reusa `TaskRepository.reschedule`, coberto por `LocalTaskRepositoryTest` (instrumentado, rodado antes; não reexecutado nesta etapa). Fluxo na tela não executado.
- [~] Navegar semanas não dispara escrita: por código (a navegação só altera `weekFocus` local; `WeekScreen` não chama repositório). Sem teste automatizado.
- [ ] Manual: TalkBack lê "segunda-feira, 17 de novembro, 3 tarefas"; fonte grande; tema escuro. Não feito.

## Fora de escopo

Visão mensal, grade por horas, eventos com duração, leitura/escrita do `CalendarContract` do Android, compartilhamento de agenda.

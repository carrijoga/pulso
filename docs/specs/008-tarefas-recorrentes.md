# SPEC 008 — Tarefas recorrentes (M1.5, WP1a + WP1)

**Status:** Parcial (WP1a e WP1 implementados em 2026-11-19; unitários e instrumentados do repositório **EXECUTADOS** no emulador API 37 em 2026-11-19 (39/39, incluindo `RecurrenceRepositoryTest`); correções de revisão (ver Aceites) compiladas, instrumentados novos **não executados**; widget e Moto G84 pendentes). Depende do WP0 (SPEC 012). ADR: [0005](../adr/0005-recorrencia-por-ocorrencias.md).

### Implementação (WP1)

- Regras puras: `domain/RecurrenceRules.kt` (WP1a) e `domain/RecurrencePresets.kt` (opções do seletor, `adjust` de unidade/base/dias).
- Repositório: `data/TaskRepository.kt`. `observeTasks` preenche `TaskItem.recurrence` e `recurrenceAnchorDueAt` (campo novo, a âncora é usada no rótulo mensal/anual). `setCompleted` devolve a ocorrência criada (`TaskItem?`). `deleteTask` = "Excluir só esta"; `endRecurrence` = "Encerrar repetição". Queries novas em DAOs existentes: `TaskDao.findOpenBySeries`, `ChecklistDao.activeForTask`. Schema, migrações e backup não mudaram.
- UI: `ui/RecurrenceEditor.kt` (seletor "Repetir" com prévia "Próximas: …"), linha "Repetição" e confirmação de exclusão em `TaskEditorDialog`, ícone `Repeat` com descrição em `TaskCard`, snackbar "Concluída. Próxima: …" com "Desfazer" ao concluir uma ocorrência repetida.
- Decisões: "Não repetir" encerra a série e mantém `seriesId` para histórico; alterar a regra de uma ocorrência ativa reinicia a âncora no `dueAt` atual; editar regra de uma ocorrência concluída é recusado ("Reabra a tarefa para mudar a repetição"); tarefa repetida não permite remover o prazo; excluir ocorrência já concluída (histórico) não gera próxima; checklist é copiado desmarcado para a próxima.
- Revisão (2026-11-19): "Encerrar repetição" só para ocorrência aberta (o repositório recusa encerrar conclusão); "Desfazer" de encerrar restaura tarefa e série (`restoreEndedRecurrence`, só a série apagada no mesmo instante); "Sem prazo"/"Tirar prazo" ocultos para tarefas repetidas; repetição não começa em tarefa concluída sem série.
- Invariante relaxada: "no máximo uma ocorrência aberta por série" vale na operação normal, mas o Desfazer de uma conclusão cuja próxima já foi editada mantém as duas abertas (sem apagar dados do usuário).
- Limitações: "Pular esta vez" não implementado (botão não mostrado); ícone de repetição no widget não implementado (WP4 mexe em `widget/`); alterar só a hora de uma ocorrência não muda a série (segue a âncora original, conforme a tabela).

## Objetivo

Permitir que uma tarefa se repita ("pagar aluguel todo dia 5", "trocar filtro a cada 3 meses", "lixo toda ter e sex") sem perder o histórico de cada vez que foi feita, sem quebrar lembretes, widget, busca, revisão semanal e backup.

## Modelo (ocorrências reais)

- Uma **série** (`task_series`) guarda só a regra e a âncora. Cada **ocorrência** é uma `TaskEntity` normal com `seriesId`.
- Em qualquer momento existe **no máximo uma ocorrência ativa** (não concluída, não excluída) por série.
- Ao concluir a ocorrência ativa, a próxima é criada **na mesma transação**, copiando título, observações, categoria, prioridade e links da ocorrência concluída (edições na ocorrência atual valem para as próximas). Checklist (SPEC 013), se existir, é copiado desmarcado.
- Ocorrências concluídas ficam como histórico (aparecem em "Concluídas" e na busca, como hoje).
- Lembretes: cada ocorrência é uma tarefa; `TaskChangeListener.taskChanged(novaId, dueAt)` agenda a próxima. Nada muda em `reminders/`.

## Regras puras (`domain/RecurrenceRules.kt`, WP1a)

Assinaturas sugeridas (todas puras, com `ZoneId` explícito):

```kotlin
object RecurrenceRules {
    fun validate(rule: RecurrenceRule): RecurrenceRule
    /** Próximo vencimento depois de concluir uma ocorrência. */
    fun nextDueAt(rule: RecurrenceRule, anchorDueAt: Long, currentDueAt: Long,
                  completedAt: Long, nowMillis: Long, zone: ZoneId): Long
    /** Ocorrências futuras projetadas (para o calendário), sem criar nada. */
    fun occurrencesBetween(rule: RecurrenceRule, anchorDueAt: Long,
                           fromMillis: Long, toMillis: Long, zone: ZoneId, limit: Int = 50): List<Long>
    /** Texto pt-BR: "Todo dia", "A cada 2 semanas: seg, qua", "Todo mês no dia 5", "Todo ano em 12/03", "3 dias após concluir". */
    fun describe(rule: RecurrenceRule, anchorDueAt: Long, zone: ZoneId): String
}
```

### Validação

- `interval` 1..99. `WEEKLY` exige ao menos um dia; outras frequências exigem `daysOfWeek` vazio. `COMPLETION` não usa `daysOfWeek` (rejeitar se informado).
- Tarefa recorrente exige `dueAt`. Se o usuário liga a repetição sem prazo, o editor preenche hoje no horário padrão.

### Cálculo — base `SCHEDULE` (calendário fixo)

- A sequência é gerada **a partir da âncora**, nunca acumulando a partir da última data (evita deriva). Hora local = hora local da âncora.
  - `DAILY`: âncora + k·interval dias.
  - `WEEKLY`: semanas (início segunda) a partir da semana da âncora, de `interval` em `interval`; dentro de cada semana, os dias marcados, em ordem.
  - `MONTHLY`: âncora + k·interval meses no dia do mês da âncora; se o mês não tem o dia (31, 30, 29), usar o último dia do mês **sem** alterar a âncora (31/jan → 28/fev → 31/mar).
  - `YEARLY`: mesmo dia/mês; 29/fev → 28/fev em anos não bissextos.
- Próximo vencimento = primeira data da sequência **estritamente posterior** a `max(currentDueAt, nowMillis)`. Consequência: ocorrências perdidas não se acumulam (concluir hoje uma tarefa diária atrasada 5 dias gera a de amanhã, não cinco atrasadas).
- Se a ocorrência foi reagendada manualmente (ex.: de segunda para quarta), a próxima volta ao ritmo da série (próxima segunda depois de quarta).

### Cálculo — base `COMPLETION`

- Próximo = data local de `completedAt` + interval unidades, na hora local da âncora (ex.: "3 meses após concluir").

### Fuso e horário de verão

- Datas locais via `java.time` no `zone` atual; lacunas de DST resolvidas como em `TaskRules.dueAtFor`. Testar `America/Sao_Paulo` e um fuso com DST.

## Comportamento no repositório (WP1)

| Ação | Efeito |
|---|---|
| Criar/organizar com `draft.recurrence` | Cria `task_series` (âncora = `dueAt`) e a tarefa com `seriesId`, numa transação. |
| Editar ocorrência ativa mudando a regra | Atualiza a série (`updatedAt`); âncora passa a ser o `dueAt` atual da ocorrência. |
| Editar removendo a repetição ("Não repetir") | Encerra a série (`deletedAt`); a ocorrência atual vira tarefa comum (mantém `seriesId` para histórico). |
| `setCompleted(id, true)` em ocorrência de série ativa | Conclui e, se não houver outra ocorrência ativa da série, cria a próxima. Idempotente: concluir duas vezes (widget + notificação) não cria duas. |
| `setCompleted(id, false)` (Desfazer/reabrir) | Se a próxima ocorrência gerada por esta conclusão ainda está intocada (`createdAt == updatedAt`, não concluída), ela recebe tombstone na mesma transação. Se já foi editada, mantém as duas e não apaga nada. |
| "Pular esta vez" | Conclusão não conta: tombstone na ocorrência atual + cria a próxima. (Opcional P2; se não implementar, não mostrar o botão.) |
| Excluir ocorrência | Pergunta: "Excluir só esta" (tombstone + cria a próxima) ou "Encerrar repetição" (tombstone na ocorrência e na série). |
| `reschedule` / `snooze` | Só afetam a ocorrência; histórico de reagendamento por ocorrência, como hoje. |
| Import de backup | Não gera ocorrências; importa como está. |

Todas as escritas acima chamam `taskChanged` para a ocorrência afetada e para a nova (lembrete + widget).

## UI (WP1)

- Editor (`TaskEditorDialog`): linha "Repetir" com valor atual ("Não repete"). Ao tocar: seletor com Não repetir / Todo dia / Toda semana (dias) / Todo mês / Todo ano / Personalizar (intervalo + unidade + base "após concluir"). Prévia: "Próximas: 05/12, 05/01, 05/02" usando `occurrencesBetween`.
- `TaskCard` e widget: ícone de repetição (`Icons.Rounded.Repeat`) com `contentDescription` "Repete: <describe>".
- Snackbar ao concluir: "Concluída. Próxima: sex. 09:00" com "Desfazer".
- Busca e revisão semanal: sem mudanças obrigatórias; ocorrências são tarefas.

## Aceites

- [x] `RecurrenceRulesTest` (29 testes, WP1a, 2026-11-19): diário/semanal múltiplos dias/intervalo 2/mensal dia 31/anual 29-fev/`COMPLETION`/atraso de vários dias/reagendamento manual/DST/validações inválidas. Evidência: `testDebugUnitTest` (2026-11-19, 95 testes no total, 0 falhas).
- [x] `RecurrencePresetsTest` (6 testes, WP1): opções do seletor, dia da âncora no fuso (23:30 em São Paulo), DST de Nova York, `adjust` de dias/base. Evidência: mesmo `testDebugUnitTest`.
- [x] Repositório (instrumentado, `RecurrenceRepositoryTest`, 15 testes; 12 executados no emulador API 37 em 2026-11-19, dentro da rodada 39/39): concluir cria exatamente uma próxima; concluir duas vezes não duplica; desfazer remove a próxima intocada; desfazer preserva próxima editada; encerrar série; excluir só esta (e desfazer); excluir histórico não gera próxima; "Não repetir"; mudança de regra reinicia âncora; sem prazo recusado; regra de concluída recusada; checklist copiado; organizar Inbox com repetição converte uma vez e cria uma série. Os 3 testes novos da revisão (desfazer de encerrar restaura tarefa e série; encerrar recusado em ocorrência concluída; repetição recusada em tarefa concluída sem série) estão **compilados, NÃO executados**.
- [x] Lembrete agendado para a nova ocorrência: asserções de `listener.calls` nos testes de conclusão (`RecordingListener`), executados no emulador API 37 (2026-11-19).
- [x] Organizar entrada da Inbox com repetição mantém `INBOX -> CONVERTED` atômico: teste `organizingRepeatingCaptureConvertsOnceAndCreatesOneSeries` (executado no emulador API 37, 2026-11-19).
- [ ] Backup v3 ida e volta com séries (coberto pela SPEC 012; não verificado por teste de recorrência).
- [ ] Manual: concluir pela notificação e pelo widget gera a próxima e atualiza o widget (não feito).
- [ ] Manual: editor "Repetir" e rótulo no `TaskCard` em tela (não feito; só build e lint).
- [ ] Pendente: diálogo de exclusão em fonte 200% e alvos ≥ 48dp (verificado só por código, não em tela).

## Fora de escopo

Fim da série por data/contagem, exceções por data, "editar todas as ocorrências passadas", RRULE/iCal, recorrência sem prazo.

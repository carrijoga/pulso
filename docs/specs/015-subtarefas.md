# SPEC 015 — Subtarefas (hierarquia de um nível)

**Status:** Parcial (WP6 em `m1.5/wp6-subtarefas`, 2026-11-20). Unitários executados (143 no total, 0 falhas). Instrumentados **executados pelo dono no emulador API 37** em 2026-11-20: 76/78; as 2 falhas (`BackupServiceTest`, versões desatualizadas) foram corrigidas nesta etapa e os testes corrigidos ainda **não foram reexecutados**. Moto G84 pendente. Decisão do dono do produto em 2026-11-20; ADR: [0009](../adr/0009-subtarefas-hierarquia.md). Relaciona-se com a [SPEC 013 §5](013-visoes-por-categoria-e-checklist.md).

## Objetivo

Permitir que uma tarefa tenha passos que também são tarefas: cada subtarefa tem prazo, lembrete, categoria e conclusão próprios. Serve a casos como "Casamento" com "Reservar buffet" e "Enviar convites", sem criar projetos hierárquicos. O checklist (SPEC 013 §4) continua para passos pequenos e sem prazo.

## Regras (decisão do dono do produto, 2026-11-20)

1. **Um nível só.** Tarefa → subtarefas. Subtarefa não tem filhas. Tarefa que já tem filhas não pode virar subtarefa.
2. **Independência.** Mãe e filhas têm prazo, lembrete, categoria e conclusão próprios. Mudar uma não muda a outra.
3. **Concluir a mãe com filhas abertas** abre o diálogo "Concluir também as N subtarefas?" com "Sim, concluir todas" (conclui a mãe e todas as filhas abertas na mesma transação) e "Não, só a tarefa" (conclui só a mãe; as filhas seguem abertas). Sem filhas abertas, conclui direto.
4. **Concluir a última filha aberta** mostra snackbar "Todas as subtarefas concluídas. Concluir tarefa?" com ação "Concluir". A sugestão não conclui nada sozinha. Só aparece se a mãe estiver aberta.
5. **Excluir a mãe exclui as filhas** (tombstone de todas na mesma transação). Um único "Desfazer" restaura a mãe e as filhas excluídas junto com ela. Filhas excluídas antes, separadamente, continuam excluídas.
6. **Excluir uma filha** não afeta a mãe.
7. **Recorrência:** uma tarefa repetida **pode** ter subtarefas. Ao concluir a ocorrência, a próxima recebe cópias abertas das subtarefas (ver "Recorrência e subtarefas" abaixo). **Subtarefa não pode repetir**: o app recusa com mensagem clara.

## Comportamento

### Criação e vínculo

| Ação | Resultado |
|---|---|
| "Adicionar subtarefa" no editor da mãe | Abre "Nova tarefa" com "Subtarefa de: <mãe>" preenchido. |
| Campo "Subtarefa de…" no editor | Lista tarefas abertas que podem ser mãe; "Nenhuma" solta o vínculo. |
| "Soltar" numa subtarefa (no editor da mãe) | Vira tarefa independente (`parentId = null`). |
| Vincular uma tarefa com filhas | Recusado: "Tarefa com subtarefas não pode virar subtarefa." |
| Vincular a uma subtarefa | Recusado: "Subtarefas só podem ter um nível: escolha uma tarefa principal." |
| Vincular uma tarefa repetida a uma mãe | Recusado: "Tarefa repetida não pode ser subtarefa. Remova a repetição antes." |
| Vincular a uma mãe repetida | Permitido. As próximas ocorrências da mãe recebem cópias das subtarefas. |
| Repetir uma subtarefa | Recusado: "Subtarefa não pode repetir. Solte-a da tarefa mãe antes." |
| Repetir uma tarefa com filhas | Permitido (ver recorrência abaixo). |
| Vincular a si mesma, ou a mãe excluída | Recusado (auto-referência / "A tarefa mãe não existe mais."). |

Recusas acontecem dentro da transação de gravação; nada é escrito.

### Exibição

- **Editor da mãe:** seção "Subtarefas" com "Progresso n/m" (concluídas/total de filhas vivas), lista de filhas (tocar abre a filha; "Soltar" desvincula) e botão "Adicionar subtarefa". Abrir uma filha salva antes as alterações da mãe (se a mãe não puder ser salva, a navegação não acontece e o erro aparece).
- **TaskCard:** indicador "n/m subtarefas" (concluídas/total) quando há filhas. Numa subtarefa, rótulo "Subtarefa de: <título da mãe>".
- **Aba Tarefas:** filhas aparecem recuadas logo abaixo da mãe quando a mãe também aparece na lista filtrada. Se a mãe não estiver na lista (filtro, busca, categoria), a filha aparece como tarefa comum com o rótulo da mãe.
- **Hoje, Semana, categoria, busca e widget:** subtarefas são tarefas normais com o rótulo da mãe.
- **Concluir pelo checkbox** (qualquer lista) usa a mesma regra da seção Comportamento §3.

### Excluir

- Editor da mãe com filhas vivas: a confirmação diz quantas subtarefas também saem; "Excluir" exclui tudo, com "Desfazer" no snackbar que restaura tudo.

## Regras puras (`domain/SubtaskRules.kt`)

- `linkError(...)`: valida o vínculo (um nível, auto-referência, mãe viva, repetição). Retorna mensagem ou `null`.
- `repeatError(isSubtask)`: subtarefa não pode repetir (tarefa repetida pode ter subtarefas).
- `progressLabel(done, total)`: rótulo "n/m subtarefas" usado no cartão e em testes.
- `progress(children)`: (concluídas, total) das filhas vivas.
- `suggestCompletingParent(parent, child)`: `true` quando a filha era a última aberta da mãe aberta.
- `groupForList(tasks)`: ordem da aba Tarefas (filhas logo após a mãe quando a mãe está na lista).
- `importError(local, incoming)`: validação do backup (ver abaixo).

## Recorrência e subtarefas (decisão de 2026-11-20, segunda versão)

- Ao concluir uma ocorrência (ou excluir "Só esta" de uma ocorrência aberta), a próxima ocorrência recebe **cópias abertas** das subtarefas vivas da ocorrência anterior, na mesma transação: novo UUID, mesmo título, notas, categoria, prioridade e links, `parentId` = nova ocorrência, `completedAt` nulo.
- **Prazo:** cada cópia mantém o deslocamento da subtarefa em relação à mãe, em dias locais e no mesmo horário local (`SubtaskRules.shiftedDueAt`). Subtarefa sem prazo continua sem prazo.
- **Checklist** da subtarefa é copiado desmarcado.
- **Lembretes:** cada cópia recebe `taskChanged`.
- **Mãe repetida sem prazo** (caso raro: a repetição exige prazo, então só acontece com dado antigo ou inconsistente): a cópia usa a hora da conclusão como referência para o deslocamento.
- **Concluir "Sim" na mãe repetida:** as cópias são criadas antes de as originais serem concluídas; a próxima ocorrência nasce aberta.
- **Desfazer a conclusão** remove a próxima ocorrência e as cópias **só se nenhuma delas foi editada** (`updatedAt` igual ao momento da criação). Se alguma foi editada, nada é removido e a próxima ocorrência e as cópias continuam (nada escrito pelo usuário é perdido, como na SPEC 008).
- **Encerrar repetição** e **excluir a mãe** removem as subtarefas vivas no mesmo instante; "Desfazer" restaura só as removidas nesse instante.
- Concluir pela notificação ou pelo widget conclui só a mãe, sem o diálogo: as subtarefas seguem abertas (nada se perde; o diálogo é da tela).
- Texto do diálogo com uma subtarefa: "Concluir também a subtarefa aberta?" (singular, por gramática; com mais de uma, "Concluir também as N subtarefas?").

## Dados

- Schema v4 (ADR 0009): `task_items.parentId TEXT NULL`, índice `index_task_items_parentId`. Migração `Migration3To4` aditiva.
- `TaskItem` ganha `parentId`, `parentTitle` (preenchido pelo repositório), `subtaskTotal` e `subtaskDone` (filhas vivas).
- Consultas novas em `TaskDao`: filhas vivas, filhas abertas, filhas excluídas no mesmo instante, filhas concluídas no mesmo instante, contagem de filhas.

## Backup (formato v4)

- `CURRENT_VERSION = 4`. Tarefas exportam `parentId` (`null` quando não há). Arquivos v1, v2 e v3 lêem com `parentId = null`.
- Validação antes de qualquer escrita (tudo ou nada), na ordem:
  1. o pai precisa existir no arquivo ou no banco local (senão: "Backup inconsistente: referência ausente.");
  2. auto-referência e ciclo: "Backup inconsistente: ciclo entre subtarefas.";
  3. o pai não pode ser subtarefa: "Backup inconsistente: subtarefa com mais de um nível.";
  4. filha **viva** não pode apontar para tarefa excluída: "Backup inconsistente: subtarefa aponta para tarefa excluída.". Filha excluída apontando para mãe excluída é o estado normal de uma exclusão e é aceita;
  5. filha **viva** com repetição **ativa** (`seriesId` de uma série viva) não pode existir: "Backup inconsistente: subtarefa com repetição ativa.". Uma subtarefa cuja repetição foi encerrada mantém o `seriesId` como histórico (série excluída) e é aceita, porque o app cria esse estado ao "Não repetir" de uma tarefa que depois vira subtarefa.
- Como a importação é aditiva (um UUID já existente no banco prevalece), a validação usa o estado efetivo: banco local para os IDs que já existem, arquivo para os novos.

## Migração

- `Migration3To4` só faz `ALTER TABLE task_items ADD COLUMN parentId TEXT` e `CREATE INDEX IF NOT EXISTS index_task_items_parentId ON task_items (parentId)`. Nenhuma linha é alterada; `parentId` fica `NULL` para todas as tarefas existentes.
- Testes: `MigrationTest.migrate3To4KeepsRealV3DataAndLeavesParentNull` (tarefas, séries, checklist, hábitos e check-ins de v3 preservados, `parentId` nulo) e `everyExportedVersionMigratesToLatestWithAllMigrations` (cobre 1→4 com `ALL_MIGRATIONS`).

## Aceites

- [x] Regras puras com testes unitários: `SubtaskRulesTest` (13 casos: vínculo, repetição, progresso, sugestão, agrupamento, deslocamento de prazo com horário de verão, validação de backup). Executados: `testDebugUnitTest`, 143 testes no total, 0 falhas.
- [~] Repositório: `SubtaskRepositoryTest` (instrumentado, 12 casos) cobre concluir mãe com e sem filhas, desfazer, progresso, recusa de dois níveis, de subtarefa repetida e de tarefa com filhas virando subtarefa, cópias da recorrência (cópia, deslocamento de prazo, checklist, sem prazo), desfazer que remove cópias intocadas e preserva editadas, excluir mãe + desfazer, encerrar repetição + desfazer, soltar. **Compilado; não executado no emulador nesta etapa.**
- [x] Migração 3→4 com dados de v3 (`MigrationTest`), repositório (`SubtaskRepositoryTest`) e backup (`BackupServiceTest`): executados pelo dono no emulador API 37 (76/78 na rodada; as 2 falhas de `BackupServiceTest` eram versões desatualizadas e foram corrigidas: v4 e "mais nova" = 5). Reexecução dos 2 testes corrigidos pendente.
- [~] UI: editor, TaskCard, aba Tarefas e diálogo implementados; validação em tela não feita.
- [ ] Manual no Moto G84: migração v3→v4 com dados reais, criar/concluir/excluir/desfazer subtarefas, TalkBack, fonte 200%, widget com rótulo.

## Fora de escopo

Mais de um nível; subtarefa repetida; arrastar para reorganizar subtarefas; progresso ponderado; sincronização de vínculos entre aparelhos (depende da sync futura).

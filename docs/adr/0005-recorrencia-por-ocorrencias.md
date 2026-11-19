# ADR 0005 — Recorrência por ocorrências reais

**Status:** Aceita · 2026-11-19 · ver SPEC 008

## Contexto

O M1.5 pede tarefas recorrentes. Lembretes (WorkManager por tarefa), widget, busca, revisão semanal, histórico de reagendamento e backup já funcionam sobre `task_items`. Precisamos de recorrência sem reescrever esses fluxos e sem perder o histórico de cada execução.

## Decisão

Uma tabela `task_series` guarda só a regra (frequência, intervalo, dias, base `SCHEDULE`/`COMPLETION`) e a âncora. Cada ocorrência é uma `TaskEntity` comum com `seriesId`. Existe no máximo uma ocorrência ativa por série; a próxima é criada na mesma transação em que a atual é concluída. Datas futuras só são **projetadas** (não persistidas) para exibição no calendário.

## Alternativas

- **Ocorrências virtuais calculadas da regra** (estilo agenda): exigiria estado de conclusão por data, lembretes por ocorrência virtual e mudanças em widget/busca/revisão. Mais complexo e mais frágil para sync futura.
- **Mover o `dueAt` da mesma tarefa ao concluir:** simples, mas perde o histórico de conclusões e mistura "reagendada" com "repetida".
- **RRULE (iCal) em texto:** expressivo demais para o escopo e difícil de validar/testar; pode ser exportado no futuro a partir do modelo estruturado.

## Consequências

- Lembretes, widget e busca funcionam sem mudança estrutural.
- Concluir precisa ser idempotente (widget + notificação) e "Desfazer" precisa remover a próxima ocorrência intocada.
- A tabela cresce uma linha por execução; aceitável para uso pessoal.
- Para sync futura (ADR 0003), série e ocorrências são entidades separadas com UUID e tombstone; a geração da próxima ocorrência em dois aparelhos offline pode duplicar — tratar no design de sync (ex.: ID determinístico por série+data), não agora.

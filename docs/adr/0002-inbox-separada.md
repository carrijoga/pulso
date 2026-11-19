# ADR 0002 — Inbox separada do domínio Task

**Status:** Aceita · 2026-11-19

## Decisão

`InboxEntity` armazena anotação livre; `TaskEntity` armazena trabalho organizado. Conversão cria uma tarefa e marca a origem `CONVERTED` com vínculo, dentro da mesma transação.

## Consequências

Captura sem fricção; nota pode virar outros tipos no futuro (hábito/compromisso). Evita preencher categoria/prioridade/horário durante captura. Requer revisão de estados/arquivamento e consultas específicas para histórico.

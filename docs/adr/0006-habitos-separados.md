# ADR 0006 — Hábitos como entidade separada, com data local

**Status:** Aceita · 2026-11-19 · ver SPEC 009

## Contexto

Hábitos ("ler 20 min", "academia 3x/semana") se parecem com tarefas recorrentes, mas o uso é diferente: marcar feito/não feito por dia, progresso semanal, sequência, sem prazo/hora, sem "atrasada". A ADR 0002 já previa que uma anotação poderia virar "hábito".

## Decisão

Tabelas próprias `habits` e `habit_checkins`, com `HabitRepository` separado do `TaskRepository`. O check-in guarda a **data local** (`yyyy-MM-dd`) e não um instante, com índice único (`habitId`, `date`). Desmarcar é tombstone; remarcar restaura a mesma linha.

## Alternativas

- **Hábito = tarefa recorrente diária:** polui a lista de Tarefas e a revisão semanal com centenas de ocorrências, gera "atrasadas" para dias não feitos (culpa) e não suporta "N vezes por semana".
- **Check-in como instante (epoch ms):** o dia do hábito mudaria ao viajar/trocar fuso e exigiria reconversão em toda consulta. O dia em que o usuário fez algo é um conceito de calendário local.

## Consequências

- Hoje e Semana precisam combinar dois repositórios.
- Datas de hábitos não são convertidas por fuso: quem viajar vê os mesmos dias marcados. Documentado na SPEC 009.
- Backup v3 e sync futura precisam tratar o índice único (dois aparelhos marcando o mesmo dia geram UUIDs diferentes; na importação, o local prevalece).

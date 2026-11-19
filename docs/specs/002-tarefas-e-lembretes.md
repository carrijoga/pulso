# SPEC 002 — Tarefas, prazos e lembretes

**Status:** Implementado; lembretes verificados no Moto G84 (Android 15).

## Comportamento atual

Tarefas com título (1 a 200 caracteres), notas, categoria livre, prioridade (`LOW|NORMAL|HIGH`), prazo opcional (data + hora; hora padrão configurável), conclusão reversível e exclusão lógica. Filtros ativas/concluídas/atrasadas, busca por título/notas/categoria. Tela Hoje mostra vencidas/hoje e outras pendências. Lembretes de prazo são agendados/cancelados via WorkManager, com disparo aproximado, e não são alarmes exatos.

## Aceites

- [x] Edição preserva ID, createdAt e atualiza updatedAt.
- [x] Conclusão define `completedAt` e cancela work; reabertura limpa completedAt.
- [x] Exclusão define deletedAt e oculta a tarefa, sem apagar o registro.
- [x] Prazo considera timezone ao determinar vencimento diário.
- [x] Agendamento segue alterações do repositório local.
- [x] Lembretes restaurados via import de backup.
- [x] Notificação no aparelho com permissão 13+ testada (Moto G84, Android 15).
- [x] Hora do prazo configurável por tarefa; adiamento de lembrete (ver SPEC 007).
- [x] Revisão semanal (SPEC 007).
- [ ] Recorrência (M1.5, [SPEC 008](008-tarefas-recorrentes.md)).

## Nota

WorkManager não oferece garantia de horário exato. Não prometer lembretes precisos; se necessário, especificar AlarmManager e suas permissões em outra feature.

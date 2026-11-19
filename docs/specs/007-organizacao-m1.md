# SPEC 007 — Organização do dia a dia (M1)

**Status:** Implementado; testes unitários e instrumentados passando (emulador e Moto G84); notificações validadas no Moto G84; widget e TalkBack pendentes.

## Escopo

1. **Nova tarefa direta:** aba Tarefas tem "Nova tarefa"; Hoje/Entrada mantêm "Capturar" (capture-first continua o fluxo principal).
2. **Links:** campo texto, um por linha; normalizado (trim, sem vazios/duplicados, até 20). Links `http(s)://` e `www.` ganham botão "Abrir".
3. **Categorias estruturadas:** tabela `categories` (UUID, nome, timestamps, tombstone). Nomes únicos ignorando maiúsculas e acentos. Renomear atualiza as tarefas ativas na mesma transação. Remover só oculta da lista; tarefas mantêm o texto da categoria (backup v1 continua compatível).
4. **Prazo com horário:** data + hora por tarefa; horário padrão configurável (Ajustes, padrão 09:00).
5. **Adiar lembrete:** `snoozedUntil` muda só o horário do lembrete, não o prazo. Ações na notificação: Concluir, Adiar 1h, Amanhã (horário padrão). Mudar o prazo ou concluir limpa o adiamento.
6. **Permissão de notificação:** estados Permitida / Ainda não pedida / Bloqueada (inclui canal desligado). Pedido contextual ao salvar a primeira tarefa com prazo; depois, botão abre os ajustes do Android. Reavaliado a cada retorno ao app.
7. **Busca global:** ícone na barra superior. Tarefas (título, observações, categoria, links; inclui concluídas) e Inbox pendente/arquivada (arquivadas podem voltar para a entrada). Todos os termos precisam aparecer; ignora acentos/maiúsculas.
8. **Revisão semanal:** cartão em Hoje quando a última revisão tem 7+ dias. Seções: Entrada, Atrasadas (Amanhã / +7 dias / Sem prazo, contados a partir de hoje, mantendo a hora), Muito reagendadas (3+), Próximos 7 dias, Sem prazo, Concluídas na semana. "Concluir revisão" registra a data.
9. **Histórico de reagendamento:** tabela append-only `task_reschedules`. Só conta mudança entre duas datas reais (definir/remover prazo não conta). Editor mostra "Reagendada N vezes" e as últimas mudanças.
10. **Widget responsivo:** estreito (contagem + capturar); a partir de 250 dp de largura, cabeçalho + lista rolável (`LazyColumn`, até 30 tarefas) que ocupa a altura disponível. Círculo de 48dp conclui; título abre o app.

## Dados

Room v2 com `Migration1To2` explícita (aditiva): colunas `task_items.links` (default `''`) e `snoozedUntil`; tabelas `categories` e `task_reschedules`; semeia categorias padrão + nomes usados por tarefas ativas. Backup formato v2 inclui categorias e histórico; importa v1 e v2.

## Aceites

- [x] Regras puras: busca, links, nomes de categoria, rótulos de prazo, reagendamento a partir de hoje, buckets da revisão (`OrganizeRulesTest`).
- [x] Repositório: histórico só entre datas reais, adiamento/conclusão, renomear propaga, tombstone restaurável (`LocalTaskRepositoryTest`).
- [x] Migração 1→2 preserva linhas e valida o schema exportado (`MigrationTest`); migração real de banco v1 no emulador.
- [x] Backup v2 ida e volta, idempotente, aceita v1, rejeita inválidos sem escrever, 1000 tarefas (`BackupServiceTest`).
- [x] Notificação com ações testada no Moto G84 (Android 15, permissão negada e depois concedida); texto "Vence agora · HH:mm" no horário do prazo.
- [x] Widget em launcher real (Moto G84): estreito, grande e baixo (lista rolável, concluir, abrir app, captura, reinício do processo).
- [~] TalkBack: árvore de acessibilidade auditada no Moto G84 e 7 problemas corrigidos e reauditados (ver `docs/TESTING.md`); fonte grande (200%) OK no Moto G84; falta navegação por gestos.

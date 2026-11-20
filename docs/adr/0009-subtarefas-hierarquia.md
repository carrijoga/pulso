# ADR 0009 — Subtarefas como hierarquia de tarefas (`parentId`, um nível)

**Status:** Aceita · 2026-11-20 · ver [SPEC 015](../specs/015-subtarefas.md) e [SPEC 013 §5](../specs/013-visoes-por-categoria-e-checklist.md)

## Contexto

O usuário pediu "opção de criar subtarefas" (ideia 2026-11-19-007). A SPEC 013 §5 deixou duas leituras em aberto: (a) um item do checklist promovível a tarefa própria, ou (b) hierarquia real de tarefas com `parentId`, que contraria o "Fora de escopo" e exige schema novo.

O dono do produto decidiu em 2026-11-20 pela opção (b), com estas regras:

- só um nível: tarefa → subtarefas; subtarefa não tem filhas; tarefa com filhas não vira subtarefa;
- mãe e filhas são independentes (prazo, lembrete, categoria e conclusão próprios);
- concluir a mãe com filhas abertas pergunta; concluir a última filha aberta sugere concluir a mãe;
- excluir a mãe exclui as filhas (tombstone na mesma transação), com um único "Desfazer".

O checklist (SPEC 013 §4) continua para passos pequenos.

A mudança toca `task_items`, a tabela com mais dados do usuário. O aparelho de uso real (Moto G84) tem dados e vai migrar v3 → v4. Por isso a decisão precisa ser conservadora em schema, backup e migração (AGENTS.md §4).

## Decisão

1. **Modelo:** coluna `parentId TEXT NULL` em `task_items`, com índice `index_task_items_parentId`. Sem FOREIGN KEY e sem `ON DELETE CASCADE`, pelo mesmo motivo do ADR 0008: a regra "nunca apagar dados" exige tombstone, e a integridade é validada no repositório e na importação.
2. **Migração:** `Migration3To4` é aditiva (`ALTER TABLE task_items ADD COLUMN parentId TEXT` e `CREATE INDEX IF NOT EXISTS`). Nenhuma linha é alterada. `version = 4`, schema `4.json` exportado; `1.json`, `2.json` e `3.json` não mudam. Sem `fallbackToDestructiveMigration`.
3. **Regras puras** em `domain/SubtaskRules.kt`: validação do vínculo (um nível, sem auto-referência, mãe existente e viva), repetição (tarefa repetida não tem subtarefas; subtarefa não repete), progresso "n/m", sugestão de concluir a mãe e agrupamento visual.
4. **Repositório:** toda escrita é uma transação com `withTransaction`, tombstone em vez de `DELETE`, `updatedAt` e `taskChanged` para cada tarefa afetada (lembretes e widget).
5. **Recorrência:** uma tarefa repetida pode ter subtarefas (decisão do dono do produto, 2026-11-20, depois da primeira versão desta ADR). Ao concluir a ocorrência, a próxima recebe cópias abertas das subtarefas vivas: mesmo conteúdo, prazo com o mesmo deslocamento em dias e horário local, checklist desmarcado, `parentId` da nova ocorrência. Desfazer a conclusão remove a próxima ocorrência e as cópias só se nada foi editado; senão mantém tudo. Subtarefa não repete.
6. **Backup formato v4:** exporta `parentId`; lê v1, v2, v3 (sem `parentId`, vira `null`) e v4. Antes de escrever, valida: pai existe no arquivo ou no banco; pai não é subtarefa; sem ciclo nem auto-referência; filha viva não aponta para tarefa excluída. Continua rejeitando versão mais nova com mensagem própria.

## Alternativas

- **(a) Checklist promovível:** um item vira tarefa própria mantendo o vínculo de origem. Não exige coluna nova, mas duplica o conceito: o item e a tarefa têm estados, lembretes e textos separados, e a conversão tem de copiar ou apontar dados. Também não resolve a visão "tudo de um projeto" da aba Tarefas. Fica como alternativa rejeitada para subtarefas; o checklist continua para passos pequenos.
- **Tabela `subtasks` separada (N:M ou pai-filho):** exigiria duplicar título, prazo, lembrete, categoria e conclusão, e reescrever lembretes, widget, busca e backup. Sem ganho para o caso de um nível.
- **Vários níveis (árvore):** mais complexo para UI, para o widget e para validação de ciclos, sem pedido do usuário. Pode ser revisto com ADR próprio se houver uso real.
- **FOREIGN KEY com cascata:** contradiz a regra de tombstone (AGENTS.md §1).

## Consequências

- Schema v4 e backup v4 são mudanças de risco de dados: exigem `MigrationTest` 3→4 com dados reais, cadeia 1→4 e testes de backup antes de qualquer instalação no Moto G84.
- Após o merge, v4 fica congelado. Qualquer nova mudança de tabela vira v5.
- Versões antigas do app não leem backup v4 (rejeitam com "versão mais nova"), como já ocorre com v3.
- Cada conclusão de tarefa repetida com subtarefas cria cópias extras na mesma transação; o custo cresce com o número de subtarefas, não é um problema para uso pessoal.
- Cópias de subtarefa são linhas novas com UUID próprio (sem sync de identidade ainda); a sync futura terá de tratar a geração duplicada da próxima ocorrência em dois aparelhos (mesma nota do ADR 0005).
- Sync futura (ADR 0003) precisará tratar `parentId` como referência a UUID; sem FK, conflitos de exclusão de mãe e filhas ficam para o desenho de sync.

## Rollback

- Código: reverter o merge do WP6. O banco v4 não volta para v3 (Room não faz downgrade); um aparelho que já migrou para v4 precisa de APK v4 ou de restaurar o backup JSON exportado antes da migração (exportação v3 continua lendo como antes).
- Dados: `parentId` é só uma coluna nova; as tarefas continuam válidas como tarefas independentes se a coluna for ignorada. Nenhuma linha é apagada pela migração.
- Recomendação antes de instalar v4 no Moto G84: exportar o JSON pelo app (Ajustes) e guardar o arquivo.

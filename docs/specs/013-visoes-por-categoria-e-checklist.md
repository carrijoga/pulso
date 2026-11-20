# SPEC 013 — Visões por categoria e checklist (M1.5, WP5)

**Status:** Parcial (WP5 implementado em 2026-11-20; unitários **EXECUTADOS** (115/115 na branch); instrumentados do repositório **compilados, não executados**; Moto G84 pendente). Depende do WP0 (SPEC 012).

### Implementação (WP5)

- Regras puras: `domain/ChecklistRules.kt` (texto 1..200, até 100 itens por tarefa, `isLastUnchecked`, `placeAt` para mover e desfazer exclusão) e `CategoryRules.overview`/`suggestions` em `domain/OrganizeRules.kt`. `SearchRules.matchesTask` também lê os textos dos itens.
- Dados: sem mudança de schema. `ChecklistDao.observeActive()` é a única query nova; `TaskRepository` ganhou `observeChecklist`, `addChecklistItem`, `setChecklistItemChecked`, `editChecklistItem`, `deleteChecklistItem` (tombstone), `restoreChecklistItem` (Desfazer) e `moveChecklistItem`. Cada escrita toca `task.updatedAt` na mesma transação e chama `taskChanged`. Quando um item não muda de estado, nada é gravado. `observeTasks` preenche `TaskItem.checklist`.
- UI: `ui/CategoryScreen.kt` (sobreposição, aberta por Ajustes → Categorias; "Nova tarefa" já vem com a categoria); `ui/ChecklistEditor.kt` (seção "Itens" no editor: campo com Concluir/"+" que mantém o foco, checkbox, tocar no texto abre editar/subir/descer/excluir); `TaskCard` mostra "n/m" quando há itens; Ajustes → Categorias ganhou sugestões que não repetem nomes existentes.
- Criação: itens digitados antes de salvar entram com a tarefa (`TaskDraft.checklist`). Em edição, as mudanças valem na hora pelos métodos de checklist; `editTask` ignora `draft.checklist`.
- Recorrência: a próxima ocorrência copia o checklist com todos os itens desmarcados (comportamento do WP1, `spawnNextOccurrence`).
- Decisões: "Concluir tarefa?" sai como mensagem (snackbar) pelo ViewModel, como a spec pede; dentro do editor (folha modal) ela só fica visível depois que a folha fecha. Precisa conferir no aparelho.
- Limitações: entrada da tela por categoria só por Ajustes → Categorias. Tocar no ponto da categoria no `TaskCard` e no editor **não** foi implementado (o alvo de 48dp dentro do cartão não estava resolvido). A aba Tarefas já tinha chips de categoria com "Todas" (SPEC 007); não foram alterados.
- Ambiguidade da spec: "a próxima ocorrência copia os itens desmarcados" (§4) pode ser lido como "copia só os desmarcados". O comportamento atual (WP1) copia todos, desmarcados. Mantido; confirmar com o dono do produto.
- Fora do WP5 (decisão pendente do dono do produto): subtarefas e promover item a tarefa.

## Objetivo

Atender o item do ROADMAP "Busca e organização por compras, estudos, manutenção, casamento, encomendas" reaproveitando o que existe (categorias, busca, recorrência), sem criar um tipo de dado novo por assunto.

Leitura proposta de cada caso:

| Assunto | Necessidade | Recurso |
|---|---|---|
| Compras | lista de itens marcáveis ("Mercado": arroz, café…) | checklist na tarefa |
| Estudos | ver tudo de uma matéria, prazos de prova/entrega | tela da categoria |
| Manutenção | coisas periódicas (filtro, óleo, revisão) | tela da categoria + recorrência `COMPLETION` (SPEC 008) |
| Casamento | projeto grande com muitas tarefas e sub-itens | tela da categoria + checklist |
| Encomendas | o que chega, de onde, link de rastreio | tela da categoria + links (já existem) |

Sem integração com transportadoras, lojas ou APIs (o app não tem `INTERNET`).

## 1. Tela da categoria

- Acesso: tocar no nome/ponto da categoria num `TaskCard`, no editor, ou em Ajustes → Categorias. Aberta como sobreposição (padrão de Buscar/Revisão).
- Conteúdo: título = nome da categoria; contadores "N ativas · M atrasadas"; seções Atrasadas, Com prazo (por data), Sem prazo, Concluídas (recolhida). Campo de busca restrito à categoria (`SearchRules.matchesTask` + filtro de categoria).
- "+" cria "Nova tarefa" com a categoria pré-preenchida.
- Correspondência de categoria por `SearchRules.fold` (ignora acento/maiúsculas), igual à unicidade de `CategoryRules`.
- Regra pura em `domain/`: `CategoryRules.overview(tasks, category, nowMillis, zone): CategoryOverview`.

## 2. Filtro por categoria na aba Tarefas

- Chips de categoria abaixo dos filtros existentes (`TaskFilter`), rolagem horizontal, com "Todas". Combina com o filtro atual (Ativas/Concluídas/Atrasadas).

## 3. Sugestões de categoria

- Em Ajustes → Categorias, linha "Sugestões" com chips para nomes que **não existem** ainda: Manutenção, Casamento, Encomendas (Compras e Estudos já são padrão). Tocar cria via `addCategory`. Não semear automaticamente em bancos existentes (respeita a lista do usuário).

## 4. Checklist na tarefa

- Tabela `checklist_items` (SPEC 012). No editor: seção "Itens" com campo "Adicionar item" (Enter adiciona e mantém o foco — fluxo de lista de compras), checkbox por item, excluir (tombstone com "Desfazer"), reordenar com subir/descer.
- `TaskCard`: indicador "3/8" quando há itens.
- Regras: texto 1..200, até 100 itens por tarefa; concluir a tarefa **não** marca itens; marcar todos os itens **não** conclui a tarefa (sugere com snackbar "Todos os itens marcados. Concluir tarefa?").
- Recorrência: a próxima ocorrência copia os itens desmarcados (SPEC 008).
- Busca global passa a considerar o texto dos itens (`SearchRules.matchesTask` ganha os itens).
- Backup v3 inclui os itens (SPEC 012).
- Repositório: métodos em `TaskRepository` (`observeChecklist(taskId)`, `addChecklistItem`, `setChecklistItemChecked`, `editChecklistItem`, `deleteChecklistItem`, `moveChecklistItem`) — todos atualizam `task.updatedAt` na mesma transação.

## Aceites

Evidência: `:app:testDebugUnitTest` (115 testes, 0 falhas) em 2026-11-20, JBR do Android Studio. Instrumentados só compilados.

- [x] `CategoryRules.overview` testado (acentos/maiúsculas, atrasadas, agendadas, sem prazo, concluídas em ordem de conclusão): `CategoryRulesTest`.
- [~] Filtro por chips combina com filtros existentes: os chips e a combinação já existem na aba Tarefas (SPEC 007, código lido, não coberto por teste de UI). Não verificado em tela.
- [x] Sugestões não duplicam nomes existentes, sem diferença de acento/maiúscula: `CategoryRulesTest.suggestions`. O cadastro usa o mesmo `CategoryRules.validateName` de antes.
- [~] Checklist: adicionar/marcar/reordenar/excluir com tombstone, cópia desmarcada na próxima ocorrência, busca e backup. A busca está coberta por `ChecklistRulesTest`. Adicionar/marcar/editar/mover/excluir/desfazer, o limite de 100 itens e a cópia da recorrência estão em `ChecklistRepositoryTest` (5 testes, **compilados, NÃO executados**). O backup ida e volta com itens é coberto pela SPEC 012 (não reexecutado aqui).
- [ ] Manual: lista de compras com 20 itens no Moto G84 com teclado aberto; TalkBack lê "arroz, marcado". NÃO FEITO.

## Fora de escopo

Projetos/subprojetos hierárquicos, tags múltiplas por tarefa, campos específicos por assunto (código de rastreio, orçamento), rastreamento automático de encomendas, compartilhar lista de compras.

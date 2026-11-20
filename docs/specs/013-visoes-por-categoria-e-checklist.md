# SPEC 013 — Visões por categoria e checklist (M1.5, WP5)

**Status:** Planejado. Checklist confirmado no escopo; depende do WP0 (tabela `checklist_items`).

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

## 5. Subtarefas — decisão pendente

Pedido do usuário (ideia 003, 2026-11-19, ID 2026-11-19-007): "opção de criar subtarefas". Duas leituras:

- **(a) Checklist "promovível":** usa o checklist da §4; um item pode virar tarefa própria (com prazo, lembrete e categoria), mantendo o vínculo com a tarefa de origem. Cabe neste escopo; exige só a regra de conversão (item marcado como convertido, sem apagar, como `INBOX -> CONVERTED`).
- **(b) Hierarquia de tarefas** (`parentId`): contraria o "Fora de escopo" abaixo, muda o schema (v4) e exige ADR.

Recomendação: entregar primeiro o checklist (§4) e decidir entre (a) e (b) com uso real. Nada é implementado antes da decisão do dono do produto.

## Aceites

- [ ] `CategoryRules.overview` testado (acentos/maiúsculas, atrasadas, concluídas).
- [ ] Filtro por chips combina com filtros existentes.
- [ ] Sugestões não duplicam nomes existentes (`CategoryRules.validateName`).
- [ ] Checklist: adicionar/marcar/reordenar/excluir com tombstone; cópia desmarcada na próxima ocorrência; busca encontra item; backup ida e volta.
- [ ] Manual: lista de compras com 20 itens no Moto G84 com teclado aberto; TalkBack lê "arroz, marcado".

## Fora de escopo

Projetos/subprojetos hierárquicos, tags múltiplas por tarefa, campos específicos por assunto (código de rastreio, orçamento), rastreamento automático de encomendas, compartilhar lista de compras.

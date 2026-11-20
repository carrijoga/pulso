# SPEC 014 — Widgets múltiplos e configuráveis

**Status:** Planejado (backlog, fora do plano M1.5). Nada implementado. Origem: ideias 001 e 002 (Colecionador de Ideias, 2026-11-19, IDs 2026-11-19-003 e 2026-11-19-004). Relaciona-se com: [SPEC 003](003-widget.md), [SPEC 001](001-captura-e-inbox.md), [SPEC 011](011-captura-por-voz.md).

## Objetivo

Hoje existe um único widget (`TodayWidget`, SPEC 003). O usuário relata que ele corta informações e não rola a lista, e pede:

1. Poder adicionar **mais de um widget, cada um com uma função**.
2. **Configurar cada instância**: quantidade de itens, mostrar só as tarefas do dia, outras opções a definir.
3. Um **widget de anotações rápidas**: botão "Adicionar nota" que abre um painel para digitar e salva ao concluir.

## Pré-requisito: reproduzir o problema relatado

A SPEC 003 registra o widget largo como validado no Moto G84 com lista rolável (`LazyColumn`, até 30 itens em `widget/TodayWidget.kt`). O relato do usuário diverge. Antes de qualquer implementação:

- Reproduzir no Moto G84: anotar tamanho (células), launcher e quantas tarefas existiam.
- Atualizar a SPEC 003 com a evidência (corrigir o aceite se a rolagem falhar; ou registrar que o problema é outro, por exemplo o modo estreito, que só mostra contagem).

## Proposta

### Widget "Tarefas" (o atual, configurável)

- Mantém o `TodayWidget` como base; a configuração vale **por instância** (`GlanceId`).
- Primeiro corte proposto de opções:
  - **Lista:** "Agenda" (atual, `TaskRules.agenda`) ou "Somente hoje" — usar o mesmo critério da aba Hoje (`TaskRules.isDueTodayOrOverdue`, que inclui atrasadas), sem regra nova.
  - **Quantidade máxima de itens** (ex.: 5, 10, 30).
- Persistência por instância: estado do Glance (`PreferencesGlanceStateDefinition` / `updateAppWidgetState`), sem tabela nova no Room — é preferência de exibição, não dado do usuário, e não entra no backup.
- Tela de configuração: `android:configure` no `appwidget-provider` apontando para uma Activity própria; permitir reconfigurar (`widgetFeatures="reconfigurable"`, API 28+).

### Widget "Anotação rápida" (novo)

- Pela invariante **Capture first** (AGENTS.md §1), a nota vai para a **Entrada** como `InboxItem`; nenhuma entidade nova.
- O "drawer" **abre a `CaptureActivity`** (já translúcida e usada pelo `+` do widget atual). Glance não oferece campo de texto em superfície remota, então não há painel de digitação dentro do widget.
- Conteúdo: botão "Adicionar nota" (alvo ≥ 48dp, `contentDescription`). Botão de voz opcional reaproveitando `EXTRA_START_VOICE` (cobre o P2 da SPEC 011).
- Segundo `GlanceAppWidgetReceiver` + XML próprio no manifesto.

## Decisões pendentes (dono do produto)

1. Opções do primeiro corte: só "Somente hoje" + quantidade, ou incluir filtro por categoria (depende da SPEC 013)?
2. Nota rápida: confirmar destino Entrada (recomendado) em vez de entidade "Nota".
3. Widget de anotação: só botão, ou também listar as últimas capturas da Entrada?

## Aceites

- [ ] Problema do corte/rolagem reproduzido (ou refutado) no Moto G84 e SPEC 003 atualizada.
- [ ] Dois widgets do tipo "Tarefas" com configurações diferentes coexistem e sobrevivem a reinício do processo.
- [ ] "Somente hoje" mostra o mesmo conjunto da aba Hoje (teste unitário reaproveitando `TaskRules`).
- [ ] Widget "Anotação rápida" abre a captura e a nota aparece na Entrada, offline.
- [ ] Ambos atualizam após mudanças no app (`TaskChangeListener`/Inbox) e passam TalkBack e fonte 200% no Moto G84.

## Fora de escopo

Editar texto dentro do widget, sincronização, widgets de hábitos ou de calendário (podem virar novas opções depois).

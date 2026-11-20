# SPEC 017 — Integração com o Google Calendar

**Status:** Planejado (backlog). Nada implementado. Origem: ideia 004 (Colecionador de Ideias, 2026-11-20, ID 2026-11-20-008). Relaciona-se com: [SPEC 010](010-calendario-semanal.md), [SPEC 013](013-visoes-por-categoria-e-checklist.md), ADR 0003 (sync futuro), ADR 0004 (privacidade).

## Objetivo

Ideia original: "Pulso, conexão com calendário Google." Ainda não está decidido o que isso significa:

- **A. Leitura:** mostrar compromissos do Google Calendar junto das tarefas (por exemplo, na aba Semana).
- **B. Envio:** criar no Google Calendar as tarefas com prazo.
- **C. Sincronização bidirecional:** mudanças em um lado aparecem no outro. É a mais cara e envolve conflitos, que o ADR 0003 adia.

## Restrições (AGENTS.md §1 e §0.4)

- Offline-first: a integração não pode bloquear o uso sem rede.
- O app não declara `INTERNET` (verificado em 2026-11-20); sem login nem backend sem pedido explícito.
- Privacidade: ADR 0004 exige opt-in e clareza de provedor e retenção antes de qualquer envio remoto.
- **Conflito com decisão vigente:** a SPEC 010 lista leitura/escrita do `CalendarContract` como fora de escopo, e a SPEC 013 diz que não há integração externa. Qualquer caminho exige ADR e atualização dessas specs.

## Proposta (recomendação, não decisão)

Começar por **A via `CalendarContract`, somente leitura**: lê os calendários que a conta Google já sincroniza no aparelho, sem internet nem login próprio, com permissão `READ_CALENDAR` pedida só quando o usuário ativar o recurso. Eventos lidos na hora, sem gravar no banco (sem mudança de schema nem de backup). Eventos não viram tarefa automaticamente; conversão, se existir, é ação explícita (invariante "Inbox != Task").

O caminho via API do Google Calendar (OAuth, `INTERNET`, tokens) só se justifica se B ou C forem desejados, e começa por ADR.

Ponto de entrada natural na UI: linha "Calendários" em Ajustes ([SPEC 016](016-ajustes.md)), hoje inexistente.

## Decisões pendentes (dono do produto)

1. Objetivo: A, B ou ambos?
2. Basta a agenda do aparelho (`CalendarContract`) ou é preciso alcançar o Google fora do aparelho (API)?
3. Eventos lidos na hora ou salvos no banco (muda schema e backup)?
4. Eventos podem virar tarefa? Como (ação explícita)?
5. Todos os calendários ou só os escolhidos?
6. Quem mais usa o app (peso da privacidade)?
7. ADR revogando o "fora de escopo" da SPEC 010.

## Aceites (se a opção A via `CalendarContract` for escolhida)

- [ ] ADR registrando a mudança de escopo; SPEC 010 atualizada.
- [ ] Recurso desligado por padrão; permissão pedida só ao ativar; recusa não quebra nada.
- [ ] Eventos aparecem na Semana visualmente distintos de tarefas, sem afetar contagens nem widget.
- [ ] Sem rede e sem conta Google, o app funciona igual a hoje.
- [ ] Nenhum dado de calendário entra no banco nem no backup.

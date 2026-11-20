# Roadmap do Pulso

Legenda: `[x]` implementado no código e coberto por teste automatizado ou verificação no emulador; `[~]` parcial; `[ ]` planejado. "Validado no Moto G84" só depois de teste real no aparelho.

## M0 — Base técnica (v0.1.0)

- [x] Compose e navegação básica; Room, repository, DataStore.
- [x] Caixa de Entrada/captura rápida e conversão transacional em Task.
- [x] Tarefas: editar/concluir/arquivar entrada/exclusão lógica, filtro e busca.
- [x] Widget Glance e callbacks; WorkManager aproximado.
- [x] Backup export/import e workflow CI; AGENTS.md e specs.
- [x] Build + lint + testes unitários executados localmente (Windows, JBR 21) em 2026-11-19.
- [x] CI: workflow alinhado ao compileSdk 37 e com job de testes instrumentados; executado no GitHub Actions em 2026-11-19 (push de `main` 0f4dc0f e tag `v0.3.0`): "Android checks" (`verify` e `instrumented`) e "Release" passaram; prerelease "Pulso 0.3.0" publicado com `pulso-v0.3.0.apk` e `.sha256`.
- [~] Teste em Moto G84: instrumentados, telas, permissão e lembretes com ações validados (2026-11-19); widget grande e captura pelo widget validados (com 2 correções); atalho do ícone OK; TalkBack auditado com 7 correções reconferidas (falta navegação por gestos); fonte grande (200%) OK.
- [x] Testes instrumentados de Room, migração e backup (12 testes, emulador API 37 e Moto G84). Widget ainda sem teste automatizado.
- [~] Acessibilidade: rótulos e alvos de 48dp auditados com TalkBack no Moto G84 (7 correções); fonte grande (200%) OK; falta navegação por gestos.
- [x] Notificações reagendadas após importação (coberto por teste de backup).
- [x] Ícone do app, splash, atalho "Capturar", prévia do widget e ícone de notificação ligados ao manifesto.
- [x] Confirmação antes de excluir tarefa e "Desfazer" para exclusão/arquivamento.

## M1 — MVP utilizável diariamente

- [x] Criação de tarefa sem passar pela Inbox ("Nova tarefa" na aba Tarefas).
- [x] Busca global por tarefas (título, observações, categoria, links) e Inbox pendente/arquivada, sem acento/maiúsculas.
- [x] Campo de links e categorias personalizáveis estruturadas (lista gerenciável; renomear propaga para tarefas).
- [x] Horário do prazo configurável por tarefa e horário padrão nos Ajustes; adiar lembrete ("Adiar 1h"/"Amanhã" na notificação); estados de permissão claros.
- [x] Revisão semanal guiada com histórico de reagendamento.
- [x] Widget pequeno/médio/grande (SizeMode.Responsive). Validado no Moto G84 nos tamanhos estreito, baixo e grande (lista rolável).
- [~] Melhorias de acessibilidade, UX e confiabilidade (ver lacunas em `docs/TESTING.md`).
- [x] Redesenho visual "Brasa & Tinta" (telas, editor em bottom sheet, captura, widget, ícones), verificado no emulador; falta Moto G84/TalkBack.

## M1.5 — Rotinas

Plano, pacotes de trabalho e decisões pendentes: [`docs/PLANO-M1.5.md`](PLANO-M1.5.md). Specs 008–013 planejadas; ADRs 0005–0008 aceitas em 2026-11-19.

- [x] Schema v3, backup v3 e testes de migração mais abrangentes (WP0 implementado e validado no emulador; migração real v2→v3 conferida; falta só conferir no Moto G84) — [SPEC 012](specs/012-schema-v3-backup-e-migracoes.md) (WP0, base dos demais).
- [~] Tarefas recorrentes (WP1a regras puras e WP1 repositório/editor/rótulo/Desfazer implementados; unitários passando; instrumentados executados no emulador API 37 após a revisão, 2026-11-19; falta Moto G84, widget e "Pular esta vez") — [SPEC 008](specs/008-tarefas-recorrentes.md).
- [~] Hábitos (WP2 implementado: repositório, seção em Hoje, sobreposição "Hábitos" por Hoje e Ajustes; unitários passando; instrumentados executados no emulador API 37 após a revisão, 2026-11-19; telas não conferidas em emulador/Moto G84; lembretes P2 fora) — [SPEC 009](specs/009-habitos.md).
- [~] Calendário semanal (WP3 implementado: aba "Semana", projeções de recorrência, linha de hábitos, "Mover para…"; unitários passando, 14 novos; falta validação manual/TalkBack e Moto G84) — [SPEC 010](specs/010-calendario-semanal.md).
- [~] Captura por voz (WP4 implementado: botão nas capturas, atalho, aviso de privacidade; validado no emulador API 37 sem fala real; falta Moto G84; botão no widget fica pendente, P2) — [SPEC 011](specs/011-captura-por-voz.md).
- [ ] Organização por compras, estudos, manutenção, casamento, encomendas (tela por categoria, checklist) — [SPEC 013](specs/013-visoes-por-categoria-e-checklist.md).

## Backlog — ideias incorporadas (sem milestone)

- [ ] Widgets múltiplos e configuráveis (lista "Somente hoje", quantidade de itens) e widget de anotação rápida para a Entrada; antes, reproduzir o relato de corte/rolagem no Moto G84 — [SPEC 014](specs/014-widgets-configuraveis.md).
- [ ] Subtarefas: decidir entre checklist promovível a tarefa ou hierarquia (ADR) — [SPEC 013 §5](specs/013-visoes-por-categoria-e-checklist.md).

## M2 — Sincronização em nuvem

- [ ] Definir modelo de conta, autenticação, migração de usuário local.
- [ ] API ASP.NET Core + PostgreSQL (proposta; nenhuma decisão de hospedagem tomada).
- [ ] Outbox local persistente, checkpoints, idempotência, conflitos e tombstones.
- [ ] Sync incremental transparente, sem bloquear uso offline.
- [ ] Testes com dois dispositivos, relógios divergentes, perdas de rede e deletes.

## M2.5 — Organização assistida por IA

- [ ] Propor classificação e extração de data/prioridade sem ação automática.
- [ ] Opt-in de envio, transparência do provedor, orçamento e privacidade.
- [ ] Revisão humana antes de persistir sugestões.
- [ ] Alternativa sem IA sempre disponível.

## M3 — Acesso pelo PC

- [ ] Dashboard web/desktop consumindo a mesma API; manter Android offline-first.

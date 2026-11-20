# SPEC 003 — Widget Android

**Status:** Implementado (estreito com contagem; largo com lista rolável); validado no Moto G84 (launcher Motorola).

## Contrato

- Widget mostra até 30 tarefas não concluídas (`MAX_LIST_ITEMS` em `widget/TodayWidget.kt`; a versão anterior desta spec dizia três), lidas do mesmo repository da UI, com "+N no app" quando houver mais.
- Círculo (alvo de 48dp) conclui a tarefa; título abre o app; `+` abre captura rápida. Tamanhos: estreito (contagem + `+`); largo (a partir de 250 dp) com lista rolável que ocupa a altura disponível.
- Alterações em tarefas feitas pelo app provocam atualização do widget.
- Nenhuma exigência de servidor, login ou permissão de internet.

## Aceites

- [x] Receiver no Manifest e metadata XML.
- [x] `GlanceAppWidget` e `ActionCallback` ligados ao repository.
- [x] `CaptureActivity` abre a partir do widget.
- [x] Teste real no Moto G84 (2026-11-19): estreito, grande, baixo (lista rolável), redimensionamento e reinício do processo.
- [~] Layout responsivo pequeno/médio/grande implementado; falta validar acessibilidade em launcher real.
- [ ] Respeitar Material You/dark theme e estados de erro.

## Divergência em aberto (2026-11-19)

O usuário relata que o widget principal corta informações e não rola a lista, o que contradiz o aceite acima. Ainda não reproduzido. Investigação e evolução (vários widgets, configuração por instância, widget de anotação rápida) em [SPEC 014](014-widgets-configuraveis.md).

## Riscos

Widget é uma superfície remota: atualizações são controladas pelo sistema. Não assumir renderização contínua nem usar Compose UI comum diretamente.

# SPEC 003 — Widget Android

**Status:** Implementação inicial de widget médio, aguardando teste no launcher do Moto G84.

## Contrato

- Widget mostra até três tarefas não concluídas, lidas do mesmo repository da UI.
- Círculo (alvo de 48dp) conclui a tarefa; título abre o app; `+` abre captura rápida. Tamanhos: pequeno (contagem), médio (3), grande (6).
- Alterações em tarefas feitas pelo app provocam atualização do widget.
- Nenhuma exigência de servidor, login ou permissão de internet.

## Aceites

- [x] Receiver no Manifest e metadata XML.
- [x] `GlanceAppWidget` e `ActionCallback` ligados ao repository.
- [x] `CaptureActivity` abre a partir do widget.
- [ ] Teste real no Moto G84, incluindo redimensionamento e reinício do processo.
- [~] Layout responsivo pequeno/médio/grande implementado; falta validar acessibilidade em launcher real.
- [ ] Respeitar Material You/dark theme e estados de erro.

## Riscos

Widget é uma superfície remota: atualizações são controladas pelo sistema. Não assumir renderização contínua nem usar Compose UI comum diretamente.

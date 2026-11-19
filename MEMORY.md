# MEMORY.md — contexto durável do projeto (não memória pessoal)

> Atualizado em 2026-11-19. Use para retomar sessões de IA. **Não é evidência de teste.** Atualize após mudanças concretas.

## Visão confirmada

- Nome provisório: Pulso; aplicativo Android nativo Kotlin/Compose.
- Dispositivo-alvo inicial: Moto G84 5G.
- Fluxo prioritário: capturar anotação sem metadados -> Inbox -> organizar depois.
- MVP offline-first, sem login/serviço remoto; widget Android e notificações locais.
- Futuro: sincronização em nuvem para acesso pelo PC via API, possível ASP.NET Core; organização por IA com prévia e confirmação.

## Implementação presente no código

- Room **v2** (`Migration1To2` aditiva): `InboxEntity`, `TaskEntity` (+`links`, `snoozedUntil`), `CategoryEntity`, `RescheduleEntity`. UUID, timestamps, tombstones. Schemas v1/v2 em `app/schemas`.
- Separação `domain/data/ui/widget/reminders`; repository e DI manual (`AppContainer`).
- Compose: Hoje, Entrada, Tarefas, Ajustes + sobreposições Buscar e Revisão semanal; captura rápida translúcida (`CaptureActivity`).
- M1 implementado (SPEC 007): nova tarefa direta, links, categorias gerenciáveis, prazo com hora, adiar lembrete, estados de permissão, busca global, revisão semanal com histórico de reagendamento, widget responsivo.
- Backup JSON v2 (lê v1), aditivo e validado antes de escrever.
- Identidade visual "Brasa & Tinta" aplicada (ver `docs/DESIGN.md`): tema/motion em `ui/theme/Theme.kt`, componentes em `ui/components/Components.kt`, editor em bottom sheet, widget ordenado por `TaskRules.agenda`. Regra: mudanças visuais em `ui/` não alteram domain/data/reminders nem a API do ViewModel.

## Estado de validação

- **Build local: EXECUTADO** em 2026-11-19 (Windows, Gradle 9.6.0, JBR 21 do Android Studio; `java` do PATH é 8 e não serve): `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passando, 15 testes unitários.
- **Instrumentados: EXECUTADOS** no emulador API 37 via `am instrument`: 12 testes (repositório, migração 1→2, backup) passando.
- **Migração real:** APK v2 instalado sobre banco v1 do emulador sem perda.
- **Após o redesenho (2026-11-19):** build/lint/15 unitários e 12 instrumentados passando de novo; tela Hoje conferida por screenshot.
- **CI:** workflow atualizado (SDK 37, job de emulador), **NÃO EXECUTADO** (sem remoto/commits; repositório git está na pasta pai `dev/`).
- **Moto G84: NÃO TESTADO.** Notificações com ações e widget em launcher real: não testados.

## Pendências com maior prioridade

1. Testar no Moto G84: notificações (permissão concedida/negada, ações Concluir/Adiar), widget nos 3 tamanhos, captura pelo atalho e widget.
2. Publicar o repositório e rodar o CI.
3. TalkBack/fonte grande nas telas redesenhadas.
4. Decidir nome final.
5. M1.5: recorrência, calendário semanal, voz.

## Não decidir sem solicitar

- Provedor de autenticação, hospedagem, custo de API, política de retenção, envio de dados para IA, compartilhamento entre usuários.

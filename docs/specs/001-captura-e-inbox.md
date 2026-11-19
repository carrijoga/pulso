# SPEC 001 — Captura rápida e Caixa de Entrada

**Status:** Implementação inicial no código; aguardando validação Android/usuário.

## Motivação

Usuário lembra de uma compra, estudo ou manutenção e precisa registrar em poucos segundos, sem preencher formulário. A Inbox não é uma Task.

## Fluxos

- Capturar qualquer texto não vazio até 500 caracteres; remover espaços das extremidades.
- Criar `InboxItem` `INBOX` com UUID e timestamps locais persistidos em UTC.
- Mostrar capturas mais recentes primeiro e conservar após fechar/abrir app.
- Arquivar: estado `ARCHIVED`, sem apagar histórico; "Desfazer" imediato e "Devolver para a entrada" pela busca.
- Organizar: diálogo opcional de título/notas/categoria/prioridade/prazo; transação cria `TaskItem` e muda origem para `CONVERTED`, com `convertedTaskId`.
- Captura rápida via `CaptureActivity` (painel translúcido sobre o launcher, atalho "Capturar" e widget) não exibe formulário de metadados.

## Aceites verificáveis

- [x] `TaskRules.validateCapture` rejeita vazio e strings > 500 caracteres.
- [x] `TaskRepository.capture` persiste no Room e a UI reage via Flow.
- [x] Uma conversão válida cria uma tarefa e preserva a origem de forma transacional.
- [x] `archiveInbox` não elimina dados.
- [x] Teste instrumentado de conversão transacional, arquivar/restaurar (Room em memória).
- [ ] Teste instrumentado de persistência após reiniciar processo.
- [ ] Teste de acessibilidade e digitação no Moto G84.
- [ ] Garantir concorrência de conversão com teste instrumentado.

## Fora de escopo

IA, classificação automática, parsing de linguagem natural, entrada por voz, sincronização.

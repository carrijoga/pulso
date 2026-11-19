# SPEC 005 — Organização com IA (planejamento)

**Status:** Planejado. **Nenhum modelo, prompt externo ou SDK de IA está integrado.**

## Fluxo de consentimento

Usuário escreve: "Comprar ração sábado, revisar xUnit e ver o mecânico mês que vem". Em ação explícita, um serviço opcional propõe itens com título, categoria, tipo, data estimada, origem textual e confiança. UI mostra *prévia editável*. Usuário pode aceitar, corrigir ou descartar. Só após confirmação as entidades são persistidas.

## Restrições

- Não enviar dados de Inbox sem opt-in e explicação do provedor/retencão.
- Não inventar datas quando texto ambíguo: sugerir "confirmar data".
- Não excluir/modificar informações anteriores automaticamente.
- Não exigir IA para funcionamento central do app.
- Proteger custos com limites por usuário, cache e rate limiting.
- Log sem conteúdo sensível por padrão.

## Aceites futuros

- IA responde em estrutura validada; entradas não confiáveis não são comandos.
- Usuário pode cancelar ou editar cada proposta individualmente.
- O fluxo offline continua disponível sem internet ou API key.

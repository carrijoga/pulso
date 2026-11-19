# SPEC 004 — Sincronização em nuvem (planejamento, NÃO implementar no MVP)

**Status:** Planejado, sem endpoints, credenciais ou serviços.

## Meta

Manter app Android totalmente funcional offline e sincronizar com uma API para permitir acesso no PC, preservando capturas, tarefas, históricos e deletes.

## Requisitos de implementação futura

- Conta vinculada a dados anônimos locais, com fluxo seguro de migração/mesclagem.
- Fonte de verdade de leitura no Android continua sendo Room; escrita gera evento numa outbox local transacional.
- IDs UUID imutáveis; idempotency keys em operações; versão/etag ou sequências de servidor.
- Tombstones propagados de maneira incremental, sem ressuscitar exclusões.
- Conflitos definidos por domínio (conclusão, edição, mudanças simultâneas); não confiar em relógios dos clientes.
- Políticas de retry, backoff, lote, conectividade, limite de payload e proteção a perda de rede.
- API autenticada (possível ASP.NET Core) e banco (possível PostgreSQL), decisão final por ADR.

## Critérios de aceitação futuros

- Dois aparelhos e um navegador convergem após período offline, sem perder histórico.
- Delete em um aparelho não reaparece após sincronizar.
- API repete requisição sem criar duplicados.
- Falhas parciais, fusos, mudança de horário e relógios divergentes são cobertos.

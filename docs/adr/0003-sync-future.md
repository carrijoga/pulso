# ADR 0003 — Preparação de sync sem implementar servidor

**Status:** Aceita parcialmente como diretriz · 2026-11-19

## Decisão atual

Persistir UUIDs, timestamps e tombstones em entidades; consumir dados por `TaskRepository` e não diretamente de Room na UI. Separar `TaskChangeListener` para side effects nativos.

## Não decidido

Autenticação, protocolo HTTP, estrutura de outbox, algoritmo de conflitos, política de merge, servidor, hospedagem, web app e migração de contas.

## Consequências

Não há sincronização nesta versão. Timestamps de cliente sozinhos não bastam; futuro ADR deve considerar versionamento no servidor, tombstone GC e idempotência. Preservar migrações a partir de v1.

# ADR 0008 — Schema v3 consolidado para o M1.5

**Status:** Aceita · 2026-11-19 · ver SPEC 012 e `docs/PLANO-M1.5.md`

## Contexto

Recorrência, hábitos e checklist precisam de tabelas novas. O M1.5 será implementado por vários agentes, possivelmente em paralelo. Se cada feature criar sua própria migração, os números de versão do Room e os schemas exportados entram em conflito, e cada versão intermediária que chega a um aparelho com dados reais fica congelada para sempre.

## Decisão

Um único pacote (WP0) cria todas as entidades, DAOs básicos, tipos de domínio, `Migration2To3` aditiva, schema `3.json`, backup formato v3 e os testes de migração/backup. As features (WP1, WP2, WP5) só adicionam queries e regras. Após o merge em `main`, o schema v3 está congelado; mudanças viram v4.

Sem `FOREIGN KEY` nas tabelas novas, mantendo o padrão atual (`task_reschedules`): tombstones e importação aditiva com `INSERT IGNORE` ficariam mais frágeis com restrições de FK; a integridade referencial é validada na importação e no repositório.

## Alternativas

- **Uma migração por feature (v3, v4, v5):** menos design antecipado, mas exige serializar o trabalho de dados e multiplica caminhos de migração a testar.
- **FOREIGN KEY com `ON DELETE CASCADE`:** contradiz a regra de não apagar dados (só tombstone).

## Consequências

- Requer decidir o escopo do M1.5 (especialmente checklist) antes do WP0.
- Tabelas podem existir antes da UI que as usa; isso não muda o comportamento visível.
- `MigrationTest` passa a cobrir 1→3, 2→3 e todas as versões exportadas.

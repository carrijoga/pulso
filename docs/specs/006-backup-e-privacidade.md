# SPEC 006 — Backup e privacidade local

**Status:** Implementação inicial, sem testes instrumentados.

## Comportamento

Exportar JSON com `format=pulso-backup`, versão 2 (importa também v1), categorias, histórico de reagendamento, todos os registros de Inbox (incluindo convertidos/arquivados) e Tasks (incluindo tombstones). Importar por Storage Access Framework, validar versão/estrutura/status/prioridade e realizar **INSERT IGNORE** por UUID numa transação (não substitui registros locais existentes). Reagendar lembretes de novas tarefas importadas com prazo futuro. Sem autenticação, analítica ou conexão remota.

## Aceites

- [x] Backup preserva IDs, estados e datas.
- [x] Reimportar mesmo arquivo não cria novas linhas.
- [x] Arquivo inválido/restrito é rejeitado antes de escrever.
- [x] Importação reprograma lembretes futuros das tarefas novas.
- [x] Testes instrumentados: ida e volta, reimportação, v1, inválidos sem escrita e 1000 tarefas.
- [ ] Export/import de versões futuras e políticas de migração.
- [ ] Fluxo seguro de cópia de segurança automática opcional, apenas com consentimento.

## Riscos

O JSON exportado não é criptografado. Não logar conteúdo ou enviar para terceiros. O Android Auto Backup e a transferência entre aparelhos permanecem desligados (`allowBackup=false` + `data_extraction_rules.xml`).

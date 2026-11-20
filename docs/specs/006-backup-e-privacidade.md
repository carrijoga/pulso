# SPEC 006 — Backup e privacidade local

**Status:** Implementado (formato v3); testes instrumentados no emulador. Criptografia e backup automático seguem pendentes.

## Comportamento

Exportar JSON com `format=pulso-backup`, versão 4 (importa também v1, v2 e v3; v4 acrescenta `parentId` das subtarefas, SPEC 015), com `exportedAt`, `appVersionName` e `schemaVersion` informativos; categorias, histórico de reagendamento, séries de recorrência, hábitos, check-ins, itens de checklist, todos os registros de Inbox (incluindo convertidos/arquivados) e Tasks (incluindo tombstones e `seriesId`). Arquivo com versão maior que a suportada é rejeitado com mensagem própria ("versão mais nova do Pulso"). Importar por Storage Access Framework, validar versão/estrutura/status/prioridade, enums, limites e referências (`seriesId`, `habitId`, `taskId` devem existir no arquivo ou no banco; senão "Backup inconsistente: referência ausente.") e realizar **INSERT IGNORE** por UUID numa transação (não substitui registros locais existentes; check-in duplicado para o mesmo hábito/dia com outro UUID é ignorado e o local prevalece). Nome sugerido do arquivo: `pulso-backup-v3-AAAA-MM-DD.json`. Reagendar lembretes de novas tarefas importadas com prazo futuro. Sem autenticação, analítica ou conexão remota.

## Aceites

- [x] Backup preserva IDs, estados e datas.
- [x] Reimportar mesmo arquivo não cria novas linhas.
- [x] Arquivo inválido/restrito é rejeitado antes de escrever.
- [x] Importação reprograma lembretes futuros das tarefas novas.
- [x] Testes instrumentados: ida e volta, reimportação, v1, inválidos sem escrita e 1000 tarefas.
- [x] Formato v3 (séries, hábitos, check-ins, checklist), leitura de v1/v2/v3 com fixtures, rejeição de versão 4 e de referência ausente sem escrever (SPEC 012; instrumentados no emulador API 37).
- [ ] Políticas de migração para versões futuras além da v3.
- [ ] Fluxo seguro de cópia de segurança automática opcional, apenas com consentimento.

## Riscos

O JSON exportado não é criptografado. Não logar conteúdo ou enviar para terceiros. O Android Auto Backup e a transferência entre aparelhos permanecem desligados (`allowBackup=false` + `data_extraction_rules.xml`).

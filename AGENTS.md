# AGENTS.md — contrato de desenvolvimento assistido por IA

**Âmbito:** todo o repositório Pulso. Idioma de produto/documentação: pt-BR. Código, identifiers, commits e nomes de APIs: inglês. Este arquivo é a fonte canônica de instruções para agentes (Codex, Claude, Copilot, Gemini ou outros).

## 0. Antes de qualquer mudança

1. Leia `README.md`, `MEMORY.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` e a spec relevante em `docs/specs/`.
2. Examine os arquivos relacionados e os testes existentes; **não presuma que a documentação é igual ao código**.
3. Defina o escopo menor verificável e critérios de aceitação. Se a mudança tocar persistência/fluxo, descreva impacto nos dados e no widget.
4. Não adicione backend, login, IA, telemetria nem bibliotecas de DI sem solicitação explícita.
5. Se documentos divergirem do código, mencione a divergência e corrija-a com evidência, não com suposição.

## 1. Invariantes do produto

- **Capture first:** `InboxItem` é uma captura livre; não requer categoria, prioridade ou prazo.
- **Inbox != Task:** converter uma entrada em tarefa não apaga o registro de origem. `INBOX -> CONVERTED` numa transação.
- **Local-first:** as telas observam Room; não exigem rede. Todas as ações importantes funcionam offline.
- **Não perder dados:** sem `fallbackToDestructiveMigration`, sem resetar banco, sem sobrescrever importações existentes; exclusão de tarefa usa tombstone (`deletedAt`).
- **Sync-ready sem sync fictícia:** UUID estável; `createdAt`, `updatedAt`, `deletedAt` para entidades. Timestamps ajudam, mas não são protocolo de conflitos. Não chame de sincronizado.
- **IA futura é sugeridora, não executora:** nunca enviar anotações privadas ou criar compromissos automaticamente sem consentimento e confirmação.
- **Honestidade:** marque `Implementado`, `Parcial` ou `Planejado`. Não diga que APK foi validado se não compilou.

## 2. Estrutura e dependências

- `domain/`: modelos e regras puras. NÃO importar Android, Room, Compose, WorkManager.
- `data/`: Room, DAO, mapeamento, repositório. Única fonte de escrita do domínio persistido.
- `ui/`: Compose + ViewModel. Não escrever SQL ou usar DAOs nas telas.
- `widget/`: Glance; usa o mesmo repositório, não guarda sua própria cópia de tarefas.
- `reminders/`: agendamento de lembretes e worker (inexato).
- `app/src/test/`: regras puras e testes de regressão.
- `docs/specs/`: comportamento esperado e critérios de aceitação, por funcionalidade.
- Um módulo Android é intencional no MVP. Separar módulos apenas com motivação e ADR.

## 3. Fluxo de trabalho obrigatório por tarefa

1. **Entender:** leia spec, dados, código, teste e precedentes ADR.
2. **Planejar:** enumere arquivos a tocar, riscos para persistência/offline, testes relevantes.
3. **Implementar:** pequena mudança coesa, sem refatorações não solicitadas. Evitar suprimir erros e logs de dados sensíveis.
4. **Testar:** execute `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` ou `gradlew.bat` no Windows. Se não for possível, marque **NÃO EXECUTADO** e explique o bloqueio.
5. **Revisar:** verificações de estado, nullability, tratamento de erro, permissão Android, UI em pt-BR, widget atualizado, histórico preservado.
6. **Documentar:** atualize a spec, `MEMORY.md` e/ou ADR apenas quando comportamento/decisão realmente mudar. Não marque checklist futuro como pronto sem evidência.
7. **Responder:** forneça resumo, arquivos alterados, testes executados (com resultados), limitações e próxima ação objetiva.

## 4. Qualidade e segurança

- Não cometer secrets, tokens, URLs de backend privado, chaves de assinatura ou dados pessoais reais.
- Não fazer push, publicar APK, instalar no aparelho nem modificar serviços externos sem permissão.
- Evite dependências sem justificativa; mantenha versões e canais estáveis.
- Alteração de schema exige migração explícita, teste de migração e update de documentação. Jamais apagar dados do usuário.
- Operações de importação devem validar formato e ser atômicas; teste casos inválidos e duplicados.
- WorkManager entrega lembretes **aproximados**, não no segundo exato. Documente permissões/limites.
- Cuidado com execução em background no Android, permissões 13+, versões do SDK e battery optimizations.
- Nunca implementar `AI auto-organize` sem revisão humana do resultado.

## 5. Definição de pronto (DoD)

- [ ] Aceites da spec atendidos; comportamento testado, sem mocks enganosos.
- [ ] Testes adicionados/ajustados para regras alteradas.
- [ ] Build/test/lint rodados, ou impossibilidade documentada com clareza.
- [ ] Estado offline continua correto; fluxo de backup preservado.
- [ ] Widget/Worker reavaliados quando tarefa ou prazo mudar.
- [ ] Documentação atualizada sem descrever o futuro como presente.
- [ ] PR pequeno com riscos, evidências, passos manuais e rollback.

## 6. Solicitações especiais

- Para implementar uma feature: procure `docs/specs/` e use um issue/PR pequeno.
- Para propor arquitetura: escreva ADR em `docs/adr/` (contexto, decisão, alternativas, consequências).
- Para sessão com agente novo: use `MEMORY.md` como índice de estado, não como diário de conversa; preserve apenas decisões confirmadas, pendências técnicas e resultados de validação.
- Instruções específicas de ferramentas podem existir em `CLAUDE.md`, `.github/copilot-instructions.md` e `GEMINI.md` como ponteiros, jamais cópias conflitantes.

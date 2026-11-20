# SPEC 016 — Ajustes: índice e subpáginas

**Status:** Implementado (índice e subpáginas, 2026-11-20). Linhas "Widgets" e "Sincronização": **Planejado** (apenas marcadores, sem função).

## Objetivo

Trocar a rolagem longa de seções da aba Ajustes por um índice de linhas agrupadas que abrem subpáginas, para achar cada ajuste sem rolar.

## Estrutura

- Grupo **App**: Aparência (valor: tema atual), Lembretes (valor: horário padrão "HH:mm", ou "Desativados" sem permissão de notificações), Categorias (valor: quantidade), Widgets (Planejado, [SPEC 014](014-widgets-configuraveis.md)).
- Grupo **Dados**: Backup, Privacidade, Sincronização (Planejado, [SPEC 004](004-sync-futuro.md)).
- Grupo **Sobre**: Sobre o Pulso (valor: versão do app).
- Subpáginas são sobreposições (`Overlay.SETTINGS_*` em `PulsoScreens`); o Voltar retorna ao índice, pois a aba continua Ajustes. Abrir uma categoria pela subpágina Categorias e voltar leva ao índice, não à subpágina (sem pilha de navegação).
- O conteúdo das subpáginas é o das antigas seções, sem mudança de comportamento. Os seletores de arquivo do backup ficam em `BackupSettings`, registrados só com a subpágina visível.
- A seção "Hábitos" saiu de Ajustes: hábitos abrem pela tela Hoje.

## Critérios de aceite

- Cada linha tem ícone, rótulo, valor à direita e seta; altura mínima 56 dp; a linha inteira é clicável; TalkBack lê "Rótulo, valor".
- Linhas Planejado: sem seta, esmaecidas, não clicáveis, valor "Em breve"; TalkBack lê "Widgets, em breve".
- Todas as ações antigas (tema, cores dinâmicas, horário padrão, permissão, categorias, exportar/importar, aviso de voz) seguem funcionando nas subpáginas.

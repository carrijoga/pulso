# Diretrizes visuais e experiência

- **Princípio:** capturar com um único campo; organizar depois, sem bloquear por metadados.
- **IA futura:** sugestão opcional; exibir prévia e pedir confirmação antes de criar qualquer tarefa.
- **Material 3:** respeitar tema dinâmico, modo escuro, escalas de fonte e acessibilidade.
- **Navegação atual:** Hoje, Entrada (badge de contagem), Tarefas, Ajustes; topo com marca + busca global; Revisão semanal como sobreposição; captura rápida independente (FAB, atalho do ícone e widget).
- **Hoje:** cartão de destaque (saudação, data, progresso do dia, sem gamificação); depois vencidas/hoje e próximas, sem urgência artificial.
- **Entrada:** campo aberto e itens pendentes; ações Organizar/Arquivar sempre explícitas.
- **Tarefa:** check com mola + vibração; chips de prazo coloridos (atrasada/hoje); ponto de cor por categoria. Editor em ModalBottomSheet com atalhos Hoje/Amanhã/Próxima semana, hora, categorias, links e prioridade segmentada; excluir pede confirmação e tem Desfazer.
- **Microcópia:** português simples, evitar jargão técnico, sem gamificação compulsiva.
- **Widgets:** informativos, clicáveis; fundo legível, poucas linhas, controles grandes.
- **Acessibilidade:** exigir rótulos em ações, verificar TalkBack, contraste, área de toque >= 48dp, fontes grandes e rolagem.
- **Movimento:** tokens em `ui/theme/Theme.kt` (`PulsoMotion`); AnimatedContent entre abas, `animateItem` nas listas, FAB que encolhe ao rolar.
- **Limite atual:** identidade "Brasa & Tinta" aplicada e verificada no emulador (claro/escuro), mas sem teste no Moto G84, TalkBack ou fonte grande. Nome do app ainda provisório.

## Paleta "Brasa & Tinta"

- Coral `#E5533D` (marca/primária), neutros quentes ("tinta" `#231917`), verde-azulado = concluído, âmbar = hoje, erro = atrasada. Versões claro/escuro em `res/values*/colors.xml` e `ui/theme/Theme.kt`.
- Paleta da marca por padrão; Material You é opcional (Ajustes > "Cores do papel de parede", Android 12+). Tema Sistema/Claro/Escuro.
- Widget: cores `widget_*` espelhadas em `ColorProvider(day, night)` (o `ColorProvider(resId)` do Glance é API restrita).

## Marca e ícones

Linha de pulso que termina num check (`PulseMark`, animada no topo e na captura). Ícone adaptativo + monocromático (ícone temático Android 13+), splash 12+, `ic_stat_pulso` na notificação, atalho "Capturar" (`xml/shortcuts.xml`), prévia no seletor de widgets (`previewLayout` 12+ / `previewImage`).

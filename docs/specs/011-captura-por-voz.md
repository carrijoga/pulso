# SPEC 011 — Captura por voz (M1.5, WP4)

**Status:** Planejado. Independente do schema v3. ADR: [0007](../adr/0007-voz-pelo-reconhecedor-do-sistema.md).

## Objetivo

Capturar uma anotação falando, com o mesmo princípio da captura rápida: sem metadados, direto para a Entrada. A voz só **preenche o texto**; nada de interpretar datas, criar tarefas ou lembretes automaticamente.

## Abordagem técnica

- Usar o reconhecedor de fala do sistema via `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` com `ActivityResultContracts.StartActivityForResult`.
  - Extras: `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_LANGUAGE = "pt-BR"`, `EXTRA_PROMPT = "Fale sua anotação"`, `EXTRA_PREFER_OFFLINE = true` (apenas preferência; o provedor decide), `EXTRA_MAX_RESULTS = 1`.
- O app **não** pede `RECORD_AUDIO`, **não** declara `INTERNET`, não grava nem guarda áudio. O microfone é usado pelo app reconhecedor do sistema, sob as permissões dele.
- Visibilidade de pacote (API 30+): adicionar ao manifesto
  ```xml
  <queries>
      <intent><action android:name="android.speech.action.RECOGNIZE_SPEECH" /></intent>
  </queries>
  ```
- Disponibilidade: `intent.resolveActivity(packageManager) != null` (ou `queryIntentActivities`). Sem reconhecedor → o botão de microfone **não aparece** (não mostrar controle falso — `ui/AGENTS.md`).
- Encapsular em `ui/VoiceCapture.kt` (`rememberVoiceCaptureLauncher(onResult: (String) -> Unit)`, `isVoiceCaptureAvailable(context)`), reutilizado pelas telas.

## Pontos de entrada

1. **`CaptureActivity`:** ícone de microfone no campo. Resultado é inserido no campo (anexado ao texto existente com espaço) e o foco volta ao campo; o usuário revisa e toca "Salvar".
2. **Campo de captura da Entrada/Hoje:** mesmo ícone, mesmo comportamento.
3. **Atalho do launcher "Capturar por voz"** (novo `shortcut` em `res/xml/shortcuts.xml`): abre `CaptureActivity` com extra `dev.pulso.app.extra.START_VOICE = true`, que dispara o reconhecedor ao abrir. Se indisponível, abre a captura normal.
4. **Widget (opcional, P2):** botão de microfone no cabeçalho médio/grande abrindo o mesmo intent do atalho. Só se couber sem quebrar o tamanho pequeno.

## Regras

- Resultado vazio, cancelado ou erro → nenhuma mudança no campo; snackbar/toast discreto "Não entendi. Tente de novo ou digite." apenas em erro (não em cancelamento).
- Texto reconhecido passa por `TaskRules.validateCapture` no salvar, como o texto digitado (limite 500).
- **Sem salvar automaticamente** na primeira versão. (Salvar direto ao terminar de falar pode virar preferência depois, com spec própria.)
- Não logar o texto reconhecido.

## Privacidade e transparência

- Primeira vez que o usuário toca no microfone: diálogo curto, uma vez só (flag no DataStore `voiceNoticeShown`): "A transcrição é feita pelo serviço de voz do Android do seu aparelho. Dependendo do provedor, o áudio pode ser processado online. O Pulso recebe só o texto." Botões "Continuar" / "Cancelar".
- Ajustes → seção Privacidade: mesma frase, sempre visível.
- Atualizar README §Privacidade e SPEC 006 se necessário.

## Aceites

- [ ] Botão aparece só com reconhecedor disponível (testar emulador sem Google app e aparelho com).
- [ ] Texto reconhecido entra no campo, não é salvo sem toque em "Salvar".
- [ ] Atalho "Capturar por voz" inicia o reconhecedor; fallback para captura digitada.
- [ ] Manifesto sem `RECORD_AUDIO` e sem `INTERNET` (verificar `aapt dump permissions` ou merged manifest).
- [ ] Aviso de privacidade exibido uma vez e presente em Ajustes.
- [ ] Manual no Moto G84: pt-BR, offline (modo avião) — documentar o que o reconhecedor do aparelho faz offline.
- [ ] TalkBack: botão "Ditar anotação".

## Fora de escopo

`SpeechRecognizer` embutido com UI própria, reconhecimento contínuo, extração de data/prioridade da fala (pertence à IA futura, ADR 0004), gravação de notas de áudio.

# ADR 0007 — Captura por voz pelo reconhecedor do sistema

**Status:** Aceita · 2026-11-19 · ver SPEC 011

## Contexto

O M1.5 pede captura por voz. O app é offline-first, não declara `INTERNET` e a ADR 0004 exige transparência sobre envio de dados a terceiros.

## Decisão

Usar `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (UI e microfone do reconhecedor do Android), recebendo apenas o texto, que preenche o campo de captura para revisão. Sem `RECORD_AUDIO`, sem `INTERNET`, sem áudio armazenado, sem interpretação do texto. Aviso de privacidade na primeira utilização e em Ajustes, porque o provedor do aparelho (ex.: Google) pode processar o áudio online.

## Alternativas

- **`SpeechRecognizer` embutido:** UI própria e mais controle, mas exige `RECORD_AUDIO`, tratamento de erros/estados e continua dependendo do mesmo serviço do sistema.
- **Modelo on-device embarcado (Vosk/Whisper):** privacidade total, porém APK muito maior, nova dependência pesada e qualidade em pt-BR a validar. Reavaliar se o aviso de privacidade for inaceitável.
- **Não oferecer voz:** contraria o roadmap.

## Consequências

- Comportamento offline depende do aparelho (pacotes de idioma offline). Precisa teste no Moto G84.
- Em aparelhos sem reconhecedor, o recurso some (sem controle falso).
- Nenhuma dependência nova no Gradle.

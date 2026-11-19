# Desenvolvimento local e IA

## Ambiente

- Android Studio atual, JDK 17 ou 21 (testado com JBR 21; defina `JAVA_HOME` se `java` não estiver no PATH), Android SDK Platform 37 (`compileSdk = 37`).
- Gradle wrapper 9.6.0; AGP 9.5.1; Kotlin 2.2.21; KSP 2.3.6.
- Sem backend, emulador ou Moto G84 com depuração USB.
- O jar wrapper oficial 9.6.0 é baixado na primeira execução dos scripts (da tag `v9.6.0` do repositório gradle/gradle, pois `services.gradle.org` publica só o checksum) e validado com o SHA-256 oficial. Internet necessária para baixar dependências na primeira build.

## Comandos

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./scripts/check.sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Pulso:D AndroidRuntime:E
```

No Windows substitua `./gradlew` por `.\\gradlew.bat`; `scripts/check.sh` requer Bash.

## Ciclo sugerido para agentes

1. Resuma o ticket e escolha a spec específica.
2. Liste os fluxos afetados (Room, UI, widgets, notificações, backup).
3. Escreva testes de regras antes de refatorar; preserve data local.
4. Implemente a menor mudança e rode os comandos acima.
5. Faça teste manual pertinente de `docs/TESTING.md`.
6. Atualize docs quando comportamento/decisão mudar. Declare limitações.

## Substituir dados em testes

Não apague o banco real para testes manuais. Use emulador, perfil de teste ou `adb shell pm clear dev.pulso.app` SOMENTE no ambiente de desenvolvimento e após confirmar que não há dados relevantes (esse comando apaga tudo). Versões de schema necessitam migração, não `fallbackToDestructiveMigration`.

## Checklist de PR

- [ ] Issue/spec ligada
- [ ] Código preserva o fluxo da Inbox
- [ ] Cobertura de teste razoável
- [ ] Gradle build/test/lint e verificação manual, ou bloqueios declarados
- [ ] Consistência de widget e notificação
- [ ] Risco/rollback/migração explicitados
- [ ] Docs atualizados sem afirmar que futuro é implementado

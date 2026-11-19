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

## Release (GitHub Releases)

O workflow `.github/workflows/release.yml` publica um APK de release assinado quando uma tag `vMAJOR.MINOR.PATCH` é enviada. Ele roda os testes unitários, `lintRelease` e `assembleRelease`, verifica a assinatura com `apksigner` e cria a release com o APK, o `.sha256` e notas geradas a partir dos commits. Versões `0.x` saem marcadas como pré-release.

- `versionName` vem da tag (`v1.2.3` → `1.2.3`); `versionCode = MAJOR*10000 + MINOR*100 + PATCH` (minor/patch ≤ 99). Nunca reutilize nem reduza uma tag publicada: o Android recusa atualização com `versionCode` menor.
- Assinatura somente por variáveis de ambiente (`PULSO_KEYSTORE_FILE`, `PULSO_KEYSTORE_PASSWORD`, `PULSO_KEY_ALIAS`, `PULSO_KEY_PASSWORD`). Sem elas `assembleRelease` gera APK não assinado e o workflow falha.

### Configuração única da chave (feita pelo dono do repositório)

1. Gere a keystore **fora do repositório** e guarde-a com backup seguro. Perder a chave impede atualizar o app instalado; quem a obtiver pode publicar atualizações falsas.
   ```sh
   keytool -genkeypair -keystore pulso-release.jks -alias pulso -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Em *Settings → Secrets and variables → Actions*, crie: `PULSO_KEYSTORE_BASE64` (conteúdo de `base64 -w0 pulso-release.jks`; no PowerShell `[Convert]::ToBase64String([IO.File]::ReadAllBytes("pulso-release.jks"))`), `PULSO_KEYSTORE_PASSWORD`, `PULSO_KEY_ALIAS`, `PULSO_KEY_PASSWORD`.

### Publicar

```sh
git tag -a v0.2.0 -m "Pulso 0.2.0"
git push origin v0.2.0
```

### Instalar no aparelho

- APK debug e release usam o mesmo `applicationId` com assinaturas diferentes: o Android não atualiza um sobre o outro. Antes de trocar de debug para release, **exporte o backup JSON** em Ajustes, desinstale, instale o release e importe o backup. Desinstalar apaga os dados locais.
- Entre releases assinados com a mesma chave a atualização preserva os dados normalmente.

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

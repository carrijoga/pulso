# Plano de testes

## Gates automatizados

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

**Status (2026-11-19):** executados localmente no Windows com JBR 21: 15 testes unitários, lint e APK debug passando. CI configurado, ainda não executado (repositório sem remoto).

## Testes instrumentados (emulador)

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.pulso.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use `am instrument` num aparelho/emulador com dados que importam: `connectedDebugAndroidTest` desinstala o app ao final e apaga os dados. No CI (emulador limpo) o `connectedDebugAndroidTest` é usado.

**Status (2026-11-19):** 12 testes passando no emulador API 37 (`LocalTaskRepositoryTest`, `MigrationTest`, `BackupServiceTest`). Também verificado: instalar a v2 sobre um banco v1 real migra sem perda e preserva o tema escolhido.

## Manual no Moto G84 5G

1. Instalar APK debug e abrir sem internet. Capturar "Comprar ração"; fechar e reabrir; a anotação deve persistir.
2. Organizar em tarefa, selecionar categoria "Compras", prioridade alta e data; confirmar que a anotação sai da Inbox sem criar duas tarefas.
3. Editar tarefa; concluir; reabrir. Confirmar que filtros e Hoje refletem estados.
4. Adicionar widget ao launcher, testar `+` e concluir tarefa tocando uma linha. Reiniciar app e verificar dados.
5. Em Android 13+, permitir notificações via Ajustes; programar tarefa próxima e verificar lembrete *aproximado*. Repetir com permissão negada.
6. Exportar JSON; instalar em ambiente separado/emulador; importar e confirmar dados. Importar novamente sem duplicar.
7. Usar telefone offline, modo escuro, texto grande, rotação de tela, TalkBack, teclado de terceiros e economia de bateria.
8. Validar alterações de fuso horário, prazo vencido e DST histórico em testes de regra.

## Lacunas conhecidas

- Widget sem teste automatizado; precisa de teste em launcher real (3 tamanhos, toque para concluir).
- Ações da notificação (Concluir/Adiar) sem teste automatizado.
- Sem testes de UI Compose; TalkBack e fonte grande ainda não revisados.
- Nenhum teste no Moto G84 ainda.

## Checklist de regressão para features de sync futuras

Duplicados por retries, conflitos simultâneos, tombstones, merges de relógios divergentes, dados offline por semanas, quotas e autenticação expirada.

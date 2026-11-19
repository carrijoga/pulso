# ADR 0001 — Kotlin nativo e offline-first

**Status:** Aceita · 2026-11-19

## Contexto

O objetivo principal é usar captura e widgets diretamente no Android (Moto G84) com excelente integração e sem depender de navegador. Futuramente, acesso pelo PC.

## Decisão

Kotlin + Jetpack Compose/Material 3, Glance, Room/SQLite, ViewModel + repository com fluxo reativo. Sem backend, login ou SDK remoto no MVP.

## Alternativas

- .NET MAUI: familiar ao desenvolvedor, mas widgets demandariam camada Android nativa de qualquer forma.
- Java + XML: Android nativo e suportado, mas Compose exige Kotlin; não escolhido por UX e evolução.
- PWA: não atende à preferência de uso realmente integrado com home screen.

## Consequências

Aprendizado de Kotlin/ecossistema Android; liberdade para manter API ASP.NET Core mais tarde. Offline-first exige futuro mecanismo sólido de conflitos.

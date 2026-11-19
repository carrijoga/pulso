# Data-layer agent notes

Read root `AGENTS.md` first. Database changes require a version bump, explicit Room Migration and instrumented migration tests before release. Never use destructive migrations. The inbox conversion must remain atomic, UUIDs and timestamps stable, and imports additive. Keep DB calls out of UI. Backups contain sensitive cleartext; no logging their contents. Remote sync/outbox is FUTURE functionality, not a task to implement silently.

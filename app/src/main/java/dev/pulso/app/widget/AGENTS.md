# Glance widget agent notes

Read root `AGENTS.md` and `docs/specs/003-widget.md`. Glance uses separate composables, not ordinary Compose layouts. Always reuse TaskRepository as source of truth, update widget after task changes, and test taps plus resizing on a real launcher. Keep click actions explicit and avoid network dependencies.

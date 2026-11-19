#!/usr/bin/env python3
"""Small dependency-free repo hygiene check for agents and CI."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'AGENTS.md', 'MEMORY.md', 'README.md', 'docs/ARCHITECTURE.md',
    'docs/DESIGN.md', 'docs/DEVELOPMENT.md', 'docs/ROADMAP.md',
    'docs/TESTING.md', 'docs/specs/001-captura-e-inbox.md',
    'docs/specs/002-tarefas-e-lembretes.md', 'docs/specs/003-widget.md',
    'docs/specs/004-sync-futuro.md', 'docs/specs/005-ia-futura.md',
    'docs/specs/006-backup-e-privacidade.md', 'docs/specs/007-organizacao-m1.md',
    'app/src/main/AndroidManifest.xml', 'app/build.gradle.kts',
    'gradle/wrapper/gradle-wrapper.properties', 'gradlew', 'gradlew.bat',
]
missing = [name for name in REQUIRED if not (ROOT / name).is_file()]
if missing:
    raise SystemExit('Missing files: ' + ', '.join(missing))
source = (ROOT / 'app/src/main/java/dev/pulso/app').rglob('*.kt')
files = list(source)
if not files:
    raise SystemExit('No Kotlin files')
print(f'OK: {len(REQUIRED)} required files, {len(files)} Kotlin source files')

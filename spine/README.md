# spine

Shared Java library for nubstack plugins.

## Scope

- Configuration loading helpers
- Async database access (HikariCP) once introduced
- Redis messaging helpers once introduced
- MiniMessage / i18n scaffolding once introduced
- Scheduler abstractions that stay platform-agnostic at this layer

Platform-specific code belongs in plugin modules, not here.

## Build

From the repository root:

```bash
./gradlew :spine:build
```

## Status

Foundation only. Higher-level services are added when the first consumer (aegis)
needs them.

# aegis-common

Shared domain library for [aegis](../aegis/): punishments, SQL repository, Redis
broadcast, and Discord webhook notifier.

This module is not a standalone plugin. It is shaded into `aegis-paper` and
`aegis-velocity`.

## Build

```bash
./scripts/use-java.sh ./gradlew :aegis-common:build
```

## Docs

Product README, commands, and config: [../aegis/README.md](../aegis/README.md)

# aegis-paper

Paper artifact for [aegis](../aegis/): `/aegis` commands, mute enforcement in
chat, and ban checks on join.

## Install

Drop the shaded jar into each Paper `plugins/` folder. Configure JDBC and Redis
in `plugins/aegis/config.yml`.

## Build

```bash
./scripts/use-java.sh ./gradlew :aegis-paper:build
```

## Docs

Full features, commands, permissions, and config:
[../aegis/README.md](../aegis/README.md)

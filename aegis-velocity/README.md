# aegis-velocity

Velocity artifact for [aegis](../aegis/): `/aegis` commands and ban checks on
login.

## Install

Drop the shaded jar into the Velocity `plugins/` folder. Configure JDBC and
Redis in the plugin data directory `config.yml`.

## Build

```bash
./scripts/use-java.sh ./gradlew :aegis-velocity:build
```

## Docs

Full features, commands, permissions, and config:
[../aegis/README.md](../aegis/README.md)

# spine

Shared Java library for nubstack plugins. Provides YAML config loading, HikariCP
pool creation, Redis pub/sub, and MiniMessage helpers so each plugin does not
reimplement the same infrastructure.

## Features

- Dotted-path YAML validation and typed getters
- HikariCP pools from validated JDBC settings (MariaDB driver class set for
  Paper classloaders)
- Lettuce-backed Redis pub/sub bus
- MiniMessage parsing helpers

## Requirements

| Item | Notes |
| --- | --- |
| Java | 25 |
| Consumers | Plugin modules shade or depend on spine |
| JDBC drivers | Provided by each plugin, not by spine |

Platform APIs (Paper, Velocity, Fabric) stay in plugin modules.

## Modules

| Package | Responsibility |
| --- | --- |
| `dev.nubstack.spine.config` | YAML loading and dotted-path validation |
| `dev.nubstack.spine.db` | HikariCP pool creation from validated settings |
| `dev.nubstack.spine.redis` | Lettuce-backed pub/sub bus |
| `dev.nubstack.spine.text` | MiniMessage parsing helpers |

## Install

Plugins consume spine through Gradle. Operators do not install spine as a
standalone jar; it is bundled into plugin artifacts that need it.

## Configuration

Spine itself has no `config.yml`. Each plugin maps its own YAML keys into
`DatabaseSettings` / Redis settings via spine helpers.

## Commands

None. Spine is a library.

## Permissions

None.

## Dependency pins

See root `gradle.properties` for HikariCP, Lettuce, Adventure, and SnakeYAML
versions.

## Build

```bash
./scripts/use-java.sh ./gradlew :spine:build
```

## Status

Config, database, Redis, and MiniMessage helpers are in use by SQL/Redis plugins
such as aegis, loom, ledger, and tether.

# spine

Shared Java library for nubstack plugins.

## Modules

| Package | Responsibility |
| --- | --- |
| `dev.nubstack.spine.config` | YAML loading and dotted-path validation |
| `dev.nubstack.spine.db` | HikariCP pool creation from validated settings |
| `dev.nubstack.spine.redis` | Lettuce-backed pub/sub bus |
| `dev.nubstack.spine.text` | MiniMessage parsing helpers |

Platform APIs (Paper, Velocity, Fabric) stay in plugin modules. JDBC drivers are
provided by each plugin, not by spine.

## Dependency pins

See root `gradle.properties` for HikariCP, Lettuce, Adventure, and SnakeYAML versions.

## Build

From the repository root:

```bash
./gradlew :spine:build
```

## Status

Config, database, Redis, and MiniMessage helpers are available for the first
consumer plugin (`aegis`).

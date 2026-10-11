# nubstack

Production-oriented Paper, Velocity, and Fabric plugins in one monorepo.
Each plugin ships as its own jar through GitHub Releases.

**Target:** Minecraft 26.2 on Paper `STABLE`, matching Velocity and Fabric builds.
Java 25. Versions are pinned in `gradle.properties` and verified in CI.

## Shared library

| Module | Role |
| --- | --- |
| [spine](spine/) | YAML config, HikariCP pools, Redis pub/sub, MiniMessage helpers |

## Plugins

Download jars from [Releases](../../releases) after tagging.

| Plugin | Platform | Status | Description |
| --- | --- | --- | --- |
| [aegis](aegis/) | Paper + Velocity | mvp | Network bans, mutes, warnings, audit log, Discord webhooks |
| [harbor](harbor/) | Velocity | mvp | Priority queues and backend health routing |
| [loom](loom/) | Paper | mvp | Cross-server inventory sync with transfer locks |
| [spotlight](spotlight/) | Paper | mvp | Lag attribution by chunk, entity, and hopper cost |
| [gatekeep](gatekeep/) | Velocity | mvp | Connection throttling and reputation scoring |
| [ledger](ledger/) | Paper | mvp | Vault-compatible economy with a transaction ledger |
| [cull](cull/) | Paper | mvp | Per-chunk mob limits and cleanup |
| [amber](amber/) | Paper | mvp | Async world snapshots with retention |
| [tether](tether/) | Paper | mvp | Discord account linking and chat bridge |
| [cairn](cairn/) | Fabric | mvp | Server-side death chests for vanilla clients |

## Build

Gradle needs JVM 17+ to start (this repo targets Java 25). If your shell still
has Java 8 on `PATH`, wrap the build (do not run `use-java.sh` alone):

```bash
./scripts/use-java.sh ./gradlew build
```

Or permanently in the current shell:

```bash
source ./scripts/use-java.sh
./gradlew build
```

Run writing-policy checks:

```bash
./scripts/check-writing-policy.sh
```

## Local network (Docker)

Velocity + hub + minigame + Redis + MariaDB, with Mineflayer smokes:

```bash
./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml up -d
./testbed/scripts/paste-lobbies.sh
```

See [testbed/README.md](testbed/README.md).

## Releases

Tag `nubstack-vX.Y.Z` (or run the `release` workflow) to build jars and attach
them to a GitHub Release.

## License

Source code is all rights reserved. Compiled Release jars may be used on servers
you operate. See [LICENSE](LICENSE).

## Contact

Add your preferred contact method here.

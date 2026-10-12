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

Download jars from [Releases](../../releases) after tagging. Each plugin folder
has a README with features, install, config, commands, and permissions.
`aegis/` is the product docs umbrella for the `aegis-paper` / `aegis-velocity`
jars (same monorepo, not a second repository).

| Plugin | Platform | Description |
| --- | --- | --- |
| [aegis](aegis/) | Paper + Velocity | Network bans, mutes, warnings, audit log, Discord webhooks |
| [harbor](harbor/) | Velocity | Priority queues and backend health routing |
| [loom](loom/) | Paper | Cross-server inventory sync with transfer locks |
| [spotlight](spotlight/) | Paper | Lag attribution by chunk, entity, and hopper cost |
| [gatekeep](gatekeep/) | Velocity | Connection throttling by address |
| [ledger](ledger/) | Paper | Vault-compatible economy with a transaction ledger |
| [cull](cull/) | Paper | Per-chunk mob limits and cleanup |
| [amber](amber/) | Paper | Async world snapshots with retention |
| [tether](tether/) | Paper | Discord account linking and chat bridge |
| [cairn](cairn/) | Fabric | Server-side death graves with transferable keys |

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

Velocity + Paper hub (+ optional minigame) + Redis + MariaDB + docs site, with
Mineflayer smokes:

```bash
./scripts/use-java.sh ./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml --profile minigame up -d
```

- Join `localhost:25565`
- Command guide: `http://localhost:8080`
- What we have built so far: [docs/PROGRESS.md](docs/PROGRESS.md)
- Full topology, env map, and commands: [docs/LOCAL-NETWORK.md](docs/LOCAL-NETWORK.md)
- Shorter ops notes: [testbed/README.md](testbed/README.md)

## Releases

Merging to `main` builds every module jar and attaches them to a GitHub Release
(`nubstack-vX.Y.Z` from `gradle.properties`, or `nubstack-vX.Y.Z.<run>` if that
tag already exists). You can still push a tag such as `cairn-v0.1.0` for a single
module, or run the `release` workflow manually.

## License

Source code is all rights reserved. Compiled Release jars may be used on servers
you operate. See [LICENSE](LICENSE).

## Contact

Add your preferred contact method here.

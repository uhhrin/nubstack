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

```bash
./gradlew build
```

Run writing-policy checks:

```bash
./scripts/check-writing-policy.sh
```

Live multi-plugin testing on one server comes after these MVPs.

## License

Source code is all rights reserved. Compiled Release jars may be used on servers
you operate. See [LICENSE](LICENSE).

## Contact

Add your preferred contact method here.

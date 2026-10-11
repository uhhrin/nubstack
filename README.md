# nubstack

Production-oriented Paper, Velocity, and Fabric plugins in one monorepo.
Each plugin ships as its own jar through GitHub Releases.

**Target:** Minecraft 26.2 on Paper `STABLE`, matching Velocity and Fabric builds.
Java 21. Versions are pinned in `gradle.properties` and verified in CI.

## Shared library

| Module | Role |
| --- | --- |
| [spine](spine/) | Config helpers, async database access, Redis messaging, i18n scaffolding |

## Plugins

Built one at a time. Download jars from [Releases](../../releases).

| Plugin | Platform | Status | Description |
| --- | --- | --- | --- |
| aegis | Paper + Velocity | planned | Network bans, mutes, warnings, audit log, Discord webhooks |
| harbor | Velocity | planned | Priority queues and backend health routing |
| loom | Paper | planned | Cross-server inventory and stats with locking |
| spotlight | Paper | planned | Lag attribution by chunk, entity, and hopper cost |
| gatekeep | Velocity | planned | Connection throttling and reputation scoring |
| ledger | Paper | planned | Vault-compatible economy with a transaction ledger |
| cull | Paper | planned | Per-chunk mob limits, stacking, and cleanup |
| amber | Paper | planned | Async world snapshots with retention and remote upload |
| tether | Paper | planned | Discord account linking, whitelist, chat bridge |
| cairn | Fabric | planned | Server-side death chests for vanilla clients |

Plugin module paths appear under `plugins/<name>/` when that plugin is started.

## Build

```bash
./gradlew build
```

Run writing-policy checks:

```bash
./scripts/check-writing-policy.sh
```

## License

Source code is all rights reserved. Compiled Release jars may be used on servers
you operate. See [LICENSE](LICENSE).

## Contact

Add your preferred contact method here.

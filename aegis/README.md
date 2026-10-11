# aegis

Network moderation for Paper and Velocity: bans, mutes, warnings, SQL audit trail,
Redis sync, and optional Discord webhooks.

## Artifacts

| Jar | Platform |
| --- | --- |
| `aegis-paper` | Paper 26.2 |
| `aegis-velocity` | Velocity 4.2 |

Both jars include `spine` and `aegis-common`.

## Build

```bash
./gradlew :aegis-paper:build :aegis-velocity:build
```

## Docs

- [PRD](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)

## Status

In development on `dev`.

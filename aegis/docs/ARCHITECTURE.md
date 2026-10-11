# Architecture: aegis

## Platforms

- Paper 26.2 (`aegis-paper`)
- Velocity 4.2 (`aegis-velocity`)
- Java 25
- Shared domain in `aegis-common` on top of `spine`

## Module layout

```
aegis-common   domain, SQL repository, Redis broadcast, webhook notifier
aegis-paper    commands, chat mute, join ban, bootstrap
aegis-velocity login ban, bootstrap
```

## Data model

`aegis_punishments` stores every ban, mute, and warn. Revocations flip `active`
to false and set `revoked_at` / `revoked_by`. History queries read all rows for
a target UUID.

## Threading

- JDBC and Redis publish run off the main/proxy thread
- Enforcement reads use an in-memory cache refreshed from SQL on boot and Redis events
- Bukkit/Velocity APIs only on their respective threads

## Persistence

- HikariCP via spine `DataSources`
- MariaDB in production; H2 for unit tests
- Schema created on startup (`CREATE TABLE IF NOT EXISTS`)

## Messaging

- Redis channel `aegis:punishment`
- Payload: compact JSON for create/revoke events
- Each node updates local cache, then enforces

## Failure modes

- Redis down: local punish still persists; sync resumes when Redis returns (best effort publish errors logged)
- SQL down: commands fail closed with an error message
- Webhook down: logged, never blocks moderation

## Shutdown and cleanup

- Close Redis bus and Hikari pool in `onDisable` / Velocity shutdown

## Test strategy

- Unit: repository and moderation service against H2
- Unit: duration parser and cache expiry
- Later: Mineflayer ban sync across two servers

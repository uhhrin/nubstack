# aegis

Network moderation for Paper and Velocity: bans, mutes, warnings, SQL audit
trail, Redis sync, and optional Discord webhooks.

This folder holds the product docs. Runtime jars are built from the Gradle
modules next to it:

| Module | Role |
| --- | --- |
| [`aegis-common`](../aegis-common/) | Shared domain, SQL repository, Redis broadcast, webhook notifier |
| [`aegis-paper`](../aegis-paper/) | Paper commands, mute chat, ban on join |
| [`aegis-velocity`](../aegis-velocity/) | Velocity login ban enforcement and the same `/aegis` commands |

There is no separate documentation repository. Design notes live here so the
Paper and Velocity jars share one PRD and architecture write-up.

## Features

- Ban, mute, and warn by player UUID (names are display-only)
- Temporary durations (`30s`, `12h`, `7d`) or permanent when duration is omitted
- Every punish and revoke writes an SQL audit row
- Redis channel keeps in-memory caches aligned across proxy and backends
- Optional Discord webhook on punishment events
- Paper blocks muted chat and kicks banned players on join
- Velocity rejects banned players on login

## Requirements

| Item | Notes |
| --- | --- |
| Platforms | Paper 26.2 and/or Velocity 4.2 |
| Java | 25 |
| SQL | MariaDB (H2 used in unit tests) |
| Redis | Required for multi-server sync |
| Library | Bundled `spine` (shaded into the jars) |

## Install

1. Build jars (from repo root):

   ```bash
   ./scripts/use-java.sh ./gradlew :aegis-paper:build :aegis-velocity:build
   ```

2. Copy `aegis-paper-*.jar` into each Paper `plugins/` folder.
3. Copy `aegis-velocity-*.jar` into the Velocity `plugins/` folder.
4. Start once to write `config.yml`, then set JDBC and Redis credentials.
5. Create the `aegis` database (or point JDBC at an existing schema). Tables are
   created on startup with `CREATE TABLE IF NOT EXISTS`.

Both jars already include `spine` and `aegis-common`.

## Configuration

Shared keys on Paper and Velocity (`plugins/aegis/config.yml`):

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `database.jdbc-url` | string | `jdbc:mariadb://localhost:3306/aegis` | JDBC URL |
| `database.username` | string | `aegis` | DB user |
| `database.password` | string | `change-me` | DB password |
| `database.pool-size` | int | `5` | Hikari pool size |
| `redis.host` | string | `127.0.0.1` | Redis host |
| `redis.port` | int | `6379` | Redis port |
| `redis.password` | string | `""` | Redis password (empty if none) |
| `discord.webhook-url` | string | `""` | Optional webhook; blank disables |
| `messages.banned` | MiniMessage | ban template | Shown when a ban blocks join/login |
| `messages.muted` | MiniMessage | mute template | Shown when muted chat is blocked |

Docker testbed overrides live under `testbed/plugin-config/` and use hostnames
`mariadb` / `redis`.

## Commands

Available on both Paper and Velocity.

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/aegis ban <player> [duration] <reason>` | `aegis.ban` (+ `aegis.use`) | op | Apply ban; kick if online |
| `/aegis unban <player>` | `aegis.unban` | op | Revoke active ban |
| `/aegis mute <player> [duration] <reason>` | `aegis.mute` | op | Apply mute; Paper blocks chat |
| `/aegis unmute <player>` | `aegis.unmute` | op | Revoke active mute |
| `/aegis warn <player> <reason>` | `aegis.warn` | op | Record a warning |
| `/aegis history <player>` | `aegis.history` | op | List punishment history |

Duration examples: `30s`, `12h`, `7d`. Omit duration for permanent ban/mute.
Warns do not take a duration.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `aegis.use` | op | Root command access |
| `aegis.ban` | op | Issue bans |
| `aegis.unban` | op | Revoke bans |
| `aegis.mute` | op | Issue mutes |
| `aegis.unmute` | op | Revoke mutes |
| `aegis.warn` | op | Issue warnings |
| `aegis.history` | op | View history |

## Examples

```text
/aegis ban Steve 7d griefing spawn
/aegis mute Alex 12h spam
/aegis warn Alex first warning for spam
/aegis history Steve
/aegis unban Steve
```

## Design docs

- [PRD](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)

## Status

MVP on `dev`. Core punish paths and unit tests exist. Not claimed as a full
staff suite (no vanish, freeze, or web admin).

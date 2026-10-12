# aegis

Network moderation for Paper and Velocity: bans, mutes, warnings, SQL audit
trail, Redis sync, and optional Discord webhooks.

**Who it is for:** network staff who need one `/aegis` surface on the proxy
(network truth) with a Paper jar that also works on a single server.

This folder holds the product docs. Runtime jars are built from the Gradle
modules next to it:

| Module | Role |
| --- | --- |
| [`aegis-common`](../aegis-common/) | Shared domain, SQL repository, Redis broadcast, webhook notifier |
| [`aegis-paper`](../aegis-paper/) | Paper commands, history GUI, mute chat, ban on join |
| [`aegis-velocity`](../aegis-velocity/) | Velocity login ban enforcement and network `/aegis` |

## Features

- Ban, mute, and warn by player UUID (names are display-only)
- Temporary durations (`10m`, `1h`, `1d`, `7d`, `30d`, `perm`) or permanent
- Permanent ban/mute asks for click confirm in chat
- Interactive history (hover reason/staff/time, suggest history, Copy ID)
- Paper history chest GUI with pager and active-only toggle
- Per-staff punish cooldown; soft `/aegis reload` (messages/webhook/aliases)
- Config-toggled aliases (`/gban`, `/gmute`, `/gunban`, `/gwarn`, `/hist`, `/checkpunish`)
- Redis channel keeps caches aligned; optional Discord webhook

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

2. **Network (recommended):** put `aegis-velocity-*.jar` on Velocity. That is
   where staff should run `/aegis` so punishments apply before backend join.
3. Optionally also put `aegis-paper-*.jar` on each Paper backend for mute chat
   enforcement and the history GUI.
4. **Single-server:** Paper jar alone works without Velocity.
5. Start once to write `config.yml`, then set JDBC and Redis credentials.

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
| `messages.banned` | MiniMessage | ban template | Ban kick/login text (`<reason>`, `<appeal>`, `<code>`) |
| `messages.muted` | MiniMessage | mute template | Legacy mute template key (chat uses interactive notify) |
| `appeal.copy-text` | string | `Appeal code: <code>` | Clipboard payload for appeal button |
| `rules-command` | string | `/rules` | Button on mute notify |
| `staff-cooldown-ms` | int | `1500` | Per-staff punish cooldown |
| `history.page-size` | int | `8` | Chat history page size |
| `aliases.gban` | bool | `true` | Enable `/gban` |
| `aliases.gmute` | bool | `true` | Enable `/gmute` |
| `aliases.gunban` | bool | `true` | Enable `/gunban` |
| `aliases.gwarn` | bool | `true` | Enable `/gwarn` |
| `aliases.hist` | bool | `true` | Enable `/hist` |
| `aliases.checkpunish` | bool | `true` | Enable `/checkpunish` |

## Commands

Primary surface: `/aegis` on **Velocity** for networks. Paper exposes the same
subs for single-server or backend mute/GUI.

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/aegis` | `aegis.use` | op | Branded help hub |
| `/aegis ban <player> [dur] <reason>` | `aegis.ban` | op | Ban; kick/disconnect with appeal copy |
| `/aegis unban <player>` | `aegis.unban` | op | Revoke active ban |
| `/aegis mute <player> [dur] <reason>` | `aegis.mute` | op | Mute; Paper blocks chat |
| `/aegis unmute <player>` | `aegis.unmute` | op | Revoke active mute |
| `/aegis warn <player> <reason>` | `aegis.warn` | op | Record a warning |
| `/aegis history <player> [page]` | `aegis.history` | op | Chat history; Paper also opens GUI |
| `/aegis check <player>` | `aegis.history` | op | Active punishments |
| `/aegis reason <id>` | `aegis.history` | op | Audit row detail + Copy ID |
| `/aegis rollback <id>` | `aegis.unban` or `aegis.unmute` | op | Revoke by audit id |
| `/aegis lookup <uuid\|name>` | `aegis.history` | op | Resolve with copy buttons |
| `/aegis reload` | `aegis.reload` | op | Reload messages/webhook/aliases |

Aliases (config-toggled): `/gban`, `/gmute`, `/gunban`, `/gwarn`, `/hist`,
`/checkpunish`.

Duration presets in tab complete: `10m`, `1h`, `1d`, `7d`, `30d`, `perm`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `aegis.use` | op | Root command access |
| `aegis.ban` | op | Issue bans |
| `aegis.unban` | op | Revoke bans / rollback bans |
| `aegis.mute` | op | Issue mutes |
| `aegis.unmute` | op | Revoke mutes |
| `aegis.warn` | op | Issue warnings |
| `aegis.history` | op | History, check, reason, lookup |
| `aegis.reload` | op | Soft reload |

## Examples

```text
/aegis ban Steve 7d griefing spawn
/aegis mute Alex 12h spam
/aegis warn Alex first warning for spam
/aegis history Steve
/aegis check Steve
/aegis reason <audit-uuid>
/aegis unban Steve
/gban Steve perm cheating
```

## Design docs

- [PRD](docs/PRD.md)
- [Architecture](docs/ARCHITECTURE.md)

## Status

Staff UX polish on `feat/aegis-polish`. Core punish paths, interactive chat,
history GUI (Paper), aliases, and unit tests. Not a full staff suite (no vanish,
freeze, IP ban, or web admin).

# PRD: aegis

## Problem

Network operators need bans, mutes, and warnings that stay consistent across
proxy and backend servers, with an auditable history.

## Users

- Operators and staff issuing punishments
- Players subject to punishments
- Proxy and backend servers enforcing rules

## Goals

- Ban, mute, and warn by player UUID
- Temporary and permanent durations
- Persist every action in SQL (audit trail)
- Sync enforcement across the network over Redis
- Optional Discord webhook notifications
- Paper enforces mutes in chat and bans on join
- Velocity enforces bans on login

## Non-goals

- Full staff toolkit (vanish, freeze, staff chat)
- Client-side anticheat
- Web admin panel
- Multi-language packs beyond MiniMessage config strings

## Success metrics

- Banned UUID cannot join Paper or Velocity while the ban is active
- Muted UUID cannot send chat on Paper while the mute is active
- Every punish/revoke writes an audit row
- Remote servers apply Redis updates without restart

## Commands and permissions (draft)

| Command | Permission | Default | Notes |
| --- | --- | --- | --- |
| `/aegis ban <player> [duration] <reason>` | `aegis.ban` | op | Duration like `7d`, `12h`, or omit for permanent |
| `/aegis unban <player>` | `aegis.unban` | op | |
| `/aegis mute <player> [duration] <reason>` | `aegis.mute` | op | |
| `/aegis unmute <player>` | `aegis.unmute` | op | |
| `/aegis warn <player> <reason>` | `aegis.warn` | op | |
| `/aegis history <player>` | `aegis.history` | op | |

## Configuration surface (draft)

- SQL JDBC settings
- Redis host/port/password
- Discord webhook URL (optional)
- Message templates (MiniMessage)

## Dependencies

- Required: spine, SQL database, Redis for multi-server sync
- Optional: Discord webhook URL

## Risks

- Clock skew affecting expiry checks (mitigate with Instant.now() per check)
- Name changes (always key by UUID; names are display-only)

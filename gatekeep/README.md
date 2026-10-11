# gatekeep

Velocity connection throttling by remote address. Limits how many login
attempts one address can make inside a sliding time window.

## Features

- Per-address attempt counter with configurable window
- Deny message via MiniMessage
- No player commands (enforcement is automatic on connect)

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Velocity 4.2 |
| Java | 25 |
| Library | Bundled `spine` |

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :gatekeep:build
   ```

2. Copy the jar into Velocity `plugins/`.
3. Tune `max-attempts` and `window-seconds` in `config.yml`.
4. Restart Velocity.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `max-attempts` | int | `5` | Allowed connects per address inside the window |
| `window-seconds` | int | `60` | Sliding window length in seconds |
| `deny-message` | MiniMessage | Too many connections… | Sent when the limit is exceeded |

Docker testbed raises `max-attempts` so Mineflayer smoke joins are not rejected.

## Commands

None. Behavior is entirely event-driven on proxy connect.

## Permissions

None.

## Examples

With defaults, a sixth connect from the same address within 60 seconds receives
the deny message and is rejected.

## Status

MVP. Rate limiting only; no long-term reputation store.

# harbor

Velocity priority queues and backend health routing. When a target server is
busy, unhealthy, full, or paused, connecting players are queued by permission
priority instead of failing silently.

Who it is for: players who need to know why a join is waiting, VIP players who
should move ahead fairly, and admins who pause or clear a backend during
maintenance.

## Features

- Per-server join queues when a backend is unavailable or paused
- Config-driven priority weights (`priority-permissions`, highest match wins)
- Health pings with timeout and hysteresis against flapping
- Max queue size with a friendly deny
- Status line with Leave/Refresh buttons, boss bar ETA while queued
- Title + sound on send-through; Join now when a target turns healthy
- Admin pause/resume/clear/kickqueue/send and reload

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Velocity 4.2 |
| Java | 25 |
| Library | Bundled `spine` |

A permissions plugin (for example LuckPerms on Velocity) is needed if you want
VIP/staff priority above the default queue order.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :harbor:build
   ```

2. Copy the jar into Velocity `plugins/`.
3. Edit `plugins/harbor/config.yml`.
4. Restart Velocity.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `default-server` | string | `survival` | Server used by `/harbor` and `/harbor status` when none is given |
| `queue-servers` | list | `[survival]` | Documented queue targets (health still scans registered servers) |
| `priority-permissions` | map | vip `10`, staff `50` | Priority weights by permission node; highest granted wins |
| `health-interval-seconds` | int | `15` | How often backends are pinged |
| `health-timeout-millis` | int | `3000` | Server list ping timeout |
| `health-fail-threshold` | int | `2` | Consecutive failed pings before marking unhealthy |
| `health-success-threshold` | int | `2` | Consecutive successful pings before marking healthy |
| `max-queue-size` | int | `200` | Max players per server queue |

## Commands

| Command | Who | Effect |
| --- | --- | --- |
| `/harbor` | anyone | Default server status + help |
| `/harbor status [server]` | anyone | Health, queue size, your position/priority, Leave/Refresh |
| `/harbor join <server>` | player | Enter queue, or connect immediately when healthy |
| `/harbor leave` | player | Leave all queues |
| `/harbor list` | anyone | Summary of servers and queues |
| `/harbor admin pause <server>` | `harbor.admin` | Stop sending; still accept into queue |
| `/harbor admin resume <server>` | `harbor.admin` | Resume sending |
| `/harbor admin clear <server>` | `harbor.admin` | Empty a queue |
| `/harbor admin kickqueue <player>` | `harbor.admin` | Remove one player from queue |
| `/harbor admin send <player> <server>` | `harbor.admin` | Bypass queue |
| `/harbor reload` | `harbor.admin` | Reload config and refresh health |

Alias: `/queue` (same dispatcher).

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `harbor.priority.vip` | false | Queue weight from config (default 10) |
| `harbor.priority.staff` | false | Queue weight from config (default 50) |
| `harbor.admin` | false (console always allowed) | Admin and reload commands |

## Examples

```text
/harbor status hub
/harbor join minigame
/harbor leave
/harbor admin pause minigame
/harbor admin send Steve minigame
```

Status line shape: `Hub . unhealthy . queue 12 . you #4 (priority 10) [Leave] [Refresh]`.

## Status

Queue ordering, health hysteresis, admin controls, and interactive status/HUD
are implemented. Not included: drain mode, rejoin memory, maintenance message
templates, queue-time metrics.

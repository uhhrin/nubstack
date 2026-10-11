# harbor

Velocity priority queues and backend health routing. When a target server is
marked busy or unhealthy, connecting players are queued by permission priority
instead of failing silently.

## Features

- Per-server join queues when a backend is unavailable
- Priority via permissions (`harbor.priority.vip`, `harbor.priority.staff`)
- Periodic backend health refresh
- `/harbor leave` and `/harbor status` for players and operators

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
3. Edit `plugins/harbor/config.yml` (or the Velocity data directory equivalent).
4. Restart Velocity.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `default-server` | string | `survival` | Server name used by `/harbor status` when none is given |
| `queue-servers` | list | `[survival]` | Documented queue targets (health still scans registered servers) |
| `priority-permissions` | map | vip `10`, staff `50` | Intended priority weights by permission node |
| `health-interval-seconds` | int | `15` | How often backend health is refreshed |

MVP note: the running code currently applies fixed priorities
`harbor.priority.vip` = 10 and `harbor.priority.staff` = 50. Keep those
permission nodes in sync with your permissions provider.

## Commands

| Command | Who | Effect |
| --- | --- | --- |
| `/harbor leave` | player | Leave the current queue (`Left queue` or `Not queued`) |
| `/harbor status [server]` | anyone | Queue size and health line for a server |

## Permissions

| Permission | Description |
| --- | --- |
| `harbor.priority.vip` | Queue priority weight 10 |
| `harbor.priority.staff` | Queue priority weight 50 |

## Examples

```text
/harbor status hub
/harbor leave
```

## Status

MVP. Queue and health paths exist. Full VIP demos need a permissions provider
on Velocity.

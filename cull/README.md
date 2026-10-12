# cull

Per-chunk entity limits and cleanup for Paper. Cancels new creature spawns when a
chunk is at its cap and periodically removes excess entities, hostiles before
animals and oldest first, while protecting pets, named mobs, villagers and more.

Who it is for: admins keeping farms and mob-heavy chunks from dragging TPS, and
staff who need to find the worst chunk fast.

## Features

- Cap per chunk (all entity types count), with per-world overrides and a global fallback
- Spawn cancel when a chunk is at or over its cap
- Cleanup removes hostiles first, then the oldest entities
- `mode`: `all`, `hostile` or `custom` with allow and deny type lists
- Protected by default (each configurable): named mobs, tamed pets, leashed
  entities, villagers, armor stands, vehicles carrying passengers
- Players are never removed
- `/cull scan` with clickable teleport, hover type breakdown and `[Copy coords]`
- Counters for cancelled spawns and culled entities (reset on restart)

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :cull:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Set `max-entities-per-chunk` and cleanup interval.
4. Restart Paper.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `max-entities-per-chunk` | int | `40` | Cap per loaded chunk. Values below 1 fall back to 40 |
| `cleanup-interval-ticks` | long | `200` | How often cleanup runs (20 ticks = 1s) |
| `mode` | string | `all` | `all`, `hostile` or `custom`. Unknown values fall back to `all` |
| `allow-types` | list | `[]` | Entity type names removable in `custom` mode |
| `deny-types` | list | item frames, glow item frames, paintings, leash knots, ender dragon, wither | Never removed or spawn-cancelled in any mode |
| `worlds` | map | `{}` | World name to cap, for example `world_nether: 60` |
| `protect.named` | boolean | `true` | Skip entities with a custom name |
| `protect.tamed` | boolean | `true` | Skip tamed pets |
| `protect.leashed` | boolean | `true` | Skip leashed entities |
| `protect.villagers` | boolean | `true` | Skip villagers and wandering traders |
| `protect.armor-stands` | boolean | `true` | Skip armor stands |
| `protect.vehicles` | boolean | `true` | Skip entities carrying passengers |

Protected entities still count toward the cap. If a chunk holds only protected
entities it stays over the cap and nothing is removed.

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/cull` | `cull.use` | op | Help, global max and seconds until the next cleanup |
| `/cull status [world]` | `cull.use` | op | Mode, counters, per-world cap and chunks over cap |
| `/cull scan [world] [limit]` | `cull.use` | op | Rank chunks by entity count (default top 10) |
| `/cull here` | `cull.use` | op | Entity count and type mix of your chunk |
| `/cull tp <rank>` | `cull.use` | op | Teleport to a chunk from the last scan |
| `/cull run` | `cull.admin` | op | Run cleanup now |
| `/cull reload` | `cull.admin` | op | Reload `config.yml` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `cull.use` | op | Help, status, scan, here, tp |
| `cull.admin` | op | `run` and `reload` |

## Examples

```text
/cull scan world 5
/cull tp 1
/cull run
```

Scan lines look like `#1 world 12,-3 57/40`. Hover shows `ZOMBIE x40, COW x17`.

## Status

Ops tool. Cleanup and scans walk all loaded chunks in a single tick, so run
`/cull run` and `/cull scan` with care on very large loaded areas. Counters are
not persisted. Simulation mode, runtime cap editing, bypass permission and staff
alerts are not shipped.

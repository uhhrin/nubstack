# cull

Per-chunk mob limits and cleanup for Paper. Cancels new creature spawns when a
chunk is already at the cap, and periodically removes the oldest excess
non-player entities.

## Features

- Configurable max entities per chunk
- Spawn cancel when over the limit
- Periodic cleanup that prefers older entities
- Players and item frames are never culled

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
| `max-entities-per-chunk` | int | `40` | Soft cap per loaded chunk |
| `cleanup-interval-ticks` | long | `200` | How often overflow cleanup runs (20 ticks = 1s) |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/cull` | `cull.use` | op | Prints the configured max per chunk |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `cull.use` | op | Status command |
| `cull.admin` | op | Reserved for admin use |

## Examples

```text
/cull
```

Expected reply: `cull max per chunk: 40` (or your configured value).

## Status

MVP. Counts all chunk entities toward the cap (not only hostile mobs). Tune
carefully on farms and high-density areas.

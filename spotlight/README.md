# spotlight

Paper lag attribution by loaded chunk. Scores chunks from entities, hoppers, and
extra weighted signals so staff can find hot spots quickly.

Who it is for: staff who need "what is lagging near me" in seconds, and admins
comparing worlds before teleporting to a hotspot.

## Features

- Budgeted scan of loaded chunks (configurable chunks per tick)
- Weighted score: entities, hoppers, minecarts, redstone tiles, item frames,
  villagers
- Clickable result lines with hover score math and `[Copy coords]`
- After-scan `[Rescan]` / `[Book view]` buttons
- `/spotlight here` action bar score for the current chunk

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |

No external database or Redis.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :spotlight:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Adjust weights in `config.yml` if needed.
4. Restart Paper.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `entity-weight` | int | `1` | Score per entity |
| `hopper-weight` | int | `8` | Score per hopper tile |
| `minecart-weight` | int | `4` | Extra score per minecart |
| `redstone-weight` | int | `2` | Extra score per redstone component tile |
| `item-frame-weight` | int | `3` | Extra score per item frame |
| `villager-weight` | int | `5` | Extra score per villager |
| `chunks-per-tick` | int | `32` | Loaded chunks scored per tick during a scan |
| `default-limit` | int | `10` | Default top-N for scan output |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/spotlight` | `spotlight.scan` | op | Help + age of last scan |
| `/spotlight scan [world] [limit]` | `spotlight.scan` | op | Rank loaded chunks |
| `/spotlight here` | `spotlight.scan` | op | Score current chunk (action bar + chat) |
| `/spotlight near [radius]` | `spotlight.scan` | op | Hot chunks near you from last scan |
| `/spotlight tp <rank>` | `spotlight.tp` | op | Teleport to a ranked result |
| `/spotlight book` | `spotlight.scan` | op | Open a book of the last scan |
| `/spotlight weights` | `spotlight.admin` | op | Show configured weights |
| `/spotlight reload` | `spotlight.admin` | op | Reload `config.yml` |

Alias: `/lagscan`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `spotlight.scan` | op | Demo-safe scans and result viewing |
| `spotlight.tp` | op | `/spotlight tp` and click-to-teleport on result lines |
| `spotlight.admin` | op | Weights and reload |

Without `spotlight.tp`, result clicks suggest `/tp` instead.

## Examples

```text
/spotlight scan
/spotlight here
/spotlight tp 1
```

Hover on a result shows math like:

```text
40 entities x1 + 6 hoppers x8 = 88
```

## Status

Ops tool for loaded-chunk snapshots. Not a continuous profiler and not a Spark
replacement. Stretch items (particles, watch loop, CSV export, baseline delta,
auto-alert) are not shipped.

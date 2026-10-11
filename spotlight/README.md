# spotlight

Paper lag attribution by loaded chunk. Scores chunks from entity count and
hopper tile entities so staff can find hot spots quickly.

## Features

- Scans all loaded chunks across worlds
- Weighted score from entities and hoppers
- Prints the top ten costly chunks

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
| `entity-weight` | int | `1` | Score added per entity in a chunk |
| `hopper-weight` | int | `8` | Score added per hopper tile entity |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/spotlight` | `spotlight.scan` (+ `spotlight.use`) | op | Lists top lag chunks with entity/hopper counts and score |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `spotlight.use` | op | Root command access |
| `spotlight.scan` | op | Run a lag scan |

## Examples

```text
/spotlight
```

Sample line:

```text
world 12,-3 entities=40 hoppers=6 score=88
```

## Status

MVP. Snapshot scan of loaded chunks only; not a continuous profiler and not a
replacement for Spark.

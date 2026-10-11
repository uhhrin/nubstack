# amber

Async world snapshots for Paper with a simple retention policy. Saves a copy of
a world folder under the plugin data directory and keeps only the newest N
snapshots.

## Features

- Snapshot a loaded world by name
- Async copy after `world.save()`
- Retention prune after each create
- List stored snapshot filenames

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |
| Disk | Enough space under the plugin data folder for retained copies |

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :amber:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Set `snapshot-dir`, `retain`, and `default-world` if needed.
4. Restart Paper.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `snapshot-dir` | string | `snapshots` | Directory under the plugin data folder |
| `retain` | int | `5` | How many snapshots to keep |
| `default-world` | string | `world` | World used when `/amber snapshot` omits a name |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/amber snapshot [world]` | `amber.admin` (+ `amber.use`) | op | Save a snapshot; prints `Snapshot saved: <file>` |
| `/amber list` | `amber.admin` (+ `amber.use`) | op | List snapshot filenames or `No snapshots` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `amber.use` | op | Root command access |
| `amber.admin` | op | Create and list snapshots |

## Examples

```text
/amber snapshot
/amber snapshot world_nether
/amber list
```

## Status

MVP. Folder copy based; not a full backup product with compression, remote
upload, or point-in-time restore tooling.

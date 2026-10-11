# loom

Cross-server player inventory sync for Paper networks. Saves inventory on quit,
restores on join, and uses a short transfer lock so two backends do not apply
the same profile at once.

## Features

- SQL-backed inventory blob per player UUID
- Async load/save off the main thread
- Transfer lock to reduce double-apply races during server switches
- `/loom` status command for operators

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 on every backend that should share inventories |
| Java | 25 |
| SQL | MariaDB (shared database across backends) |
| Library | Bundled `spine` |

Prove end-to-end with at least two Paper servers behind Velocity.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :loom:build
   ```

2. Install the same jar on each Paper backend.
3. Point every instance at the same JDBC database.
4. Restart Paper.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `database.jdbc-url` | string | `jdbc:mariadb://localhost:3306/nubstack` | JDBC URL |
| `database.username` | string | `nubstack` | DB user |
| `database.password` | string | `change-me` | DB password |
| `database.pool-size` | int | `5` | Hikari pool size |
| `lock-seconds` | int | `30` | Transfer lock TTL |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/loom` | `loom.use` | op | Reports that sync is active |

Passive behavior: on quit the inventory is saved; on join it is loaded. If the
transfer lock is held, the player is kicked with `Data sync in progress`.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `loom.use` | op | Status command |
| `loom.admin` | op | Reserved for admin use |

## Examples

```text
/loom
/server minigame
```

After switching backends, the inventory from the previous server should restore
once the join load finishes.

## Status

MVP. Inventory sync only (not ender chest, stats, or advancements). Needs two
Paper backends to verify in the local testbed (`--profile minigame`).

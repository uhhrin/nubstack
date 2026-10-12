# loom

Cross-server player inventory sync for Paper networks. A player keeps their
items when they switch backends, and a database lease stops two servers from
applying the same profile at once.

Who it is for: players who want to switch servers without thinking about their
items, support staff who need to see the last save, clear a stuck lock, or
preview a stored inventory, and network owners who want to prevent dupes during
laggy transfers.

## Features

- SQL-backed inventory blob per player UUID, written with a version counter
- Cross-server lease (`locked_by`, `locked_until`) held for the whole session
  and renewed in the background, so a quit save on one server always lands
  before another server loads
- Join wait: while another server still holds the lease the player sees a boss
  bar `Waiting for sync lock N` and the plugin retries every second instead of
  kicking at once
- Configurable failure policy when the wait runs out: `kick`, `join-empty`, or
  `keep-local`
- Action bar `Restoring inventory...` then green `Inventory restored`
- Versioned blob format (1-byte header plus Paper item bytes); blobs written by
  the first release (Bukkit object streams) are still readable
- Bad stored data never throws on the main thread: it is logged once with the
  player UUID, the player keeps their current inventory, and gets a message
- Staff tools: `/loom status`, `/loom inspect`, `/loom force-save`,
  `/loom unlock`, `/loom reload`
- Audit rows in SQL (`loom_audit`) for every save, load, and forced unlock
- Async database access; only the shutdown save is synchronous

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 on every backend that should share inventories |
| Java | 25 |
| SQL | MariaDB (shared database across backends) |
| Library | Bundled `spine` |

Every backend needs a different `server-id` and the same database. Backends
should use NTP so lease timestamps agree.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :loom:build
   ```

2. Install the same jar on each Paper backend.
3. Edit `plugins/loom/config.yml` on each backend: same database, unique
   `server-id`.
4. Restart Paper. Tables `loom_player_data` and `loom_audit` are created or
   upgraded on enable.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `database.jdbc-url` | string | `jdbc:mariadb://localhost:3306/nubstack` | JDBC URL |
| `database.username` | string | `nubstack` | DB user |
| `database.password` | string | `change-me` | DB password |
| `database.pool-size` | int | `5` | Hikari pool size |
| `server-id` | string | blank (`port-<server port>`) | Unique name of this backend, up to 64 characters. Needs a restart to change |
| `lock-seconds` | int | `30` | Lease lifetime without renewal, minimum 5. Held leases are renewed every third of this. A crashed server blocks its players for at most this long |
| `wait-seconds` | int | `10` | How long a joining player waits for the lease, minimum 1 |
| `failure-policy` | string | `kick` | `kick`, `join-empty`, or `keep-local` after the wait runs out. The last two never save that session |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/loom` | `loom.use` | everyone | Staff see help. Players are told sync runs automatically |
| `/loom help` | `loom.admin` | op | Clickable command list |
| `/loom status [player]` | `loom.admin` | op | Last save age, lease holder, stored bytes, version. Buttons: `[Copy UUID]`, `[Inspect]`, `[Unlock]` |
| `/loom inspect <player>` | `loom.admin` | op | Read-only chest preview of the stored inventory. All clicks are cancelled. The paper item shows saved-at, server, bytes, version. Chat shows `[Copy UUID]` |
| `/loom force-save [player]` | `loom.admin` | op | Save an online player now and keep the lease |
| `/loom unlock <player>` | `loom.admin` | op | Clear the sync lease and write an `unlock` audit row |
| `/loom reload` | `loom.admin` | op | Reload `lock-seconds`, `wait-seconds`, `failure-policy`. Database settings and `server-id` need a restart |

`<player>` must be online or in the server's name cache. `/loom inspect` is
player only. Without a name, `status` and `force-save` use the sender.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `loom.use` | everyone | Run `/loom` (non-staff only get the automatic sync notice) |
| `loom.admin` | op | `status`, `inspect`, `force-save`, `unlock`, `reload` |

## Examples

```text
/loom status Steve
/loom inspect Steve
/loom unlock Steve
/loom force-save
```

Audit rows for one player:

```sql
SELECT event_type, server_id, detail, created_at
FROM loom_audit WHERE player_id = '<uuid>' ORDER BY id DESC LIMIT 20;
```

## Manual verification (two backends)

1. Start two Paper backends against one MariaDB with `server-id: hub` and
   `server-id: minigame`.
2. Join `hub`, collect items, switch to `minigame` through the proxy.
3. Confirm the action bar shows `Restoring inventory...` then `Inventory restored`
   and the items match.
4. Switch back and forth quickly: a boss bar `Waiting for sync lock N` should
   appear instead of a kick, and the items must not duplicate.
5. Run `/loom status <you>` and `/loom inspect <you>` as an op.

## Status

Inventory only. Ender chest, stats, advancements, and per-group sync are not
implemented. Verified by unit tests (H2 with a fake clock); the two-server flow
above has not been run against a live network for this release.

Known limitations:

- A player who is still waiting for the lease can move and pick up items on the
  local inventory, which is then replaced when the stored one is applied.
- Sessions are saved on quit, on `/loom force-save`, and on shutdown. There is
  no periodic autosave, so a crash loses everything since join.
- If a lease is lost while a player is online (cleared with `/loom unlock` or
  expired during a long stall), the quit save still succeeds when no other
  server holds the lease. The version counter is not used to reject it.
- A stored blob that cannot be read leaves the player unsynced for that
  session. Their stored data is kept, but progress in the session is not saved.
- Lease timestamps use the JVM time zone of each backend; keep them identical.

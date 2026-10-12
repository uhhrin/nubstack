# amber

World snapshots for Paper with a boss bar, newest-first clickable lists, retention,
and a confirmed restore. Copies a world folder under the plugin data directory.

Who it is for: admins who checkpoint before a risky build or event, roll back a
grief quickly, and want to find the right snapshot without SSH.

## Features

- `world.save()`, then autosave is switched off for that world during the copy and
  switched back on afterwards, even when the copy fails
- Async copy with a boss bar `Snapshot <world>... 62%` driven by bytes copied
- `session.lock` is never copied; extra names can be excluded in config
- Refuses to start when the free space under the snapshot directory is smaller than the world
- One snapshot per world at a time; `/amber cancel` stops a running copy and removes the partial snapshot
- Optional label stored in `amber-meta.properties` next to the snapshot
- Retention prune after each snapshot, never touching a snapshot being restored
- Clickable list with hover details, `[Copy filename]`, `[Restore]`, `[Delete]`
- Restore with a safety snapshot first, confirm button, and typing the world name back
- File arguments must be a plain snapshot folder name; `..`, separators, and absolute paths are rejected

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
| `retain` | int | `5` | How many snapshots to keep; minimum 1 |
| `default-world` | string | `world` | World used when `/amber snapshot` omits a name, and where players are moved on restore |
| `exclude` | list of strings | `[]` | Extra file or folder names to skip while copying; `session.lock` is always skipped |
| `move-players-on-restore` | boolean | `true` | Move players out of the restored world to the spawn of `default-world`; when `false`, restore is refused while anyone is in that world |

Invalid values keep the previous settings on `/amber reload` and disable the plugin on startup.

## Commands

All subcommands need `amber.admin` (and `amber.use` for the root command).

| Command | Effect |
| --- | --- |
| `/amber` | Help and disk used by the snapshot directory |
| `/amber snapshot [world] [label]` | Snapshot a loaded world; label is up to 64 characters |
| `/amber list [world]` | Snapshots newest first, optionally for one world |
| `/amber info <file>` | World, label, size, time, copyable filename and path |
| `/amber restore <file> [world]` | Without `world`, shows the prompt; with the exact world name, asks for confirmation |
| `/amber delete <file>` | Delete after confirmation |
| `/amber prune` | Apply retention now |
| `/amber cancel` | Cancel running snapshots |
| `/amber reload` | Reload `config.yml`; refused while a snapshot or restore runs |
| `/amber confirm <token>` | Used by the confirm button |

Tab completion covers subcommands, loaded world names, and snapshot folder names.

## Restore

1. `/amber restore <file>` shows a prompt; type the world name back with `/amber restore <file> <world>`.
2. Click `[Confirm]`. The prompt expires after 30 seconds.
3. amber takes a safety snapshot labelled `pre-restore`, moves players out (or refuses, see `move-players-on-restore`), unloads the world, moves the current folder to a sibling `pre-restore-<timestamp>-<world>`, copies the snapshot in, and loads the world again.
4. If the copy fails, the original folder is put back and the world is loaded again.

Limits: the world must be loaded, and the server's main world cannot be unloaded while
the server runs, so it cannot be restored this way. For the main world, stop the server
and copy a snapshot folder over the world folder yourself. The `pre-restore-*` folders are
not pruned; delete them by hand when you no longer need them.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `amber.use` | op | Run the root command |
| `amber.admin` | op | Every subcommand |

## Examples

```text
/amber snapshot
/amber snapshot world_nether before-portal-rebuild
/amber list world_nether
/amber restore 20260101-120000-123-world_nether-before-portal-rebuild
/amber restore 20260101-120000-123-world_nether-before-portal-rebuild world_nether
```

## Status

Folder copy based. Verified by unit tests for the service, path handling, settings,
and the chat components. The Paper command flow (boss bar, unload, swap, reload) has
not been exercised on a live server. No compression, scheduled snapshots, snapshot
verification, remote upload, or chest menu.

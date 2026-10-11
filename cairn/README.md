# cairn

Fabric server-side death chests for vanilla clients. On player death, inventory
is placed into a temporary chest at (or above) the death position and the player
is told the grave coordinates.

## Features

- Chest grave on death with copied inventory items
- Clears transferred stacks from the player inventory
- Player message with grave coordinates
- Graves expire after 15 minutes; remaining items drop and the chest is removed

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Fabric dedicated server |
| Minecraft | 26.2 |
| Java | 25 |
| Dependencies | Fabric Loader and Fabric API (see `fabric.mod.json`) |

Not loaded by the Paper/Velocity Docker testbed.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :cairn:build
   ```

2. Install Fabric Loader + Fabric API on a dedicated server for Minecraft 26.2.
3. Copy the cairn jar into the server `mods/` folder.
4. Restart the server.

## Configuration

No config file yet. Expire time is fixed at 15 minutes (`20 * 60 * 15` ticks).
Grave expiry currently ticks through the overworld path only.

## Commands

None. Behavior is automatic on player death.

## Permissions

None (Fabric mod; no Bukkit permission nodes).

## Examples

Die in-game. You should see a message like:

```text
Your grave was placed at 12, 64, -40 (Steve)
```

Open the chest to recover items before expiry.

## Status

MVP. Overworld-oriented expiry tracking; multi-world grave expiry is a known
follow-up. Not exercised by the Compose Paper stack.

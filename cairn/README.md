# cairn

Fabric server-side death chests for vanilla clients. When a player dies, their
inventory goes into a chest (a double chest if needed) at or near the death
position, and the player gets the coordinates with a click-to-copy button.

Who it is for: players who die far from home and need to find and recover their
items without a client mod, and admins who need to locate or clean up graves.

## Features

- Inventory moves into a grave chest before vanilla drops run, so nothing is duplicated
- Double chest when the inventory needs more than 27 slots; anything beyond 54 stacks drops on the ground
- Finds air near the death position (above, below, then a small ring around it). If none exists, items drop normally
- Graves are tracked per dimension, so a Nether or End grave never touches the overworld
- Graves are saved to disk and survive restarts, with their remaining time
- Expiry drops the items, removes the chest and whispers the owner "Grave expired; items dropped"
- Owner-only access until expiry (configurable), admins can always open
- Maximum graves per player; the oldest is released (items dropped) when the limit is exceeded
- Death message with dimension and a clickable `[Copy coords]` button (`12 64 -40`); admins also get a clickable `[Tp]`
- `keepInventory` on means cairn does nothing
- Optional experience policy

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
4. Restart the server. `config/cairn.json` is created with defaults on first start.

## Configuration

`config/cairn.json` is read once at startup. Missing keys use the default. A file that is not valid JSON, or an
unknown `accessMode` or `xpPolicy`, is logged and cairn runs with defaults.

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `expireMinutes` | integer (>= 1) | `15` | Minutes until a grave expires and its items drop |
| `maxGravesPerPlayer` | integer (>= 1) | `3` | Oldest grave is released when a new one exceeds this |
| `accessMode` | `owner` or `public` | `owner` | `owner`: only the owner and admins can open or break the grave. `public`: anyone can |
| `xpPolicy` | `drop` or `keep` | `drop` | `drop`: vanilla experience orbs at the death spot. `keep`: the player respawns with their experience |
| `searchRadius` | integer (0 to 16) | `6` | Horizontal rings searched for free air when the death block is taken |

Grave state is stored in `config/cairn-graves.json`. Do not edit it while the server runs. An unreadable file is
renamed to `cairn-graves.json.corrupt` and cairn starts with no graves.

## Commands

| Command | Who | Description |
| --- | --- | --- |
| `/cairn` | everyone | Short help |
| `/cairn locate` | players | Your most recent grave: id, dimension, coordinates, time left, copy button |
| `/cairn list` | players | All your graves |
| `/cairn admin list [player]` | level 2 | All graves, or those of one owner (by name, any case) |
| `/cairn admin tp <id>` | level 2 | Teleport to a grave, across dimensions |
| `/cairn admin expire <id>` | level 2 | Expire a grave on the next tick, same as a natural expiry |
| `/cairn admin clear <id\|expired\|all>` | level 2 | `id` or `all`: remove the chest and drop its items without telling the owner. `expired`: forget graves whose chest no longer exists |

Admin commands need permission level 2 (game master). There are no Bukkit-style permission nodes.

## Permissions

| Action | Default |
| --- | --- |
| `/cairn`, `/cairn locate`, `/cairn list` | everyone |
| `/cairn admin ...` | permission level 2 |
| Open or break another player's grave in `owner` mode | permission level 2 |

## Examples

```text
Your grave was placed at 12 64 -40 in minecraft:overworld [Copy coords]
#3 12 64 -40 in minecraft:overworld, 11m left [Copy coords]
```

Admin view adds the owner and a `[Tp]` button that runs `/cairn admin tp 3`.

## Status

Unit tested (grave planning, registry, persistence, config, chat components). Not yet exercised on a live Fabric
server. Known limitations:

- Only breaking and opening are protected. Explosions, pistons and hoppers are not
- Breaking one half of a double grave ends tracking; the other half stays as an ordinary chest with its items
- Vanishing-cursed items are stored in the grave instead of being destroyed
- `xpPolicy: keep` holds the experience in memory until respawn, so a restart while a player is on the death screen loses it
- Expiry loads the grave's chunk on the server thread; graves are saved on the server thread
- `/cairn admin tp` stands one block above the chest without checking headroom
- Items are moved before any later death-cancelling mod runs, so a mod that cancels the death after cairn leaves the items in the grave
- Not done: action bar near your own grave, recovery compass, death cause in chat, metrics

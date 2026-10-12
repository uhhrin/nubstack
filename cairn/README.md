# cairn

Fabric server-side death graves for vanilla clients. When a player dies, their
inventory is stored on a world marker (a barrel; loot is virtual, not in the
block) at or near the death position. They receive a transferable **grave key**
that opens that grave. When the grave is emptied or expires, it despawns with a
short soul particle animation.

Who it is for: players who die far from home and need to find and recover their
items without a client mod, and admins who need to locate or clean up graves.

## Features

- Inventory moves into a grave before vanilla drops run, so nothing is duplicated
- Up to 54 stacks stored on the grave; anything beyond that drops on the ground
- Grave marker is a barrel (loot is virtual, not in the block; break is blocked for non-admins)
- Transferable grave key on death; only the matching key (or an admin) can open
- Finds air near the death position (above, below, then a small ring around it). If none exists, items drop normally
- Graves are tracked per dimension, so a Nether or End grave never touches the overworld
- Graves (including remaining items) are saved to disk and survive restarts
- Expiry drops the items, plays the despawn animation, and whispers the owner "Grave expired; items dropped"
- Empty graves despawn with particles and sound; matching keys are removed from online inventories
- Maximum graves per player; the oldest is released (items dropped) when the limit is exceeded
- Death message with dimension and a clickable `[Copy coords]` button (`12 64 -40`); admins also get a clickable `[Tp]`
- `keepInventory` on means cairn does nothing
- Optional experience policy
- Legacy chest graves from older cairn builds are migrated once on load

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Fabric dedicated server |
| Minecraft | 26.2 |
| Java | 25 |
| Dependencies | Fabric Loader and Fabric API (see `fabric.mod.json`) |
| Resource pack | `cairn/resourcepack/` (or `cairn-resourcepack.zip`) for custom grave/key models |

Local testbed: start the Fabric profile, then `/server cairn` from the hub:

```bash
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml --profile fabric up -d
```

A prebuilt pack is at `testbed/resourcepacks/cairn-resourcepack.zip` (source:
`cairn/resourcepack/`). The local Fabric testbed forces this pack on join via
`server.properties` (`RESOURCE_PACK` + `RESOURCE_PACK_ENFORCE`), served from the
docs nginx URL. Without the pack, clients still see a barrel marker and a trial-key item.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :cairn:build
   ```

2. Install Fabric Loader + Fabric API on a dedicated server for Minecraft 26.2.
3. Copy the cairn jar into the server `mods/` folder.
4. Copy or zip `cairn/resourcepack/` and install it as a resource pack (client or forced server pack).
5. Restart the server. `config/cairn.json` is created with defaults on first start.

## Configuration

`config/cairn.json` is read once at startup. Missing keys use the default. A file that is not valid JSON, or an
unknown `xpPolicy`, is logged and cairn runs with defaults.

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `expireMinutes` | integer (>= 1) | `15` | Minutes until a grave expires and its items drop |
| `maxGravesPerPlayer` | integer (>= 1) | `3` | Oldest grave is released when a new one exceeds this |
| `xpPolicy` | `drop` or `keep` | `drop` | `drop`: vanilla experience orbs at the death spot. `keep`: the player respawns with their experience |
| `searchRadius` | integer (0 to 16) | `6` | Horizontal rings searched for free air when the death block is taken |

`accessMode` from older configs is ignored. Access is always key-gated (admins bypass).

Grave state is stored in `config/cairn-graves.json`. Do not edit it while the server runs. An unreadable file is
renamed to `cairn-graves.json.corrupt` and cairn starts with no graves.

## Commands

| Command | Who | Effect |
| --- | --- | --- |
| `/cairn` | anyone | Help text |
| `/cairn locate` | players | Your most recent grave: id, dimension, coordinates, time left, copy button |
| `/cairn list` | players | All your graves |
| `/cairn admin list [player]` | level 2 | All graves, or those of one owner (by name, any case) |
| `/cairn admin tp <id>` | level 2 | Teleport to a grave, across dimensions |
| `/cairn admin key <id>` | level 2 | Reissue a transferable key for a grave (lost-key recovery) |
| `/cairn admin expire <id>` | level 2 | Expire a grave on the next tick, same as a natural expiry |
| `/cairn admin clear <id\|expired\|all>` | level 2 | `id` or `all`: remove the marker and drop its items without telling the owner. `expired`: forget graves whose marker no longer exists |

## Permissions

| Action | Requirement |
| --- | --- |
| Open any grave without a key | permission level 2 |
| Admin subcommands | permission level 2 (`Commands.LEVEL_GAMEMASTERS`) |

## Example

```
Your grave was placed at 12 64 -40 in minecraft:overworld [Copy coords]
```

Hold the Grave Key in main or off hand, then right-click the grave marker to open it.
Hand the key to a friend if you want them to recover your items.

## Testing and limitations

Unit tested (grave planning, registry, persistence, config, chat components, key helpers). Live Fabric
scenarios are covered separately by the testbed Mineflayer suite when present.

- If the marker block is removed, `/cairn admin clear expired` drops leftover loot and forgets the record
- Vanishing-cursed items are stored in the grave instead of being destroyed
- `xpPolicy: keep` holds the experience in memory until respawn, so a restart while a player is on the death screen loses it
- Expiry loads the grave's chunk on the server thread; graves are saved on the server thread
- `/cairn admin tp` stands one block above the grave without checking headroom
- Items are moved before any later death-cancelling mod runs, so a mod that cancels the death after cairn leaves the items in the grave
- Transferable keys mean anyone who holds a key can open that grave
- Without the resource pack, the marker is still a barrel and the key is a trial key (custom models need the pack)
- Grave keys are not placeable (use is cancelled) so the key NBT cannot be stripped by placing the item
- Not covered yet: action bar near your own grave, recovery compass, death cause in chat, metrics

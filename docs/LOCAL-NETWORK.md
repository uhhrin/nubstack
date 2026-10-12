# Local network (testbed)

This document describes the Docker Velocity stack under `testbed/`, what ships
in the monorepo, how pieces connect, and the player-facing commands for each
plugin MVP.

Live command guide (same content, HTML): after Compose is up, open
[http://localhost:8080](http://localhost:8080).

## What exists today

### Monorepo modules

| Module | Platform | Role |
| --- | --- | --- |
| `spine` | library | YAML config, HikariCP, Redis (Lettuce), MiniMessage helpers |
| `aegis-paper` / `aegis-velocity` / `aegis-common` | Paper + Velocity | Network bans, mutes, warnings, SQL audit, Redis sync |
| `harbor` | Velocity | Priority queues and backend health routing |
| `gatekeep` | Velocity | Connection rate limiting by address |
| `loom` | Paper | Cross-server inventory sync with transfer locks |
| `spotlight` | Paper | Lag attribution by chunk cost |
| `cull` | Paper | Per-chunk mob limits |
| `ledger` | Paper | Economy + ledger (`/bal`, `/pay`, `/ledger give`) |
| `amber` | Paper | Async world snapshots |
| `tether` | Paper | Discord link + chat bridge (needs bot token) |
| `cairn` | Fabric | Death graves + keys (`--profile fabric`, `/server cairn`) |

Status for all plugins above is **MVP**: commands and core paths exist; they are
not production-hardened.

### Testbed stack

| Service | Image / role | Default |
| --- | --- | --- |
| `velocity` | itzg/mc-proxy | Always on, host port `25565` |
| `hub` | Paper 26.2 | Always on, creative, plain world spawn |
| `minigame` | Paper 26.2 | Profile `minigame` only |
| `fabric` | Fabric 26.2 + cairn | Profile `fabric` only (`/server cairn`) |
| `mariadb` | MariaDB 11 | Shared SQL (`nubstack` + `aegis` DBs) |
| `redis` | Redis 7 | Punishment sync (aegis) |
| `docs` | nginx | Static guide on host port `8080` |
| `bots` | Node + Mineflayer | Profile `bots` only |

Also installed on Paper backends via Modrinth: **ViaVersion** and **ViaBackwards**
so Mineflayer (protocol data through 26.1) can join Paper 26.2.

Removed from the testbed (on purpose):

- Lobby schematics / FAWE / `paste-lobbies.sh`
- Fancy hub builds (both worlds use default generation and vanilla spawn)

## How it works

```
Minecraft client or Mineflayer
        |
        v
  Velocity :25565  (offline mode, modern forwarding)
        |
        +-- hub:25565      (Paper, plugins + Via*)
        +-- minigame:25565 (Paper, same jars; optional profile)
        |
  MariaDB (mariadb:3306)   Redis (redis:6379)
```

1. Build jars with `./scripts/use-java.sh ./gradlew build`.
2. `./testbed/scripts/sync-plugins.sh` copies jars into `testbed/plugins/{paper,velocity}`
   and stages Docker-ready configs from `testbed/plugin-config/`.
3. Compose starts the network. Paper gets Velocity secret via
   `PAPER_VELOCITY_SECRET` (must match `velocity/forwarding.secret`).
4. Plugin configs inside Docker use hostnames `mariadb` / `redis` and password
   `nubstack` (not `localhost` / `change-me`).
5. Docs site is served from `testbed/site/` on port 8080.

### Resource model

Default Compose runs **hub only** (~1.5 CPU / 1G heap) so WSL stays usable.
Start the second world when testing cross-server features:

```bash
docker compose -f testbed/docker-compose.yml --profile minigame up -d
```

MariaDB allows 150 connections; Docker plugin pools use `pool-size: 2`.

### Mineflayer smoke

`testbed/bots/smoke.mjs` joins `BOT_COUNT` offline bots (`Bot1`…), waits for
spawn, then quits. It does not exercise plugin commands.

```bash
cd testbed/bots && npm install
PROXY_HOST=127.0.0.1 PROXY_PORT=25565 BOT_COUNT=3 npm run smoke
```

Bots use `BOT_VERSION=26.1` by default. Gatekeep’s Docker config allows more
attempts so rapid joins are not rejected.

## Quick start

```bash
cp testbed/.env.example testbed/.env
./scripts/use-java.sh ./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml --profile minigame up -d
# optional: op yourself
./testbed/scripts/wait-and-op.sh thatnubarin
```

Join `localhost:25565`. Docs: `http://localhost:8080`.

Wipe corrupted volumes if needed:

```bash
docker compose -f testbed/docker-compose.yml --profile minigame down -v
```

## Plugin commands and permissions

Defaults below match `plugin.yml` / Velocity command checks. Give yourself
**op** on Paper for op-default permissions.

### aegis (Paper + Velocity)

Network moderation. Needs MariaDB + Redis in Docker.

| Command | Permission | Default | Expected effect |
| --- | --- | --- | --- |
| `/aegis ban <player> [duration] <reason>` | `aegis.ban` (+ `aegis.use`) | op | `BAN applied to <player>`; kick if online |
| `/aegis unban <player>` | `aegis.unban` | op | `Revoked` or `No active punishment` |
| `/aegis mute <player> [duration] <reason>` | `aegis.mute` | op | `MUTE applied to <player>`; chat blocked |
| `/aegis unmute <player>` | `aegis.unmute` | op | `Revoked` or `No active punishment` |
| `/aegis warn <player> <reason>` | `aegis.warn` | op | `WARN applied to <player>` |
| `/aegis history <player>` | `aegis.history` | op | History lines or `No history` |

Duration examples: `30s`, `12h`, `7d`. Omit for permanent.

### harbor (Velocity)

| Command | Permission | Expected effect |
| --- | --- | --- |
| `/harbor leave` | player | `Left queue` or `Not queued` |
| `/harbor status [server]` | anyone | Queue size and health line |

Priority permissions (config): `harbor.priority.vip` (10), `harbor.priority.staff` (50).
Docker config queues `hub` and `minigame`.

### gatekeep (Velocity)

No player command. Burst connects from one address are denied with the configured
MiniMessage (`Too many connections…` by default). Docker raises `max-attempts` to 30.

### loom (Paper)

Needs MariaDB. Test with **both** backends up.

| Command / event | Permission | Expected effect |
| --- | --- | --- |
| `/loom` | `loom.use` | `loom sync is active` |
| Quit then join other Paper | (passive) | Inventory restored; lock kick if sync in progress |

### spotlight (Paper)

| Command | Permission | Expected effect |
| --- | --- | --- |
| `/spotlight` | `spotlight.use` + `spotlight.scan` | `Top lag chunks:` plus ranked lines |

### cull (Paper)

| Command / event | Permission | Expected effect |
| --- | --- | --- |
| `/cull` | `cull.use` | `cull max per chunk: <n>` |
| Spawn over limit | (passive) | Extra spawns cancelled / culled |

### ledger (Paper)

Needs MariaDB. Soft-depends on Vault if present.

| Command | Permission | Default | Expected effect |
| --- | --- | --- | --- |
| `/bal [player]` | `ledger.use` | true | `<name> balance: <amount>` |
| `/pay <player> <amount>` | `ledger.use` | true | `Paid <amount>` or `Insufficient funds` |
| `/ledger give <player> <amount>` | `ledger.admin` | op | `Deposited <amount>` |

### amber (Paper)

| Command | Permission | Expected effect |
| --- | --- | --- |
| `/amber snapshot [world]` | `amber.use` + `amber.admin` | `Snapshot saved: <file>` |
| `/amber list` | `amber.use` + `amber.admin` | Filenames or `No snapshots` |

### tether (Paper)

Needs MariaDB. Discord features need token/channel via Docker env
(`TETHER_DISCORD_*` in `testbed/.env`, interpolated into tether `config.yml`).

| Command / flow | Permission | Expected effect |
| --- | --- | --- |
| `/tether link` | `tether.use` | `In Discord, send: !link <code>` |
| Discord `!link <code>` | (Discord) | `Linked to Minecraft account.` |
| Game / Discord chat | (passive) | Bridged when token + channel set |

Without a token the plugin still enables and logs that the Discord bridge is disabled.

### cairn (Fabric only)

Not in the Compose Paper stack. On the Fabric profile (`/server cairn`): death
places a key-gated grave marker, gives a transferable grave key on respawn, and
tells the player the coordinates. Install `testbed/resourcepacks/cairn-resourcepack.zip`
for the custom model.

### Velocity built-ins

| Command | Use |
| --- | --- |
| `/server hub` | Switch to hub |
| `/server minigame` | Switch to minigame (when profile is up) |
| `/server` | List backends |

## Config and secrets map

| Source | Contents |
| --- | --- |
| `testbed/.env` (from `.env.example`) | Ports, memory, MariaDB/RCON passwords, bot knobs, forwarding secret, tether Discord secrets |
| `testbed/velocity/forwarding.secret` | Velocity modern forwarding secret (file) |
| `testbed/plugin-config/` | Docker JDBC/Redis/harbor/gatekeep/tether overrides staged by `sync-plugins.sh` |
| `testbed/mariadb/init/` | Creates `aegis` database on first MariaDB volume |
| `testbed/config/paper-global.yml` | Copied into Paper `/data/config` (Velocity enabled); secret overridden by env |

Do **not** bind-mount files into `/data/plugins/...` (that broke jar sync with
`AccessDeniedException`). Always stage via `sync-plugins.sh`.

## Fixes landed with this testbed work

- Hikari loads the MariaDB driver from the plugin classloader (`spine` `DataSources`)
- Blank optional YAML strings no longer crash enable (`optionalString`)
- Loom table uses `LONGTEXT` for MariaDB
- Docker configs point at `mariadb` / `redis`
- Crash loop from config bind-mounts removed
- MariaDB `max-connections` raised; pool sizes lowered for two Papers
- Lobby schematic path removed in favor of plain spawn
- Mineflayer 26.1 + ViaBackwards path documented and verified for smoke joins

## Related files

- Compose: `testbed/docker-compose.yml`
- Ops helper: `testbed/scripts/wait-and-op.sh`
- Sync: `testbed/scripts/sync-plugins.sh`
- HTML guide: `testbed/site/`
- Module overview: [README.md](../README.md)

# nubstack local network

Velocity proxy with a Paper hub, Redis, MariaDB, optional second Paper backend,
optional Mineflayer bots, and a static command/permission guide.

For the full inventory of modules, how the stack works, and every plugin command,
see [docs/LOCAL-NETWORK.md](../docs/LOCAL-NETWORK.md).

Default stack runs **one** Paper server so WSL/laptops stay usable. Worlds use
default generation and vanilla spawn (no schematics / WorldEdit).

## Topology

```
players / bots -> velocity:25565
                   |- hub (Paper, always on)
                   |- minigame (Paper, --profile minigame)
                   |- cairn (Fabric, --profile fabric)  # death chests
redis + mariadb shared for plugin configs
docs -> http://localhost:8080
```

Offline mode is intentional so Mineflayer can join without Mojang accounts.
Do not expose this stack to the public internet.

## Prereqs

- Docker Compose
- Java toolchain via `./scripts/use-java.sh ./gradlew build`
- Node 22+ if you run bots on the host

## Environment

Compose reads `testbed/.env` automatically. Copy the example once:

```bash
cp testbed/.env.example testbed/.env
```

`.env` is gitignored. Defaults in compose match the example, so the stack
still starts without a local `.env`.

What `.env` actually drives today:

| Variable | Used by |
|---|---|
| `MC_VERSION`, `*_MEMORY`, `*_MEM_LIMIT`, `PAPER_CPUS`, view/sim distance | Compose resource + world knobs |
| `RCON_PASSWORD`, `MARIADB_*` | Hub, minigame, MariaDB |
| `VELOCITY_FORWARDING_SECRET` | Paper `PAPER_VELOCITY_SECRET` (must match `velocity/forwarding.secret`) |
| `TETHER_DISCORD_TOKEN`, `TETHER_DISCORD_CHANNEL_ID`, `TETHER_DISCORD_GUILD_ID` | Interpolated into tether `config.yml` via `${CFG_TETHER_DISCORD_*}` |
| `PROXY_HOST`, `PROXY_PORT`, `BOT_COUNT`, `BOT_VERSION` | Host Mineflayer smoke and suite (`bots/`) |
| `BOT_COUNT` | Compose `--profile bots` (host/port are fixed to `velocity:25577`) |

### Resource notes

- Default: hub only (~1.5 CPU / 1G heap). Add minigame only when needed.
- `PAPER_CPU_COUNT` caps HotSpot worker threads (`ActiveProcessorCount`).
- View/simulation distance default to 4; mob spawning is off.

Not injected from `.env`:

- Velocity forwarding file: `velocity/forwarding.secret`
- Docker plugin JDBC/Redis overrides under `plugin-config/` (MariaDB host
  `mariadb`, Redis host `redis`, password `nubstack`). `sync-plugins.sh` stages
  those folders next to the jars so the itzg image copies them in. Do not
  bind-mount into `/data/plugins` (that causes AccessDenied restart loops).
  MariaDB creates the `aegis` database via `mariadb/init/`.

tether Discord secrets stay in `.env`. The staged YAML uses
`${CFG_TETHER_DISCORD_*}` placeholders; Paper substitutes them when
`REPLACE_ENV_DURING_SYNC=true`.

## Quick start

```bash
cp testbed/.env.example testbed/.env
./scripts/use-java.sh ./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml up -d
```

Optional second Paper backend:

```bash
docker compose -f testbed/docker-compose.yml --profile minigame up -d
```

Optional Fabric backend for **cairn** (survival world; `/server cairn` from hub):

```bash
./testbed/scripts/sync-plugins.sh   # includes cairn jar -> plugins/fabric
docker compose -f testbed/docker-compose.yml --profile fabric up -d
```

In game: `/server cairn`, die with items, check the grave message, `/server hub` to return.
FabricProxy-Lite uses `FABRIC_PROXY_SECRET` matching `velocity/forwarding.secret`.
ViaFabric (alpha) + ViaBackwards (beta) are installed for Mineflayer 26.1; prefer a real 26.2 client for cairn.

If Paper crash-loops with `Overworld settings missing`, wipe volumes and recreate:

```bash
docker compose -f testbed/docker-compose.yml down -v
docker compose -f testbed/docker-compose.yml up -d
```

- Join Minecraft at `localhost:25565`
- Open the plugin test guide at [http://localhost:8080](http://localhost:8080)
- `/server hub` (and `/server minigame` when that profile is up)

### Mineflayer suite

Mineflayer's published protocol data currently ends at **26.1**, while Paper is
**26.2**. Hub, minigame and Fabric install ViaVersion or ViaBackwards (ViaFabric
on Fabric) so bots can join. The scripts pin `BOT_VERSION=26.1` by default.

From the host (proxy published on 25565):

```bash
cd testbed/bots
npm install
npm run smoke
npm run test:ledger
npm run test:all -- --continue
```

| Script | Backends needed | Notes |
|---|---|---|
| `smoke` | hub | Three bots join. |
| `test:ledger`, `test:spotlight`, `test:cull`, `test:amber`, `test:aegis`, `test:gatekeep` | hub | `cull`, `gatekeep` and `amber` restore what they change. |
| `test:tether` | hub | Skipped unless `TETHER_LIVE=1`; see the Discord note below. |
| `test:loom`, `test:harbor` | hub + `--profile minigame` | |
| `test:cairn` | hub + `--profile fabric` | |
| `test:all` | any | Runs the suite in order; missing backends are skipped with a hint. `-- --continue` keeps going after a failure. |

Every run writes `testbed/bots/reports/<timestamp>.json` and `latest.json`
(gitignored). On failure the bot's last chat lines are printed. Other knobs:
`PROXY_HOST`, `PROXY_PORT`, `BOT_VERSION`, `BOT_SEND_GAP_MS` (default 350, spacing
between one bot's commands because Velocity can mis-forward back-to-back sends).

Or inside Compose (smoke only):

```bash
docker compose -f testbed/docker-compose.yml --profile bots run --rm bots
```

Scenarios drive Paper through `docker compose exec <service> rcon-cli`, so run them
from a checkout that owns the stack.

### Staff permissions on Velocity

Velocity has no op list. The Velocity container installs LuckPerms (JSON storage,
seeded from `plugin-config/velocity/luckperms/`): `Bot1` is `staff`
(aegis, gatekeep, harbor admin), `Bot2` is `default`, `Bot3` is `vip`
(harbor priority 10). Backend commands still use `/op`.

```bash
./testbed/scripts/wait-and-op.sh your-ign   # waits for rcon, ops on hub/minigame/fabric
./testbed/scripts/seed-demo.sh your-ign     # ops bots, ledger set 500, LuckPerms staff for your-ign
./testbed/scripts/gatekeep-profile.sh strict  # 3 attempts per 60s; then /gatekeep reload
./testbed/scripts/gatekeep-profile.sh default
```

### Known limits

- With the Tether Discord settings filled in `.env`, `test:tether` and any chat from
  bots go through the real Discord bridge and post to your channel. Blank
  `TETHER_DISCORD_*` for quiet runs.
- On a host with full swap, a backend JVM can be killed at startup. Set
  `HUB_MEMORY=768M`, `MINIGAME_MEMORY=768M`, `FABRIC_MEMORY=768M` in `.env`.
- Bots cannot right-click a cairn grave, so key use and the resource pack are manual checks.

## Releases

GitHub Actions workflow `.github/workflows/release.yml`:

- Tag `nubstack-v0.1.0` builds every module and attaches jars to a Release
- Tag `aegis-paper-v0.1.0` builds a single module
- Or use Actions -> release -> Run workflow

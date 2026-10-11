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
| `PROXY_HOST`, `PROXY_PORT`, `BOT_COUNT` | Host Mineflayer smoke (`bots/smoke.mjs`) |
| `BOT_COUNT` | Compose `--profile bots` (host/port are fixed to `velocity:25577`) |

### Resource notes

- Default: hub only (~1.5 CPU / 1G heap). Add minigame only when needed.
- `PAPER_CPU_COUNT` caps HotSpot worker threads (`ActiveProcessorCount`).
- View/simulation distance default to 4; mob spawning is off.

Not injected from `.env`:

- Velocity forwarding file: `velocity/forwarding.secret`
- Docker plugin configs under `plugin-config/` (MariaDB host `mariadb`, Redis host
  `redis`, password `nubstack`). `sync-plugins.sh` stages those folders next to
  the jars so the itzg image copies them in. Do not bind-mount into
  `/data/plugins` (that causes AccessDenied restart loops). MariaDB creates the
  `aegis` database via `mariadb/init/`.

## Quick start

```bash
cp testbed/.env.example testbed/.env
./scripts/use-java.sh ./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml up -d
```

Optional second backend:

```bash
docker compose -f testbed/docker-compose.yml --profile minigame up -d
```

If Paper crash-loops with `Overworld settings missing`, wipe volumes and recreate:

```bash
docker compose -f testbed/docker-compose.yml down -v
docker compose -f testbed/docker-compose.yml up -d
```

- Join Minecraft at `localhost:25565`
- Open the plugin test guide at [http://localhost:8080](http://localhost:8080)
- `/server hub` (and `/server minigame` when that profile is up)

### Mineflayer smoke

Mineflayer’s published protocol data currently ends at **26.1**, while Paper is
**26.2**. Hub and minigame install ViaVersion + ViaBackwards so bots can join.
The smoke script pins `BOT_VERSION=26.1` by default.

From the host (proxy published on 25565):

```bash
cd testbed/bots
npm install
PROXY_HOST=127.0.0.1 PROXY_PORT=25565 npm run smoke
```

Or inside Compose:

```bash
docker compose -f testbed/docker-compose.yml --profile bots run --rm bots
```

## Releases

GitHub Actions workflow `.github/workflows/release.yml`:

- Tag `nubstack-v0.1.0` builds every module and attaches jars to a Release
- Tag `aegis-paper-v0.1.0` builds a single module
- Or use Actions -> release -> Run workflow

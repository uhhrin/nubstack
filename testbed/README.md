# nubstack local network

Velocity proxy with a hub Paper server, a minigame Paper server, Redis, MariaDB,
and optional Mineflayer bots.

## Topology

```
players / bots -> velocity:25565
                   |- hub (Paper)
                   |- minigame (Paper)
redis + mariadb shared for plugin configs
```

Offline mode is intentional so Mineflayer can join without Mojang accounts.
Do not expose this stack to the public internet.

## Prereqs

- Docker Compose
- Java toolchain for `./gradlew build`
- Node 22+ if you run bots on the host

## Quick start

```bash
./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml up -d
./testbed/scripts/paste-lobbies.sh
```

Join `localhost:25565`, then `/server minigame` from Velocity (or use a forced
server switch plugin later).

### Mineflayer smoke

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

## Lobbies

- Hub schematic: `schematics/hub-lobby.schem` (SpringtimeSpawn, see attribution)
- Minigame: procedural stone arena from `scripts/paste-lobbies.sh`

FAWE is installed on hub for schematic paste. Console paste support varies by
FAWE build; if paste fails, join as an op and run:

```
//schem load hub-lobby
//paste -a
```

## Releases

GitHub Actions workflow `.github/workflows/release.yml`:

- Tag `nubstack-v0.1.0` builds every module and attaches jars to a Release
- Tag `aegis-paper-v0.1.0` builds a single module
- Or use Actions -> release -> Run workflow

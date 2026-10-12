# Progress so far

Snapshot of what nubstack contains after the initial monorepo build-out through
the hardened local Docker testbed (commit era ending at `db91345` on `dev`).

This is a project status document, not a marketing page. Compatibility and
feature claims match what is in the repo today.

For day-to-day Docker ops and every plugin command, prefer
[LOCAL-NETWORK.md](LOCAL-NETWORK.md) and the live guide at `http://localhost:8080`
when Compose is running.

## Goals established

- Java monorepo of Paper, Velocity, and Fabric plugins under `dev.nubstack`
- Unique plugin names (not generic “ban plugin” branding)
- Proprietary source with binary-use rights for operators (see `LICENSE`)
- Strict writing policy: no Unicode em dash in project-authored text; no AI
  attribution in committed artifacts
- TDD for non-trivial logic; Conventional Commits; local-only agent tooling
  (`.cursor/`, `.agents/`, not published)
- Platform docs taken from the web for versions pinned in `gradle.properties`
  (Paper / Velocity / Fabric 26.2, Java 25)

## Platform pins

| Item | Value |
| --- | --- |
| Java | 25 |
| Minecraft | 26.2 |
| Paper API | `26.2.build.133-stable` |
| Velocity API | 4.2.0 |
| Fabric Loader / API / Loom | see `gradle.properties` |

Build wrapper for machines with an old default JDK:

```bash
./scripts/use-java.sh ./gradlew build
```

## Timeline of work (high level)

1. **Bootstrap** (`00120e4`): monorepo layout, Gradle, constitution/rules, license,
   writing-policy script, contributing notes.
2. **spine** (`013fc13`): shared YAML, Hikari, Redis, MiniMessage helpers + tests.
3. **aegis** (`26c9d23`): Paper + Velocity moderation MVP with SQL audit and Redis.
4. **Remaining plugin MVPs** (`045ce03`): harbor, gatekeep, loom, spotlight, cull,
   ledger, amber, tether, cairn (Fabric).
5. **CI + first testbed** (`de153a8`): GitHub Release workflow; Velocity + Paper
   Compose; Mineflayer smoke scaffold; schematics attempt.
6. **Build green** (`fedc7ab`): Gradle 9 / JUnit launcher / cairn Loom fixes so
   `./gradlew build` passes.
7. **Testbed hardening + docs** (`db91345`): plain spawns, Docker DB configs,
   ViaBackwards for bots, resource limits, HTML guide, `LOCAL-NETWORK.md`,
   spine/loom runtime fixes.

## What each module does

| Module | Status | Notes |
| --- | --- | --- |
| `spine` | library, tested | Used by SQL/Redis plugins |
| `aegis-*` | MVP | Ban/mute/warn/history; Paper + Velocity; PRD/architecture under `aegis/docs/` |
| `harbor` | MVP | Velocity queues + health |
| `gatekeep` | MVP | Velocity connect rate limit |
| `loom` | MVP | Cross-server inventory; needs two Papers to prove |
| `spotlight` | MVP | Chunk lag scan command |
| `cull` | MVP | Per-chunk mob cap |
| `ledger` | MVP | `/bal`, `/pay`, `/ledger give` |
| `amber` | MVP | World snapshot + list |
| `tether` | MVP | Discord link/bridge; idle without bot token |
| `cairn` | MVP | Fabric death graves (key + display); Fabric testbed profile |

Honest limitation: these are showcase MVPs. They are not claimed as
production-complete, fully live-verified product suites.

## Local Docker network

### Topology

```
Client / Mineflayer -> Velocity :25565
                         |- hub (Paper, always)
                         |- minigame (Paper, --profile minigame)
MariaDB + Redis shared
docs nginx :8080
```

### Notable decisions

- Offline mode so Mineflayer can join without Mojang accounts (do not expose
  publicly).
- Default Compose runs **hub only** for CPU/RAM; enable minigame for loom /
  cross-server aegis / harbor tests.
- Plain vanilla world spawn on both backends (lobby schematics and FAWE removed).
- Plugin Docker configs live in `testbed/plugin-config/` and are staged by
  `sync-plugins.sh` (bind-mounting into `/data/plugins` caused restart loops).
- Paper Velocity secret via `PAPER_VELOCITY_SECRET`; file secret remains in
  `velocity/forwarding.secret`.
- ViaVersion + ViaBackwards on Paper so Mineflayer (protocol data through 26.1)
  can join Paper 26.2.
- Resource caps: Paper ~1.5 CPU / 1G heap defaults; MariaDB `max-connections=150`;
  Docker plugin pools `pool-size: 2`.

### Operator quick path

```bash
cp testbed/.env.example testbed/.env
./scripts/use-java.sh ./gradlew build
./testbed/scripts/sync-plugins.sh
docker compose -f testbed/docker-compose.yml --profile minigame up -d
./testbed/scripts/wait-and-op.sh <ign>
```

Mineflayer smoke:

```bash
cd testbed/bots && npm install
PROXY_HOST=127.0.0.1 PROXY_PORT=25565 BOT_COUNT=3 npm run smoke
```

## Releases

`.github/workflows/release.yml` builds jars and attaches them to GitHub Releases
on version tags such as `nubstack-vX.Y.Z` or module tags like `aegis-paper-vX.Y.Z`,
or via workflow_dispatch.

## Runtime fixes discovered while testing Docker

| Problem | Fix |
| --- | --- |
| `No suitable driver` under Paper classloaders | `DataSources` sets MariaDB/H2 driver class names |
| Empty Discord/webhook strings crashed enable | `YamlConfig.optionalString` treats blank as absent |
| Loom `CLOB` invalid on MariaDB | Use `LONGTEXT` |
| Configs pointed at `localhost` / `change-me` | Docker overrides under `plugin-config/` |
| Bind-mount configs -> `AccessDenied` jar sync | Stage configs next to jars instead |
| `Too many connections` with two Papers | Raise MariaDB limit; lower pool sizes |
| Mineflayer “Outdated client! use 26.2” | ViaBackwards + bots pin `BOT_VERSION=26.1` |
| Flat world / overworld settings crash | Drop FLAT lobby approach; default worlds |

## Documentation map

| Doc | Purpose |
| --- | --- |
| [PROGRESS.md](PROGRESS.md) | This file: what was built and decided |
| [LOCAL-NETWORK.md](LOCAL-NETWORK.md) | Docker topology, env, commands, permissions |
| [PROCESS.md](PROCESS.md) | How the team/agents are expected to work |
| `testbed/site/` | Browser command guide on `:8080` |
| `aegis/README.md` + `aegis/docs/` | aegis product README, PRD, architecture |
| Each plugin folder `README.md` | Purpose, features, install, config, commands, permissions |
| Root `README.md` | Monorepo overview and plugin index |

## Still open / known gaps

- Tether Discord bridge needs a real bot token and channel id to do more than
  enable with a warning.
- Harbor priority permissions need a permissions provider (or LuckPerms) for
  full VIP/staff queue demos.
- Plugin JDBC defaults in jars still use `localhost` / `change-me` for non-Docker
  installs; Docker is covered by `plugin-config/`.
- Cairn is Fabric-only and not exercised by the Velocity/Paper Compose stack.
- Mineflayer smoke only proves join/spawn; it does not automate plugin commands.
- Many MVP paths are unit-tested; not every command has a full live integration
  suite beyond manual and smoke checks.

## Branch note

Primary integration work described here landed on `dev` (including
`feat(testbed): harden local stack and document plugin commands`).

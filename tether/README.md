# tether

Discord account linking and optional chat bridge for Paper. Players generate a
one-time code in-game, confirm it in Discord, and (when configured) messages
flow between the game and a Discord channel.

## Features

- `/tether link` issues a short-lived link code
- Discord `!link <code>` binds Discord user id to Minecraft UUID
- Optional require-link gate on pre-login
- Optional two-way chat bridge when bot token and channel id are set
- SQL store for link codes and linked accounts

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |
| SQL | MariaDB |
| Discord | Bot token, channel id, and Message Content intent for bridge/link |
| Library | Bundled `spine` and JDA |

Without a Discord token the plugin still enables and logs that the bridge is
disabled.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :tether:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Configure JDBC and Discord settings in `plugins/tether/config.yml`.
4. Invite the bot to your guild with permission to read/send in the bridge
   channel.
5. Restart Paper.

For the Docker testbed, leave secrets out of git: set `TETHER_DISCORD_TOKEN`,
`TETHER_DISCORD_CHANNEL_ID`, and `TETHER_DISCORD_GUILD_ID` in `testbed/.env`.
`testbed/plugin-config/paper/tether/config.yml` references
`${CFG_TETHER_DISCORD_*}`; the Paper image interpolates those on sync.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `database.jdbc-url` | string | `jdbc:mariadb://localhost:3306/nubstack` | JDBC URL |
| `database.username` | string | `nubstack` | DB user |
| `database.password` | string | `change-me` | DB password |
| `database.pool-size` | int | `5` | Hikari pool size |
| `discord.token` | string | `""` | Bot token; blank disables Discord features |
| `discord.channel-id` | string | `""` | Bridge / link channel snowflake |
| `discord.guild-id` | string | `""` | Reserved guild id field |
| `require-link-to-join` | boolean | `false` | Deny login when the UUID is not linked |

## Commands

| Command / flow | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/tether link` | `tether.use` / `tether.link` | op / true | Issue a code: `In Discord, send: !link <code>` |
| Discord `!link <code>` | (Discord) | n/a | Completes the link |
| Game or Discord chat | (passive) | n/a | Bridged when token and channel are set |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `tether.use` | op | Root command access |
| `tether.link` | true | Generate a link code |
| `tether.admin` | op | Reserved for admin use |

## Examples

In Minecraft:

```text
/tether link
```

In Discord (same channel as `discord.channel-id`):

```text
!link ABCD1234
```

## Status

MVP. Needs a real bot token and channel id for link confirmation and chat
bridge. Idle-with-warning is expected when those are blank.

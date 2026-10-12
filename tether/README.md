# tether

Discord account linking and optional chat bridge for Paper.

**Who it is for:** new players who want to link Discord in under 30 seconds
without asking staff, and staff who need to look up or unlink accounts.

## Features

- `/tether link` shows a link card: the code in bold gold (click to copy), `[Copy code]`,
  `[Cancel code]`, and an action bar countdown (`Link code . 4:32 left`)
- Discord `!link <code>` or slash `/link code:<code>` binds the Discord user to the
  Minecraft UUID; slash replies are ephemeral
- On success the card is replaced by `Linked as @User`, a title, and a level-up sound
- `/tether unlink` asks for a click confirmation; staff can look up, unlink, and list pending codes
- Gate modes: `off`, `soft` (link card on join until linked), `hard` (kick with a code until linked)
- Optional two-way chat bridge when a bot token and channel id are set
- Outgoing Discord messages use empty allowed mentions, escaped markdown, a bounded queue, and a 4 per second drain
- Discord text shown in game is plain text, so Discord users cannot inject formatting or click events

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |
| SQL | MariaDB |
| Discord | Bot token, channel id, and the Message Content intent for `!link` and the bridge |
| Library | Bundled `spine` and JDA |

Without a Discord token the plugin still enables, logs that Discord is disabled, and
`/tether link` tells players Discord is not configured. A `soft` or `hard` gate is
ignored (with a warning) when the token is blank so nobody is locked out.

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :tether:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Configure JDBC and Discord settings in `plugins/tether/config.yml`.
4. Invite the bot to your guild with permission to read and send in the bridge channel.
   Slash commands need the `applications.commands` scope.
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
| `discord.channel-id` | string | `""` | Bridge and `!link` channel snowflake |
| `discord.guild-id` | string | `""` | Guild for slash commands; set it so they appear instantly (global commands can take up to an hour) |
| `discord.prefer-slash-commands` | boolean | `true` | Register `/link` and `/unlink` and mention them on the card; `!link` always works in the channel |
| `discord.outbound-queue-capacity` | int | `64` | Pending Minecraft-to-Discord messages (1..1000); extras are dropped with a log line |
| `link.code-length` | int | `8` | Code length (4..16) |
| `link.ttl-seconds` | int | `300` | Code lifetime (30..3600) |
| `link.max-redeem-attempts` | int | `5` | Attempt limit stored per code (1..20) |
| `link.clipboard-payload` | string | `full` | `code` copies `ABCD1234`; `full` copies `!link ABCD1234` |
| `gate.mode` | string | `"off"` | `off`, `soft`, or `hard`. Quote `"off"`, since YAML reads bare `off` as false (the plugin tolerates it) |
| `require-link-to-join` | boolean | not in default file | Legacy key: `true` with `gate.mode` off means `hard` |

`/tether reload` re-reads everything except `database.*`, which needs a restart.
Config errors on reload keep the old settings.

## Commands

Aliases: `/link` runs `/tether link`, `/discord` runs `/tether status`.

| Command | Permission | Effect |
| --- | --- | --- |
| `/tether` | none | Help, bridge status, and whether you are linked |
| `/tether help [page]` | none | Clickable help filtered by your permissions |
| `/tether link` | `tether.link` | Show the link card (reuses a pending code) |
| `/tether link renew` | `tether.link` | Replace your code with a new one |
| `/tether cancel` | `tether.link` | Cancel your pending code |
| `/tether status` | `tether.link` | Linked tag, time since linking, `[Copy Discord id]` |
| `/tether unlink` | `tether.link` | Unlink after a `[Confirm]` / `[Cancel]` click |
| `/tether admin lookup <player\|discordId>` | `tether.admin.lookup` | Show a link with copy buttons for UUID and Discord id |
| `/tether admin unlink <player>` | `tether.admin.unlink` | Force unlink (logged to console) |
| `/tether admin codes` | `tether.admin.codes` | Pending count, owner, time left, attempts; codes are never shown |
| `/tether reload` | `tether.admin.reload` | Reload `config.yml` |

Staff lookups resolve player names from the server profile cache, so the player must have joined before.

Discord side:

| Command | Where | Effect |
| --- | --- | --- |
| `/link code:<code>` | any channel the bot can see | Link; reply is ephemeral |
| `/unlink` | any channel the bot can see | Unlink; reply is ephemeral |
| `!link <code>` | `discord.channel-id` | Link; public reply that never repeats the code |
| `!unlink` | `discord.channel-id` | Unlink |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `tether.link` | true | Link, cancel, status, unlink. Enough on its own |
| `tether.admin.lookup` | op | Look up a link |
| `tether.admin.unlink` | op | Force unlink |
| `tether.admin.codes` | op | List pending codes |
| `tether.admin.reload` | op | Reload config |
| `tether.admin.*` | op | All of the above |

## Examples

In Minecraft:

```text
/tether link
```

In Discord:

```text
/link code:ABCD1234
!link ABCD1234
```

Soft gate, nag on join until linked:

```yaml
gate:
  mode: soft
```

## Status

Unit tested: code lifecycle, settings parsing, card components, text escaping, the Discord
link handler, countdown, and command routing. Not verified against a live Discord bot or a
running server: slash command registration, the JDA shutdown wait, the action bar, and the
join nag need a manual check.

Known limitations:

- `hard` has no bypass permission: pre-login has no permission context, so ops must be linked too.
- `link.max-redeem-attempts` is stored and enforced by `LinkService.noteFailedAttempt`, but the
  Discord handlers do not call it, so there is no per-user guess limit beyond code length and TTL.
- Linking a Discord account that is already linked to another player moves the link (the reply says so).
- Not built: rewards on first link, `/tether ignorebridge`, staff bridge channel, `admin bridge`,
  alt alerts, name sync, a chest menu.

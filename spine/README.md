# spine

Shared Java library for nubstack plugins. Provides YAML config loading, HikariCP
pool creation, Redis pub/sub, MiniMessage helpers, and the interactive chat kit
so each plugin does not reimplement the same infrastructure.

Who it is for: plugin authors who want one consistent hover/copy/help/confirm
style across aegis, tether, ledger, and the rest of the portfolio.

## Features

- Dotted-path YAML validation and typed getters
- HikariCP pools from validated JDBC settings (MariaDB driver class set for
  Paper classloaders)
- Lettuce-backed Redis pub/sub bus
- MiniMessage parsing helpers
- Interactive chat kit: prefixed messages, copy/suggest/run buttons, help,
  confirmations, durations, action/boss bars
- `ConfigFiles.copyDefaultIfMissing` for safe default config installs

## Requirements

| Item | Notes |
| --- | --- |
| Java | 25 |
| Consumers | Plugin modules shade or depend on spine |
| JDBC drivers | Provided by each plugin, not by spine |

Platform APIs (Paper, Velocity, Fabric) stay in plugin modules. Spine uses
Adventure only.

## Modules

| Package | Responsibility |
| --- | --- |
| `dev.nubstack.spine.config` | YAML loading, dotted-path validation, default file copy |
| `dev.nubstack.spine.db` | HikariCP pool creation from validated settings |
| `dev.nubstack.spine.redis` | Lettuce-backed pub/sub bus |
| `dev.nubstack.spine.text` | MiniMessage + interactive chat kit |
| `dev.nubstack.spine.ui` | Action bar and boss bar progress helpers |

## Install

Plugins consume spine through Gradle. Operators do not install spine as a
standalone jar; it is bundled into plugin artifacts that need it.

## Configuration

Spine itself has no `config.yml`. Each plugin maps its own YAML keys into
`DatabaseSettings` / Redis settings via spine helpers.

### MiniMessage snippets for config authors

```text
<click:copy_to_clipboard:'ABCD1234'><hover:show_text:'Click to copy'><aqua>[Copy]</aqua></hover></click>
<click:suggest_command:'/pay Steve '><aqua>[Pay]</aqua></click>
<click:run_command:'/harbor leave'><aqua>[Leave]</aqua></click>
<hover:show_text:'Full reason here'><yellow>Banned</yellow></hover>
```

## Commands

None. Spine is a library. Plugins wire `/<root> confirm <token>` to
`ConfirmChat.resolve`.

## Permissions

None.

## For plugin authors

Public names used by wave 1 plugins:

| Helper | Entry point |
| --- | --- |
| Prefixed messages | `Messages.prefixed("tether")` |
| Copy / suggest / run | `.copyButton`, `.suggestButton`, `.runButton` |
| Hover / insert | `.hoverDetail`, `.insert` |
| Standalone copy | `ClickCopy.of(label, payload)` |
| Help | `HelpBuilder.root("aegis").entry(...).build(permPred, page, size)` |
| Confirm | `new ConfirmChat().ask(uuid, prompt, root, onYes, onNo)` |
| Durations | `Durations.parse`, `humanize`, `remaining` |
| Progress | `ActionBars.countdown`, `BossBars.progress` |
| Defaults | `ConfigFiles.copyDefaultIfMissing(dataFolder, "config.yml", supplier)` |

### Copy-paste snippets

```java
Audience audience = ...;
PrefixedMessages tether = Messages.prefixed("tether");
audience.sendMessage(Component.text()
        .append(tether.info("In Discord, send: "))
        .append(Component.space())
        .append(tether.copyButton("Copy code", "!link ABCD"))
        .build());

ConfirmChat confirms = new ConfirmChat();
ConfirmChat.Prompt prompt = confirms.ask(playerId, "Ban Steve permanently?", "aegis",
        () -> applyBan(),
        () -> audience.sendMessage(tether.warn("Cancelled")));
audience.sendMessage(prompt.message());
// In command: confirms.resolve(playerId, token, true|false);

HelpBuilder help = HelpBuilder.root("ledger")
        .entry("/bal [player]", "Show balance", "ledger.use")
        .entry("/pay <player> <amount>", "Pay a player", "ledger.use");
audience.sendMessage(help.build(audience::hasPermission, 1, 8));

ConfigFiles.copyDefaultIfMissing(dataFolder, "config.yml",
        () -> getResource("config.yml"));
```

`PrefixedMessages` methods each return a single `Component` (info line or
button). Compose with `Component.text().append(...)`.

### RedisBus

Connections open lazily on first publish/subscribe. `close()` shuts down the
Lettuce client. Automatic reconnect after a dropped connection is not wrapped
here; callers should recreate the bus or rely on Lettuce defaults and treat a
closed bus as terminal.

### Not shipped in this pass

| Item | Notes |
| --- | --- |
| Chest menu router (`Menu.builder`) | Needs Paper API; keep in a future `spine-paper` module |
| Enable-time JDBC/Redis health probe | Stretch not landed |

## Dependency pins

See root `gradle.properties` for HikariCP, Lettuce, Adventure, and SnakeYAML
versions.

## Build

```bash
./scripts/use-java.sh ./gradlew :spine:build
```

## Status

Interactive chat kit (Must + Durations/ActionBars/BossBars) is ready for wave 1
plugins. Chest UI and enable-time health probes are not included yet.

# ledger

Vault-compatible Paper economy with SQL balances and a simple transaction path
for pays and admin deposits.

## Features

- Per-player balances stored in SQL
- `/bal` and `/pay` for players
- `/ledger give` for staff deposits
- Soft-depends on Vault and registers an economy hook when Vault is present

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |
| SQL | MariaDB |
| Optional | Vault (for other plugins to use this economy) |
| Library | Bundled `spine` |

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :ledger:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Configure JDBC credentials.
4. Optionally install Vault before first enable.
5. Restart Paper.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `database.jdbc-url` | string | `jdbc:mariadb://localhost:3306/nubstack` | JDBC URL |
| `database.username` | string | `nubstack` | DB user |
| `database.password` | string | `change-me` | DB password |
| `database.pool-size` | int | `5` | Hikari pool size |
| `currency-name` | string | `Credits` | Display name for the currency |

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/bal [player]` | `ledger.use` | true | Show balance (self or named player) |
| `/pay <player> <amount>` | `ledger.use` | true | Transfer funds to another player |
| `/ledger give <player> <amount>` | `ledger.admin` | op | Deposit funds as staff |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `ledger.use` | true | Balance and pay |
| `ledger.admin` | op | Admin give |

## Examples

```text
/bal
/bal Steve
/pay Alex 25
/ledger give Steve 1000
```

## Status

MVP. Covers balance, pay, and admin give. Not a full shop or multi-currency
suite.

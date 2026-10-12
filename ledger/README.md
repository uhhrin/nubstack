# ledger

Vault-compatible Paper economy with SQL balances, an auditable transaction log,
and clickable chat feedback.

**Who it is for:** players who want to check a balance, pay friends and trust the
number; staff who need to give, take, fix mistakes and inspect history; and
other plugins that talk to the Vault economy API.

## Features

- Per-player balances stored in SQL, two decimal places, comma formatted with the currency name
- `/pay` runs as one SQL transaction (both rows locked in a fixed order), so two servers sharing one MariaDB cannot double spend
- Every balance change writes a row to `ledger_transactions` with a receipt id and a reason
- Clickable chat: hover a balance for the exact value, `[Pay]` and `[History]` buttons, confirmation buttons for large pays and for `/ledger set`, copyable receipt ids, hover text showing how much more you need when funds are short
- Receiver of a pay sees an action bar line such as `+25 Credits from Steve`
- Amounts are validated before any database work: numeric only, greater than zero, at most 2 decimals, below `max-amount`
- Paying yourself is rejected
- Player names are resolved from online players and the server cache only, never with a blocking lookup
- Soft-depends on Vault and registers an economy hook when Vault is present; hook methods return failure responses instead of throwing

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Paper 26.2 |
| Java | 25 |
| SQL | MariaDB (tests run against H2 in MySQL mode) |
| Optional | Vault (for other plugins to use this economy) |
| Library | Bundled `spine` |

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :ledger:build
   ```

2. Copy the jar into Paper `plugins/`.
3. Configure JDBC credentials in `plugins/ledger/config.yml`.
4. Optionally install Vault before first enable.
5. Restart Paper.

Tables are created on first start. A `ledger_schema` row records the schema version.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `database.jdbc-url` | string | `jdbc:mariadb://localhost:3306/nubstack` | JDBC URL |
| `database.username` | string | `nubstack` | DB user |
| `database.password` | string | `change-me` | DB password |
| `database.pool-size` | int | `5` | Hikari pool size |
| `currency-name` | string | `Credits` | Display name for the currency |
| `max-amount` | string (decimal) | `1000000.00` | Largest amount accepted by `/pay` and `/ledger give\|take\|set` |
| `confirm-above` | string (decimal) | `500.00` | `/pay` amounts at or above this ask for a click confirmation |

`/ledger reload` re-reads `currency-name`, `max-amount` and `confirm-above` for chat and
commands. Database settings, and the currency name shown to other plugins through Vault,
need a restart. A malformed value keeps the old settings and reports the error.

## Commands

| Command | Permission | Default | Effect |
| --- | --- | --- | --- |
| `/bal [player]` (aliases `/balance`, `/money`) | `ledger.use` | true | Show balance with `[Pay]` and `[History]` buttons |
| `/pay <player> <amount> [reason]` | `ledger.use` | true | Transfer funds; large amounts ask for confirmation |
| `/baltop [n]` | `ledger.use` | true | Leaderboard, default 10, at most 50 |
| `/ledger` or `/ledger help [page]` | `ledger.use` | true | Help filtered to what you may run |
| `/ledger history [player] [page]` | `ledger.use` (own), `ledger.admin` or `ledger.audit` (others) | true / op | Newest first, 8 per page |
| `/ledger receipt <id>` | `ledger.use` (own), `ledger.admin` or `ledger.audit` (any) | true / op | Show one transaction |
| `/ledger top [n]` | `ledger.use` | true | Same as `/baltop` |
| `/ledger give <player> <amount> [reason]` | `ledger.admin` | op | Deposit; works from console |
| `/ledger take <player> <amount> [reason]` | `ledger.admin` | op | Withdraw; fails if the balance is too low |
| `/ledger set <player> <amount> [reason]` | `ledger.admin` | op | Set an exact balance (zero allowed); players confirm by click, console runs it directly |
| `/ledger reload` | `ledger.admin` | op | Reload settings listed above |

`/ledger confirm <token>` and `/ledger cancel <token>` are what the chat buttons run.
Tokens expire after 30 seconds and only work for the player they were issued to.

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `ledger.use` | true | Balance, pay, own history and receipts, leaderboard |
| `ledger.audit` | op | View other players' history and any receipt, without admin rights |
| `ledger.admin` | op | Give, take, set, reload, plus everything in `ledger.audit` |

## Examples

```text
/bal
/bal Steve
/pay Alex 25
/pay Alex 25 thanks for the iron
/pay Alex 750            (asks: [Confirm pay 750 to Alex] [Cancel])
/ledger history 2
/ledger history Steve
/ledger receipt 42
/ledger give Steve 1000 event prize
/ledger set Steve 0
```

## Status

Covers balance, pay, history, receipts, leaderboard and staff give, take and set.
Not a shop or multi-currency suite.

Known limits:

- Only players online or in the server name cache can be targeted; a player who never joined is "unknown".
- `/ledger history 123` treats an all-digit argument as a page number, not a player name.
- Pending confirmations are kept in memory until used, and are lost on restart.
- Transfer atomicity relies on `SELECT ... FOR UPDATE`, which needs InnoDB on MariaDB.
- The Vault hook runs database calls on the calling thread, as the Vault API is synchronous.
- Not built: freeze, payall, request, minimum pay, history GUI.

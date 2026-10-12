# gatekeep

Velocity connection throttling by remote address. Limits how many login
attempts one address can make inside a sliding time window, with exempt ranges
and admin tools to inspect or clear counters.

Who it is for: server owners who need to absorb reconnect floods without
locking out legitimate players, and staff who must answer "why was I
throttled" with a support code.

## Features

- Per-address attempt counter with a configurable sliding window
- Stale addresses are evicted so memory stays bounded
- Exempt IPv4/IPv6 addresses and CIDR ranges (localhost by default)
- Optional Docker/LAN private ranges exemption
- IPv6 addresses in the same /64 share one counter (on by default)
- Deny screen shows a support code (for example `GK-7F2A`); hover shows
  attempts, window and retry time, click copies the code. Worded as a
  temporary throttle, not a ban
- Structured log line per denial:
  `event=gatekeep.deny ip=1.2.3.4 attempts=5 window=60s`
- Admin commands: help, status, check, clear, clear all with confirm, reload

## Requirements

| Item | Notes |
| --- | --- |
| Platform | Velocity 4.2 |
| Java | 25 |
| Library | Bundled `spine` |

## Install

1. Build:

   ```bash
   ./scripts/use-java.sh ./gradlew :gatekeep:build
   ```

2. Copy the jar into Velocity `plugins/`.
3. Tune `plugins/gatekeep/config.yml`.
4. Restart Velocity, or run `/gatekeep reload` for later edits.

## Configuration

| Key | Type | Default | Description |
| --- | --- | --- | --- |
| `max-attempts` | int | `5` | Allowed connects per address inside the window (minimum 1) |
| `window-seconds` | int | `60` | Sliding window length in seconds (minimum 1) |
| `deny-message` | MiniMessage | Too many connection attempts... | Shown on the disconnect screen; the support code is appended |
| `ipv6-group-64` | boolean | `true` | Count all addresses in one IPv6 /64 as one client |
| `exempt` | list of IP or CIDR | `127.0.0.0/8`, `::1/128` | Never throttled and never tracked |
| `exempt-docker-private` | boolean | `false` | Also exempt `172.16.0.0/12`, `192.168.0.0/16`, `10.0.0.0/8` |

An invalid `exempt` entry or limit stops startup; on `/gatekeep reload` it
keeps the previous settings and reports the error. Docker testbed raises
`max-attempts` so Mineflayer smoke joins are not rejected.

## Commands

All commands require `gatekeep.admin`. The console always has access.

| Command | Effect |
| --- | --- |
| `/gatekeep` or `/gatekeep help` | Clickable help |
| `/gatekeep status` | Totals plus the ten busiest addresses; click an address to suggest `/gatekeep clear <ip>` |
| `/gatekeep check <ip>` | Attempts, retry time and support code for one address, with a copy button |
| `/gatekeep clear <ip>` | Forget one address (an IPv6 address clears its whole /64 when grouping is on) |
| `/gatekeep clear *` | Forget every tracked address. Players get Confirm/Cancel buttons; the console must append `confirm` |
| `/gatekeep reload` | Reload `config.yml` without resetting counters |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `gatekeep.admin` | false (console always allowed) | All `/gatekeep` subcommands |

## Examples

```text
/gatekeep status
/gatekeep check 203.0.113.9
/gatekeep clear 203.0.113.9
/gatekeep clear * confirm        (console)
```

With defaults, a sixth connect from the same address within 60 seconds is
refused with the deny message and a support code. Staff can look the code up
with `/gatekeep check <ip>`.

## Status

Rate limiting with in-memory counters. Counters and reputation reset on
restart, and reputation is forgotten after ten quiet windows. Not included:
runtime `exempt add|remove`, runtime limit changes, strike escalation, soft
delay mode, Redis-backed counters, webhooks. The disconnect screen shows hover
and copy only where the client supports them; the code is always visible as
plain text.

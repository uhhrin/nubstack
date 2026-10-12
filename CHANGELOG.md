# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Conventional Commits](https://www.conventionalcommits.org/).

Seeded from `git log` on 2026-10-12. Future release notes are generated with
git-cliff (`cliff.toml`) when a version tag is pushed.

## [Unreleased]

### Added

- Interactive chat kit helpers in spine (`7ede76f`)
- Testbed loads tether Discord secrets from env (`7871711`)
- Per-plugin READMEs and clarified aegis layout (`799d3fa`)
- Hardened local Docker stack and plugin command docs (`db91345`)
- GitHub Release workflow and local Velocity testbed (`de153a8`)
- Remaining plugins: harbor, gatekeep, loom, spotlight, cull, ledger,
  amber, tether, cairn (`045ce03`)
- Network moderation for Paper and Velocity in aegis (`26c9d23`)
- spine config, database, redis, and text helpers (`013fc13`)
- Monorepo bootstrap (`00120e4`)

### Fixed

- Full Gradle 9 build green (`fedc7ab`)
